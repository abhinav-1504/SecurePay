package main.java.com.securepay.security;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

public class PasswordHasher {

    private static final int ITERATIONS = 100_000;
    private static final int KEY_LENGTH = 265;
    private static final int SALT_LENGTH = 16;
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";

    private PasswordHasher() {

    }

    public static String generateSalt() {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[SALT_LENGTH];
        random.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    public static String hashPassword(String password, String salt) {
        try {
            byte[] saltBytes = Base64.getDecoder().decode(salt);
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), saltBytes, ITERATIONS, KEY_LENGTH);
            SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
            byte[] hash = factory.generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new RuntimeException("Password hashing failed", e);
        }
    }

    public static boolean verifyPassword(String password, String salt, String expectedHash) {
        String actualHash = hashPassword(password, salt);
        return constantTimeEquals(actualHash, expectedHash);
    }

    public static boolean constantTimeEquals(String a, String b) {
        if(a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for(int i=0; i<a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
