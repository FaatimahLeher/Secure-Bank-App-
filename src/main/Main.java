package main;

import entity.Transaction;
import entity.User;
import java.io.Console;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;
import repository.UserRepository;
import service.UserService;
import util.BankException;
import util.Validator;

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
                error("Invalid option. Please enter one of the numbers shown in the menu.");
            }
        }
    }

    private void login() {
        String username = prompt("Username: ");
        char[] password = readPassword("Password: ");
        if (username.isEmpty() || password.length == 0) {
            error("Username and password are both required.");
            return;
        }
        try {
            User user = userService.login(username, password);
            System.out.println("\nWelcome, " + user.getUsername() + "!");
            if (user.getRole() == entity.Role.ADMIN) {
                adminMenu(user);
            } else {
                customerMenu(user);
            }
        } catch (BankException e) {
            error(e.getMessage());
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
                    error("Invalid option. Please enter one of the numbers shown in the menu.");
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
            success("Customer account created successfully.");
        } catch (BankException e) {
            error(e.getMessage());
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private void approveCheckbooks(User admin) {
        try {
            List<String> pending = userService.getPendingCheckbookRequests(admin);
            if (pending.isEmpty()) {
                info("There are no pending checkbook requests.");
                return;
            }
            System.out.println("Pending requests: " + pending);
            String selected = prompt("Enter the username to approve: ");
            userService.approveCheckbook(admin, selected);
            success("Checkbook request approved for " + selected + ".");
        } catch (BankException e) {
            error(e.getMessage());
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
                        info(userService.requestCheckbook(user));
                        System.out.println("Checkbook request submitted. Please wait for admin approval.");
                    } catch (BankException e) {
                        error(e.getMessage());
                    }
                    break;
                case 8:
                    changePassword(user);
                    break;
                default:
                    error("Invalid option. Please enter one of the numbers shown in the menu.");
            }
        }
    }

    private void deposit(User user) {
        try {
            BigDecimal amount = Validator.parseAmount(prompt("Amount to deposit: "));
            userService.deposit(user, amount);
            success("Deposit successful. New balance: " + userService.getBalance(user, user.getUsername()));
        } catch (BankException e) {
            error(e.getMessage());
        }
    }

    private void withdraw(User user) {
        try {
            BigDecimal amount = Validator.parseAmount(prompt("Amount to withdraw: "));
            userService.withdraw(user, amount);
            success("Withdrawal successful. New balance: " + userService.getBalance(user, user.getUsername()));
        } catch (BankException e) {
            error(e.getMessage());
        }
    }

    private void transfer(User user) {
        String payee = prompt("Recipient username: ");
        try {
            BigDecimal amount = Validator.parseAmount(prompt("Amount to transfer: "));
            userService.transfer(user, payee, amount);
            success("Amount transferred successfully.");
        } catch (BankException e) {
            error(e.getMessage());
        }
    }

    // ------------------------------------------------------------------ shared

    private void viewBalance(User actor, String username) {
        try {
            info("Balance for " + username + ": " + userService.getBalance(actor, username));
        } catch (BankException e) {
            error(e.getMessage());
        }
    }

    private void viewTransactions(User actor, String username) {
        try {
            List<Transaction> list = userService.getTransactions(actor, username);
            if (list.isEmpty()) {
                info("No transactions found for " + username + " yet.");
                return;
            }
            System.out.println(String.format("%-19s %-12s %-13s %12s %12s %12s  %s",
                    "Date/Time", "Account", "Type", "Amount", "Before", "After", "By/With"));
            System.out.println("-".repeat(100));
            list.forEach(System.out::println);
        } catch (BankException e) {
            error(e.getMessage());
        }
    }

    private void changePassword(User user) {
        char[] oldPw = readPassword("Current password: ");
        char[] newPw = readPassword("New password: ");
        try {
            userService.changePassword(user.getUsername(), oldPw, newPw);
            success("Password changed successfully.");
        } catch (BankException e) {
            error(e.getMessage());
        } finally {
            Arrays.fill(oldPw, '\0');
            Arrays.fill(newPw, '\0');
        }
    }

    // ------------------------------------------------------------------ output

    private static void error(String message) { System.out.println("[ERROR] " + message); }

    private static void success(String message) { System.out.println("[OK] " + message); }

    private static void info(String message) { System.out.println("[INFO] " + message); }

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
