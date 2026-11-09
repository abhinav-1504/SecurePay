package main.java.com.securepay.account;

import main.java.com.securepay.exception.AccountLockedException;
import main.java.com.securepay.exception.SecurePayException;
import main.java.com.securepay.model.Account;
import main.java.com.securepay.model.AccountStatus;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class AccountService {

    private final ConcurrentHashMap<String, Account> accountsById = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> userToAccount = new ConcurrentHashMap<>();

    public Account createAccount(String ownerUserId, BigDecimal initialBalance) throws SecurePayException{
        if (ownerUserId == null || ownerUserId.isBlank()) {
            throw new SecurePayException("Owner user ID cannot be empty");
        }
        if (userToAccount.containsKey(ownerUserId)) {
            throw new SecurePayException("User already has an account");
        }
        Account account = new Account(ownerUserId, initialBalance);
        accountsById.put(account.getAccountId(), account);
        userToAccount.put(ownerUserId, account.getAccountId());
        return account;
    }

    public Optional<Account> findById(String accountId) {
        return Optional.ofNullable(accountsById.get(accountId));
    }

    public Optional<Account> findByUserId(String userId) {
        String accountId = userToAccount.get(userId);
        if (accountId == null) return Optional.empty();
        return Optional.ofNullable(accountsById.get(accountId));
    }

    public Account getRequired(String accountId) throws SecurePayException {
        Account account = accountsById.get(accountId);
        if (account == null) {
            throw new SecurePayException("Account not found: " + accountId);
        }
        return account;
    }

    public void freezeAccount(String accountId) throws SecurePayException {
        Account account = getRequired(accountId);
        account.setStatus(AccountStatus.FROZEN);
    }

    public void unfreezeAccount(String accountId) throws SecurePayException {
        Account account = getRequired(accountId);
        if (account.getStatus() == AccountStatus.FROZEN) {
            account.setStatus(AccountStatus.ACTIVE);
        }
    }

    public void blockAccount(String accountId) throws SecurePayException {
        Account account = getRequired(accountId);
        account.setStatus(AccountStatus.BLOCKED);
    }

    public void ensureActive(Account account) throws AccountLockedException {
        if(account.getStatus() == AccountStatus.FROZEN) {
            throw new AccountLockedException("Account is frozen: "+ account.getAccountId());
        }
        if (account.getStatus() == AccountStatus.BLOCKED) {
            throw new AccountLockedException("Account is blocked: " + account.getAccountId());
        }
        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new AccountLockedException("Account is closed: " + account.getAccountId());
        }
    }

    public void deposit(String accountId, BigDecimal amount) throws SecurePayException{
        Account account = getRequired(accountId);
        ensureActive(account);

        account.getLock().lock();
        try {
            account.credit(amount);
        } finally {
            account.getLock().unlock();
        }
    }

    public void withdraw(String accountId, BigDecimal amount) throws SecurePayException {
        Account account = getRequired(accountId);
        ensureActive(account);

        account.getLock().lock();
        try {
            if (!account.hasSufficientBalance(amount)) {
                throw new SecurePayException("Insufficient balance. Available: " + account.getBalance());
            }
            account.debit(amount);
        } finally {
            account.getLock().unlock();
        }
    }

    public BigDecimal getBalance(String accountId) throws SecurePayException {
        return getRequired(accountId).getBalance();
    }

    public Collection<Account> getAllAccounts() {
        return accountsById.values();
    }

    public Map<String, Account> getAccountsMap() {
        return accountsById;
    }
}

