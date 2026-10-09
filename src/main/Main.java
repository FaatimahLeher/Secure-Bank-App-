
import java.io.Console;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;

/** Console front-end. Contains no business rules: it reads input, calls the service and prints results. */
public class Main {

    private static final Scanner SCANNER = new Scanner(System.in);
    private static final Console CONSOLE = System.console();

    private final UserService userService;

    private Main(UserService userService) {
        this.userService = userService;
    }

    public static void main(String[] args) {
        try {
            UserRepository repository = new UserRepository(Path.of("data"));
            new Main(new UserService(repository)).run();
        } catch (IOException | BankException e) {
            System.err.println("Fatal: could not start the application (" + e.getMessage() + ").");
            System.exit(1);
        } catch (NoSuchElementException e) {
            System.out.println("\nInput closed. Goodbye.");
        }
    }

    private void run() {
        System.out.println("=== Secure Banking Application ===");
        while (true) {
            System.out.println("\n1. Login");
            System.out.println("2. Exit");
            int choice = readMenuChoice();
            if (choice == 2) {
                System.out.println("Goodbye.");
                return;
            } else if (choice == 1) {
                login();
            } else {
                System.out.println("Invalid option selected.");
            }
        }
    }

    private void login() {
        String username = prompt("Username: ");
        char[] password = readPassword("Password: ");
        try {
            User user = userService.login(username, password);
            System.out.println("\nWelcome, " + user.getUsername() + "!");
            if (user.getRole() == Role.ADMIN) {
                adminMenu(user);
            } else {
                customerMenu(user);
            }
        } catch (BankException e) {
            System.out.println(e.getMessage());
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    // ------------------------------------------------------------------- admin

    private void adminMenu(User admin) {
        while (true) {
            System.out.println("\n--- Admin Menu ---");
            System.out.println("1. Logout");
            System.out.println("2. Create a customer account");
            System.out.println("3. View a customer's balance");
            System.out.println("4. View a customer's transactions");
            System.out.println("5. Approve checkbook requests");
            System.out.println("6. Change my password");
            switch (readMenuChoice()) {
                case 1:
                    System.out.println("You are logged out.");
                    return;
                case 2:
                    createCustomer(admin);
                    break;
                case 3:
                    viewBalance(admin, prompt("Customer username: "));
                    break;
                case 4:
                    viewTransactions(admin, prompt("Customer username: "));
                    break;
                case 5:
                    approveCheckbooks(admin);
                    break;
                case 6:
                    changePassword(admin);
                    break;
                default:
                    System.out.println("Invalid option selected.");
            }
        }
    }

    private void createCustomer(User admin) {
        String username = prompt("New username: ");
        char[] password = readPassword("New password: ");
        String contact = prompt("Contact number: ");
        String opening = prompt("Opening balance (0 for none): ");
        try {
            BigDecimal openingBalance = opening.trim().equals("0") ? BigDecimal.ZERO : Validator.parseAmount(opening);
            userService.createCustomerAccount(admin, username, password, contact, openingBalance);
            System.out.println("Customer account created successfully.");
        } catch (BankException e) {
            System.out.println(e.getMessage());
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private void approveCheckbooks(User admin) {
        try {
            List<String> pending = userService.getPendingCheckbookRequests(admin);
            if (pending.isEmpty()) {
                System.out.println("No pending checkbook requests.");
                return;
            }
            System.out.println("Pending requests: " + pending);
            String selected = prompt("Enter the username to approve: ");
            userService.approveCheckbook(admin, selected);
            System.out.println("Checkbook request approved for " + selected + ".");
        } catch (BankException e) {
            System.out.println(e.getMessage());
        }
    }

    // ---------------------------------------------------------------- customer

    private void customerMenu(User user) {
        while (true) {
            System.out.println("\n--- Customer Menu ---");
            System.out.println("1. Logout");
            System.out.println("2. View account balance");
            System.out.println("3. Deposit");
            System.out.println("4. Withdraw");
            System.out.println("5. Fund transfer");
            System.out.println("6. View transaction history");
            System.out.println("7. Raise checkbook request");
            System.out.println("8. Change my password");
            switch (readMenuChoice()) {
                case 1:
                    System.out.println("You are logged out.");
                    return;
                case 2:
                    viewBalance(user, user.getUsername());
                    break;
                case 3:
                    deposit(user);
                    break;
                case 4:
                    withdraw(user);
                    break;
                case 5:
                    transfer(user);
                    break;
                case 6:
                    viewTransactions(user, user.getUsername());
                    break;
                case 7:
                    try {
                        System.out.println(userService.requestCheckbook(user));
                    } catch (BankException e) {
                        System.out.println(e.getMessage());
                    }
                    break;
                case 8:
                    changePassword(user);
                    break;
                default:
                    System.out.println("Invalid option selected.");
            }
        }
    }

    private void deposit(User user) {
        try {
            BigDecimal amount = Validator.parseAmount(prompt("Amount to deposit: "));
            userService.deposit(user, amount);
            System.out.println("Deposit successful. New balance: " + userService.getBalance(user, user.getUsername()));
        } catch (BankException e) {
            System.out.println(e.getMessage());
        }
    }

    private void withdraw(User user) {
        try {
            BigDecimal amount = Validator.parseAmount(prompt("Amount to withdraw: "));
            userService.withdraw(user, amount);
            System.out.println("Withdrawal successful. New balance: " + userService.getBalance(user, user.getUsername()));
        } catch (BankException e) {
            System.out.println(e.getMessage());
        }
    }

    private void transfer(User user) {
        String payee = prompt("Recipient username: ");
        try {
            BigDecimal amount = Validator.parseAmount(prompt("Amount to transfer: "));
            userService.transfer(user, payee, amount);
            System.out.println("Amount transferred successfully.");
        } catch (BankException e) {
            System.out.println(e.getMessage());
        }
    }

    // ------------------------------------------------------------------ shared

    private void viewBalance(User actor, String username) {
        try {
            System.out.println("Balance for " + username + ": " + userService.getBalance(actor, username));
        } catch (BankException e) {
            System.out.println(e.getMessage());
        }
    }

    private void viewTransactions(User actor, String username) {
        try {
            List<Transaction> list = userService.getTransactions(actor, username);
            System.out.println(String.format("%-19s %-12s %-13s %12s %12s %12s  %s",
                    "Date/Time", "Account", "Type", "Amount", "Before", "After", "By/With"));
            System.out.println("-".repeat(100));
            if (list.isEmpty()) {
                System.out.println("(no transactions)");
            }
            list.forEach(System.out::println);
        } catch (BankException e) {
            System.out.println(e.getMessage());
        }
    }

    private void changePassword(User user) {
        char[] oldPw = readPassword("Current password: ");
        char[] newPw = readPassword("New password: ");
        try {
            userService.changePassword(user.getUsername(), oldPw, newPw);
            System.out.println("Password changed successfully.");
        } catch (BankException e) {
            System.out.println(e.getMessage());
        } finally {
            Arrays.fill(oldPw, '\0');
            Arrays.fill(newPw, '\0');
        }
    }

    // ------------------------------------------------------------------- input

    private static String prompt(String message) {
        System.out.print(message);
        return SCANNER.nextLine().trim();
    }

    /** Reads a menu choice; anything that is not a number becomes -1 (invalid) instead of crashing. */
    private static int readMenuChoice() {
        String line = prompt("Select an option: ");
        try {
            return Integer.parseInt(line);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Masks the password when a real console is attached; falls back to plain input (e.g. inside an IDE). */
    private static char[] readPassword(String message) {
        if (CONSOLE != null) {
            char[] pw = CONSOLE.readPassword(message);
            if (pw == null) throw new NoSuchElementException();
            return pw;
        }
        return prompt(message).toCharArray();
    }
}
