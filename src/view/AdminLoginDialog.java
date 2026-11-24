
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




public static boolean showLogin(
        Component parent,
        java.util.function.Predicate<char[]> verifyPassword,
        int maxAttempts) {

    Window owner = SwingUtilities.getWindowAncestor(parent);

    JDialog dialog = new JDialog(
            owner,
            "Admin Login",
            Dialog.ModalityType.APPLICATION_MODAL
    );

    JPasswordField passwordField = new JPasswordField(20);
    JLabel status = new JLabel(
            "Attempts remaining: " + maxAttempts
    );
    status.setForeground(Color.RED);

    JButton loginButton = new JButton("LOGIN");
    JButton cancelButton = new JButton("CANCEL");

    JPanel fields = new JPanel(new BorderLayout(0, 12));
    fields.add(
            new JLabel("Enter your administrator password:"),
            BorderLayout.NORTH
    );
    fields.add(passwordField, BorderLayout.CENTER);
    fields.add(status, BorderLayout.SOUTH);

    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    buttons.add(cancelButton);
    buttons.add(loginButton);

    JPanel content = new JPanel(new BorderLayout(0, 15));
    content.setBorder(
            BorderFactory.createEmptyBorder(20, 20, 20, 20)
    );
    content.add(fields, BorderLayout.CENTER);
    content.add(buttons, BorderLayout.SOUTH);

    dialog.setContentPane(content);
    dialog.getRootPane().setDefaultButton(loginButton);
    dialog.setDefaultCloseOperation(
            WindowConstants.DISPOSE_ON_CLOSE
    );

    final boolean[] authenticated = {false};
    final int[] attempts = {0};

    loginButton.addActionListener(event -> {
        char[] entered = passwordField.getPassword();
        passwordField.setText("");

        boolean valid;
        try {
            valid = verifyPassword.test(entered);
        } finally {
            Arrays.fill(entered, '\0');
        }

        if (valid) {
            authenticated[0] = true;
            dialog.dispose();
            return;
        }

        attempts[0]++;
        int remaining = maxAttempts - attempts[0];

        if (remaining <= 0) {
            JOptionPane.showMessageDialog(
                    dialog,
                    "Too many failed attempts.\n"
                    + "Admin login is locked for 5 minutes.",
                    "Admin Account Locked",
                    JOptionPane.WARNING_MESSAGE
            );
            dialog.dispose();
            return;
        }

        status.setText(
                "Incorrect password. Attempts remaining: "
                + remaining
        );
        passwordField.requestFocusInWindow();
    });

    cancelButton.addActionListener(event -> dialog.dispose());

    dialog.pack();
    dialog.setLocationRelativeTo(parent);
    dialog.setVisible(true);

    return authenticated[0];
}



}
