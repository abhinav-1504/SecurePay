package main.java.com.securepay.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class Transaction {
    private final String transactionId;
    private final String idempotencyKey;
    private final String fromAccountId;
    private final String toAccountId;
    private final BigDecimal amount;
    private final String initiatedByUserId;
    private volatile TransactionStatus status;
    private volatile String failureReason;
    private final Instant createdAt;
    private volatile Instant completedAt;

    public Transaction(String idempotencyKey, String fromAccountId, String toAccountId,
                       BigDecimal amount, String initiatedByUserId) {
        this.transactionId = UUID.randomUUID().toString();
        this.idempotencyKey = idempotencyKey;
        this.fromAccountId = fromAccountId;
        this.toAccountId = toAccountId;
        this.amount = amount;
        this.initiatedByUserId = initiatedByUserId;
        this.status = TransactionStatus.CREATED;
        this.createdAt = Instant.now();
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getFromAccountId() {
        return fromAccountId;
    }

    public String getToAccountId() {
        return toAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getInitiatedByUserId() {
        return initiatedByUserId;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public synchronized void transitionTo(TransactionStatus newStatus) {
        validateTransition(this.status, newStatus);
        this.status = newStatus;
        if (newStatus == TransactionStatus.SUCCESS ||
                newStatus == TransactionStatus.FAILED ||
                newStatus == TransactionStatus.FLAGGED ||
                newStatus == TransactionStatus.ROLLED_BACK) {
            this.completedAt = Instant.now();
        }
    }

    public synchronized void fail(String reason) {
        this.failureReason = reason;
        transitionTo(TransactionStatus.FAILED);
    }

    private void validateTransition(TransactionStatus from, TransactionStatus to) {
        boolean valid = switch (from) {
            case CREATED -> to == TransactionStatus.VALIDATED || to == TransactionStatus.FAILED;
            case VALIDATED -> to == TransactionStatus.PROCESSING || to == TransactionStatus.FAILED;
            case PROCESSING -> to == TransactionStatus.SUCCESS || to == TransactionStatus.FAILED
                    || to == TransactionStatus.FLAGGED || to == TransactionStatus.ROLLED_BACK;
            case SUCCESS, FAILED, FLAGGED, ROLLED_BACK -> false;
        };
        if (!valid) {
            throw new IllegalStateException(
                    "Invalid transition from " + from + " to " + to);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Transaction)) return false;
        Transaction that = (Transaction) o;
        return Objects.equals(transactionId, that.transactionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(transactionId);
    }

    @Override
    public String toString() {
        return "Transaction{id=" + transactionId + ", from=" + fromAccountId +
                ", to=" + toAccountId + ", amount=" + amount + ", status=" + status + "}";
    }
}
