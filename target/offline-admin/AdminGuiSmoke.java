import com.cheetahludo.Main;
import security.AdminAuthService;
import javax.swing.*;
import java.awt.*;
import java.nio.file.*;
import java.security.Permission;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.function.Supplier;

class AdminGuiSmoke {
    public static void main(String[] args) {
        try {
            run();
            System.out.println("Offline JAR Swing smoke test passed");
            System.exit(0);
        } catch (Throwable error) {
            error.printStackTrace();
            System.exit(1);
        }
    }

    private static void run() throws Exception {
        System.setSecurityManager(new SecurityManager() {
            @Override public void checkPermission(Permission permission) {}
            @Override public void checkConnect(String host, int port) {
                throw new SecurityException("Outbound connections disabled for smoke test");
            }
        });
        Path home = Files.createTempDirectory(Path.of("target"), "offline-gui-home-");
        System.setProperty("user.home", home.toAbsolutePath().toString());
        var auth = new AdminAuthService();
        String password = new String(auth.generateCode());
        String original = new String(auth.generateCode());
        String changed = new String(auth.generateCode());
        Main.main(new String[0]);
        click(waitFor(() -> visibleButton(null, "ADMIN LOGIN")));
        dismiss(waitFor(() -> dialog("Admin Setup")));
        if (auth.isProvisioned()) throw new AssertionError("Fresh UI provisioned credentials implicitly");
        auth.provision(password.toCharArray(), original.toCharArray());

        signIn(password, original, true);
        click(waitFor(() -> visibleButton(null, "QUESTION MANAGEMENT")));
        waitFor(() -> visibleComponent(null, JLabel.class,
                label -> label.getText() != null && label.getText().toLowerCase().contains("question management")));
        onEdt(() -> {
            for (Frame frame : Frame.getFrames()) frame.dispose();
            return null;
        });

        Main.main(new String[0]);
        signIn(password, original, false);
        click(waitFor(() -> visibleButton(null, "ACCOUNT SETTINGS")));
        JDialog account = waitFor(() -> dialog("Account Settings"));
        waitFor(() -> visibleComponent(account, JLabel.class, label -> "Configured".equals(label.getText())));
        click(waitFor(() -> visibleButton(account, "Change Authentication Code")));
        reauthenticate(password, original);
        JDialog change = waitFor(() -> dialog("Change Authentication Code"));
        enter(change, changed, 2);
        dismiss(change);
        waitFor(() -> visibleButton(account, "Generate a New Authentication Code"));
        click(waitFor(() -> visibleButton(account, "Generate a New Authentication Code")));
        reauthenticate(password, changed);
        JDialog generated = waitFor(() -> dialog("New Authentication Code"));
        String generatedCode = onEdt(() -> visibleComponent(generated, JTextField.class, field -> !field.isEditable()).getText());
        if (!generatedCode.matches("[0-9a-f]{32}")) throw new AssertionError("Generated code format");
        dismiss(generated);
        click(waitFor(() -> visibleButton(account, "CLOSE")));
        click(waitFor(() -> visibleButton(null, "PLAYER MODE")));
        signIn(password, generatedCode, true);
    }

    private static void signIn(String password, String code, boolean tryWrong) throws Exception {
        click(waitFor(() -> visibleButton(null, "ADMIN LOGIN")));
        submit("Admin Login", password);
        if (tryWrong) {
            submit("Authentication Code", new String(new AdminAuthService().generateCode()));
            waitFor(() -> visibleComponent(dialog("Authentication Code"), JLabel.class,
                    label -> label.getText() != null && label.getText().startsWith("Incorrect code")));
        }
        submit("Authentication Code", code);
        waitFor(() -> visibleButton(null, "QUESTION MANAGEMENT"));
    }

    private static void reauthenticate(String password, String code) throws Exception {
        submit("Admin Login", password);
        submit("Authentication Code", code);
    }

    private static void submit(String title, String value) throws Exception {
        JDialog prompt = waitFor(() -> dialog(title));
        JButton login = waitFor(() -> {
            JButton button = visibleButton(prompt, "LOGIN");
            return button != null && button.isEnabled() ? button : null;
        });
        enter(prompt, value, 1);
        click(login);
    }

    private static void enter(JDialog dialog, String value, int expected) throws Exception {
        onEdt(() -> {
            var fields = new ArrayList<JPasswordField>();
            collect(dialog, JPasswordField.class, fields);
            if (fields.size() != expected) throw new AssertionError("Unexpected number of credential fields");
            fields.forEach(field -> field.setText(value));
            return null;
        });
    }

    private static void dismiss(JDialog dialog) throws Exception {
        click(waitFor(() -> dialog.getRootPane().getDefaultButton()));
    }

    private static void click(JButton button) {
        SwingUtilities.invokeLater(button::doClick);
    }

    private static JDialog dialog(String title) {
        for (Window window : Window.getWindows()) {
            if (window instanceof JDialog dialog && dialog.isShowing()
                    && (dialog.getTitle().equals(title) || dialog.getTitle().endsWith(" " + title))) {
                return dialog;
            }
        }
        return null;
    }

    private static JButton visibleButton(Window scope, String text) {
        return visibleComponent(scope, JButton.class, button -> text.equalsIgnoreCase(button.getText()));
    }

    private static <T extends Component> T visibleComponent(Window scope, Class<T> type,
                                                             java.util.function.Predicate<T> matches) {
        var components = new ArrayList<T>();
        if (scope == null) {
            for (Frame frame : Frame.getFrames()) collect(frame, type, components);
        } else {
            collect(scope, type, components);
        }
        return components.stream().filter(matches).findFirst().orElse(null);
    }

    private static <T extends Component> void collect(Component component, Class<T> type, ArrayList<T> found) {
        if (!component.isShowing()) return;
        if (type.isInstance(component)) found.add(type.cast(component));
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) collect(child, type, found);
        }
    }

    private static <T> T onEdt(Callable<T> action) throws Exception {
        var task = new FutureTask<>(action);
        SwingUtilities.invokeAndWait(task);
        return task.get();
    }

    private static <T> T waitFor(Supplier<T> lookup) throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(8);
        do {
            T value = onEdt(lookup::get);
            if (value != null) return value;
            Thread.sleep(50);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("Expected Swing component did not appear");
    }
}
