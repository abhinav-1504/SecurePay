package main.java.com.securepay.exception;

public class AccountLockedException extends SecurePayException{

    public AccountLockedException(String message) {
        super(message);
    }
}
