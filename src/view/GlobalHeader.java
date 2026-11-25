package view;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.util.prefs.BackingStoreException;

import static view.Theme.Tone.*;

/** Shared appearance and language controls for the frame and modal windows. */
final class GlobalHeader extends JPanel {
    private final JToggleButton theme = new ThemeSwitch();
    private final JComboBox<String> language = new JComboBox<>(new String[]{"English", "עברית"});
    private boolean syncing;

    GlobalHeader() {
        super(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        setBorder(new EmptyBorder(0, 4, 0, 6));
        Theme.background(this, SURFACE);
        language.setPreferredSize(new Dimension(108, 28));
        add(theme);
        add(language);
        Theme.onAppearance(this, this::sync);
        Messages.bind(this, this::sync);
        theme.addActionListener(event -> change(() -> Theme.setDarkMode(theme.isSelected())));
        language.addActionListener(event -> {
            boolean hebrew = language.getSelectedIndex() == 1;
            if (hebrew != Messages.isHebrew()) change(() -> Messages.select(hebrew));
        });
    }

    private void sync() {
        syncing = true;
        try {
            boolean dark = Theme.isDarkMode();
            theme.setSelected(dark);
            String action = Messages.get(dark ? "Switch to Day Mode" : "Switch to Dark Mode");
            theme.setToolTipText(action);
            theme.getAccessibleContext().setAccessibleName(action);
            language.getAccessibleContext().setAccessibleName(Messages.get("Language"));
            language.setSelectedIndex(Messages.isHebrew() ? 1 : 0);
        } finally {
            syncing = false;
        }
    }

    private void change(PreferenceChange change) {
        if (syncing) return;
        try {
            change.apply();
        } catch (BackingStoreException | SecurityException | IllegalStateException error) {
            Dialogs.showMessageDialog(this, "The change applies to this session. Saving the preference failed.");
        }
    }

    @FunctionalInterface
    private interface PreferenceChange { void apply() throws BackingStoreException; }

    private static final class ThemeSwitch extends JToggleButton {
        ThemeSwitch() {
            setPreferredSize(new Dimension(68, 28));
            setContentAreaFilled(false);
            setBorderPainted(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = MenuStyle.smooth(graphics);
            int width=getWidth(), height=getHeight();
            g.setColor(SECONDARY.color());
            g.fillRoundRect(1,1,width-3,height-3,24,24);
            g.setColor((hasFocus()||getModel().isRollover()?ACCENT:BORDER).color());
            g.drawRoundRect(1,1,width-3,height-3,24,24);
            g.setColor(PRIMARY.color());
            g.fillRoundRect(isSelected()?width/2:3,3,width/2-4,height-7,20,20);
            int cy=height/2-1, sun=width/4, moon=width*3/4-1;
            g.setColor((isSelected()?TEXT:ON_PRIMARY).color());
            g.fillOval(sun-4,cy-4,8,8);
            g.setStroke(new BasicStroke(1.1f));
            for(int i=0;i<8;i++){
                double a=i*Math.PI/4;
                g.drawLine(sun+(int)Math.round(5.5*Math.cos(a)),cy+(int)Math.round(5.5*Math.sin(a)),
                        sun+(int)Math.round(8*Math.cos(a)),cy+(int)Math.round(8*Math.sin(a)));
            }
            g.setColor((isSelected()?ON_PRIMARY:TEXT).color());
            Area crescent=new Area(new Ellipse2D.Double(moon-7,cy-7,14,14));
            crescent.subtract(new Area(new Ellipse2D.Double(moon-2,cy-9,12,12)));
            g.fill(crescent);
            g.dispose();
        }
    }

    static JPanel wrap(Component body, GlobalHeader header) {
        JPanel shell = new JPanel(new BorderLayout());
        shell.add(header, BorderLayout.NORTH);
        shell.add(body, BorderLayout.CENTER);
        return shell;
    }

    static void install(JDialog dialog, Container body) {
        String title = dialog.getTitle();
        JPanel shell = wrap(body, new GlobalHeader());
        Messages.bind(shell, () -> dialog.setTitle(Messages.get(title)));
        dialog.setContentPane(shell);
        Messages.refresh(dialog);
    }
}
