package view;

import static view.Theme.Tone.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/** Styling shared by the menu and administration screens. */
final class MenuStyle {
    private MenuStyle() {}

    static JLabel label(String text, int size, int style, Theme.Tone color) {
        return Theme.label(text, size, style, color);
    }

    static JPanel column() {
        JPanel panel = UiKit.transparent();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        return panel;
    }

    static void addLine(JPanel column, JComponent line, int gap) {
        if (gap > 0) column.add(Box.createVerticalStrut(gap));
        line.setAlignmentX(Messages.isHebrew() ? Component.RIGHT_ALIGNMENT : Component.LEFT_ALIGNMENT);
        line.putClientProperty("cheetah.leadingAlignment", Boolean.TRUE);
        if (line instanceof JLabel label) {
            label.setMaximumSize(new Dimension(Integer.MAX_VALUE, label.getPreferredSize().height));
            label.setHorizontalAlignment(SwingConstants.LEADING);
        }
        column.add(line);
    }

    static JPanel card(LayoutManager layout, int padding) {
        JPanel panel = new UiKit.GlassCard(layout);
        panel.setBorder(new EmptyBorder(padding, padding, padding + 4, padding));
        return panel;
    }

    static JButton button(String title, boolean primary, Runnable action) {
        return button(title, null, primary, action);
    }

    static JButton button(String title, boolean primary) {
        return new MenuButton(title, null, primary);
    }

    static JButton button(String title, String detail, boolean primary, Runnable action) {
        JButton button = new MenuButton(title, detail, primary);
        button.addActionListener(event -> action.run());
        return button;
    }

    static Graphics2D smooth(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return g;
    }

    static void field(JTextField field) {
        UiKit.styleField(field);
    }

    static JScrollPane scroll(Component content) {
        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(20);
        return scroll;
    }

    static class Page extends JPanel {
        private long enteredAt;
        private final Timer reveal = new Timer(16, event -> advanceReveal());

        Page(LayoutManager layout) {
            super(layout);
            setOpaque(true);
            addHierarchyListener(event -> {
                if ((event.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) == 0) return;
                if (isShowing()) {
                    enteredAt = System.nanoTime();
                    reveal.start();
                } else {
                    reveal.stop();
                }
            });
        }

        private static Color tint(Color color, int alpha) {
            return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
        }

        private void advanceReveal() {
            if (System.nanoTime() - enteredAt >= 220_000_000L) reveal.stop();
            repaint();
        }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = smooth(graphics);
            g.setPaint(new GradientPaint(0, 0, PAGE_TOP.color(), getWidth(), getHeight(), PAGE_BOTTOM.color()));
            g.fillRect(0, 0, getWidth(), getHeight());
            if (reveal.isRunning()) {
                float remaining = Math.max(0, 1 - (System.nanoTime() - enteredAt) / 220_000_000f);
                g.setPaint(new GradientPaint(0, 0, tint(ACCENT.color(), (int) (28 * remaining)),
                        0, Math.max(1, getHeight() / 2), tint(ACCENT.color(), 0)));
                g.fillRect(0, 0, getWidth(), getHeight());
            }
            g.dispose();
        }
    }

    private static final class MenuButton extends JButton {
        private final String detail;
        private final boolean primary;
        private float highlight;
        private final Timer animation = new Timer(16, event -> animate());

        MenuButton(String title, String detail, boolean primary) {
            super();
            Messages.set(this, title);
            this.detail = detail;
            this.primary = primary;
            setFont(Theme.font(detail == null ? 13 : 16, Font.BOLD));
            setRolloverEnabled(true);
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(detail == null
                    ? getFontMetrics(getFont()).stringWidth(title) + 42 : 300,
                    detail == null ? 43 : 78));
            if (detail != null) Messages.bind(this, () -> {
                setText(Messages.get(title));
                getAccessibleContext().setAccessibleDescription(Messages.get(detail));
            });
            getModel().addChangeListener(event -> {
                if (isShowing() && highlight != target()) animation.start();
                repaint();
            });
            addHierarchyListener(event -> {
                if (!isShowing()) {
                    animation.stop();
                    highlight = 0;
                }
            });
        }

        private float target() {
            return isEnabled() && getModel().isRollover() ? 1 : 0;
        }

        private void animate() {
            float goal = target();
            highlight += (goal - highlight) * .24f;
            if (Math.abs(goal - highlight) < .02f) {
                highlight = goal;
                animation.stop();
            }
            repaint();
        }

        @Override public Dimension getPreferredSize() {
            Dimension size = super.getPreferredSize();
            Icon icon = getIcon();
            int iconSpace = icon == null ? 0 : icon.getIconWidth() + getIconTextGap();
            int width = getFontMetrics(getFont()).stringWidth(getText()) + 42 + iconSpace;
            return new Dimension(Math.max(size.width,width),size.height);
        }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = smooth(graphics);
            int width = getWidth();
            int height = getHeight();
            UiKit.paintButton(g, this, primary, false, highlight);
            g.setFont(getFont());
            FontMetrics metrics = g.getFontMetrics();
            g.setColor((!isEnabled() ? MUTED : primary ? ON_PRIMARY : TEXT).color());
            if (detail == null) {
                Icon icon = getIcon();
                int iconSpace = icon == null ? 0 : icon.getIconWidth() + getIconTextGap();
                int x = (width - metrics.stringWidth(getText()) - iconSpace) / 2;
                if (icon != null) icon.paintIcon(this, g, x, (height - icon.getIconHeight()) / 2);
                g.drawString(getText(), x + iconSpace, (height - metrics.getHeight()) / 2 + metrics.getAscent());
            } else {
                boolean ltr = getComponentOrientation().isLeftToRight();
                g.drawString(getText(), ltr ? 19 : width - 19 - metrics.stringWidth(getText()), height / 2 - 3);
                g.drawString(ltr ? "›" : "‹", ltr ? width - 27 : 19, height / 2 + 4);
                g.setFont(Theme.font(12, Font.PLAIN));
                g.setColor((primary && isEnabled() ? ON_PRIMARY : MUTED).color());
                String translated = Messages.get(detail);
                g.drawString(translated, ltr ? 19 : width - 19 - g.getFontMetrics().stringWidth(translated), height / 2 + 17);
            }
            g.dispose();
        }
    }
}
