package main.java.com.securepay.exception;

public class RateLimitExceededException extends SecurePayException{

    public RateLimitExceededException(String message) {
        super(message);
    }
}
