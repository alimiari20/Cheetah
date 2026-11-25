package view;

import static view.Theme.Tone.*;

import model.PlayerColor;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.*;

final class PlayerMenuPage extends MenuStyle.Page {
    PlayerMenuPage(Runnable play, Runnable history, Runnable login, Runnable rules, Runnable exit) {
        super(new BorderLayout(0, 22));
        setBorder(new EmptyBorder(24, 34, 24, 34));
        JPanel top = UiKit.transparent(new BorderLayout(12, 0));
        top.add(MenuStyle.label("CHEETAH LUDO", 17, Font.BOLD, ACCENT), BorderLayout.LINE_START);
        top.add(MenuStyle.button("ADMIN LOGIN", false, login), BorderLayout.LINE_END);
        add(top, BorderLayout.NORTH);

        JPanel feature = MenuStyle.card(new GridBagLayout(), 22);
        JPanel copy = MenuStyle.column();
        MenuStyle.addLine(copy, MenuStyle.label("LOCAL MULTIPLAYER  /  2–4 PLAYERS",
                12, Font.BOLD, ACCENT), 0);
        MenuStyle.addLine(copy, MenuStyle.label("Cheetah Ludo", 44, Font.BOLD, TEXT), 11);
        MenuStyle.addLine(copy, MenuStyle.label("Race your rivals to Home.", 17, Font.PLAIN, MUTED), 5);
        JButton start = MenuStyle.button("NEW GAME", "Choose a board and set up your match", true, play);
        start.setMaximumSize(start.getPreferredSize());
        MenuStyle.addLine(copy, start, 26);
        JButton matches = MenuStyle.button("GAME HISTORY", "Review past matches", false, history);
        matches.setMaximumSize(matches.getPreferredSize());
        MenuStyle.addLine(copy, matches, 9);

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.weightx = .42;
        c.weighty = 1;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(0, 14, 0, 12);
        feature.add(copy, c);
        c.gridx = 1;
        c.weightx = .58;
        c.fill = GridBagConstraints.BOTH;
        c.insets = new Insets(0, 0, 0, 0);
        feature.add(new HeroArtwork(), c);
        JPanel center = UiKit.transparent(new GridBagLayout());
        GridBagConstraints stage = new GridBagConstraints();
        stage.weightx = 1;
        stage.fill = GridBagConstraints.HORIZONTAL;
        center.add(feature, stage);
        add(MenuStyle.scroll(center), BorderLayout.CENTER);

        JPanel footer = UiKit.transparent(new FlowLayout(FlowLayout.LEADING, 8, 0));
        footer.add(MenuStyle.button("RULES & CONTROLS", false, rules));
        footer.add(MenuStyle.button("EXIT", false, exit));
        add(footer, BorderLayout.SOUTH);
    }

    private static final class HeroArtwork extends JComponent {
        private static final PlayerColor[] TEAMS = {
            PlayerColor.RED, PlayerColor.YELLOW, PlayerColor.GREEN, PlayerColor.BLUE
        };
        private long start;
        private final Timer motion = new Timer(40, event -> repaint());

        HeroArtwork() {
            setPreferredSize(new Dimension(380, 365));
            setMinimumSize(new Dimension(215, 310));
            addHierarchyListener(event -> {
                if ((event.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) == 0) return;
                if (isShowing()) {
                    start = System.nanoTime();
                    motion.start();
                } else {
                    motion.stop();
                }
            });
        }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = MenuStyle.smooth(graphics);
            double size = Math.min(getWidth(), getHeight());
            if (size <= 0) {
                g.dispose();
                return;
            }
            g.translate(getWidth() / 2.0, getHeight() / 2.0);
            g.scale(size / 400, size / 400);
            double time = (System.nanoTime() - start) / 1_000_000_000.0;
            float glow = (float) (Math.sin(time * 1.2) * .07 + .93);
            g.setPaint(new RadialGradientPaint(new Point2D.Double(0, 0), 193,
                    new float[]{0, .67f, 1}, new Color[]{
                        Theme.blend(SURFACE.color(), BORDER.color(), .65f), SECONDARY.color(), SURFACE.color()
                    }));
            g.fillOval(-193, -193, 386, 386);
            g.setColor(new Color(ACCENT.color().getRed(), ACCENT.color().getGreen(), ACCENT.color().getBlue(), (int) (95 * glow)));
            g.drawOval(-150, -150, 300, 300);
            g.drawOval(-166, -166, 332, 332);
            g.rotate(Math.PI / 4);
            g.drawRoundRect(-106, -106, 212, 212, 23, 23);
            g.rotate(-Math.PI / 4);
            for (int i = 0; i < TEAMS.length; i++) {
                double x = i % 2 == 0 ? -66 : 66;
                double y = i < 2 ? -58 : 66;
                y += 2.6 * Math.sin(time * 1.5 + i);
                g.setColor(new Color(0, 0, 0, 90));
                g.fill(new Ellipse2D.Double(x - 50, y + 41, 100, 15));
                BoardPanel.paintHero(g, i, Theme.player(TEAMS[i]), x, y, 102, false);
            }
            g.dispose();
        }
    }
}
