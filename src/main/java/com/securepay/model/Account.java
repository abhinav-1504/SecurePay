package main.java.com.securepay.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

public class Account {
    private final String accountId;
    private final String ownerUserId;
    private volatile BigDecimal balance;
    private volatile AccountStatus status;
    private final Instant createdAt;
    private final ReentrantLock lock = new ReentrantLock(true); // fair lock

    public Account(String ownerUserId, BigDecimal initialBalance) {
        this.accountId = UUID.randomUUID().toString();
        this.ownerUserId = ownerUserId;
        this.balance = initialBalance != null ? initialBalance : BigDecimal.ZERO;
        this.status = AccountStatus.ACTIVE;
        this.createdAt = Instant.now();
    }

    public String getAccountId() {
        return accountId;
    }

    public String getOwnerUserId() {
        return ownerUserId;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public ReentrantLock getLock() {
        return lock;
    }

    public void setStatus(AccountStatus status) {
        this.status = status;
    }

    /** Must be called while holding this account's lock */
    public void credit(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Credit amount must be positive");
        }
        this.balance = this.balance.add(amount);
    }

    /** Must be called while holding this account's lock */
    public void debit(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Debit amount must be positive");
        }
        if (this.balance.compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient balance");
        }
        this.balance = this.balance.subtract(amount);
    }

    public boolean hasSufficientBalance(BigDecimal amount) {
        return this.balance.compareTo(amount) >= 0;
    }

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Account)) return false;
        Account account = (Account) o;
        return Objects.equals(accountId, account.accountId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountId);
    }

    @Override
    public String toString() {
        return "Account{id=" + accountId + ", owner=" + ownerUserId +
                ", balance=" + balance + ", status=" + status + "}";
    }
}