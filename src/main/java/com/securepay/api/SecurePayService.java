package main.java.com.securepay.api;

import main.java.com.securepay.exception.SecurePayException;
import main.java.com.securepay.model.Account;
import main.java.com.securepay.model.Role;
import main.java.com.securepay.model.Transaction;
import main.java.com.securepay.model.User;

import java.math.BigDecimal;

public interface SecurePayService {

    User register(String username, String password, Role role) throws SecurePayException;
    String login(String username, String password) throws Exception;
    void logout(String token);
    void deposit(String token, BigDecimal amount) throws Exception;
    Transaction transfer(String token, String toAccountId, BigDecimal amount, String idempotencyKey) throws Exception;
    BigDecimal getBalance(String token) throws Exception;
    Account getAccount(String token) throws Exception;
    void freezeAccount(String adminToken, String accountId) throws Exception;
    void unfreezeAccount(String adminToken, String accountId) throws Exception;
    void blockAccount(String adminToken, String accountId) throws Exception;
}