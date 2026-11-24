
package view;

import java.awt.*;
import java.util.function.Consumer;
import javax.swing.*;

public final class AdminAccountDialog extends JDialog {

    private final JLabel emailLabel = new JLabel();
    private final JTextField emailField = new JTextField();
    private final JButton setupEmailButton =
            new JButton("SET UP EMAIL");
    private final JButton changePasswordButton =
            new JButton("CHANGE PASSWORD");

    public AdminAccountDialog(
            JFrame parent,
            String currentEmail,
            Consumer<AdminAccountDialog> onSetupEmail,
            Runnable onChangePassword) {

        super(parent, "CHEETAH LUDO - EDIT ACCOUNT", true);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(440, 310);
        setResizable(false);
        setLocationRelativeTo(parent);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(
                25, 30, 25, 30));
        content.setBackground(new Color(20, 35, 75));

        JLabel title = new JLabel("ADMIN ACCOUNT SETTINGS");
        title.setFont(new Font("Arial", Font.BOLD, 19));
        title.setForeground(new Color(255, 210, 100));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        emailLabel.setForeground(Color.WHITE);
        emailLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        emailField.setEditable(false);
        emailField.setMaximumSize(new Dimension(
                Integer.MAX_VALUE, emailField.getPreferredSize().height));

        setupEmailButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        changePasswordButton.setAlignmentX(Component.CENTER_ALIGNMENT);

        setupEmailButton.addActionListener(e -> onSetupEmail.accept(this));
        changePasswordButton.addActionListener(
                e -> onChangePassword.run());

        JButton closeButton = new JButton("CLOSE");
        closeButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        closeButton.addActionListener(e -> dispose());

        content.add(title);
        content.add(Box.createVerticalStrut(25));
        content.add(emailLabel);
        content.add(Box.createVerticalStrut(5));
        content.add(emailField);
        content.add(Box.createVerticalStrut(15));
        content.add(setupEmailButton);
        content.add(Box.createVerticalStrut(10));
        content.add(changePasswordButton);
        content.add(Box.createVerticalStrut(15));
        content.add(closeButton);

        setContentPane(content);
        updateEmail(currentEmail);
    }

    public void updateEmail(String email) {
        emailLabel.setText(email == null ? "Current Email" : "Current Email (unverified)");
        emailField.setText(email == null ? "Not configured yet" : email);
        emailField.setCaretPosition(0);
        setupEmailButton.setVisible(email == null);
    }
}
