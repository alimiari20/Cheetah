package security;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import static security.AdminAuthService.Factor.CODE;
import static security.AdminAuthService.Factor.PASSWORD;

public final class AdminAuthServiceTests {
    public static void main(String[] args) throws IOException {
        Path directory = Files.createTempDirectory("cheetah-auth-");
        try {
            checkProvisioning(directory.resolve("fresh"));
            checkLegacyEnrollment(directory.resolve("legacy"));
        } finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
        System.out.println("AdminAuthServiceTests passed");
    }

    private static void checkProvisioning(Path home) throws IOException {
        var repository = new AdminCredentialRepository(home);
        var auth = new AdminAuthService(repository);
        var password = auth.generateCode();
        var original = auth.generateCode();
        var incorrect = auth.generateCode();
        check(!auth.isProvisioned(), "Fresh profile was already provisioned");
        auth.provision(password, original);
        expectIOException(() -> auth.provision(password, original));

        var reopened = new AdminAuthService(new AdminCredentialRepository(home));
        check(reopened.verify(PASSWORD, password), "Provisioned password did not persist");
        check(reopened.verify(CODE, original), "Provisioned code did not persist");
        for (int i = 0; i < AdminAuthService.MAX_ATTEMPTS; i++) {
            check(!auth.verify(CODE, incorrect), "Incorrect code accepted");
            check(reopened.verify(PASSWORD, password), "Code failures affected password verification");
        }
        check(reopened.attempts(CODE).lockedSeconds() > 0, "Code lockout did not persist");
        expectIOException(() -> reopened.verify(CODE, original));
        new AdminLockoutRepository(home.resolve("admin.code.lockout")).save(0, System.currentTimeMillis() - 1);
        check(reopened.verify(CODE, original), "Correct code did not work after lockout expired");

        for (int i = 0; i < AdminAuthService.MAX_ATTEMPTS; i++) {
            check(!auth.verify(PASSWORD, incorrect), "Incorrect password accepted");
        }
        check(reopened.attempts(PASSWORD).lockedSeconds() > 0, "Password lockout did not persist");
        expectIOException(() -> reopened.verify(PASSWORD, password));
        new AdminLockoutRepository(home.resolve("admin.lockout")).save(0, System.currentTimeMillis() - 1);
        check(reopened.verify(PASSWORD, password), "Correct password did not work after lockout expired");

        var oldVersion = reopened.codeVersion();
        var changed = auth.generateCode();
        reopened.replaceCode(oldVersion, changed);
        expectIOException(() -> auth.replaceCode(oldVersion, original));
        check(!auth.verify(CODE, original), "Old code worked after replacement");
        check(auth.verify(CODE, changed), "Changed code did not work");
        var generated = auth.generateCode();
        check(generated.length == 32, "Unexpected generated code length");
        auth.replaceCode(auth.codeVersion(), generated);
        check(!reopened.verify(CODE, changed), "Old code worked after regeneration");
        check(reopened.verify(CODE, generated), "Regenerated code did not persist");

        String stored = Files.readString(home.resolve("admin.credentials"));
        for (char[] value : List.of(password, original, incorrect, changed, generated)) {
            check(!stored.contains(new String(value)), "Plaintext credential was stored");
            Arrays.fill(value, '\0');
        }
    }

    private static void checkLegacyEnrollment(Path home) throws IOException {
        Files.createDirectories(home);
        var auth = new AdminAuthService(new AdminCredentialRepository(home));
        char[] password = auth.generateCode();
        byte[] salt = auth.generateSalt();
        var encoder = Base64.getEncoder();
        Files.write(home.resolve("admin.credentials"), List.of("PBKDF2WithHmacSHA256",
                encoder.encodeToString(salt), encoder.encodeToString(auth.hashPassword(password, salt))));
        check(auth.verify(PASSWORD, password), "Legacy password was rejected");
        check(auth.codeVersion() == null, "Legacy account unexpectedly has a code");
        char[] code = auth.generateCode();
        auth.replaceCode(null, code);
        check(auth.verify(PASSWORD, password) && auth.verify(CODE, code), "Legacy enrollment failed");
        Arrays.fill(password, '\0');
        Arrays.fill(code, '\0');
    }

    private static void expectIOException(IoAction action) {
        try {
            action.run();
            throw new AssertionError("Expected an IOException");
        } catch (IOException expected) {
            // Expected.
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    @FunctionalInterface
    private interface IoAction {
        void run() throws IOException;
    }
}
