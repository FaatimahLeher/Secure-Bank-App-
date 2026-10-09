
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/** Business logic: authentication, accounts, transactions, checkbook requests. */
public class UserService {

    private static final int MAX_FAILED_ATTEMPTS = 3;
    private static final long LOCKOUT_MILLIS = 60_000;
    private static final String GENERIC_LOGIN_ERROR = "Invalid username or password.";
    /** Hash checked when the username does not exist, so response time doesn't reveal valid usernames. */
    private static final String DUMMY_SALT = PasswordUtil.generateSalt();
    private static final String DUMMY_HASH = PasswordUtil.hash("dummy-Password1!".toCharArray(), DUMMY_SALT);

    /** First-run administrator password (documented in the README; change it after first login). */
    private static final String DEFAULT_ADMIN_PASSWORD = "Admin@1234";

    private final UserRepository repository;
    private final Map<String, Integer> failedAttempts = new HashMap<>();
    private final Map<String, Long> lockedUntil = new HashMap<>();

    public UserService(UserRepository repository) throws BankException {
        this.repository = repository;
        if (repository.isEmpty()) {
            createAdminAccount();
        }
    }

    private void createAdminAccount() throws BankException {
        String salt = PasswordUtil.generateSalt();
        String hash = PasswordUtil.hash(DEFAULT_ADMIN_PASSWORD.toCharArray(), salt);
        repository.addUser(new User("admin", salt, hash, "0000000000", Role.ADMIN, BigDecimal.ZERO.setScale(2)));
        persistUsers();
    }

    // ----------------------------------------------------------- authentication

    public synchronized User login(String username, char[] password) throws BankException {
        String key = username == null ? "" : username;
        Long until = lockedUntil.get(key);
        if (until != null) {
            if (System.currentTimeMillis() < until) {
                long secs = (until - System.currentTimeMillis() + 999) / 1000;
                throw new BankException("Too many failed attempts. Try again in " + secs + " seconds.");
            }
            lockedUntil.remove(key);
            failedAttempts.remove(key);
        }

        User user = repository.findUser(key);
        boolean ok;
        if (user == null) {
            PasswordUtil.verify(password, DUMMY_SALT, DUMMY_HASH);
            ok = false;
        } else {
            ok = PasswordUtil.verify(password, user.getSalt(), user.getPasswordHash());
        }

        if (!ok) {
            int count = failedAttempts.merge(key, 1, Integer::sum);
            if (count >= MAX_FAILED_ATTEMPTS) {
                lockedUntil.put(key, System.currentTimeMillis() + LOCKOUT_MILLIS);
            }
            throw new BankException(GENERIC_LOGIN_ERROR);
        }
        failedAttempts.remove(key);
        return user;
    }

    public synchronized void changePassword(String username, char[] oldPassword, char[] newPassword)
            throws BankException {
        User user = requireUser(username);
        if (!PasswordUtil.verify(oldPassword, user.getSalt(), user.getPasswordHash())) {
            throw new BankException("Current password is incorrect.");
        }
        Validator.validatePassword(newPassword);
        String oldSalt = user.getSalt();
        String oldHash = user.getPasswordHash();
        String salt = PasswordUtil.generateSalt();
        user.setCredentials(salt, PasswordUtil.hash(newPassword, salt));
        try {
            repository.saveUsers();
        } catch (IOException e) {
            user.setCredentials(oldSalt, oldHash);
            throw new BankException("Could not save data. Password was not changed.", e);
        }
    }

    // ------------------------------------------------------ account management

    /** Admin operation: opens a new customer account (with an optional opening deposit). */
    public synchronized void createCustomerAccount(User actor, String username, char[] password,
                                                   String contactNumber, BigDecimal openingBalance)
            throws BankException {
        requireAdmin(actor);
        Validator.validateUsername(username);
        Validator.validatePassword(password);
        Validator.validateContact(contactNumber);
        if (openingBalance.signum() < 0 || openingBalance.compareTo(Validator.MAX_AMOUNT) > 0) {
            throw new BankException("Opening balance must be between 0 and " + Validator.MAX_AMOUNT.toPlainString() + ".");
        }
        if (repository.exists(username)) {
            throw new BankException("That username is already taken.");
        }
        String salt = PasswordUtil.generateSalt();
        User user = new User(username, salt, PasswordUtil.hash(password, salt), contactNumber,
                Role.USER, openingBalance.setScale(2));
        repository.addUser(user);

        List<Transaction> opening = new ArrayList<>();
        if (openingBalance.signum() > 0) {
            opening.add(new Transaction(LocalDateTime.now(), username, openingBalance.setScale(2), "DEPOSIT",
                    BigDecimal.ZERO.setScale(2), openingBalance.setScale(2), actor.getUsername()));
            repository.addTransactions(opening);
        }
        try {
            repository.saveUsers();
            if (!opening.isEmpty()) repository.saveTransactions();
        } catch (IOException e) {
            repository.removeUser(username);
            repository.removeTransactions(opening);
            throw new BankException("Could not save data. Account was not created.", e);
        }
    }

    /** Customers may only see their own balance; admins may see anyone's. */
    public synchronized BigDecimal getBalance(User actor, String username) throws BankException {
        authorizeAccess(actor, username);
        return requireUser(username).getAccountBalance();
    }

    public synchronized List<Transaction> getTransactions(User actor, String username) throws BankException {
        authorizeAccess(actor, username);
        requireUser(username);
        return repository.findTransactions(username);
    }

    // ------------------------------------------------------------ transactions

    public synchronized void deposit(User actor, BigDecimal amount) throws BankException {
        requireCustomer(actor);
        User user = requireUser(actor.getUsername());
        BigDecimal before = user.getAccountBalance();
        BigDecimal after = before.add(amount);
        if (after.compareTo(new BigDecimal("1000000000.00")) > 0) {
            throw new BankException("Deposit would exceed the maximum account balance.");
        }
        applyChanges(List.of(user), List.of(after),
                List.of(new Transaction(LocalDateTime.now(), user.getUsername(), amount, "DEPOSIT",
                        before, after, user.getUsername())));
    }

    public synchronized void withdraw(User actor, BigDecimal amount) throws BankException {
        requireCustomer(actor);
        User user = requireUser(actor.getUsername());
        BigDecimal before = user.getAccountBalance();
        if (before.compareTo(amount) < 0) {
            throw new BankException("Insufficient funds. Your balance is " + before.toPlainString() + ".");
        }
        BigDecimal after = before.subtract(amount);
        applyChanges(List.of(user), List.of(after),
                List.of(new Transaction(LocalDateTime.now(), user.getUsername(), amount, "WITHDRAWAL",
                        before, after, user.getUsername())));
    }

    public synchronized void transfer(User actor, String payeeUsername, BigDecimal amount) throws BankException {
        requireCustomer(actor);
        if (actor.getUsername().equals(payeeUsername)) {
            throw new BankException("You cannot transfer money to your own account.");
        }
        User payer = requireUser(actor.getUsername());
        User payee = repository.findUser(payeeUsername);
        if (payee == null || payee.getRole() != Role.USER) {
            throw new BankException("Recipient account not found.");
        }
        BigDecimal payerBefore = payer.getAccountBalance();
        if (payerBefore.compareTo(amount) < 0) {
            throw new BankException("Insufficient funds. Your balance is " + payerBefore.toPlainString() + ".");
        }
        BigDecimal payerAfter = payerBefore.subtract(amount);
        BigDecimal payeeBefore = payee.getAccountBalance();
        BigDecimal payeeAfter = payeeBefore.add(amount);
        LocalDateTime now = LocalDateTime.now();
        applyChanges(List.of(payer, payee), List.of(payerAfter, payeeAfter), List.of(
                new Transaction(now, payer.getUsername(), amount, "TRANSFER_OUT", payerBefore, payerAfter,
                        payee.getUsername()),
                new Transaction(now, payee.getUsername(), amount, "TRANSFER_IN", payeeBefore, payeeAfter,
                        payer.getUsername())));
    }

    /**
     * Applies new balances and records transactions as ONE unit: if saving to disk fails,
     * balances and history are rolled back so memory and files never disagree.
     */
    private void applyChanges(List<User> accounts, List<BigDecimal> newBalances, List<Transaction> newTransactions)
            throws BankException {
        List<BigDecimal> oldBalances = new ArrayList<>();
        for (User u : accounts) oldBalances.add(u.getAccountBalance());
        for (int i = 0; i < accounts.size(); i++) accounts.get(i).setAccountBalance(newBalances.get(i));
        repository.addTransactions(newTransactions);
        try {
            repository.saveUsers();
            repository.saveTransactions();
        } catch (IOException e) {
            for (int i = 0; i < accounts.size(); i++) accounts.get(i).setAccountBalance(oldBalances.get(i));
            repository.removeTransactions(newTransactions);
            throw new BankException("Could not save data. The transaction was cancelled.", e);
        }
    }

    // ---------------------------------------------------------------- checkbook

    public synchronized String requestCheckbook(User actor) throws BankException {
        requireCustomer(actor);
        Boolean status = repository.getCheckbookRequests().get(actor.getUsername());
        if (status != null) {
            return status ? "Your checkbook request has already been approved."
                          : "You already have a pending checkbook request.";
        }
        repository.setCheckbookStatus(actor.getUsername(), false);
        try {
            repository.saveCheckbook();
        } catch (IOException e) {
            repository.removeCheckbookRequest(actor.getUsername());
            throw new BankException("Could not save data. Request was not submitted.", e);
        }
        return "Checkbook request submitted and awaiting approval.";
    }

    public synchronized List<String> getPendingCheckbookRequests(User actor) throws BankException {
        requireAdmin(actor);
        List<String> pending = new ArrayList<>();
        for (Map.Entry<String, Boolean> e : repository.getCheckbookRequests().entrySet()) {
            if (!e.getValue()) pending.add(e.getKey());
        }
        return pending;
    }

    public synchronized void approveCheckbook(User actor, String username) throws BankException {
        requireAdmin(actor);
        Boolean status = repository.getCheckbookRequests().get(username);
        if (status == null || status) {
            throw new BankException("No pending checkbook request for that user.");
        }
        repository.setCheckbookStatus(username, true);
        try {
            repository.saveCheckbook();
        } catch (IOException e) {
            repository.setCheckbookStatus(username, false);
            throw new BankException("Could not save data. Request was not approved.", e);
        }
    }

    // ------------------------------------------------------------------ helpers

    private User requireUser(String username) throws BankException {
        User user = repository.findUser(username);
        if (user == null) throw new BankException("Account not found.");
        return user;
    }

    private void requireAdmin(User actor) throws BankException {
        if (actor == null || actor.getRole() != Role.ADMIN) {
            throw new BankException("You are not authorised to perform this action.");
        }
    }

    private void requireCustomer(User actor) throws BankException {
        if (actor == null || actor.getRole() != Role.USER) {
            throw new BankException("Only customers can perform this action.");
        }
    }

    private void authorizeAccess(User actor, String username) throws BankException {
        if (actor == null) throw new BankException("You are not authorised to perform this action.");
        if (actor.getRole() != Role.ADMIN && !actor.getUsername().equals(username)) {
            throw new BankException("You are not authorised to view that account.");
        }
    }

    private void persistUsers() throws BankException {
        try {
            repository.saveUsers();
        } catch (IOException e) {
            throw new BankException("Could not save data.", e);
        }
    }
}
