package view;

import static view.Theme.Tone.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public final class AdminAccountDialog extends JDialog {
    private final JLabel emailLabel = MenuStyle.label("ACCOUNT EMAIL", 13, Font.BOLD, ACCENT);
    private final JTextField emailField = new JTextField(32);
    public AdminAccountDialog(JFrame parent, String currentEmail, boolean configured,
                              java.util.function.BiPredicate<Component, Boolean> updateCode) {
        super(parent, "Cheetah Ludo — Account Settings", true);
        JPanel content = dialogContent("Account Settings");
        JPanel profile = MenuStyle.card(new BorderLayout(0, 12), 18);
        JPanel details = MenuStyle.column();
        MenuStyle.addLine(details, MenuStyle.label("Administrator", 21, Font.BOLD, TEXT), 0);
        MenuStyle.addLine(details, MenuStyle.label("Display name editing is unavailable.",
                13, Font.PLAIN, MUTED), 5);
        emailLabel.setLabelFor(emailField);
        MenuStyle.addLine(details, emailLabel, 17);
        MenuStyle.field(emailField);
        emailField.setEditable(false);
        emailField.setMaximumSize(new Dimension(Integer.MAX_VALUE, emailField.getPreferredSize().height));
        MenuStyle.addLine(details, emailField, 4);
        MenuStyle.addLine(details, MenuStyle.label("Email changes are unavailable.",
                13, Font.PLAIN, MUTED), 10);
        MenuStyle.addLine(details, MenuStyle.label("Authentication Code", 20, Font.BOLD, TEXT), 20);
        JLabel status = MenuStyle.label(configured ? "Configured" : "Not Configured",
                13, Font.BOLD, ACCENT);
        MenuStyle.addLine(details, status, 6);
        MenuStyle.addLine(details, MenuStyle.button("Change Authentication Code", false, () -> {
            if (updateCode.test(this, false)) {
                Messages.set(status,"Configured");
            }
        }), 10);
        MenuStyle.addLine(details, MenuStyle.button("Generate a New Authentication Code", false, () -> {
            if (updateCode.test(this, true)) {
                Messages.set(status,"Configured");
            }
        }), 8);
        profile.add(details, BorderLayout.CENTER);
        content.add(profile, BorderLayout.CENTER);
        finish(this, content, parent);

        emailField.setText(currentEmail);
        emailField.setCaretPosition(0);
        pack();
        setLocationRelativeTo(parent);
    }

    public static void showGeneratedCode(Component parent, char[] code) {
        JTextField display = new JTextField(new String(code), 32);
        MenuStyle.field(display);
        display.setEditable(false);
        JPanel fields = MenuStyle.card(new GridLayout(0, 1, 0, 8), 16);
        fields.add(MenuStyle.label("Save this code securely before closing this dialog.",
                14, Font.BOLD, TEXT));
        fields.add(display);
        try {
            Dialogs.showMessageDialog(parent, fields,
                    "New Authentication Code", JOptionPane.PLAIN_MESSAGE);
        } finally {
            display.setText("");
        }
    }

    static void showSecurity(JFrame parent, int attempts, long minutes) {
        JDialog dialog = new JDialog(parent, "Cheetah Ludo — Security Settings", true);
        JPanel content = dialogContent("Security Settings");
        JPanel card = MenuStyle.card(new BorderLayout(), 18);
        JPanel details = MenuStyle.column();
        MenuStyle.addLine(details, MenuStyle.label("Password and Authentication Code", 20, Font.BOLD, TEXT), 0);
        MenuStyle.addLine(details, MenuStyle.label("Password changes are unavailable.",
                13, Font.PLAIN, MUTED), 5);
        MenuStyle.addLine(details, MenuStyle.label("LOGIN LOCKOUT", 13, Font.BOLD, ACCENT), 18);
        MenuStyle.addLine(details, MenuStyle.label(
                attempts + " failed attempts per credential lock that step for " + minutes + " minutes.",
                13, Font.PLAIN, TEXT), 5);
        card.add(details);
        content.add(card, BorderLayout.CENTER);
        finish(dialog, content, parent);
        dialog.setVisible(true);
    }

    private static JPanel dialogContent(String title) {
        JPanel content = new MenuStyle.Page(new BorderLayout(0, 15));
        content.setBorder(new EmptyBorder(20, 24, 19, 24));
        content.add(MenuStyle.label(title, 27, Font.BOLD, TEXT), BorderLayout.NORTH);
        return content;
    }

    private static void finish(JDialog dialog, JPanel content, JFrame parent) {
        JPanel footer = UiKit.transparent(new FlowLayout(FlowLayout.TRAILING, 0, 0));
        JButton close = MenuStyle.button("CLOSE", false, dialog::dispose);
        footer.add(close);
        content.add(footer, BorderLayout.SOUTH);
        GlobalHeader.install(dialog,content);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.getRootPane().setDefaultButton(close);
        dialog.getRootPane().registerKeyboardAction(event -> dialog.dispose(),
                KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
    }
}
