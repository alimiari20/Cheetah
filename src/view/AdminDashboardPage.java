package view;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

final class AdminDashboardPage extends MenuStyle.Page {
    private final JTextField address = new JTextField(26);
    private final JLabel emailStatus = MenuStyle.label("ACCOUNT EMAIL", 12, Font.BOLD, MenuStyle.GOLD);

    AdminDashboardPage(Runnable logout, Runnable questions, Runnable history,
                       Runnable account, Runnable security) {
        super(new BorderLayout(0, 24));
        setBorder(new EmptyBorder(24, 34, 24, 34));
        JPanel top = UiKit.transparent(new BorderLayout(12, 0));
        top.add(MenuStyle.label("CHEETAH LUDO", 17, Font.BOLD, MenuStyle.GOLD), BorderLayout.WEST);
        top.add(MenuStyle.button("PLAYER MODE", false, logout), BorderLayout.EAST);
        add(top, BorderLayout.NORTH);

        JPanel workspace = MenuStyle.column();
        MenuStyle.addLine(workspace, MenuStyle.label("ADMIN DASHBOARD", 13, Font.BOLD, MenuStyle.GOLD), 0);
        MenuStyle.addLine(workspace, MenuStyle.label("Welcome, Administrator", 36, Font.BOLD, MenuStyle.TEXT), 5);
        MenuStyle.addLine(workspace, profile(), 22);
        JPanel tools = UiKit.transparent(new GridLayout(2, 2, 12, 12));
        tools.add(MenuStyle.button("QUESTION MANAGEMENT", "Find and maintain the question bank", false, questions));
        tools.add(MenuStyle.button("GAME HISTORY", "Review and export past matches", false, history));
        tools.add(MenuStyle.button("ACCOUNT SETTINGS", "View your profile and email", false, account));
        tools.add(MenuStyle.button("SECURITY SETTINGS", "View password and lockout details", false, security));
        tools.setMaximumSize(new Dimension(Integer.MAX_VALUE, tools.getPreferredSize().height));
        MenuStyle.addLine(workspace, tools, 16);

        JPanel center = UiKit.transparent(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.weightx = 1;
        c.weighty = 1;
        c.anchor = GridBagConstraints.NORTH;
        c.fill = GridBagConstraints.HORIZONTAL;
        center.add(workspace, c);
        add(MenuStyle.scroll(center), BorderLayout.CENTER);
        setEmail(null, false);
    }

    private JPanel profile() {
        JPanel card = MenuStyle.card(new BorderLayout(20, 0), 18);
        card.add(new Avatar(), BorderLayout.WEST);
        JPanel details = MenuStyle.column();
        MenuStyle.addLine(details, MenuStyle.label("Administrator", 22, Font.BOLD, MenuStyle.TEXT), 0);
        emailStatus.setLabelFor(address);
        MenuStyle.addLine(details, emailStatus, 8);
        MenuStyle.field(address);
        address.setEditable(false);
        address.setMaximumSize(new Dimension(Integer.MAX_VALUE, address.getPreferredSize().height));
        MenuStyle.addLine(details, address, 5);
        card.add(details, BorderLayout.CENTER);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
        return card;
    }

    void setEmail(String email, boolean unavailable) {
        emailStatus.setText(email == null ? "ACCOUNT EMAIL" : "ACCOUNT EMAIL  /  UNVERIFIED");
        address.setText(unavailable ? "Email could not be loaded"
                : email == null ? "Set up an email in Account Settings" : email);
        address.setCaretPosition(0);
    }

    private static final class Avatar extends JComponent {
        Avatar() {
            setPreferredSize(new Dimension(94, 84));
        }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = MenuStyle.smooth(graphics);
            g.translate(getWidth() / 2 - 40, getHeight() / 2 - 40);
            g.setPaint(new GradientPaint(0, 0, MenuStyle.SURFACE, 80, 80, MenuStyle.MIDNIGHT));
            g.fillOval(0, 0, 80, 80);
            g.setColor(MenuStyle.GOLD);
            g.drawOval(1, 1, 78, 78);
            g.drawOval(6, 6, 68, 68);
            g.fillOval(31, 20, 18, 18);
            g.fillRoundRect(23, 43, 34, 18, 14, 14);
            g.dispose();
        }
    }
}
