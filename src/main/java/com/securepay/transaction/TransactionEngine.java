package main.java.com.securepay.transaction;

import main.java.com.securepay.account.AccountService;
import main.java.com.securepay.audit.AuditLogger;
import main.java.com.securepay.concurrency.LockManager;
import main.java.com.securepay.exception.AccountLockedException;
import main.java.com.securepay.exception.InsufficientBalanceException;
import main.java.com.securepay.exception.SecurePayException;
import main.java.com.securepay.fraud.FraudDetector;
import main.java.com.securepay.model.Account;
import main.java.com.securepay.model.Transaction;
import main.java.com.securepay.model.TransactionStatus;
import main.java.com.securepay.recovery.TransactionJournal;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

public class TransactionEngine {

    private final AccountService accountService;
    private final LockManager lockManager;
    private final IdempotencyStore idempotencyStore;
    private final AuditLogger auditLogger;
    private final FraudDetector fraudDetector;
    private final TransactionJournal journal;

    public TransactionEngine(AccountService accountService, LockManager lockManager, IdempotencyStore idempotencyStore, AuditLogger auditLogger, FraudDetector fraudDetector, TransactionJournal journal) {
        this.accountService = accountService;
        this.lockManager = lockManager;
        this.idempotencyStore = idempotencyStore;
        this.auditLogger = auditLogger;
        this.fraudDetector = fraudDetector;
        this.journal = journal;
    }

    public Transaction process(Transaction transaction) {

        var existing = idempotencyStore.getExisting(transaction.getIdempotencyKey());
        if (existing.isPresent()) {
            Transaction prev = existing.get();
            if (idempotencyStore.isCompleted(transaction.getIdempotencyKey())) {
                auditLogger.log("IDEMPOTENT_HIT",
                        transaction.getInitiatedByUserId(),
                        transaction.getFromAccountId(),
                        prev.getTransactionId(),
                        "Returned previous result for key " + transaction.getIdempotencyKey());
                return prev;
            }
            return prev;
        }

        idempotencyStore.put(transaction.getIdempotencyKey(), transaction);

        try {
            // 2. Validate
            transaction.transitionTo(TransactionStatus.VALIDATED);
            journal.record(transaction.getTransactionId(), "VALIDATED", transaction);

            Account from = accountService.getRequired(transaction.getFromAccountId());
            Account to = accountService.getRequired(transaction.getToAccountId());

            accountService.ensureActive(from);
            accountService.ensureActive(to);

            if (transaction.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new SecurePayException("Amount must be positive");
            }
            if (transaction.getFromAccountId().equals(transaction.getToAccountId())) {
                throw new SecurePayException("Cannot transfer to the same account");
            }

            // 3. Process under locks
            transaction.transitionTo(TransactionStatus.PROCESSING);
            journal.record(transaction.getTransactionId(), "PROCESSING", transaction);

            try (LockManager.LockPair locks =
                         lockManager.tryAcquireLocks(from, to, 5, TimeUnit.SECONDS)) {

                if (locks == null) {
                    throw new SecurePayException("Could not acquire locks within timeout");
                }

                // Re-check balance under lock
                if (!from.hasSufficientBalance(transaction.getAmount())) {
                    throw new InsufficientBalanceException(
                            "Insufficient balance. Available: " + from.getBalance());
                }

                journal.record(transaction.getTransactionId(), "DEBIT_STARTED", transaction);
                from.debit(transaction.getAmount());
                journal.record(transaction.getTransactionId(), "DEBIT_COMPLETED", transaction);

                journal.record(transaction.getTransactionId(), "CREDIT_STARTED", transaction);
                to.credit(transaction.getAmount());
                journal.record(transaction.getTransactionId(), "CREDIT_COMPLETED", transaction);

                transaction.transitionTo(TransactionStatus.SUCCESS);
                journal.record(transaction.getTransactionId(), "SUCCESS", transaction);

                auditLogger.log("TRANSACTION_SUCCESS",
                        transaction.getInitiatedByUserId(),
                        transaction.getFromAccountId(),
                        transaction.getTransactionId(),
                        "Transferred " + transaction.getAmount() + " successfully");

                // Async fraud check (non-blocking)
                fraudDetector.submit(transaction);
            }

        } catch (InsufficientBalanceException | AccountLockedException e) {
            transaction.fail(e.getMessage());
            journal.record(transaction.getTransactionId(), "FAILED", transaction);
            auditLogger.log("TRANSACTION_FAILED",
                    transaction.getInitiatedByUserId(),
                    transaction.getFromAccountId(),
                    transaction.getTransactionId(),
                    transaction.getFailureReason());

        } catch (Exception e) {
            String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            transaction.fail(reason);
            journal.record(transaction.getTransactionId(), "FAILED", transaction);
            auditLogger.log("TRANSACTION_FAILED",
                    transaction.getInitiatedByUserId(),
                    transaction.getFromAccountId(),
                    transaction.getTransactionId(),
                    transaction.getFailureReason());
        }

        return transaction;
    }
}
