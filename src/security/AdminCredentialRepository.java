
package security;

import java.io.IOException;
import java.nio.file.*;
import java.util.Base64;
import java.util.List;

public final class AdminCredentialRepository {

    private static final Path FILE = Path.of(
        System.getProperty("user.home"),
        ".cheetah-ludo",
        "admin.credentials"
    );

    public boolean exists() {
        return Files.isRegularFile(FILE);
    }

    public void save(byte[] salt, byte[] hash)
            throws IOException {

        Files.createDirectories(FILE.getParent());

        List<String> lines = List.of(
            "PBKDF2WithHmacSHA256",
            Base64.getEncoder().encodeToString(salt),
            Base64.getEncoder().encodeToString(hash)
        );

        Files.write(
            FILE,
            lines,
            StandardOpenOption.CREATE_NEW,
            StandardOpenOption.WRITE
        );
    }

    public Credentials load() throws IOException {

        List<String> lines = Files.readAllLines(FILE);

        if (lines.size() != 3 ||
            !lines.get(0).equals("PBKDF2WithHmacSHA256")) {
            throw new IOException(
                "Invalid admin credentials file");
        }

        try {
            return new Credentials(
                Base64.getDecoder().decode(lines.get(1)),
                Base64.getDecoder().decode(lines.get(2))
            );
        } catch (IllegalArgumentException e) {
            throw new IOException(
                "Corrupted admin credentials file", e);
        }
    }

    public record Credentials(byte[] salt, byte[] hash) {}
}
