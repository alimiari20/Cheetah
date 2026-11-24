package security;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AdminEmailRepositoryTests {
    public static void main(String[] args) throws IOException {
        Path directory = Files.createTempDirectory("cheetah-admin-email-");
        Path credentials = directory.resolve("admin.credentials");
        Path emailFile = directory.resolve("admin.email");
        try {
            Files.writeString(credentials, "existing credentials fixture");
            AdminEmailRepository repository = new AdminEmailRepository(directory);
            check(repository.load().isEmpty(), "Email should initially be absent");

            for (String invalid : new String[]{"", "missing-at.example", "a..b@example.test",
                    "a@-example.test", "a@example.test\nb@example.test"}) {
                try {
                    repository.saveFirstEmail(invalid);
                    throw new AssertionError("Accepted an invalid email address");
                } catch (IllegalArgumentException expected) {
                    check(repository.load().isEmpty(), "Invalid input created an email file");
                }
            }

            String address = "Admin+game@example.test";
            check(repository.saveFirstEmail(" " + address + " ").equals(address),
                    "Setup should trim surrounding whitespace");
            AdminEmailRepository reopened = new AdminEmailRepository(directory);
            check(reopened.load().orElseThrow().equals(address), "Saved email did not reload");

            try {
                reopened.saveFirstEmail("other@example.test");
                throw new AssertionError("Replaced an existing email");
            } catch (FileAlreadyExistsException expected) {
                check(reopened.load().orElseThrow().equals(address), "Stored email changed");
            }

            Files.writeString(emailFile, "invalid stored email");
            try {
                reopened.load();
                throw new AssertionError("Accepted a corrupt email file");
            } catch (IOException expected) {
                check(Files.readString(emailFile).equals("invalid stored email"),
                        "Loading changed the corrupt file");
            }
            check(Files.readString(credentials).equals("existing credentials fixture"),
                    "Email operations changed credentials");
        } finally {
            Files.deleteIfExists(emailFile);
            Files.deleteIfExists(credentials);
            Files.deleteIfExists(directory);
        }
        System.out.println("AdminEmailRepositoryTests passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
