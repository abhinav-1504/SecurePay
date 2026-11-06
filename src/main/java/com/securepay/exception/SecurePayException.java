package main.java.com.securepay.exception;

public class SecurePayException extends Exception{
    public SecurePayException(String message) {
        super(message);
    }

    public SecurePayException(String message, Throwable cause) {
        super(message, cause);
    }

}
