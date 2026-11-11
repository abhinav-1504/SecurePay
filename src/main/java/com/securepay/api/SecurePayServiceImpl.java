package main.java.com.securepay.api;

import main.java.com.securepay.account.AccountService;
import main.java.com.securepay.audit.AuditLogger;
import main.java.com.securepay.auth.AuthenticationService;
import main.java.com.securepay.auth.SessionManager;
import main.java.com.securepay.exception.AuthenticationException;
import main.java.com.securepay.exception.RateLimitExceededException;
import main.java.com.securepay.exception.SecurePayException;
import main.java.com.securepay.model.Account;
import main.java.com.securepay.model.Role;
import main.java.com.securepay.model.Transaction;
import main.java.com.securepay.model.User;
import main.java.com.securepay.security.RateLimiter;
import main.java.com.securepay.transaction.TransactionProcessor;

import java.math.BigDecimal;

public class SecurePayServiceImpl implements SecurePayService {

    private final AuthenticationService authService;
    private final AccountService accountService;
    private final TransactionProcessor transactionProcessor;
    private final RateLimiter rateLimiter;
    private final AuditLogger auditLogger;

    public SecurePayServiceImpl(AuthenticationService authService, AccountService accountService, TransactionProcessor transactionProcessor, RateLimiter rateLimiter, AuditLogger auditLogger) {
        this.authService = authService;
        this.accountService = accountService;
        this.transactionProcessor = transactionProcessor;
        this.rateLimiter = rateLimiter;
        this.auditLogger = auditLogger;
    }

    @Override
    public User register(String username, String password, Role role) throws SecurePayException {
        User user = authService.register(username, password, role);
        Account account = accountService.createAccount(user.getUserId(), BigDecimal.ZERO);
        auditLogger.log("USER_REGISTERED", user.getUserId(), account.getAccountId(), null, "Registered as " + role);
        return user;
    }

    @Override
    public String login(String username, String password) throws Exception {
        String token = authService.login(username, password);
        SessionManager.Session session = authService.validateToken(token);
        auditLogger.log("USER_LOGIN", session.userId, null, null, "Login successful");
        return token;
    }

    @Override
    public void logout(String token) {
        authService.logout(token);
    }

    @Override
    public void deposit(String token, BigDecimal amount) throws Exception {
        SessionManager.Session session = requireSession(token);
        ensureRateLimit(session.userId);
        Account account = accountService.findByUserId(session.userId)
                .orElseThrow(() -> new SecurePayException("No account found"));
        accountService.deposit(account.getAccountId(), amount);
        auditLogger.log("DEPOSIT", session.userId, account.getAccountId(), null,
                "Deposited " + amount);
    }

    @Override
    public Transaction transfer(String token, String toAccountId, BigDecimal amount, String idempotencyKey) throws Exception {
        SessionManager.Session session = requireSession(token);
        ensureRateLimit(session.userId);
        Account from = accountService.findByUserId(session.userId)
                .orElseThrow(() -> new SecurePayException("No account found for user"));

        Transaction transaction = new Transaction(idempotencyKey, from.getAccountId(), toAccountId, amount, session.userId);
        return transactionProcessor.submitSync(transaction);
    }

    @Override
    public BigDecimal getBalance(String token) throws Exception {
        SessionManager.Session session = requireSession(token);
        return accountService.findByUserId(session.userId)
                .map(Account::getBalance)
                .orElse(BigDecimal.ZERO);
    }

    @Override
    public Account getAccount(String token) throws Exception {
        SessionManager.Session session = requireSession(token);
        return accountService.findByUserId(session.userId)
                .orElseThrow(() -> new SecurePayException("No account found"));
    }

    private SessionManager.Session requireSession(String token) throws AuthenticationException {
        SessionManager.Session session = authService.validateToken(token);
        if (session == null) {
            throw new AuthenticationException("Invalid or expired session");
        }
        return session;
    }

    private void ensureRateLimit(String userId) throws RateLimitExceededException {
        if (!rateLimiter.tryAcquire(userId)) {
            throw new RateLimitExceededException("Rate limit exceeded. Please try again later.");
        }
    }

    @Override
    public void freezeAccount(String adminToken, String accountId) throws Exception {
        SessionManager.Session session = requireSession(adminToken);
        requireAdmin(session);
        accountService.freezeAccount(accountId);
        auditLogger.log("ACCOUNT_FROZEN", session.userId, accountId, null, "Frozen by admin");
    }

    @Override
    public void unfreezeAccount(String adminToken, String accountId) throws Exception {
        SessionManager.Session session = requireSession(adminToken);
        requireAdmin(session);
        accountService.unfreezeAccount(accountId);
        auditLogger.log("ACCOUNT_UNFROZEN", session.userId, accountId, null, "Unfrozen by admin");
    }

    @Override
    public void blockAccount(String adminToken, String accountId) throws Exception {
        SessionManager.Session session = requireSession(adminToken);
        requireAdmin(session);
        accountService.blockAccount(accountId);
        auditLogger.log("ACCOUNT_BLOCKED", session.userId, accountId, null, "Blocked by admin");
    }

    private void requireAdmin(SessionManager.Session session) throws SecurePayException {
        if (session.role != Role.ADMIN) {
            throw new SecurePayException("Admin role required");
        }
    }
}