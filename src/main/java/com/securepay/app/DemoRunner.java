package main.java.com.securepay.app;

import main.java.com.securepay.api.SecurePayService;
import main.java.com.securepay.model.Account;
import main.java.com.securepay.model.Role;
import main.java.com.securepay.model.Transaction;
import main.java.com.securepay.model.User;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class DemoRunner {

    private DemoRunner() {}

    public static void run(SecurePayService app) throws Exception {

        System.out.println("========== 1. USER REGISTRATION ==========");
        User rahul = app.register("rahul", "password123", Role.USER);
        User priya = app.register("priya", "password123", Role.USER);
        User amit  = app.register("amit",  "password123", Role.USER);
        User admin = app.register("admin", "adminpass", Role.ADMIN);

        System.out.println("Registered: Rahul, Priya, Amit, Admin");
        System.out.println();

        System.out.println("========== 2. LOGIN ==========");
        String rahulToken = app.login("rahul", "password123");
        String priyaToken = app.login("priya", "password123");
        String amitToken  = app.login("amit",  "password123");
        System.out.println("Rahul, Priya, Amit logged in successfully");
        System.out.println();

        System.out.println("========== 3. INITIAL DEPOSITS ==========");
        app.deposit(rahulToken, new BigDecimal("10000"));
        app.deposit(priyaToken, new BigDecimal("5000"));
        app.deposit(amitToken,  new BigDecimal("3000"));

        System.out.println("Rahul balance: " + app.getBalance(rahulToken));
        System.out.println("Priya balance: " + app.getBalance(priyaToken));
        System.out.println("Amit  balance: " + app.getBalance(amitToken));
        System.out.println();

        Account priyaAccount = app.getAccount(priyaToken);
        Account amitAccount  = app.getAccount(amitToken);
        System.out.println();

        System.out.println("========== 4. CONCURRENT TRANSFERS (Race Condition Test) ==========");
        System.out.println("Rahul has 10000. Launching 3 concurrent transfers that sum > balance:");
        System.out.println("  Request 1: Rahul -> Priya  7000");
        System.out.println("  Request 2: Rahul -> Amit   6000");
        System.out.println("  Request 3: Rahul -> Priya  5000");
        System.out.println();

        ExecutorService concurrent = Executors.newFixedThreadPool(3);
        List<Future<Transaction>> futures = new ArrayList<>();

        futures.add(concurrent.submit(() ->
                app.transfer(rahulToken, priyaAccount.getAccountId(),
                        new BigDecimal("7000"), "idem-concurrent-1")));

        futures.add(concurrent.submit(() ->
                app.transfer(rahulToken, amitAccount.getAccountId(),
                        new BigDecimal("6000"), "idem-concurrent-2")));

        futures.add(concurrent.submit(() ->
                app.transfer(rahulToken, priyaAccount.getAccountId(),
                        new BigDecimal("5000"), "idem-concurrent-3")));

        for (int i = 0; i < futures.size(); i++) {
            Transaction t = futures.get(i).get();
            System.out.println("  Result #" + (i + 1) + ": status=" + t.getStatus()
                    + (t.getFailureReason() != null ? " reason=" + t.getFailureReason() : "")
                    + " amount=" + t.getAmount());
        }
        concurrent.shutdown();

        System.out.println();
        System.out.println("After concurrent transfers:");
        System.out.println("  Rahul: " + app.getBalance(rahulToken));
        System.out.println("  Priya: " + app.getBalance(priyaToken));
        System.out.println("  Amit : " + app.getBalance(amitToken));
        System.out.println("  (Rahul balance should never be negative)");
        System.out.println();

        System.out.println("========== 5. IDEMPOTENCY TEST ==========");
        String idemKey = "idem-retry-demo-001";

        Transaction first = app.transfer(rahulToken, priyaAccount.getAccountId(),
                new BigDecimal("100"), idemKey);
        System.out.println("First  -> status=" + first.getStatus() + " id=" + first.getTransactionId());

        Transaction second = app.transfer(rahulToken, priyaAccount.getAccountId(),
                new BigDecimal("100"), idemKey);
        System.out.println("Retry  -> status=" + second.getStatus() + " id=" + second.getTransactionId());
        System.out.println("Same transaction returned? " +
                first.getTransactionId().equals(second.getTransactionId()));
        System.out.println("Rahul balance after idempotent retry: " + app.getBalance(rahulToken));
        System.out.println();

        System.out.println("========== 6. FAILED LOGIN LOCKOUT ==========");
        for (int i = 1; i <= 6; i++) {
            try {
                app.login("rahul", "wrongpassword");
            } catch (Exception e) {
                System.out.println("  Attempt " + i + ": " + e.getMessage());
            }
        }
        System.out.println();

        System.out.println("========== 7. LARGE TRANSFER (Fraud Detection) ==========");
        app.deposit(rahulToken, new BigDecimal("100000"));

        Transaction large = app.transfer(rahulToken, priyaAccount.getAccountId(),
                new BigDecimal("60000"), "idem-large-001");

        System.out.println("Large transfer status: " + large.getStatus() +
                " amount=" + large.getAmount());
        System.out.println("(Fraud engine will flag LARGE_AMOUNT asynchronously)");
        System.out.println();


        String adminToken = app.login("admin", "adminpass");
        Account rahulAccount = app.getAccount(rahulToken);

        System.out.println("========== 8. ACCOUNT FREEZE / UNFREEZE / BLOCK ==========");
        System.out.println("8a. Freezing Rahul's account...");
        System.out.println();
        app.freezeAccount(adminToken, rahulAccount.getAccountId());
        try {
            app.transfer(rahulToken, priyaAccount.getAccountId(),
                    new BigDecimal("50"), "idem-freeze-test");
            System.out.println("  ERROR: Transfer should have failed on frozen account!");
        } catch (Exception e) {
            System.out.println("  Correctly blocked: " + e.getMessage());
        }
        System.out.println();

        System.out.println("8b. Unfreezing Rahul's account...");
        app.unfreezeAccount(adminToken, rahulAccount.getAccountId());
        Transaction afterUnfreeze = app.transfer(rahulToken, priyaAccount.getAccountId(),
                new BigDecimal("50"), "idem-unfreeze-test");
        System.out.println("  Transfer after unfreeze: " + afterUnfreeze.getStatus());
        System.out.println();

        System.out.println("8c. Blocking Amit's account...");
        app.blockAccount(adminToken, amitAccount.getAccountId());
        try {
            app.transfer(amitToken, priyaAccount.getAccountId(),
                    new BigDecimal("10"), "idem-block-test");
            System.out.println("  ERROR: Transfer should have failed on blocked account!");
        } catch (Exception e) {
            System.out.println("  Correctly blocked: " + e.getMessage());
        }

        System.out.println();

        System.out.println("========== 9. FINAL BALANCES ==========");
        System.out.println("  Rahul: " + app.getBalance(rahulToken));
        System.out.println("  Priya: " + app.getBalance(priyaToken));
        System.out.println("  Amit : " + app.getBalance(amitToken));
        System.out.println();
        System.out.println("========== DEMO COMPLETE ==========");
        System.out.println("Check data/audit.log and data/transaction_journal.log for full trail.");
    }
}
