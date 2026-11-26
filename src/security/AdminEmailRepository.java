package security;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.regex.Pattern;

public final class AdminEmailRepository {
    private static final String INITIAL_EMAIL = "kabiyaarghad@outlook.com";
    private static final String ATOM = "[a-zA-Z0-9!#$%&'*+/=?^_`{|}~-]+";
    private static final String DOMAIN_LABEL =
            "[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?";
    private static final Pattern EMAIL = Pattern.compile(
            ATOM + "(?:\\." + ATOM + ")*@"
                    + DOMAIN_LABEL + "(?:\\." + DOMAIN_LABEL + ")+");

    private final Path file;

    public AdminEmailRepository() {
        this(Path.of(System.getProperty("user.home"), ".cheetah-ludo"));
    }

    AdminEmailRepository(Path directory) {
        file = directory.resolve("admin.email");
    }

    public String loadOrInitialize() throws IOException {
        Optional<String> savedEmail = load();
        if (savedEmail.isPresent()) {
            return savedEmail.get();
        }

        try {
            return saveFirstEmail(INITIAL_EMAIL);
        } catch (FileAlreadyExistsException ex) {
            return load().orElseThrow(() -> ex);
        }
    }

    public Optional<String> load() throws IOException {
        String email;
        try {
            email = Files.readString(file, StandardCharsets.UTF_8);
        } catch (NoSuchFileException ex) {
            return Optional.empty();
        }

        if (!isValid(email)) {
            throw new IOException("The saved administrator email file is invalid.");
        }
        return Optional.of(email);
    }

    public String saveFirstEmail(String input) throws IOException {
        String email = input.strip();
        if (!isValid(email)) {
            throw new IllegalArgumentException("Enter a valid email address.");
        }

        Files.createDirectories(file.getParent());
        OutputStream output = Files.newOutputStream(
                file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        try (output) {
            output.write(email.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ex) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException cleanupError) {
                ex.addSuppressed(cleanupError);
            }
            throw ex;
        }
        return email;
    }

    private static boolean isValid(String email) {
        int at = email.indexOf('@');
        return email.length() <= 254 && at > 0 && at <= 64
                && EMAIL.matcher(email).matches();
    }
}
