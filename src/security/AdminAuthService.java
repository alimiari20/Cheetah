
package security;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class AdminAuthService {

    private static final int ITERATIONS = 600_000;
    private static final int KEY_BITS = 256;
    private static final int SALT_BYTES = 16;

    private final SecureRandom random = new SecureRandom();

    public byte[] generateSalt() {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        return salt;
    }

    public byte[] hashPassword(char[] password, byte[] salt) {
        PBEKeySpec spec =
            new PBEKeySpec(password, salt, ITERATIONS, KEY_BITS);

        try {
            SecretKeyFactory factory =
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");

            return factory.generateSecret(spec).getEncoded();

        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(
                "Password hashing failed", e);
        } finally {
            spec.clearPassword();
        }
    }

    public boolean verifyPassword(
            char[] password,
            byte[] salt,
            byte[] expectedHash) {

        byte[] actualHash = hashPassword(password, salt);

        try {
            return MessageDigest.isEqual(
                actualHash, expectedHash);
        } finally {
            Arrays.fill(actualHash, (byte) 0);
        }
    }

public void registerAdmin(
        char[] password,
        AdminCredentialRepository repository)
        throws java.io.IOException {

    if (password == null || password.length < 12) {
        throw new IllegalArgumentException(
            "Password must contain at least 12 characters");
    }

    if (repository.exists()) {
        throw new IllegalStateException(
            "Admin account already exists");
    }

    byte[] salt = generateSalt();
    byte[] hash = hashPassword(password, salt);

    try {
        repository.save(salt, hash);
    } finally {
        Arrays.fill(salt, (byte) 0);
        Arrays.fill(hash, (byte) 0);
    }
}

public boolean authenticate(
        char[] password,
        AdminCredentialRepository repository)
        throws java.io.IOException {

    if (password == null || !repository.exists()) {
        return false;
    }

    AdminCredentialRepository.Credentials credentials =
        repository.load();

    try {
        return verifyPassword(
            password,
            credentials.salt(),
            credentials.hash()
        );
    } finally {
        Arrays.fill(credentials.salt(), (byte) 0);
        Arrays.fill(credentials.hash(), (byte) 0);
    }
}

}
