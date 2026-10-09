import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Immutable record of a single balance change on one account. */
public final class Transaction {

    private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final LocalDateTime timestamp;
    private final String accountUsername;
    private final BigDecimal amount;
    private final String type;            // DEPOSIT, WITHDRAWAL, TRANSFER_OUT, TRANSFER_IN
    private final BigDecimal initialBalance;
    private final BigDecimal finalBalance;
    private final String performedBy;     // who initiated it / the counter-party

    public Transaction(LocalDateTime timestamp, String accountUsername, BigDecimal amount, String type,
                       BigDecimal initialBalance, BigDecimal finalBalance, String performedBy) {
        this.timestamp = timestamp;
        this.accountUsername = accountUsername;
        this.amount = amount;
        this.type = type;
        this.initialBalance = initialBalance;
        this.finalBalance = finalBalance;
        this.performedBy = performedBy;
    }

    public LocalDateTime getTimestamp() { return timestamp; }
    public String getAccountUsername() { return accountUsername; }
    public BigDecimal getAmount() { return amount; }
    public String getType() { return type; }
    public BigDecimal getInitialBalance() { return initialBalance; }
    public BigDecimal getFinalBalance() { return finalBalance; }
    public String getPerformedBy() { return performedBy; }

    @Override
    public String toString() {
        return String.format("%-19s %-12s %-13s %12s %12s %12s  %s",
                timestamp.format(DISPLAY), accountUsername, type,
                amount.toPlainString(), initialBalance.toPlainString(), finalBalance.toPlainString(), performedBy);
    }
}
