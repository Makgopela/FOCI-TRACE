package za.co.focigroup.focitrace;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

public final class Passwords {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int ITERATIONS = 210_000;
    private static final int KEY_BITS = 256;

    private Passwords() {}

    public static Hash hash(String password) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        byte[] hash = derive(password, salt, ITERATIONS);
        return new Hash(Base64.getEncoder().encodeToString(hash), Base64.getEncoder().encodeToString(salt), ITERATIONS);
    }

    public static boolean verify(String password, String hashBase64, String saltBase64, int iterations) {
        byte[] expected = Base64.getDecoder().decode(hashBase64);
        byte[] actual = derive(password, Base64.getDecoder().decode(saltBase64), iterations);
        if (expected.length != actual.length) return false;
        int diff = 0;
        for (int i=0;i<expected.length;i++) diff |= expected[i] ^ actual[i];
        return diff == 0;
    }

    private static byte[] derive(String password, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("Password hashing unavailable", e);
        }
    }

    public record Hash(String hashBase64, String saltBase64, int iterations) {}
}
