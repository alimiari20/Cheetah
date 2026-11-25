package view;

import javax.swing.*;
import java.awt.Component;
import java.awt.Container;

/** Standard Swing option panes with a live global header. */
public final class Dialogs {
    private Dialogs() {}

    public static JFileChooser fileChooser() {
        return new JFileChooser() {
            @Override public void updateUI() {
                super.updateUI();
                bindChooserLabels(this);
            }

            @Override protected JDialog createDialog(Component parent) {
                JDialog dialog = super.createDialog(parent);
                GlobalHeader.install(dialog, dialog.getContentPane());
                dialog.pack();
                dialog.setLocationRelativeTo(parent);
                return dialog;
            }
        };
    }

    private static void bindChooserLabels(Container parent) {
        for (Component child : parent.getComponents()) {
            if (child instanceof AbstractButton button && button.getText() != null) {
                String label = button.getText();
                String tip = button.getToolTipText();
                Messages.bind(button, () -> {
                    button.setText(Messages.get(label));
                    button.setToolTipText(Messages.get(tip));
                });
            } else if (child instanceof JLabel label && label.getText() != null) {
                Messages.set(label,label.getText());
            }
            if (child instanceof Container container) bindChooserLabels(container);
        }
    }

    public static void showMessageDialog(Component parent, Object message) {
        showMessageDialog(parent, message, "Message", JOptionPane.INFORMATION_MESSAGE);
    }

    public static void showMessageDialog(Component parent, Object message, String title, int type) {
        show(parent, message, title, type, JOptionPane.DEFAULT_OPTION);
    }

    public static int showConfirmDialog(Component parent, Object message, String title, int options) {
        return showConfirmDialog(parent, message, title, options, JOptionPane.QUESTION_MESSAGE);
    }

    public static int showConfirmDialog(Component parent, Object message, String title, int options, int type) {
        return show(parent, message, title, type, options);
    }

    private static int show(Component parent, Object message, String title, int type, int options) {
        Object[] keys = switch (options) {
            case JOptionPane.YES_NO_OPTION -> new Object[]{"Yes", "No"};
            case JOptionPane.YES_NO_CANCEL_OPTION -> new Object[]{"Yes", "No", "Cancel"};
            case JOptionPane.OK_CANCEL_OPTION -> new Object[]{"OK", "Cancel"};
            default -> new Object[]{"OK"};
        };
        JButton[] buttons = new JButton[keys.length];
        Object body = message;
        if (message instanceof String source) {
            JTextArea text = UiKit.area("", Math.max(2, Math.min(10, 2 + source.length() / 62)));
            text.setColumns(42);
            text.setEditable(false);
            Messages.set(text, source);
            body = text;
        }
        for (int i = 0; i < buttons.length; i++) {
            buttons[i] = Theme.button(keys[i].toString(), i == 0);
        }
        JOptionPane pane = new JOptionPane(body, type, options, null, buttons);
        for (int i = 0; i < buttons.length; i++) {
            final int choice = i;
            buttons[i].addActionListener(event -> pane.setValue(choice));
        }
        JDialog dialog = pane.createDialog(parent, title);
        try {
            dialog.getRootPane().setDefaultButton(buttons[0]);
            GlobalHeader.install(dialog, dialog.getContentPane());
            dialog.pack();
            dialog.setLocationRelativeTo(parent);
            dialog.setVisible(true);
            return pane.getValue() instanceof Integer value ? value : JOptionPane.CLOSED_OPTION;
        } finally {
            dialog.dispose();
        }
    }
}
