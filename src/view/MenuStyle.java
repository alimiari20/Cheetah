package view;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/** Styling shared by the menu and administration screens. */
final class MenuStyle {
    static final Color MIDNIGHT = new Color(8, 14, 28);
    static final Color NAVY = new Color(15, 26, 48);
    static final Color SURFACE = new Color(20, 34, 58);
    static final Color BORDER = new Color(57, 73, 101);
    static final Color GOLD = new Color(238, 193, 110);
    static final Color TEXT = new Color(245, 240, 232);
    static final Color MUTED = new Color(173, 187, 209);

    private MenuStyle() {}

    static JLabel label(String text, int size, int style, Color color) {
        return Theme.label(text, size, style, color);
    }

    static JPanel column() {
        JPanel panel = UiKit.transparent();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        return panel;
    }

    static void addLine(JPanel column, JComponent line, int gap) {
        if (gap > 0) column.add(Box.createVerticalStrut(gap));
        line.setAlignmentX(Component.LEFT_ALIGNMENT);
        column.add(line);
    }

    static JPanel card(LayoutManager layout, int padding) {
        JPanel panel = new JPanel(layout) {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = smooth(graphics);
                g.setColor(new Color(0, 0, 0, 55));
                g.fillRoundRect(2, 4, getWidth() - 4, getHeight() - 4, 24, 24);
                g.setColor(NAVY);
                g.fillRoundRect(1, 1, getWidth() - 3, getHeight() - 6, 24, 24);
                g.setColor(BORDER);
                g.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 6, 24, 24);
                g.dispose();
            }
        };
        panel.setOpaque(false);
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
        field.setBackground(MIDNIGHT);
        field.setForeground(TEXT);
        field.setCaretColor(GOLD);
        field.setSelectionColor(new Color(61, 82, 120));
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER), new EmptyBorder(9, 11, 9, 11)));
    }

    static JScrollPane scroll(Component content) {
        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
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

        private void advanceReveal() {
            if (System.nanoTime() - enteredAt >= 220_000_000L) reveal.stop();
            repaint();
        }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = smooth(graphics);
            g.setPaint(new GradientPaint(0, 0, NAVY, getWidth(), getHeight(), MIDNIGHT));
            g.fillRect(0, 0, getWidth(), getHeight());
            if (reveal.isRunning()) {
                float remaining = Math.max(0, 1 - (System.nanoTime() - enteredAt) / 220_000_000f);
                g.setPaint(new GradientPaint(0, 0, new Color(114, 143, 207, (int) (40 * remaining)),
                        0, Math.max(1, getHeight() / 2), new Color(114, 143, 207, 0)));
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
            super(title);
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
            if (detail != null) getAccessibleContext().setAccessibleDescription(detail);
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

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = smooth(graphics);
            int width = getWidth();
            int height = getHeight();
            Color base = primary ? GOLD : SURFACE;
            Color hover = primary ? new Color(255, 224, 161) : new Color(36, 53, 81);
            Color fill = Theme.blend(base, hover, highlight);
            if (getModel().isPressed()) fill = fill.darker();
            Shape shape = new RoundRectangle2D.Float(2, 2, width - 5, height - 5, 15, 15);
            g.setColor(fill);
            g.fill(shape);
            g.setColor(hasFocus() || primary ? GOLD : Theme.blend(BORDER, GOLD, highlight * .6f));
            g.draw(shape);
            if (hasFocus()) {
                g.setColor(primary ? MIDNIGHT : GOLD);
                g.drawRoundRect(5, 5, width - 11, height - 11, 12, 12);
            }
            g.setFont(getFont());
            FontMetrics metrics = g.getFontMetrics();
            g.setColor(!isEnabled() ? MUTED : primary ? MIDNIGHT : TEXT);
            if (detail == null) {
                g.drawString(getText(), (width - metrics.stringWidth(getText())) / 2,
                        (height - metrics.getHeight()) / 2 + metrics.getAscent());
            } else {
                g.drawString(getText(), 19, height / 2 - 3);
                g.drawString("›", width - 27, height / 2 + 4);
                g.setFont(Theme.font(12, Font.PLAIN));
                g.setColor(primary ? MIDNIGHT : MUTED);
                g.drawString(detail, 19, height / 2 + 17);
            }
            g.dispose();
        }
    }
}
