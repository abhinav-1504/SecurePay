package main.java.com.securepay.fraud;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class FraudContext {

    private final ConcurrentHashMap<String, WindowCounter> recentTxnCount = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> failedLoginCounts = new ConcurrentHashMap<>();

    public void recordTransaction(String userId) {
        long now = System.currentTimeMillis();
        WindowCounter counter = recentTxnCount.computeIfAbsent(userId, k -> new WindowCounter());
        synchronized (counter) {
            if (now - counter.windowStart > 60_000) {
                counter.windowStart = now;
                counter.count = 0;
            }
            counter.count++;
        }
    }

    public int getRecentTxnCount(String userId) {
        WindowCounter counter = recentTxnCount.get(userId);
        if (counter == null) return 0;
        synchronized (counter) {
            if (System.currentTimeMillis() - counter.windowStart > 60_000) {
                return 0;
            }
            return counter.count;
        }
    }

    public void recordFailedLogin(String userId) {
        failedLoginCounts.computeIfAbsent(userId, k -> new AtomicInteger(0)).incrementAndGet();
    }

    public int getFailedLoginCount(String userId) {
        AtomicInteger c = failedLoginCounts.get(userId);
        return c == null ? 0 : c.get();
    }

    public void cleanup() {
        long now = System.currentTimeMillis();
        recentTxnCount.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                return now - e.getValue().windowStart > 120_000;
            }
        });
    }

    private static class WindowCounter {
        long windowStart = System.currentTimeMillis();
        int count = 0;
    }

}
