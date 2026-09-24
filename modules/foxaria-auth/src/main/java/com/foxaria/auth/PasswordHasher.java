package com.foxaria.auth;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

public final class PasswordHasher {

    private final SecureRandom secureRandom = new SecureRandom();

    public HashPayload hash(String password, int iterations) {
        try {
            byte[] salt = new byte[16];
            secureRandom.nextBytes(salt);
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, 256);
            byte[] hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return new HashPayload(
                Base64.getEncoder().encodeToString(hash),
                Base64.getEncoder().encodeToString(salt),
                iterations
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to hash password", exception);
        }
    }

    public boolean verify(String password, String encodedHash, String encodedSalt, int iterations) {
        try {
            byte[] salt = Base64.getDecoder().decode(encodedSalt);
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, 256);
            byte[] hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            String recalculated = Base64.getEncoder().encodeToString(hash);
            return recalculated.equals(encodedHash);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to verify password", exception);
        }
    }

    public record HashPayload(String hash, String salt, int iterations) {
    }
}
