
package security;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

public final class AdminLockoutRepository {

    private final Path file;

    AdminLockoutRepository(Path file) {
        this.file = file;
    }

    public record LockoutState(
            int failedAttempts,
            long lockedUntil
    ) {}

    public LockoutState load() throws IOException {
        if (Files.notExists(file)) {
            return new LockoutState(0, 0L);
        }

        List<String> lines = Files.readAllLines(file);

        if (lines.size() != 2) {
            throw new IOException("Invalid admin lockout file.");
        }

        try {
            int attempts = Integer.parseInt(lines.get(0));
            long lockedUntil = Long.parseLong(lines.get(1));

            if (attempts < 0 || attempts >= AdminAuthService.MAX_ATTEMPTS || lockedUntil < 0) {
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

        Files.createDirectories(file.getParent());

        Path temp = Files.createTempFile(
                file.getParent(),
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
                    file,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
