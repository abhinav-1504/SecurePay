package main.java.com.securepay.exception;

public class IdempotencyException extends SecurePayException{

    public IdempotencyException(String message) {
        super(message);
    }
}
