package view;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.function.Consumer;

public final class AdminAccountDialog extends JDialog {
    private final JLabel emailLabel = MenuStyle.label("", 13, Font.BOLD, MenuStyle.GOLD);
    private final JTextField emailField = new JTextField(32);
    private final JButton setupEmailButton;

    public AdminAccountDialog(JFrame parent, String currentEmail,
                              Consumer<AdminAccountDialog> onSetupEmail) {
        super(parent, "Cheetah Ludo — Account Settings", true);
        JPanel content = dialogContent("Account Settings");
        JPanel profile = MenuStyle.card(new BorderLayout(0, 12), 18);
        JPanel details = MenuStyle.column();
        MenuStyle.addLine(details, MenuStyle.label("Administrator", 21, Font.BOLD, MenuStyle.TEXT), 0);
        MenuStyle.addLine(details, MenuStyle.label("Display name editing is unavailable.",
                13, Font.PLAIN, MenuStyle.MUTED), 5);
        emailLabel.setLabelFor(emailField);
        MenuStyle.addLine(details, emailLabel, 17);
        MenuStyle.field(emailField);
        emailField.setEditable(false);
        emailField.setMaximumSize(new Dimension(Integer.MAX_VALUE, emailField.getPreferredSize().height));
        MenuStyle.addLine(details, emailField, 4);
        MenuStyle.addLine(details, MenuStyle.label("Email changes and verification are unavailable.",
                13, Font.PLAIN, MenuStyle.MUTED), 10);
        profile.add(details, BorderLayout.CENTER);
        setupEmailButton = MenuStyle.button("SET UP EMAIL", true, () -> onSetupEmail.accept(this));
        profile.add(setupEmailButton, BorderLayout.SOUTH);
        content.add(profile, BorderLayout.CENTER);
        finish(this, content, parent);
        updateEmail(currentEmail);
        pack();
        setLocationRelativeTo(parent);
    }

    public void updateEmail(String email) {
        emailLabel.setText(email == null ? "ACCOUNT EMAIL" : "ACCOUNT EMAIL  /  UNVERIFIED");
        emailField.setText(email == null ? "Not configured yet" : email);
        emailField.setCaretPosition(0);
        setupEmailButton.setVisible(email == null);
    }

    static void showSecurity(JFrame parent, int attempts, long minutes) {
        JDialog dialog = new JDialog(parent, "Cheetah Ludo — Security Settings", true);
        JPanel content = dialogContent("Security Settings");
        JPanel card = MenuStyle.card(new BorderLayout(), 18);
        JPanel details = MenuStyle.column();
        MenuStyle.addLine(details, MenuStyle.label("Administrator password", 20, Font.BOLD, MenuStyle.TEXT), 0);
        MenuStyle.addLine(details, MenuStyle.label("Password changes are unavailable.",
                13, Font.PLAIN, MenuStyle.MUTED), 5);
        MenuStyle.addLine(details, MenuStyle.label("LOGIN LOCKOUT", 13, Font.BOLD, MenuStyle.GOLD), 18);
        MenuStyle.addLine(details, MenuStyle.label(
                attempts + " failed attempts lock sign-in for " + minutes + " minutes.",
                13, Font.PLAIN, MenuStyle.TEXT), 5);
        card.add(details);
        content.add(card, BorderLayout.CENTER);
        finish(dialog, content, parent);
        dialog.setVisible(true);
    }

    private static JPanel dialogContent(String title) {
        JPanel content = new MenuStyle.Page(new BorderLayout(0, 15));
        content.setBorder(new EmptyBorder(20, 24, 19, 24));
        content.add(MenuStyle.label(title, 27, Font.BOLD, MenuStyle.TEXT), BorderLayout.NORTH);
        return content;
    }

    private static void finish(JDialog dialog, JPanel content, JFrame parent) {
        JPanel footer = UiKit.transparent(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        JButton close = MenuStyle.button("CLOSE", false, dialog::dispose);
        footer.add(close);
        content.add(footer, BorderLayout.SOUTH);
        dialog.setContentPane(content);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.getRootPane().setDefaultButton(close);
        dialog.getRootPane().registerKeyboardAction(event -> dialog.dispose(),
                KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
    }
}
