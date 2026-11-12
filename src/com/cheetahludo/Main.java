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
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Random;

/** Entry point for Eclipse and the executable JAR. No dependency on Eclipse itself. */
public final class Main {
    private Main() {}

    public static void main(String[] args) {
        if (Arrays.asList(args).contains("--check")) {
            try {
                checkInstallation();
            } catch (Throwable error) {
                System.err.println("\nCHECK FAILED: " + error);
                error.printStackTrace(System.err);
                System.exit(1);
            }
            return;
        }

        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("Cheetah Ludo needs a graphical desktop to open a Swing window.");
            System.err.println("Try launching from your Mac/Windows desktop, or run with --check for diagnostics.");
            System.exit(1);
        }

        System.out.println("CHEETAH LUDO: Launching graphical desktop game (not the diagnostic check)...");
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // The cross-platform Swing theme still works.
            }
            try {
                GameFrame view = new GameFrame();
                GameController controller = new GameController(view, new GameEngine(new BoardRepository()),
                        new HistoryRepository(), new Random());
                controller.start();
                view.toFront();
                view.requestFocus();
                System.out.println("CHEETAH LUDO: Main menu window is now open. Look for a separate application window.");
            } catch (Throwable error) {
                reportStartupError(error);
            }
        });
    }

    /** Runs without opening a window, useful when Eclipse or Java shows an empty/failed launch. */
    public static void checkInstallation() {
        System.out.println("CHEETAH LUDO - STARTUP CHECK");
        System.out.println("Java version: " + System.getProperty("java.version"));
        System.out.println("Java vendor:  " + System.getProperty("java.vendor"));
        System.out.println("Java home:    " + System.getProperty("java.home"));
        System.out.println("OS:           " + System.getProperty("os.name") + " " + System.getProperty("os.version"));
        System.out.println("Headless:     " + GraphicsEnvironment.isHeadless());
        System.out.println("Working dir:  " + System.getProperty("user.dir"));
        int feature = Runtime.version().feature();
        if (feature < 17) throw new IllegalStateException("Java 17 or later is required, found Java " + feature);
        BoardRepository boardRepository = new BoardRepository();
        for (Difficulty difficulty : Difficulty.values()) {
            var board = boardRepository.load(difficulty);
            System.out.println(difficulty + " board: OK (" + board.size() + " x " + board.size() + ")");
        }
        int count = new QuestionBank(new Random(1)).total();
        System.out.println("Questions:    OK (" + count + ")");
        System.out.println("CHECK PASSED. The game data and runtime are available.");
    }

    private static void reportStartupError(Throwable error) {
        error.printStackTrace(System.err);
        StringWriter stack = new StringWriter();
        error.printStackTrace(new PrintWriter(stack));
        Path log = Path.of(System.getProperty("user.home", "."), ".cheetah-ludo", "last-startup-error.txt");
        try {
            Files.createDirectories(log.getParent());
            Files.writeString(log, stack.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ioError) {
            System.err.println("Could not save error log: " + ioError);
        }
        String message = "The game could not start.\n\n" + error.getClass().getSimpleName()
                + ": " + String.valueOf(error.getMessage()) + "\n\nError details:\n" + log;
        try {
            JOptionPane.showMessageDialog(null, message, "Cheetah Ludo startup error", JOptionPane.ERROR_MESSAGE);
        } catch (Throwable ignored) {
            // In an unavailable display environment, stderr is still useful.
        }
    }
}
