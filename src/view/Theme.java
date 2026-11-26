package view;

import javax.swing.*;
import javax.swing.border.*;

import model.PlayerColor;

import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.metal.DefaultMetalTheme;
import javax.swing.plaf.metal.MetalLookAndFeel;
import java.awt.*;
import java.util.EnumMap;
import java.util.function.Consumer;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/** Prototype-inspired shared palette and cross-platform Swing components. */
public final class Theme {
    private Theme() {}
    /** Semantic Swing colors. Board and character artwork use their own fixed colors. */
    enum Tone {
        PAGE_TOP(0x111C30, 0x2563B9),
        PAGE_BOTTOM(0x0B1120, 0x65BDF1),
        BACKGROUND(0x0B1120, 0x195B9F),
        SURFACE(0x111C30, 0x2874BD),
        SECONDARY(0x1B2940, 0x195B9F),
        TEXT(0xF8FAFC, 0xFFFFFF),
        MUTED(0xA5B4CC, 0xE0EEFF),
        BORDER(0x344560, 0x85CFFF),
        PRIMARY(0xF0C36A, 0xF0C36A),
        ON_PRIMARY(0x0B1120, 0x0B1120),
        ACCENT(0xF0C36A, 0xFFE0A2),
        SUCCESS(0x83D8B3, 0xCBFFDD),
        DANGER(0xFF9AA8, 0xFFE1E8),
        SURPRISE(0xC9A0FF, 0xF1DFFF),
        RED_TEXT(0xFF8794, 0xFFE1E8),
        GREEN_TEXT(0x78D5AD, 0xCBFFDD),
        YELLOW_TEXT(0xF0C36A, 0xFFE0A2),
        BLUE_TEXT(0x8AB9FF, 0xE0EEFF),
        SHADOW(new Color(0, 0, 0, 65), new Color(41, 32, 17, 30)),
        SCRIM(new Color(11, 17, 32, 180), new Color(25, 35, 52, 120));

        private final Color dark;
        private final Color light;

        Tone(int dark, int light) {
            this(new Color(dark), new Color(light));
        }

        Tone(Color dark, Color light) {
            this.dark = dark;
            this.light = light;
        }

        Color color() { return darkMode ? dark : light; }
    }

    private enum Property { FOREGROUND, BACKGROUND, SELECTION, SELECTED_TEXT, CARET, BORDER, CONTENT }
    private static final String STYLES = Theme.class.getName() + ".styles";
    private static boolean darkMode = true;

    static boolean isDarkMode() { return darkMode; }

    static Preferences preferences() {
        return Preferences.userRoot().node("/com/cheetahludo/appearance");
    }

    static boolean loadDarkMode(Preferences preferences) {
        return preferences.getBoolean("darkMode", true);
    }

    static void saveDarkMode(Preferences preferences, boolean dark) throws BackingStoreException {
        preferences.putBoolean("darkMode", dark);
        preferences.flush();
    }

    /** Restore the saved appearance before the first Swing window is built. */
    public static void install() {
        try {
            darkMode = loadDarkMode(preferences());
        } catch (SecurityException | IllegalStateException ignored) {
            darkMode = true;
        }
        installSwingDefaults();
        Messages.install();
    }

    static void setDarkMode(boolean dark) throws BackingStoreException {
        applyAppearance(dark);
        saveDarkMode(preferences(), dark);
    }

    static void applyAppearance(boolean dark) {
        if (darkMode == dark) return;
        darkMode = dark;
        installSwingDefaults();
        Messages.installSwingLabels();
        for (Window window : Window.getWindows()) {
            if (!window.isDisplayable()) continue;
            SwingUtilities.updateComponentTreeUI(window);
            applyStyles(window);
            Messages.refresh(window);
            window.validate();
            window.repaint();
        }
    }

    private static void applyStyles(Component component) {
        if (component instanceof JComponent swing && swing.getClientProperty(STYLES) instanceof Styles styles) {
            styles.values.values().forEach(Runnable::run);
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) applyStyles(child);
        }
    }

    private static final class Styles {
        private final EnumMap<Property, Runnable> values = new EnumMap<>(Property.class);
    }

    private static void bind(JComponent component, Property property, Runnable apply) {
        Styles styles = (Styles) component.getClientProperty(STYLES);
        if (styles == null) {
            styles = new Styles();
            component.putClientProperty(STYLES, styles);
        }
        styles.values.put(property, apply);
        apply.run();
    }

    private static void bind(JComponent component, Property property, Tone tone, Consumer<Color> setter) {
        bind(component, property, () -> setter.accept(tone.color()));
    }

    static void onAppearance(JComponent component, Runnable apply) {
        bind(component, Property.CONTENT, apply);
    }

    static void foreground(JComponent c, Tone tone) { bind(c, Property.FOREGROUND, tone, c::setForeground); }
    static void background(JComponent c, Tone tone) { bind(c, Property.BACKGROUND, tone, c::setBackground); }
    static void textSelection(javax.swing.text.JTextComponent c) {
        bind(c, Property.SELECTION, Tone.PRIMARY, c::setSelectionColor);
        bind(c, Property.SELECTED_TEXT, Tone.ON_PRIMARY, c::setSelectedTextColor);
        bind(c, Property.CARET, Tone.ACCENT, c::setCaretColor);
    }
    static void tableSelection(JTable c) {
        bind(c, Property.SELECTION, Tone.PRIMARY, c::setSelectionBackground);
        bind(c, Property.SELECTED_TEXT, Tone.ON_PRIMARY, c::setSelectionForeground);
    }
    static void listSelection(JList<?> c) {
        bind(c, Property.SELECTION, Tone.SECONDARY, c::setSelectionBackground);
        bind(c, Property.SELECTED_TEXT, Tone.TEXT, c::setSelectionForeground);
    }
    static void lineBorder(JComponent c, Border padding) {
        bind(c, Property.BORDER, Tone.BORDER, color -> {
            Border line = BorderFactory.createLineBorder(color);
            c.setBorder(padding == null ? line : BorderFactory.createCompoundBorder(line, padding));
        });
    }

    private static ColorUIResource resource(Tone tone) { return new ColorUIResource(tone.color()); }

    /** Install shared defaults before building the Swing component tree. */
    private static void installSwingDefaults() {
        MetalLookAndFeel.setCurrentTheme(new DefaultMetalTheme() {
            @Override protected ColorUIResource getPrimary1() { return resource(Tone.BORDER); }
            @Override protected ColorUIResource getPrimary2() { return resource(Tone.ACCENT); }
            @Override protected ColorUIResource getPrimary3() { return resource(Tone.SECONDARY); }
            @Override protected ColorUIResource getSecondary1() { return resource(Tone.BORDER); }
            @Override protected ColorUIResource getSecondary2() { return resource(Tone.MUTED); }
            @Override protected ColorUIResource getSecondary3() { return resource(Tone.SURFACE); }
            @Override protected ColorUIResource getBlack() { return resource(Tone.TEXT); }
            @Override protected ColorUIResource getWhite() { return resource(Tone.BACKGROUND); }
            @Override public FontUIResource getControlTextFont() { return new FontUIResource(font(13, Font.BOLD)); }
            @Override public FontUIResource getSystemTextFont() { return new FontUIResource(font(13, Font.PLAIN)); }
            @Override public FontUIResource getUserTextFont() { return getSystemTextFont(); }
        });
        try {
            UIManager.setLookAndFeel(new MetalLookAndFeel());
        } catch (UnsupportedLookAndFeelException ex) {
            throw new IllegalStateException("Could not initialize Swing appearance", ex);
        }

        UIDefaults defaults = UIManager.getLookAndFeelDefaults();
        for (String control : new String[]{"TextField", "PasswordField", "FormattedTextField", "TextArea",
                "List", "Table", "ComboBox"}) {
            defaults.put(control + ".background", resource(Tone.BACKGROUND));
            defaults.put(control + ".foreground", resource(Tone.TEXT));
            defaults.put(control + ".selectionBackground", resource(Tone.PRIMARY));
            defaults.put(control + ".selectionForeground", resource(Tone.ON_PRIMARY));
            defaults.put(control + ".caretForeground", resource(Tone.ACCENT));
        }
        defaults.put("OptionPane.messageForeground", resource(Tone.TEXT));
        defaults.put("Button.background", resource(Tone.SECONDARY));
        defaults.put("Button.foreground", resource(Tone.TEXT));
        defaults.put("Button.select", resource(Tone.BORDER));
        defaults.put("Button.focus", resource(Tone.ACCENT));
        defaults.put("Button.disabledText", resource(Tone.MUTED));
        defaults.put("Menu.selectionForeground", resource(Tone.TEXT));
        defaults.put("MenuItem.selectionForeground", resource(Tone.TEXT));
        defaults.put("RadioButton.focus", resource(Tone.ACCENT));
        defaults.put("ScrollBar.thumb", resource(Tone.BORDER));
    }

    static Tone playerText(PlayerColor color) {
        return switch (color) {
            case RED -> Tone.RED_TEXT;
            case GREEN -> Tone.GREEN_TEXT;
            case YELLOW -> Tone.YELLOW_TEXT;
            case BLUE -> Tone.BLUE_TEXT;
        };
    }

    static Color teamInk(Color team) {
        return team.getGreen() < 120 ? Color.WHITE : Tone.ON_PRIMARY.color();
    }

    public static Color player(PlayerColor color) {
        return switch (color) {
            case RED -> new Color(230, 24, 50);
            case GREEN -> new Color(6, 158, 103);
            case YELLOW -> new Color(255, 179, 13);
            case BLUE -> new Color(27, 111, 236);
        };
    }
    public static Color light(Color c) { return blend(c,Color.WHITE,.82f); }
    public static Color blend(Color a, Color b,float k) {
        k=Math.max(0,Math.min(1,k));
        return new Color((int)(a.getRed()*(1-k)+b.getRed()*k),(int)(a.getGreen()*(1-k)+b.getGreen()*k),
                (int)(a.getBlue()*(1-k)+b.getBlue()*k));
    }
    public static Font font(float size,int style){return new Font("SansSerif",style,Math.round(size));}
    static JLabel label(String text, int size, int weight, Tone tone) {
        JLabel label = label(text, size, weight, tone.color());
        foreground(label, tone);
        return label;
    }
    public static JLabel label(String text,int size,int weight,Color color){
        JLabel l=new JLabel();Messages.set(l,text);l.setFont(font(size,weight));l.setForeground(color);return l;
    }
    public static JButton button(String text,boolean primary){return new UiKit.GlowButton(text,primary?UiKit.Kind.GOLD:UiKit.Kind.BLUE);}
    public static JPanel surface(LayoutManager layout,int pad) {
        JPanel p=new UiKit.GlassCard(layout);
        p.setBorder(new EmptyBorder(pad,pad,pad,pad));
        return p;
    }
}
