
package view;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;

public final class AdminLoginDialog {

    private AdminLoginDialog() {
    }

    public static char[] askPassword(
            Component parent,
            boolean firstTime) {

        JPasswordField password = new JPasswordField(20);
        JPasswordField confirmPassword = new JPasswordField(20);
        JPanel panel = new JPanel(
            new BorderLayout(0, 12)
        );

        String message = firstTime
            ? "Create an administrator password (12+ characters):"
            : "Enter your administrator password:";

        panel.add(
            new JLabel(message),
            BorderLayout.NORTH
        );


if (firstTime) {
  
    JPanel fields = new JPanel(
        new GridLayout(2, 1, 0, 8)
    );

    fields.add(password);
    fields.add(confirmPassword);

    panel.add(fields, BorderLayout.CENTER);

} else {
    panel.add(password, BorderLayout.CENTER);
}


        int result = JOptionPane.showConfirmDialog(
            parent,
            panel,
            firstTime ? "Create Admin Account" : "Admin Login",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE
        );

        if (result != JOptionPane.OK_OPTION) {
            password.setText("");
            return null;
        }


char[] entered = password.getPassword();

char[] confirmation = firstTime
        ? confirmPassword.getPassword()
        : new char[0];

password.setText("");
confirmPassword.setText("");

if (firstTime) {
    boolean matches = Arrays.equals(entered, confirmation);
    Arrays.fill(confirmation, '\0');

    if (!matches) {
        Arrays.fill(entered, '\0');

        JOptionPane.showMessageDialog(
            parent,
            "Passwords do not match.",
            "Admin Security",
            JOptionPane.ERROR_MESSAGE
        );

        return null;
    }
}


        if (entered.length == 0) {
            Arrays.fill(entered, '\0');
            JOptionPane.showMessageDialog(
                parent,
                "Password cannot be empty.",
                "Admin Security",
                JOptionPane.WARNING_MESSAGE
            );
            return null;
        }

        return entered;
    }




    public static boolean showLogin(Component parent,
                                    java.util.function.Predicate<char[]> verifyPassword,
                                    int maxAttempts) {
        Window owner = parent instanceof Window window ? window : SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(owner, "Cheetah Ludo — Admin Login", Dialog.ModalityType.APPLICATION_MODAL);
        JPanel content = new MenuStyle.Page(new BorderLayout(0, 15));
        content.setBorder(BorderFactory.createEmptyBorder(22, 24, 20, 24));
        content.add(MenuStyle.label("Admin Login", 28, Font.BOLD, MenuStyle.TEXT), BorderLayout.NORTH);

        JPanel card = MenuStyle.card(new BorderLayout(), 17);
        JPanel fields = MenuStyle.column();
        JPasswordField password = new JPasswordField(26);
        MenuStyle.field(password);
        password.setMaximumSize(new Dimension(Integer.MAX_VALUE, password.getPreferredSize().height));
        JLabel passwordLabel = MenuStyle.label("Administrator password", 14, Font.BOLD, MenuStyle.TEXT);
        passwordLabel.setLabelFor(password);
        JLabel status = MenuStyle.label("Attempts remaining: " + maxAttempts, 13, Font.PLAIN, MenuStyle.MUTED);
        MenuStyle.addLine(fields, passwordLabel, 0);
        MenuStyle.addLine(fields, password, 7);
        MenuStyle.addLine(fields, status, 11);
        card.add(fields);
        content.add(card, BorderLayout.CENTER);

        JButton cancel = MenuStyle.button("CANCEL", false, dialog::dispose);
        JButton login = MenuStyle.button("LOGIN", true);
        JPanel buttons = UiKit.transparent(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(cancel);
        buttons.add(login);
        content.add(buttons, BorderLayout.SOUTH);

        class Attempt {
            int remaining = maxAttempts;
            boolean authenticated;
            RuntimeException failure;

            void submit() {
                if (!login.isEnabled()) return;
                char[] entered = password.getPassword();
                password.setText("");
                login.setEnabled(false);
                cancel.setEnabled(false);
                password.setEnabled(false);
                dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
                status.setText("Checking password…");

                new SwingWorker<Boolean, Void>() {
                    @Override protected Boolean doInBackground() {
                        try {
                            return verifyPassword.test(entered);
                        } finally {
                            Arrays.fill(entered, '\0');
                        }
                    }

                    @Override protected void done() {
                        try {
                            authenticated = get();
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            failure = new IllegalStateException("Administrator login was interrupted.", ex);
                        } catch (java.util.concurrent.ExecutionException ex) {
                            Throwable cause = ex.getCause();
                            failure = cause instanceof RuntimeException runtime ? runtime
                                    : new IllegalStateException("Unable to verify administrator password.", cause);
                        }

                        if (authenticated || failure != null) {
                            dialog.dispose();
                            return;
                        }
                        if (--remaining <= 0) {
                            JOptionPane.showMessageDialog(dialog,
                                    "Too many failed attempts. Try again in 5 minutes.",
                                    "Admin Account Locked", JOptionPane.WARNING_MESSAGE);
                            dialog.dispose();
                            return;
                        }
                        status.setText("Incorrect password. Attempts remaining: " + remaining);
                        status.setForeground(MenuStyle.GOLD);
                        login.setEnabled(true);
                        cancel.setEnabled(true);
                        password.setEnabled(true);
                        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                        password.requestFocusInWindow();
                    }
                }.execute();
            }
        }

        Attempt attempt = new Attempt();
        login.addActionListener(event -> attempt.submit());
        dialog.setContentPane(content);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.getRootPane().setDefaultButton(login);
        dialog.getRootPane().registerKeyboardAction(event -> {
            if (cancel.isEnabled()) dialog.dispose();
        }, KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        SwingUtilities.invokeLater(password::requestFocusInWindow);
        try {
            dialog.setVisible(true);
        } finally {
            password.setText("");
        }
        if (attempt.failure != null) throw attempt.failure;
        return attempt.authenticated;
    }
}
