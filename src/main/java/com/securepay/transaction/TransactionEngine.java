package main.java.com.securepay.transaction;

import main.java.com.securepay.account.AccountService;
import main.java.com.securepay.concurrency.LockManager;
import main.java.com.securepay.exception.AccountLockedException;
import main.java.com.securepay.exception.InsufficientBalanceException;
import main.java.com.securepay.exception.SecurePayException;
import main.java.com.securepay.model.Account;
import main.java.com.securepay.model.Transaction;
import main.java.com.securepay.model.TransactionStatus;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

public class TransactionEngine {

    private final AccountService accountService;
    private final LockManager lockManager;
    private final IdempotencyStore idempotencyStore;

    public TransactionEngine(AccountService accountService, LockManager lockManager, IdempotencyStore idempotencyStore) {
        this.accountService = accountService;
        this.lockManager = lockManager;
        this.idempotencyStore = idempotencyStore;
    }

    public Transaction process(Transaction transaction) {

        var existing = idempotencyStore.getExisting(transaction.getIdempotencyKey());
        if (existing.isPresent()) {
            Transaction prev = existing.get();
            if (idempotencyStore.isCompleted(transaction.getIdempotencyKey())) {
                return prev;
            }
            return prev;
        }

        idempotencyStore.put(transaction.getIdempotencyKey(), transaction);
        try {
            transaction.transitionTo(TransactionStatus.VALIDATED);

            Account from = accountService.getRequired(transaction.getFromAccountId());
            Account to = accountService.getRequired(transaction.getToAccountId());

            accountService.ensureActive(from);
            accountService.ensureActive(to);

            if (transaction.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new SecurityException("Amount must be positive");
            }
            if (transaction.getFromAccountId().equals(transaction.getToAccountId())) {
                throw new SecurityException("Cannot transfer to the same account");
            }

            transaction.transitionTo(TransactionStatus.PROCESSING);
            try (LockManager.LockPair locks = lockManager.tryAcquireLocks(from, to, 5, TimeUnit.SECONDS)) {
                if (locks == null) {
                    throw new SecurePayException("Could not acquire locks within timeout");
                }

                if (!from.hasSufficientBalance(transaction.getAmount())) {
                    throw new InsufficientBalanceException("Insufficient balance. Available: " + from.getBalance());
                }

                from.debit(transaction.getAmount());
                to.credit(transaction.getAmount());

                transaction.transitionTo(TransactionStatus.SUCCESS);
            } catch (InsufficientBalanceException | AccountLockedException e) {
                transaction.fail(e.getMessage());
            }
        }catch (Exception e) {
            String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            transaction.fail(reason);
        }
        return transaction;
    }
}
