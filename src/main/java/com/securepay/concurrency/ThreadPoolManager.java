package main.java.com.securepay.concurrency;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ThreadPoolManager {

    private final ExecutorService transactionWorkers;
    private final ExecutorService fraudWorkers;
    private final ExecutorService auditWorkers;
    private final ScheduledExecutorService scheduler;

    public ThreadPoolManager(int transactionThreads, int fraudThreads, int auditThreads) {
        this.transactionWorkers = new ThreadPoolExecutor(
                transactionThreads,
                transactionThreads,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(1000),
                new NamedThreadFactory("txn-worker"),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        this.fraudWorkers = new ThreadPoolExecutor(
                fraudThreads,
                fraudThreads,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(500),
                new NamedThreadFactory("fraud-worker"),
                new ThreadPoolExecutor.DiscardOldestPolicy()
        );

        this.auditWorkers = new ThreadPoolExecutor(
                auditThreads,
                auditThreads,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(2000),
                new NamedThreadFactory("audit-worker"),
                new ThreadPoolExecutor.DiscardOldestPolicy()
        );

        this.scheduler = Executors.newScheduledThreadPool(
                2, new NamedThreadFactory("scheduler"));
    }

    public ExecutorService getTransactionWorkers() {
        return transactionWorkers;
    }

    public ExecutorService getFraudWorkers() {
        return fraudWorkers;
    }

    public ExecutorService getAuditWorkers() {
        return auditWorkers;
    }

    public ScheduledExecutorService getScheduler() {
        return scheduler;
    }

    public void shutdown() {
        shutdownPool(transactionWorkers, "transaction");
        shutdownPool(fraudWorkers, "fraud");
        shutdownPool(auditWorkers, "audit");
        shutdownPool(scheduler, "scheduler");
    }

    private void shutdownPool(ExecutorService pool, String name) {
        pool.shutdown();
        try {
            if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static class NamedThreadFactory implements ThreadFactory {
        private final String prefix;
        private final AtomicInteger counter = new AtomicInteger(1);

        NamedThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, prefix + "-" + counter.getAndIncrement());
            t.setDaemon(false);
            return t;
        }
    }
}
