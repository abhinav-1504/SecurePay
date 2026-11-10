package main.java.com.securepay.concurrency;

import main.java.com.securepay.model.Transaction;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class TransactionQueue {

    private final BlockingQueue<Transaction> queue;

    public TransactionQueue(int capacity) {
        this.queue = new LinkedBlockingQueue<>(capacity);
    }

    public void submit(Transaction transaction) throws InterruptedException {
        queue.put(transaction);          // blocks if queue is full
    }

    public boolean offer(Transaction transaction) {
        return queue.offer(transaction); // non-blocking
    }

    public Transaction take() throws InterruptedException {
        return queue.take();     // blocks until available
    }

    public Transaction poll(long timeout, TimeUnit unit) throws InterruptedException {
        return queue.poll(timeout, unit);
    }

    public int size() {
        return queue.size();
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

}
