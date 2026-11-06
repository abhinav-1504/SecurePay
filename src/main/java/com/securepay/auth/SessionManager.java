package main.java.com.securepay.auth;

import main.java.com.securepay.model.Role;
import main.java.com.securepay.model.User;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager {

    private static final long SESSION_TTL_SECONDS = 30 * 60;
    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();

    public String createSession(User user) {
        String token = UUID.randomUUID().toString();
        Session session = new Session(user.getUserId(), user.getUsername(), user.getRole(), Instant.now().plusSeconds(SESSION_TTL_SECONDS));
        sessions.put(token, session);
        return token;
    }

    public Session validateSession(String token) {
        if(token == null) return null;
        Session session = sessions.get(token);
        if(session == null) return null;
        if(Instant.now().isAfter(session.expiresAt)) {
            sessions.remove(token);
            return null;
        }
        return session;
    }

    public void invalidateSession(String token) {
        sessions.remove(token);
    }

    public void cleanupExpiredSessions() {
        Instant now = Instant.now();
        sessions.entrySet().removeIf(e -> now.isAfter(e.getValue().expiresAt));
    }

    public int activeSessionCount() {
        return sessions.size();
    }

    public static class Session{
        public final String userId;
        public final String username;
        public final Role role;
        public final Instant expiresAt;

        Session(String userId, String username, Role role, Instant expiresAt) {
            this.userId = userId;
            this.username = username;
            this.role = role;
            this.expiresAt = expiresAt;
        }

        @Override
        public String toString() {
            return "Session{user=" + username + ", role=" + role + ", expires=" + expiresAt + "}";
        }
    }
}
