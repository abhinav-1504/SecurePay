package main.java.com.securepay.security;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class RateLimiter {

    private final int maxRequests;
    private final long windowMillis;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimiter(int maxRequests, int windowSeconds) {
        this.maxRequests = maxRequests;
        this.windowMillis = windowSeconds * 1000L;
    }


    public boolean tryAcquire(String key) {
        long now = System.currentTimeMillis();
        Window window = windows.computeIfAbsent(key, k -> new Window(now));

        synchronized (window) {
            if (now - window.windowStart >= windowMillis) {
                window.windowStart = now;
                window.count.set(0);
            }

            if (window.count.get() >= maxRequests) {
                return false;
            }

            window.count.incrementAndGet();
            return true;
        }
    }


    public int remaining(String key) {
        Window window = windows.get(key);
        if (window == null) return maxRequests;

        synchronized (window) {
            long now = System.currentTimeMillis();
            if (now - window.windowStart >= windowMillis) {
                return maxRequests;
            }
            return Math.max(0, maxRequests - window.count.get());
        }
    }


    public void cleanup() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, Window>> it = windows.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Window> entry = it.next();
            Window w = entry.getValue();
            synchronized (w) {
                if (now - w.windowStart > windowMillis * 2) {
                    it.remove();
                }
            }
        }
    }

    private static class Window {
        volatile long windowStart;
        final AtomicInteger count = new AtomicInteger(0);

        Window(long start) {
            this.windowStart = start;
        }
    }
}
