package main.java.com.securepay.auth;

import main.java.com.securepay.exception.AuthenticationException;
import main.java.com.securepay.model.Role;
import main.java.com.securepay.model.User;
import main.java.com.securepay.security.PasswordHasher;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class AuthenticationService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCK_DURATION_SECONDS = 15 * 60;

    private final ConcurrentHashMap<String, User> usersByUsername = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, User> usersById = new ConcurrentHashMap<>();
    private final SessionManager sessionManager = new SessionManager();

    public User register(String username, String password, Role role) throws  AuthenticationException{
        if (username == null || username.isBlank()) {
            throw new AuthenticationException("Username cannot be empty");
        }
        if (password == null || password.length() < 6) {
            throw new AuthenticationException("Password must be at least 6 characters");
        }
        if (usersByUsername.containsKey(username.toLowerCase())) {
            throw new AuthenticationException("Username already exists");
        }

        String salt = PasswordHasher.generateSalt();
        String hash = PasswordHasher.hashPassword(password, salt);

        User user = new User(username.toLowerCase(), hash, salt, role == null ? Role.USER : role);

        usersByUsername.put(user.getUsername(), user);
        usersById.put(user.getUserId(), user);

        return user;
    }

    public String login(String username, String password) throws  AuthenticationException {
        User user = usersByUsername.get(username == null ? "" : username.toLowerCase());

        if (user == null) {
            throw new AuthenticationException("Invalid username or password");
        }

        if (user.isLocked()) {
            throw new AuthenticationException("Account is temporarily locked due to too many failed attempts");
        }

        boolean passwordCorrect = PasswordHasher.verifyPassword(password, user.getSalt(), user.getPasswordHash());
        if(!passwordCorrect) {
            user.incrementFailedAttempts();
            if(user.getFailedLoginAttempts() >= MAX_FAILED_ATTEMPTS) {
                user.lockAccount(LOCK_DURATION_SECONDS);
            }
            throw new AuthenticationException("Invalid username or password");
        }

        user.resetFailedAttempt();
        user.setLastLoginAt(Instant.now());
        return sessionManager.createSession(user);
    }

    public void logout(String token) {
        sessionManager.invalidateSession(token);
    }


    public SessionManager.Session validateToken(String token) {
        return sessionManager.validateSession(token);
    }

    public Optional<User> findById(String userId) {
        return Optional.ofNullable(usersById.get(userId));
    }

    public Optional<User> findByUsername(String username) {
        return Optional.ofNullable(usersByUsername.get(username.toLowerCase()));
    }

    public SessionManager getSessionManager() {
        return sessionManager;
    }

    public Map<String, User> getAllUsers() {
        return Map.copyOf(usersById);
    }

}
