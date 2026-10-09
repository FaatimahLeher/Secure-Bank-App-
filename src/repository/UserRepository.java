
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Data-access layer. Holds the data in memory and persists it to plain text files:
 * <pre>
 *   data/users.txt        username|salt|passwordHash|contact|role|balance
 *   data/transactions.txt timestamp|account|amount|type|initialBalance|finalBalance|performedBy
 *   data/checkbook.txt    username|APPROVED or PENDING
 * </pre>
 * Everything is loaded when the repository is constructed (application start).
 */
public class UserRepository {

    private static final String SEP = "|";

    private final Path usersFile;
    private final Path transactionsFile;
    private final Path checkbookFile;

    private final Map<String, User> users = new LinkedHashMap<>();
    private final List<Transaction> transactions = new ArrayList<>();
    private final Map<String, Boolean> checkbookRequests = new LinkedHashMap<>(); // true = approved

    public UserRepository(Path dataDir) throws IOException {
        Files.createDirectories(dataDir);
        this.usersFile = dataDir.resolve("users.txt");
        this.transactionsFile = dataDir.resolve("transactions.txt");
        this.checkbookFile = dataDir.resolve("checkbook.txt");
        loadUsers();
        loadTransactions();
        loadCheckbook();
    }

    // ---------------------------------------------------------------- queries

    public boolean isEmpty() { return users.isEmpty(); }

    public User findUser(String username) { return users.get(username); }

    public boolean exists(String username) { return users.containsKey(username); }

    public Collection<User> findAllUsers() { return new ArrayList<>(users.values()); }

    public List<Transaction> findTransactions(String username) {
        return transactions.stream()
                .filter(t -> t.getAccountUsername().equals(username))
                .collect(Collectors.toList());
    }

    public Map<String, Boolean> getCheckbookRequests() { return new LinkedHashMap<>(checkbookRequests); }

    // --------------------------------------------------------------- mutations

    public void addUser(User user) { users.put(user.getUsername(), user); }

    public void removeUser(String username) { users.remove(username); }

    public void setCheckbookStatus(String username, boolean approved) { checkbookRequests.put(username, approved); }

    public void removeCheckbookRequest(String username) { checkbookRequests.remove(username); }

    public void addTransactions(List<Transaction> newTransactions) { transactions.addAll(newTransactions); }

    public void removeTransactions(List<Transaction> toRemove) { transactions.removeAll(toRemove); }

    // ------------------------------------------------------------- persistence

    public void saveUsers() throws IOException {
        List<String> lines = new ArrayList<>();
        for (User u : users.values()) {
            lines.add(String.join(SEP, u.getUsername(), u.getSalt(), u.getPasswordHash(),
                    u.getContactNumber(), u.getRole().name(), u.getAccountBalance().toPlainString()));
        }
        writeAtomically(usersFile, lines);
    }

    public void saveTransactions() throws IOException {
        List<String> lines = new ArrayList<>();
        for (Transaction t : transactions) {
            lines.add(String.join(SEP, t.getTimestamp().toString(), t.getAccountUsername(),
                    t.getAmount().toPlainString(), t.getType(), t.getInitialBalance().toPlainString(),
                    t.getFinalBalance().toPlainString(), t.getPerformedBy()));
        }
        writeAtomically(transactionsFile, lines);
    }

    public void saveCheckbook() throws IOException {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, Boolean> e : checkbookRequests.entrySet()) {
            lines.add(e.getKey() + SEP + (e.getValue() ? "APPROVED" : "PENDING"));
        }
        writeAtomically(checkbookFile, lines);
    }

    /** Writes to a temp file first and then swaps it in, so a crash can never leave a half-written data file. */
    private void writeAtomically(Path target, List<String> lines) throws IOException {
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.write(temp, lines, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    // ----------------------------------------------------------------- loading

    private void loadUsers() throws IOException {
        for (String line : readLines(usersFile)) {
            String[] p = line.split("\\|", -1);
            try {
                if (p.length != 6) throw new IllegalArgumentException("wrong field count");
                BigDecimal balance = new BigDecimal(p[5]);
                if (balance.signum() < 0) throw new IllegalArgumentException("negative balance");
                users.put(p[0], new User(p[0], p[1], p[2], p[3], Role.valueOf(p[4]), balance));
            } catch (IllegalArgumentException e) {
                System.err.println("Warning: skipped corrupt line in " + usersFile.getFileName());
            }
        }
    }

    private void loadTransactions() throws IOException {
        for (String line : readLines(transactionsFile)) {
            String[] p = line.split("\\|", -1);
            try {
                if (p.length != 7) throw new IllegalArgumentException("wrong field count");
                transactions.add(new Transaction(LocalDateTime.parse(p[0]), p[1], new BigDecimal(p[2]), p[3],
                        new BigDecimal(p[4]), new BigDecimal(p[5]), p[6]));
            } catch (IllegalArgumentException | java.time.format.DateTimeParseException e) {
                System.err.println("Warning: skipped corrupt line in " + transactionsFile.getFileName());
            }
        }
    }

    private void loadCheckbook() throws IOException {
        for (String line : readLines(checkbookFile)) {
            String[] p = line.split("\\|", -1);
            if (p.length == 2 && (p[1].equals("APPROVED") || p[1].equals("PENDING"))) {
                checkbookRequests.put(p[0], p[1].equals("APPROVED"));
            } else {
                System.err.println("Warning: skipped corrupt line in " + checkbookFile.getFileName());
            }
        }
    }

    private List<String> readLines(Path file) throws IOException {
        if (!Files.exists(file)) {
            return new ArrayList<>();
        }
        return Files.readAllLines(file, StandardCharsets.UTF_8).stream()
                .filter(l -> !l.isBlank())
                .collect(Collectors.toList());
    }
}
