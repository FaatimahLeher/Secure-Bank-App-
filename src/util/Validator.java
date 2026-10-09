

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Pattern;

/** Central input validation (whitelisting) for everything that comes from the user. */
public final class Validator {

    public static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000.00");

    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_]{3,20}$");
    private static final Pattern CONTACT = Pattern.compile("^\\+?[0-9]{7,15}$");

    private Validator() { }

    public static void validateUsername(String username) throws BankException {
        if (username == null || !USERNAME.matcher(username).matches()) {
            throw new BankException("Username must be 3-20 characters: letters, digits or underscore only.");
        }
    }

    public static void validateContact(String contact) throws BankException {
        if (contact == null || !CONTACT.matcher(contact).matches()) {
            throw new BankException("Contact number must be 7-15 digits (optional leading +).");
        }
    }

    public static void validatePassword(char[] password) throws BankException {
        if (password == null || password.length < 8 || password.length > 64) {
            throw new BankException("Password must be 8-64 characters long.");
        }
        boolean upper = false, lower = false, digit = false, special = false;
        for (char c : password) {
            if (c == ' ' || c == '|') {
                throw new BankException("Password must not contain spaces or the '|' character.");
            }
            if (Character.isUpperCase(c)) upper = true;
            else if (Character.isLowerCase(c)) lower = true;
            else if (Character.isDigit(c)) digit = true;
            else special = true;
        }
        if (!(upper && lower && digit && special)) {
            throw new BankException("Password needs an upper-case letter, a lower-case letter, a digit and a symbol.");
        }
    }

    /** Parses a money amount: positive, at most 2 decimal places, bounded. */
    public static BigDecimal parseAmount(String text) throws BankException {
        BigDecimal amount;
        try {
            amount = new BigDecimal(text == null ? "" : text.trim());
        } catch (NumberFormatException e) {
            throw new BankException("Amount must be a valid number.");
        }
        if (amount.signum() <= 0) {
            throw new BankException("Amount must be greater than zero.");
        }
        if (amount.scale() > 2) {
            throw new BankException("Amount can have at most 2 decimal places.");
        }
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            throw new BankException("Amount exceeds the maximum of " + MAX_AMOUNT.toPlainString() + " per transaction.");
        }
        return amount.setScale(2, RoundingMode.UNNECESSARY);
    }
}
