package main.java.com.securepay.fraud;

import main.java.com.securepay.model.Transaction;

public interface FraudRule {
    String name();
    boolean isSuspicious(Transaction transaction, FraudContext context);
}
