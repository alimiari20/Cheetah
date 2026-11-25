package view;

import model.BoardRepository;
import model.Difficulty;
import model.GameEngine;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Random;

public final class LanguageTests {
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var label = Theme.label("NEW GAME", 14, Font.BOLD, Theme.Tone.TEXT);
            var board = new BoardPanel();
            var editor = new JTextField("Alice — אליס");
            var root = new JPanel();
            root.add(label);
            root.add(board);
            root.add(editor);

            Messages.apply(true);
            Messages.refresh(root);
            check(label.getText().equals("משחק חדש"), "Existing label changes immediately");
            check(!root.getComponentOrientation().isLeftToRight(), "Hebrew uses RTL");
            check(board.getComponentOrientation().isLeftToRight(), "Board stays LTR");
            check(editor.getText().equals("Alice — אליס"), "User text is preserved");
            check(Messages.get("Alice loses their next turn!").equals("Alice loses their next turn!"),
                    "Names without a message key remain intact");
            check(Messages.get("Surprise: Alice loses their next turn!").contains("Alice מפסיד"),
                    "Surprise translation preserves name");
            check(Messages.get("Alice moved piece 2 by 3 squares and captured Bob's 1.").contains("כלי 1 של Bob"),
                    "Capture translation preserves player names");
            var engine = new GameEngine(new BoardRepository());
            for (var difficulty : Difficulty.values()) {
                var game = engine.start(difficulty, List.of("Alice", "Bob"));
                engine.roll(game, new Random(3));
                String event = game.events().get(game.events().size()-1).replaceFirst("^#\\d+\\s+", "");
                String translated = Messages.get(event);
                check(translated.contains("Alice") && translated.contains("קובייה רגילה")
                        && !translated.contains("Regular:"), "Actual dice event: " + difficulty);
            }
            Messages.apply(false);
            Messages.refresh(root);
            check(label.getText().equals("NEW GAME") && root.getComponentOrientation().isLeftToRight(),
                    "English is restored");
        });
        System.out.println("Language tests passed: live text, RTL, name preservation, and dice/capture/surprise messages.");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
