package main.java.com.securepay.exception;

public class InvalidTransactionStateException extends SecurePayException{

    public InvalidTransactionStateException(String message) {
        super(message);
    }
}
