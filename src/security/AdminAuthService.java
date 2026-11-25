
package security;

import java.io.IOException;
import java.security.GeneralSecurityException;
import security.AdminCredentialRepository.Account;
import security.AdminCredentialRepository.Credentials;
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

    public static final int MAX_ATTEMPTS = 5;
    public static final long LOCKOUT_MINUTES = 5;
    private static final int MIN_CODE_LENGTH = 16;

    private final AdminCredentialRepository repository;

    public AdminAuthService() {
        this(new AdminCredentialRepository());
    }

    AdminAuthService(AdminCredentialRepository repository) {
        this.repository = repository;
    }

    public boolean isProvisioned() {
        return repository.exists();
    }

    public void provision(char[] password, char[] code) throws IOException {
        if (password.length < 12) {
            throw new IllegalArgumentException("Password must contain at least 12 characters.");
        }
        validateCode(code);
        if (Arrays.equals(password, code)) {
            throw new IllegalArgumentException("Choose different values for the password and Authentication Code.");
        }
        repository.exclusive(() -> {
            if (repository.exists()) {
                throw new IOException("An administrator account already exists.");
            }
            repository.save(new Account(hash(password), hash(code)));
            return null;
        });
    }

    public enum Factor {
        PASSWORD("admin.lockout"), CODE("admin.code.lockout");

        private final String lockoutFile;

        Factor(String lockoutFile) {
            this.lockoutFile = lockoutFile;
        }

        private Credentials credential(Account account) {
            return this == PASSWORD ? account.password() : account.code();
        }
    }

    public record AttemptStatus(int remaining, long lockedSeconds) {}

    public AttemptStatus attempts(Factor factor) throws IOException {
        var state = lockout(factor).load();
        long remainingMillis = Math.max(0, state.lockedUntil() - System.currentTimeMillis());
        return new AttemptStatus(MAX_ATTEMPTS - state.failedAttempts(), (remainingMillis + 999) / 1000);
    }

    public boolean verify(Factor factor, char[] value) throws IOException {
        return repository.exclusive(() -> {
            var lockout = lockout(factor);
            var state = lockout.load();
            if (state.lockedUntil() > System.currentTimeMillis()) {
                throw new IOException("Administrator authentication is locked. Try again after the lockout expires.");
            }

            Credentials credentials = factor.credential(repository.load());
            if (credentials == null) {
                throw new IOException("The Authentication Code has not been configured.");
            }
            if (matches(value, credentials)) {
                lockout.save(0, 0);
                return true;
            }

            int failures = state.failedAttempts() + 1;
            if (failures >= MAX_ATTEMPTS) {
                lockout.save(0, System.currentTimeMillis() + LOCKOUT_MINUTES * 60_000);
            } else {
                lockout.save(failures, 0);
            }
            return false;
        });
    }

    private AdminLockoutRepository lockout(Factor factor) {
        return new AdminLockoutRepository(repository.directory().resolve(factor.lockoutFile));
    }

    // The salt identifies the code version observed before re-authentication.
    public byte[] codeVersion() throws IOException {
        Credentials code = repository.load().code();
        return code == null ? null : code.salt();
    }

    public void replaceCode(byte[] expectedVersion, char[] code) throws IOException {
        validateCode(code);
        repository.exclusive(() -> {
            Account account = repository.load();
            byte[] currentVersion = account.code() == null ? null : account.code().salt();
            if (!Arrays.equals(expectedVersion, currentVersion)) {
                throw new IOException("The Authentication Code changed. Start again.");
            }
            if (matches(code, account.password())) {
                throw new IllegalArgumentException("Choose a code different from the administrator password.");
            }
            if (account.code() != null && matches(code, account.code())) {
                throw new IllegalArgumentException("Choose a code different from the current Authentication Code.");
            }
            repository.save(new Account(account.password(), hash(code)));
            return null;
        });
    }

    public char[] generateCode() {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        char[] code = new char[32];
        for (int i = 0; i < bytes.length; i++) {
            code[2 * i] = Character.forDigit((bytes[i] & 0xff) >>> 4, 16);
            code[2 * i + 1] = Character.forDigit(bytes[i] & 0x0f, 16);
        }
        Arrays.fill(bytes, (byte) 0);
        return code;
    }

    private static void validateCode(char[] code) {
        if (code.length < MIN_CODE_LENGTH || code.length > 128 || isBlank(code)) {
            throw new IllegalArgumentException("Use an Authentication Code of 16 to 128 characters.");
        }
    }

    private static boolean isBlank(char[] code) {
        for (char character : code) {
            if (!Character.isWhitespace(character)) {
                return false;
            }
        }
        return true;
    }

    private Credentials hash(char[] value) {
        byte[] salt = generateSalt();
        return new Credentials(salt, hashPassword(value, salt));
    }

    private boolean matches(char[] value, Credentials credential) {
        return verifyPassword(value, credential.salt(), credential.hash());
    }
}
