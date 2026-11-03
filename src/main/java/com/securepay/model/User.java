package main.java.com.securepay.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class User {
    private final String userId;
    private final String username;
    private final String passwordHash;
    private final String salt;
    private final Role role;
    private volatile int failedLoginAttempts;
    private volatile Instant lockedUntil;
    private volatile Instant createdAt;
    private volatile Instant lastLoginAt;

    public User(String username, String passwordHash, String salt, Role role) {
        this.userId = UUID.randomUUID().toString();
        this.username = username;
        this.passwordHash = passwordHash;
        this.salt = salt;
        this.role = role;
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
        this.createdAt = Instant.now();
        this.lastLoginAt = null;
    }

    public String getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getSalt() {
        return salt;
    }

    public Role getRole() {
        return role;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public synchronized void incrementFailedAttempts() {
        failedLoginAttempts++;
    }

    public synchronized  void resetFailedAttempt() {
        failedLoginAttempts = 0;
        lockedUntil = null;
    }

    public synchronized void lockAccount(long durationSeconds) {
        lockedUntil = Instant.now().plusSeconds(durationSeconds);
    }

    public synchronized boolean isLocked() {
        if(lockedUntil == null) return false;
        if(Instant.now().isAfter(lockedUntil)) {
            lockedUntil = null;
            failedLoginAttempts = 0;
            return false;
        }
        return true;
    }

    public synchronized void setLastLoginAt(Instant time) {
        this.lastLoginAt = time;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User)) return false;
        User user = (User) o;
        return Objects.equals(userId, user.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId);
    }

    @Override
    public String toString() {
        return "User{username='" + username + "', role=" + role + "}";
    }

}
