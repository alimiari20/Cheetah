package view;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.util.Locale;

import static view.Theme.Tone.*;

final class LoginSecretField extends JPanel {
    private static final String HEBREW = Locale.forLanguageTag("he").getLanguage();
    private final JPasswordField input = new JPasswordField(26);
    private final JToggleButton visibility = new JToggleButton();
    private final char mask;

    LoginSecretField(Locale locale) {
        super(new BorderLayout());
        setLocale(locale);
        Theme.background(this, BACKGROUND);
        Theme.lineBorder(this, null);
        input.setFont(Theme.font(13, Font.PLAIN));
        input.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        mask = input.echoCharIsSet() ? input.getEchoChar() : '•';
        input.setEchoChar(mask);
        add(input, BorderLayout.CENTER);

        visibility.setPreferredSize(new Dimension(38, 34));
        visibility.setBorder(BorderFactory.createEmptyBorder());
        visibility.setContentAreaFilled(false);
        visibility.setRolloverEnabled(true);
        visibility.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        visibility.setIcon(new EyeIcon());
        visibility.addItemListener(event -> updateVisibility());
        addPropertyChangeListener("locale", event -> updateVisibility());
        add(visibility, BorderLayout.LINE_END);
        applyComponentOrientation(ComponentOrientation.getOrientation(locale));
        updateVisibility();
        setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
    }

    JPasswordField input() {
        return input;
    }

    void clear() {
        input.setText("");
        visibility.setSelected(false);
    }

    @Override public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        input.setEnabled(enabled);
        visibility.setEnabled(enabled);
    }

    private void updateVisibility() {
        boolean revealed = visibility.isSelected();
        input.setEchoChar(revealed ? (char) 0 : mask);
        boolean hebrew = HEBREW.equals(getLocale().getLanguage());
        String action = revealed
                ? (hebrew ? "הסתרת הטקסט שהוזן" : "Hide entered text")
                : (hebrew ? "הצגת הטקסט שהוזן" : "Show entered text");
        visibility.setToolTipText(action);
        visibility.getAccessibleContext().setAccessibleName(action);
    }

    private static final class EyeIcon implements Icon {
        @Override public int getIconWidth() { return 18; }
        @Override public int getIconHeight() { return 16; }

        @Override public void paintIcon(Component component, Graphics graphics, int x, int y) {
            AbstractButton button = (AbstractButton) component;
            Graphics2D g = MenuStyle.smooth(graphics);
            g.translate(x, y);
            Color ink = (button.isEnabled() && (button.hasFocus() || button.getModel().isRollover())
                    ? ACCENT : MUTED).color();
            g.setColor(ink);
            g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Path2D eye = new Path2D.Float();
            eye.moveTo(1, 8);
            eye.quadTo(9, -2, 17, 8);
            eye.quadTo(9, 18, 1, 8);
            g.draw(eye);
            g.draw(new Ellipse2D.Float(6.5f, 5.5f, 5, 5));
            if (button.isSelected()) {
                g.setColor(BACKGROUND.color());
                g.setStroke(new BasicStroke(4, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawLine(2, 2, 16, 14);
                g.setColor(ink);
                g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawLine(2, 2, 16, 14);
            }
            g.dispose();
        }
    }
}
