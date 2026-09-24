package com.foxaria.proxy;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

final class Passwords {

    private static final int SALT_BYTES = 16;
    private static final int HASH_BYTES = 32;
    private static final int ITERATIONS = 120_000;
    private static final String ALGO = "PBKDF2WithHmacSHA256";
    private static final SecureRandom RNG = new SecureRandom();

    private Passwords() {
    }

    static String randomSaltBase64() {
        byte[] salt = new byte[SALT_BYTES];
        RNG.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    static String hashPassword(String password, String saltBase64) {
        try {
            byte[] salt = Base64.getDecoder().decode(saltBase64);
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, HASH_BYTES * 8);
            SecretKeyFactory skf = SecretKeyFactory.getInstance(ALGO);
            byte[] hash = skf.generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash password", e);
        }
    }

    static boolean verify(String password, String saltBase64, String expectedHashBase64) {
        String actual = hashPassword(password, saltBase64);
        return slowEquals(actual, expectedHashBase64);
    }

    private static boolean slowEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }
}
