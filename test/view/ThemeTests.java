package view;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Color;
import java.nio.file.Path;
import java.util.UUID;
import java.util.prefs.Preferences;

import static view.Theme.Tone.*;

public final class ThemeTests {
    public static void main(String[] args) throws Exception {
        if (args.length == 2) {
            var preferences = Preferences.userRoot().node(args[0]);
            check(Theme.loadDarkMode(preferences) == Boolean.parseBoolean(args[1]), "Saved appearance after restart");
            return;
        }

        var preferences = Preferences.userRoot().node("/com/cheetahludo/tests/" + UUID.randomUUID());
        try {
            check(Theme.loadDarkMode(preferences), "Dark Mode by default");
            Theme.saveDarkMode(preferences, false);
            checkInNewJvm(preferences, false);
            Theme.saveDarkMode(preferences, true);
            checkInNewJvm(preferences, true);
        } finally {
            var parent = preferences.parent();
            preferences.removeNode();
            parent.flush();
        }

        SwingUtilities.invokeAndWait(() -> {
            Theme.applyAppearance(false);
            check(!Theme.isDarkMode() && PAGE_TOP.color().equals(new Color(0x2563B9))
                    && PAGE_BOTTOM.color().equals(new Color(0x65BDF1))
                    && SURFACE.color().equals(new Color(0x2874BD))
                    && SECONDARY.color().equals(new Color(0x195B9F))
                    && BORDER.color().equals(new Color(0x85CFFF))
                    && MUTED.color().equals(new Color(0xE0EEFF))
                    && TEXT.color().equals(Color.WHITE), "Day palette");
            verifySwingColors();
            Theme.applyAppearance(true);
            check(Theme.isDarkMode() && BACKGROUND.color().equals(new Color(0x0B1120)), "Dark palette");
            verifySwingColors();
        });
        System.out.println("Theme tests passed: default, both restart preferences, Swing defaults and contrast.");
    }

    private static void checkInNewJvm(Preferences preferences, boolean expected) throws Exception {
        String executable = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Process child = new ProcessBuilder(executable, "-cp", System.getProperty("java.class.path"),
                ThemeTests.class.getName(), preferences.absolutePath(), Boolean.toString(expected))
                .inheritIO().start();
        check(child.waitFor() == 0, "Preference reload process");
    }

    private static void verifySwingColors() {
        check(BACKGROUND.color().equals(UIManager.getColor("TextField.background")), "Native fields use active palette");
        check(TEXT.color().equals(UIManager.getColor("OptionPane.messageForeground")), "Messages use active palette");
        for (var background : new Theme.Tone[]{BACKGROUND, SURFACE, SECONDARY}) {
            for (var foreground : new Theme.Tone[]{TEXT, MUTED, ACCENT, SUCCESS, DANGER}) {
                double minimum = !Theme.isDarkMode() && background == SURFACE && foreground != TEXT ? 3.8 : 4.5;
                check(contrast(foreground.color(), background.color()) >= minimum,
                        "Readable text: " + foreground + " on " + background);
            }
        }
        check(contrast(PRIMARY.color(), ON_PRIMARY.color()) >= 4.5, "Readable primary action");
    }

    private static double contrast(Color first, Color second) {
        double a = luminance(first);
        double b = luminance(second);
        return (Math.max(a, b) + .05) / (Math.min(a, b) + .05);
    }

    private static double luminance(Color color) {
        return .2126 * linear(color.getRed()) + .7152 * linear(color.getGreen()) + .0722 * linear(color.getBlue());
    }

    private static double linear(int channel) {
        double value = channel / 255.0;
        return value <= .04045 ? value / 12.92 : Math.pow((value + .055) / 1.055, 2.4);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
