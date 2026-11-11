package main.java.com.securepay.app;

import main.java.com.securepay.account.AccountService;
import main.java.com.securepay.api.SecurePayService;
import main.java.com.securepay.api.SecurePayServiceImpl;
import main.java.com.securepay.audit.AuditLogger;
import main.java.com.securepay.auth.AuthenticationService;
import main.java.com.securepay.concurrency.LockManager;
import main.java.com.securepay.concurrency.ThreadPoolManager;
import main.java.com.securepay.concurrency.TransactionQueue;
import main.java.com.securepay.fraud.FraudDetector;
import main.java.com.securepay.recovery.RecoveryService;
import main.java.com.securepay.recovery.TransactionJournal;
import main.java.com.securepay.security.RateLimiter;
import main.java.com.securepay.transaction.IdempotencyStore;
import main.java.com.securepay.transaction.TransactionEngine;
import main.java.com.securepay.transaction.TransactionProcessor;

import java.util.concurrent.*;

public class SecurePayApplication {

    private final ThreadPoolManager poolManager;
    private final AuditLogger auditLogger;
    private final FraudDetector fraudDetector;
    private final TransactionProcessor transactionProcessor;
    private final AuthenticationService authService;
    private final RateLimiter rateLimiter;
    private final RecoveryService recoveryService;
    private final SecurePayService service;

    public SecurePayApplication() {
        String dataDir = "data";

        this.poolManager = new ThreadPoolManager(8, 4, 2);
        this.auditLogger = new AuditLogger(poolManager, dataDir);
        AccountService accountService = new AccountService();
        this.authService = new AuthenticationService();
        IdempotencyStore idempotencyStore = new IdempotencyStore();
        this.rateLimiter = new RateLimiter(20, 60);

        TransactionJournal journal = new TransactionJournal(dataDir);
        this.fraudDetector = new FraudDetector(accountService, auditLogger, poolManager);
        LockManager lockManager = new LockManager();

        TransactionEngine engine = new TransactionEngine(
                accountService,
                lockManager,
                idempotencyStore,
                auditLogger,
                fraudDetector,
                journal
        );

        TransactionQueue queue = new TransactionQueue(1000);
        this.transactionProcessor = new TransactionProcessor(queue, engine, poolManager);
        this.recoveryService = new RecoveryService(journal, auditLogger);

        this.service = new SecurePayServiceImpl(
                authService,
                accountService,
                transactionProcessor,
                rateLimiter,
                auditLogger
        );
    }

    public void start() {
        System.out.println("============================================================");
        System.out.println("|     SecurePay - Concurrent Transaction Processing        |");
        System.out.println("|              & Risk Engine  v1.0                         |");
        System.out.println("============================================================");
        System.out.println();

        auditLogger.start();
        fraudDetector.start();
        transactionProcessor.start();

        poolManager.getScheduler().scheduleAtFixedRate(
                () -> authService.getSessionManager().cleanupExpiredSessions(),
                1, 5, TimeUnit.MINUTES);

        poolManager.getScheduler().scheduleAtFixedRate(
                rateLimiter::cleanup,
                1, 2, TimeUnit.MINUTES);

        poolManager.getScheduler().scheduleAtFixedRate(
                () -> fraudDetector.getContext().cleanup(),
                1, 2, TimeUnit.MINUTES);

        recoveryService.recover();
        System.out.println("[SYSTEM] All modules started successfully.\n");
    }

    public void shutdown() {
        System.out.println("\n[SYSTEM] Shutting down...");
        transactionProcessor.stop();
        fraudDetector.stop();
        auditLogger.stop();
        poolManager.shutdown();
        System.out.println("[SYSTEM] Shutdown complete.");
    }

    public SecurePayService getService() {
        return service;
    }

    public static void main(String[] args) throws Exception {
        SecurePayApplication app = new SecurePayApplication();
        app.start();

        try {
            DemoRunner.run(app.getService());
        } finally {
            Thread.sleep(2000);
            app.shutdown();
        }
    }
}