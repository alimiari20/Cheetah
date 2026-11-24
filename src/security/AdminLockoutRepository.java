
package security;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

public final class AdminLockoutRepository {

    private static final Path FILE = Path.of(
            System.getProperty("user.home"),
            ".cheetah-ludo",
            "admin.lockout"
    );

    public record LockoutState(
            int failedAttempts,
            long lockedUntil
    ) {}

    public LockoutState load() throws IOException {
        if (!Files.exists(FILE)) {
            return new LockoutState(0, 0L);
        }

        List<String> lines = Files.readAllLines(FILE);

        if (lines.size() != 2) {
            throw new IOException("Invalid admin lockout file.");
        }

        try {
            int attempts = Integer.parseInt(lines.get(0));
            long lockedUntil = Long.parseLong(lines.get(1));

            if (attempts < 0 || attempts >= 5 || lockedUntil < 0) {
                throw new IOException("Invalid admin lockout values.");
            }

            return new LockoutState(attempts, lockedUntil);

        } catch (NumberFormatException ex) {
            throw new IOException(
                    "Corrupted admin lockout file.", ex
            );
        }
    }

    public void save(int failedAttempts, long lockedUntil)
            throws IOException {

        Files.createDirectories(FILE.getParent());

        Path temp = Files.createTempFile(
                FILE.getParent(),
                "admin-lockout-",
                ".tmp"
        );

        try {
            Files.write(
                    temp,
                    List.of(
                            Integer.toString(failedAttempts),
                            Long.toString(lockedUntil)
                    )
            );

            Files.move(
                    temp,
                    FILE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
