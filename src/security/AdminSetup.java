package security;

import java.io.Console;
import java.io.IOException;
import java.util.Arrays;

public final class AdminSetup {
    private AdminSetup() {}

    public static void main(String[] args) {
        Console console = System.console();
        if (console == null) {
            System.err.println("Run java -jar CheetahLudo.jar --setup-admin in an interactive terminal.");
            return;
        }

        AdminAuthService auth = new AdminAuthService();
        if (auth.isProvisioned()) {
            console.printf("An administrator account already exists.%n");
            return;
        }

        console.printf("Set up administrator access for this operating-system account.%n");
        char[] password = readConfirmed(console, "Administrator password (12+ characters): ", "Confirm password: ");
        if (password == null) {
            return;
        }

        try {
            char[] code = readConfirmed(console, "Authentication Code (16 to 128 characters): ", "Confirm code: ");
            if (code == null) {
                return;
            }
            try {
                auth.provision(password, code);
                console.printf("Administrator account created. Open the JAR and select Admin Login.%n");
            } finally {
                Arrays.fill(code, '\0');
            }
        } catch (IOException | IllegalArgumentException ex) {
            System.err.println("Administrator setup failed. " + ex.getMessage());
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private static char[] readConfirmed(Console console, String prompt, String confirmPrompt) {
        char[] secret = console.readPassword("%s", prompt);
        if (secret == null) {
            return null;
        }
        char[] confirm = console.readPassword("%s", confirmPrompt);
        try {
            if (Arrays.equals(secret, confirm)) {
                return secret;
            }
            Arrays.fill(secret, '\0');
            console.printf("Entries did not match. Run setup again.%n");
            return null;
        } finally {
            if (confirm != null) {
                Arrays.fill(confirm, '\0');
            }
        }
    }
}
