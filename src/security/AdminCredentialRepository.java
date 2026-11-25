package security;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public final class AdminCredentialRepository {
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private final Path file;

    public AdminCredentialRepository() {
        this(Path.of(System.getProperty("user.home"), ".cheetah-ludo"));
    }

    AdminCredentialRepository(Path directory) {
        file = directory.resolve("admin.credentials");
    }

    public boolean exists() {
        return !Files.notExists(file, LinkOption.NOFOLLOW_LINKS);
    }

    // Serialize credential changes and attempt counters across app instances.
    <T> T exclusive(IoOperation<T> operation) throws IOException {
        Files.createDirectories(file.getParent());
        try (var channel = FileChannel.open(file.resolveSibling("admin.security.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            var lock = channel.tryLock();
            if (lock == null) {
                throw new IOException("Administrator security is busy. Try again.");
            }
            try (lock) {
                return operation.run();
            }
        } catch (java.nio.channels.OverlappingFileLockException ex) {
            throw new IOException("Administrator security is busy. Try again.", ex);
        }
    }

    void save(Account account) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add(ALGORITHM);
        encode(lines, account.password());
        encode(lines, account.code());

        Path temp = Files.createTempFile(file.getParent(), "admin-credentials-", ".tmp");
        try {
            Files.write(temp, lines);
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static void encode(List<String> lines, Credentials credentials) {
        lines.add(Base64.getEncoder().encodeToString(credentials.salt()));
        lines.add(Base64.getEncoder().encodeToString(credentials.hash()));
    }

    Account load() throws IOException {
        List<String> lines = Files.readAllLines(file);
        if ((lines.size() != 3 && lines.size() != 5) || !ALGORITHM.equals(lines.get(0))) {
            throw new IOException("Invalid administrator credentials file.");
        }

        return new Account(decode(lines, 1), lines.size() == 5 ? decode(lines, 3) : null);
    }

    private static Credentials decode(List<String> lines, int start) throws IOException {
        try {
            byte[] salt = Base64.getDecoder().decode(lines.get(start));
            byte[] hash = Base64.getDecoder().decode(lines.get(start + 1));
            if (salt.length == 16 && hash.length == 32) {
                return new Credentials(salt, hash);
            }
        } catch (IllegalArgumentException ex) {
            throw new IOException("Corrupted administrator credentials file.", ex);
        }
        throw new IOException("Invalid administrator credential lengths.");
    }

    Path directory() {
        return file.getParent();
    }

    record Credentials(byte[] salt, byte[] hash) {}
    record Account(Credentials password, Credentials code) {}

    @FunctionalInterface
    interface IoOperation<T> {
        T run() throws IOException;
    }
}
