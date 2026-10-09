import java.math.BigDecimal;
import java.util.Objects;

/** A bank customer / administrator. Each user owns exactly one account (identified by the username). */
public class User {

    private final String username;
    private String salt;
    private String passwordHash;
    private final String contactNumber;
    private final Role role;
    private BigDecimal accountBalance;

    public User(String username, String salt, String passwordHash, String contactNumber,
                Role role, BigDecimal accountBalance) {
        this.username = username;
        this.salt = salt;
        this.passwordHash = passwordHash;
        this.contactNumber = contactNumber;
        this.role = role;
        this.accountBalance = accountBalance;
    }

    public String getUsername() { return username; }
    public String getSalt() { return salt; }
    public String getPasswordHash() { return passwordHash; }
    public String getContactNumber() { return contactNumber; }
    public Role getRole() { return role; }
    public BigDecimal getAccountBalance() { return accountBalance; }

    public void setAccountBalance(BigDecimal accountBalance) {
        this.accountBalance = accountBalance;
    }

    public void setCredentials(String salt, String passwordHash) {
        this.salt = salt;
        this.passwordHash = passwordHash;
    }

    /** Intentionally leaves out the salt and hash so credentials can never leak through logging. */
    @Override
    public String toString() {
        return "User{username='" + username + "', role=" + role + ", balance=" + accountBalance + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User)) return false;
        return username.equals(((User) o).username);
    }

    @Override
    public int hashCode() {
        return Objects.hash(username);
    }
}
