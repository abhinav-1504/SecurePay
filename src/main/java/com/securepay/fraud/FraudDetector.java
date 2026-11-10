package main.java.com.securepay.fraud;

import main.java.com.securepay.account.AccountService;
import main.java.com.securepay.audit.AuditLogger;
import main.java.com.securepay.concurrency.ThreadPoolManager;
import main.java.com.securepay.model.AccountStatus;
import main.java.com.securepay.model.Transaction;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class FraudDetector {
    private final BlockingQueue<Transaction> eventQueue = new LinkedBlockingQueue<>(1000);
    private final List<FraudRule> rules = new ArrayList<>();
    private final FraudContext context = new FraudContext();
    private final AccountService accountService;
    private final AuditLogger auditLogger;
    private final ThreadPoolManager poolManager;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread worker;

    // Thresholds
    private static final int MAX_TXN_PER_MINUTE = 8;
    private static final BigDecimal LARGE_AMOUNT_THRESHOLD = new BigDecimal("50000");

    public FraudDetector(AccountService accountService, AuditLogger auditLogger, ThreadPoolManager poolManager) {
        this.accountService = accountService;
        this.auditLogger = auditLogger;
        this.poolManager = poolManager;
        registerDefaultRules();
    }

    private void registerDefaultRules() {
        // Rule 1: Too many transactions in short time
        rules.add(new FraudRule() {
            @Override
            public String name() {
                return "HIGH_FREQUENCY";
            }
            @Override
            public boolean isSuspicious(Transaction txn, FraudContext ctx) {
                return ctx.getRecentTxnCount(txn.getInitiatedByUserId()) > MAX_TXN_PER_MINUTE;
            }
        });

        // Rule 2: Unusually large amount
        rules.add(new FraudRule() {
            @Override
            public String name() {
                return "LARGE_AMOUNT";
            }
            @Override
            public boolean isSuspicious(Transaction txn, FraudContext ctx) {
                return txn.getAmount().compareTo(LARGE_AMOUNT_THRESHOLD) > 0;
            }
        });

        // Rule 3: Transaction from blocked/frozen account
        rules.add(new FraudRule() {
            @Override public String name() { return "BLOCKED_ACCOUNT"; }
            @Override
            public boolean isSuspicious(Transaction txn, FraudContext ctx) {
                return accountService.findById(txn.getFromAccountId())
                        .map(a -> a.getStatus() == AccountStatus.BLOCKED
                                || a.getStatus() == AccountStatus.FROZEN)
                        .orElse(false);
            }
        });
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            worker = new Thread(this::processLoop, "fraud-dispatcher");
            worker.setDaemon(true);
            worker.start();
        }
    }

    public void stop() {
        running.set(false);
        if (worker != null) worker.interrupt();
    }

    public void submit(Transaction txn) {
        if (!eventQueue.offer(txn)) {
            auditLogger.log("FRAUD_QUEUE_FULL", txn.getInitiatedByUserId(),
                    txn.getFromAccountId(), txn.getTransactionId(),
                    "Fraud event dropped because queue is full");
        }
    }

    private void processLoop() {
        while (running.get()) {
            try {
                Transaction txn = eventQueue.poll(500, TimeUnit.MILLISECONDS);
                if (txn != null) {
                    poolManager.getFraudWorkers().submit(() -> evaluate(txn));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void evaluate(Transaction txn) {
        context.recordTransaction(txn.getInitiatedByUserId());

        for (FraudRule rule : rules) {
            if (rule.isSuspicious(txn, context)) {
                auditLogger.log("FRAUD_FLAGGED", txn.getInitiatedByUserId(),
                        txn.getFromAccountId(), txn.getTransactionId(),
                        "Rule triggered: " + rule.name());

                if ("HIGH_FREQUENCY".equals(rule.name()) || "BLOCKED_ACCOUNT".equals(rule.name())) {
                    try {
                        accountService.freezeAccount(txn.getFromAccountId());
                        auditLogger.log("ACCOUNT_FROZEN", txn.getInitiatedByUserId(),
                                txn.getFromAccountId(), txn.getTransactionId(),
                                "Auto-frozen due to " + rule.name());
                    } catch (Exception ignored) {}
                }
                break;
            }
        }
    }

    public FraudContext getContext() {
        return context;
    }
}
