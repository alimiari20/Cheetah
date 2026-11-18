
package com.cheetahludo;

import controller.GameController;
import model.BoardRepository;
import model.Difficulty;
import model.GameEngine;
import model.HistoryRepository;
import model.QuestionBank;
import view.GameFrame;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.GraphicsEnvironment;
import java.util.Random;

/** Entry point for the Java Swing version of Cheetah Ludo. */
public final class Main {

    private Main() {
        // Utility class: no instances.
    }

    public static void main(String[] args) {
        if (args.length > 0 && "--check".equals(args[0])) {
            checkInstallation();
            return;
        }

        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("Cheetah Ludo requires a graphical desktop to display Swing.");
            System.err.println("Use --check to verify the installation without opening a window.");
            return;
        }

        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // Swing's default look and feel is also supported.
            }

            try {
                GameFrame frame = new GameFrame();
                GameEngine engine = new GameEngine(new BoardRepository());
                GameController controller = new GameController(
                        frame, engine, new HistoryRepository(), new Random());

                controller.start();
                frame.toFront();
                frame.requestFocus();
                System.out.println("Cheetah Ludo Swing launched successfully.");
            } catch (Exception error) {
                error.printStackTrace();
                JOptionPane.showMessageDialog(null,
                        "Cannot start Cheetah Ludo:\n" + error.getMessage(),
                        "Startup Error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    /** Optional command-line diagnostic: java -jar CheetahLudo.jar --check */
    private static void checkInstallation() {
        if (Runtime.version().feature() < 17) {
            throw new IllegalStateException("Java 17 or later is required.");
        }

        BoardRepository repository = new BoardRepository();
        for (Difficulty difficulty : Difficulty.values()) {
            var board = repository.load(difficulty);
            System.out.println(difficulty + " board: OK (" + board.size() + " x " + board.size() + ")");
        }
        System.out.println("Questions loaded: " + new QuestionBank(new Random(1)).total());
        System.out.println("CHECK PASSED");
    }
}
