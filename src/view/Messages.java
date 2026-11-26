package view;

import javax.swing.*;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.prefs.BackingStoreException;

/** Presentation-only localization. English source strings remain available for live retranslation. */
public final class Messages {
    private static final String UPDATE = Messages.class.getName();
    private static final Locale HEBREW = Locale.forLanguageTag("he");
    private static final ResourceBundle HEBREW_MESSAGES = ResourceBundle.getBundle("view.HebrewMessages", HEBREW);
    private static final Pattern PARAMETER = Pattern.compile("\\{(\\d+)(t?)}");
    private static final java.util.List<Template> TEMPLATES = HEBREW_MESSAGES.keySet().stream()
            .filter(key -> PARAMETER.matcher(key).find())
            .sorted(java.util.Comparator.comparingInt(String::length).reversed())
            .map(Template::new).toList();
    private static boolean hebrew;

    private Messages() {}

    static void install() {
        try {
            hebrew = Theme.preferences().get("language", "en").equals("he");
        } catch (SecurityException | IllegalStateException ignored) {
            hebrew = false;
        }
        installSwingLabels();
    }

    static boolean isHebrew() { return hebrew; }

    static void select(boolean selected) throws BackingStoreException {
        apply(selected);
        var preferences = Theme.preferences();
        preferences.put("language", hebrew ? "he" : "en");
        preferences.flush();
    }

    static void apply(boolean selected) {
        if (hebrew == selected) return;
        hebrew = selected;
        installSwingLabels();
        for (Window window : Window.getWindows()) {
            if (!window.isDisplayable()) continue;
            refresh(window);
            window.validate();
            window.repaint();
        }
    }

    static void installSwingLabels() {
        UIManager.put("OptionPane.okButtonText", get("OK"));
        UIManager.put("OptionPane.cancelButtonText", get("Cancel"));
        UIManager.put("OptionPane.yesButtonText", get("Yes"));
        UIManager.put("OptionPane.noButtonText", get("No"));
    }

    public static String get(String source) {
        if (!hebrew || source == null || source.isEmpty()) return source;
        if (HEBREW_MESSAGES.containsKey(source)) return HEBREW_MESSAGES.getString(source);
        for (Template template : TEMPLATES) {
            String translated = template.translate(source);
            if (translated != null) return translated;
        }
        return source;
    }

    private record Template(Pattern pattern, int[] slots, String translation) {
        Template(String key) {
            this(compile(key), PARAMETER.matcher(key).results().mapToInt(m -> Integer.parseInt(m.group(1))).toArray(),
                    HEBREW_MESSAGES.getString(key));
        }

        private static Pattern compile(String key) {
            Matcher parameters = PARAMETER.matcher(key);
            StringBuilder regex = new StringBuilder();
            int start = 0;
            while (parameters.find()) {
                regex.append(Pattern.quote(key.substring(start, parameters.start()))).append("(.*?)");
                start = parameters.end();
            }
            return Pattern.compile(regex.append(Pattern.quote(key.substring(start))).toString(), Pattern.DOTALL);
        }

        String translate(String value) {
            Matcher match = pattern.matcher(value);
            if (!match.matches()) return null;
            Matcher placeholders = PARAMETER.matcher(translation);
            return placeholders.replaceAll(token -> {
                int slot = Integer.parseInt(token.group(1));
                for (int i = 0; i < slots.length; i++) {
                    if (slots[i] == slot) {
                        String argument = match.group(i + 1);
                        return Matcher.quoteReplacement(token.group(2).isEmpty() ? argument : get(argument));
                    }
                }
                return token.group();
            });
        }
    }

    static ComponentOrientation orientation() {
        return hebrew ? ComponentOrientation.RIGHT_TO_LEFT : ComponentOrientation.LEFT_TO_RIGHT;
    }

    static void bind(JComponent component, Runnable update) {
        component.putClientProperty(UPDATE, update);
        update.run();
    }

    static void set(JLabel label, String source) { bind(label, () -> label.setText(get(source))); }
    static void set(AbstractButton button, String source) { bind(button, () -> button.setText(get(source))); }
    static void set(JTextArea area, String source) { bind(area, () -> area.setText(get(source))); }

    static void refresh(Component component) {
        if (component instanceof BoardPanel) {
            component.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
            return;
        }
        component.setComponentOrientation(component instanceof GlobalHeader
                || SwingUtilities.getAncestorOfClass(GlobalHeader.class, component) != null
                ? ComponentOrientation.LEFT_TO_RIGHT : orientation());
        if (component instanceof LoginSecretField field) field.setLocale(hebrew ? HEBREW : Locale.ENGLISH);
        if (component instanceof JComponent swing && swing.getClientProperty(UPDATE) instanceof Runnable update) {
            update.run();
        }
        if (component instanceof JComponent swing && Boolean.TRUE.equals(swing.getClientProperty("cheetah.leadingAlignment"))) {
            swing.setAlignmentX(hebrew ? Component.RIGHT_ALIGNMENT : Component.LEFT_ALIGNMENT);
        }
        if (component instanceof JMenu menu) refresh(menu.getPopupMenu());
        if (component instanceof JTable table) {
            JTableHeader header = table.getTableHeader();
            if (header != null) header.repaint();
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) refresh(child);
        }
    }

    static <T> void renderCombo(JComboBox<T> box) {
        box.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                                     boolean selected, boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                setText(value == null ? "" : get(value.toString()));
                setComponentOrientation(orientation());
                return this;
            }
        });
    }
}
