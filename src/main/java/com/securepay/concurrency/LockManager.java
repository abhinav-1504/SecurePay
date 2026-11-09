package main.java.com.securepay.concurrency;

import main.java.com.securepay.model.Account;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class LockManager {

    public static class LockPair implements AutoCloseable {
        private final ReentrantLock first;
        private final ReentrantLock second;
        private final boolean sameAccount;

        LockPair(ReentrantLock first, ReentrantLock second, boolean sameAccount) {
            this.first = first;
            this.second = second;
            this.sameAccount = sameAccount;
        }

        @Override
        public void close() {
            if(!sameAccount) {
                second.unlock();
            }
            first.unlock();
        }

    }

    public LockPair acquireLocks(Account a, Account b) throws InterruptedException {
        if(a.getAccountId().equals(b.getAccountId())) {
            a.getLock().lockInterruptibly();
            return new LockPair(a.getLock(), a.getLock(), true);
        }

        Account first, second;
        if(a.getAccountId().compareTo(b.getAccountId()) < 0) {
            first = a;
            second = b;
        } else {
            first = b;
            second = a;
        }
        first.getLock().lockInterruptibly();
        try {
            second.getLock().lockInterruptibly();
        } catch (InterruptedException e) {
            first.getLock().unlock();
            throw e;
        }
        return new LockPair(first.getLock(), second.getLock(), false);
    }

    public LockPair tryAcquireLocks(Account a, Account b, long timeout, TimeUnit unit)
            throws InterruptedException {

        if (a.getAccountId().equals(b.getAccountId())) {
            if (a.getLock().tryLock(timeout, unit)) {
                return new LockPair(a.getLock(), a.getLock(), true);
            }
            return null;
        }

        Account first, second;
        if (a.getAccountId().compareTo(b.getAccountId()) < 0) {
            first = a;
            second = b;
        } else {
            first = b;
            second = a;
        }

        long deadline = System.nanoTime() + unit.toNanos(timeout);

        if (!first.getLock().tryLock(timeout, unit)) {
            return null;
        }

        try {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0 || !second.getLock().tryLock(remaining, TimeUnit.NANOSECONDS)) {
                first.getLock().unlock();
                return null;
            }
        } catch (InterruptedException e) {
            first.getLock().unlock();
            throw e;
        }

        return new LockPair(first.getLock(), second.getLock(), false);
    }
}
