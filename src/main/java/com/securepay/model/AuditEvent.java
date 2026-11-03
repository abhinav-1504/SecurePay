package main.java.com.securepay.model;

import java.time.Instant;
import java.util.UUID;

public class AuditEvent {
    private final String eventId;
    private final String eventType;
    private final String userId;
    private final String accountId;
    private final String transactionId;
    private final String details;
    private final Instant timestamp;

    public AuditEvent(String eventType, String userId, String accountId,
                      String transactionId, String details) {
        this.eventId = UUID.randomUUID().toString();
        this.eventType = eventType;
        this.userId = userId;
        this.accountId = accountId;
        this.transactionId = transactionId;
        this.details = details;
        this.timestamp = Instant.now();
    }

    public String getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getUserId() {
        return userId;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getDetails() {
        return details;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return String.format("[%s] %s | user=%s | account=%s | txn=%s | %s",
                timestamp, eventType, userId, accountId, transactionId, details);
    }
}
