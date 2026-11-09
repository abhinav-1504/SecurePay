package main.java.com.securepay.transaction;

import main.java.com.securepay.model.Transaction;
import main.java.com.securepay.model.TransactionStatus;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class IdempotencyStore {

    private final ConcurrentHashMap<String, Transaction> store = new ConcurrentHashMap<>();

    public Optional<Transaction> getExisting(String idempotencyKey) {
        return Optional.ofNullable(store.get(idempotencyKey));
    }

    public void put(String idempotencyKey, Transaction transaction) {
        store.put(idempotencyKey, transaction);
    }

    public boolean isCompleted(String idempotencyKey) {
        Transaction transaction = store.get(idempotencyKey);
        if (transaction== null) return false;

        TransactionStatus status = transaction.getStatus();
        return status == TransactionStatus.SUCCESS
                || status == TransactionStatus.FAILED
                || status == TransactionStatus.FLAGGED
                || status == TransactionStatus.ROLLED_BACK;
    }

    public int size() {
        return store.size();
    }
}
