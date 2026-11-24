
package security;

import java.io.Console;
import java.io.IOException;
import java.util.Arrays;

public final class AdminSetup {

    public static void main(String[] args) {
        Console console = System.console();

        if (console == null) {
            System.err.println(
                "Run AdminSetup from a terminal, not Eclipse Run."
            );
            return;
        }

        AdminCredentialRepository repository =
                new AdminCredentialRepository();

        if (repository.exists()) {
            System.out.println(
                "Admin account already exists."
            );
            return;
        }

        char[] password = console.readPassword(
                "Create admin password: "
        );

        if (password == null) {
            return;
        }

        char[] confirmation = console.readPassword(
                "Confirm admin password: "
        );

        if (confirmation == null) {
            Arrays.fill(password, '\0');
            return;
        }

        try {
            if (!Arrays.equals(password, confirmation)) {
                System.out.println(
                    "Passwords do not match."
                );
                return;
            }

            AdminAuthService authService =
                    new AdminAuthService();

            authService.registerAdmin(password, repository);

            System.out.println(
                "Admin account created successfully."
            );

        } catch (IOException | RuntimeException ex) {
            System.err.println(
                "Admin setup failed: " + ex.getMessage()
            );
        } finally {
            Arrays.fill(password, '\0');
            Arrays.fill(confirmation, '\0');
        }
    }
}
