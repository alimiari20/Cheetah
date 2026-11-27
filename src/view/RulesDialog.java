package view;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

final class RulesDialog {
    private RulesDialog() {}

    static void show(GameFrame owner, String rules) {
        var dialog = new JDialog(owner, Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        var body = new UiKit.GradientPage(new BorderLayout(10, 12));
        body.setBorder(new EmptyBorder(14, 20, 16, 20));
        body.add(UiKit.title("HOW TO PLAY", 25), BorderLayout.NORTH);

        var text = UiKit.area("", 16);
        text.setEditable(false);
        Messages.set(text, rules);
        var scroll = new JScrollPane(text);
        scroll.setPreferredSize(new Dimension(600, 325));
        body.add(scroll, BorderLayout.CENTER);

        var actions = UiKit.transparent(new FlowLayout(FlowLayout.TRAILING, 8, 0));
        var close = Theme.button("CLOSE", false);
        close.addActionListener(e -> dialog.dispose());
        var watch = Theme.button("Watch Tutorial", true);
        watch.addActionListener(e -> new TutorialWindow(dialog).setVisible(true));
        actions.add(close);
        actions.add(watch);
        body.add(actions, BorderLayout.SOUTH);
        Messages.bind(body, () -> dialog.setTitle(Messages.get("HOW TO PLAY")));
        GlobalHeader.install(dialog, body);
        dialog.getRootPane().setDefaultButton(watch);
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }
}
