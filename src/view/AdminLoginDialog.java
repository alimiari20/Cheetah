
package view;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;

public final class AdminLoginDialog {

    private AdminLoginDialog() {
    }

    public static char[] askNewCode(Component parent, boolean enrollment) {
        JPanel fields = MenuStyle.card(new GridLayout(0, 1, 0, 6), 16);
        JPasswordField code = new JPasswordField(26);
        JPasswordField confirmation = new JPasswordField(26);
        MenuStyle.field(code);
        MenuStyle.field(confirmation);
        JLabel codeLabel = MenuStyle.label("New Authentication Code (16 to 128 characters)",
                14, Font.BOLD, MenuStyle.TEXT);
        codeLabel.setLabelFor(code);
        fields.add(codeLabel);
        fields.add(code);
        JLabel confirmLabel = MenuStyle.label("Confirm Authentication Code", 14, Font.BOLD, MenuStyle.TEXT);
        confirmLabel.setLabelFor(confirmation);
        fields.add(confirmLabel);
        fields.add(confirmation);
        try {
            int result = JOptionPane.showConfirmDialog(parent, fields,
                    enrollment ? "Set Up Authentication Code" : "Change Authentication Code",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (result != JOptionPane.OK_OPTION) {
                return null;
            }

            char[] entered = code.getPassword();
            char[] confirmed = confirmation.getPassword();
            boolean matches = Arrays.equals(entered, confirmed);
            Arrays.fill(confirmed, '\0');
            if (matches) {
                return entered;
            }
            Arrays.fill(entered, '\0');
            JOptionPane.showMessageDialog(parent, "The Authentication Codes did not match.",
                    "Admin Security", JOptionPane.WARNING_MESSAGE);
            return null;
        } finally {
            code.setText("");
            confirmation.setText("");
        }
    }

    public static boolean showLogin(Component parent, String title, String fieldLabel, String secretName,
                                    java.util.function.Predicate<char[]> verify, int maxAttempts,
                                    long lockoutMinutes) {
        Window owner = parent instanceof Window window ? window : SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(owner, "Cheetah Ludo — " + title, Dialog.ModalityType.APPLICATION_MODAL);
        JPanel content = new MenuStyle.Page(new BorderLayout(0, 15));
        content.setBorder(BorderFactory.createEmptyBorder(22, 24, 20, 24));
        content.add(MenuStyle.label(title, 28, Font.BOLD, MenuStyle.TEXT), BorderLayout.NORTH);

        JPanel card = MenuStyle.card(new BorderLayout(), 17);
        JPanel fields = MenuStyle.column();
        JPasswordField password = new JPasswordField(26);
        MenuStyle.field(password);
        password.setMaximumSize(new Dimension(Integer.MAX_VALUE, password.getPreferredSize().height));
        JLabel passwordLabel = MenuStyle.label(fieldLabel, 14, Font.BOLD, MenuStyle.TEXT);
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
                status.setText("Checking " + secretName + "...");

                new SwingWorker<Boolean, Void>() {
                    @Override protected Boolean doInBackground() {
                        try {
                            return verify.test(entered);
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
                                    : new IllegalStateException("Unable to verify administrator credential.", cause);
                        }

                        if (authenticated || failure != null) {
                            dialog.dispose();
                            return;
                        }
                        if (--remaining <= 0) {
                            JOptionPane.showMessageDialog(dialog,
                                    "Too many failed attempts. Try again in " + lockoutMinutes + " minutes.",
                                    "Admin Account Locked", JOptionPane.WARNING_MESSAGE);
                            dialog.dispose();
                            return;
                        }
                        status.setText("Incorrect " + secretName + ". Attempts remaining: " + remaining);
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
