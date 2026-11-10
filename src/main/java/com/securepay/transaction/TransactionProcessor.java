package main.java.com.securepay.transaction;

import main.java.com.securepay.concurrency.ThreadPoolManager;
import main.java.com.securepay.concurrency.TransactionQueue;
import main.java.com.securepay.model.Transaction;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class TransactionProcessor {

    private final TransactionQueue queue;
    private final TransactionEngine engine;
    private final ExecutorService workers;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread dispatcher;

    public TransactionProcessor(TransactionQueue queue, TransactionEngine engine, ThreadPoolManager poolManager) {
        this.queue = queue;
        this.engine = engine;
        this.workers = poolManager.getTransactionWorkers();
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            dispatcher = new Thread(this::dispatchLoop, "txn-dispatcher");
            dispatcher.setDaemon(true);
            dispatcher.start();
        }
    }

    public void stop() {
        running.set(false);
        if (dispatcher != null) {
            dispatcher.interrupt();
        }
    }

    private void dispatchLoop() {
        while (running.get()) {
            try {
                Transaction txn = queue.poll(500, TimeUnit.MILLISECONDS);
                if (txn != null) {
                    workers.submit(() -> engine.process(txn));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    public CompletableFuture<Transaction> submitAsync(Transaction txn) {
        CompletableFuture<Transaction> future = new CompletableFuture<>();
        workers.submit(() -> {
            try {
                Transaction result = engine.process(txn);
                future.complete(result);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }


    public Transaction submitSync(Transaction txn) {
        return engine.process(txn);
    }


    public void enqueue(Transaction txn) throws InterruptedException {
        queue.submit(txn);
    }

}
