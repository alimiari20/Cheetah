package view;

import model.BoardRepository;
import model.Difficulty;
import model.GameEngine;
import model.TutorialScript;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Exercises the real modal Rules entry point and tutorial transport on the EDT. */
public final class TutorialWindowTests {
    public static void main(String[] args) throws Exception {
        var isolatedHome = Files.createTempDirectory(Path.of("."), "tutorial-");
        System.setProperty("user.home", isolatedHome.toAbsolutePath().toString());
        try {
            SwingUtilities.invokeAndWait(TutorialWindowTests::verify);
            try (var files = Files.list(isolatedHome)) {
                check(files.findAny().isEmpty(), "tutorial wrote to the user data directory");
            }
        } finally {
            Files.delete(isolatedHome);
        }
        System.out.println("Tutorial desktop tests passed: Rules, transport, RTL, themes, fullscreen, reopen and isolation.");
    }

    private static void verify() {
        Theme.install();
        boolean initialTheme = Theme.isDarkMode();
        boolean initialLanguage = Messages.isHebrew();
        var failure = new AtomicReference<Throwable>();
        var frame = new GameFrame();
        var live = new GameEngine(new BoardRepository()).start(Difficulty.MEDIUM, List.of("Live", "Opponent"));
        var originalEvents = List.copyOf(live.events());
        var originalPositions = live.players().stream().flatMap(p -> p.pieces().stream())
                .map(piece -> piece.pathIndex()).toList();
        try {
            Messages.apply(false);
            frame.setVisible(true);
            frame.showGame();
            frame.render(live, false);
            check(visibleTutorials().isEmpty(), "tutorial appeared on startup");
            SwingUtilities.invokeLater(() -> {
                var rules = Arrays.stream(frame.getOwnedWindows())
                        .filter(Window::isShowing).map(JDialog.class::cast).findFirst().orElseThrow();
                try {
                    check(all(rules, JTextArea.class).stream().anyMatch(a -> a.getText().contains("Roll 6")),
                            "the written rules are missing");
                    check(button(rules, "Watch Tutorial").isVisible(), "entry button hidden");
                    exercise(rules, failure);
                    if (failure.get() == null) reopen(rules, failure);
                } catch (Throwable error) {
                    failure.set(error);
                } finally {
                    rules.dispose();
                }
            });
            frame.showRules();
            if (failure.get() != null) throw new AssertionError("desktop verification failed", failure.get());
            check(frame.isGameVisible() && live.events().equals(originalEvents)
                    && live.players().stream().flatMap(p -> p.pieces().stream())
                    .map(piece -> piece.pathIndex()).toList().equals(originalPositions), "live game changed");
            check(visibleTutorials().isEmpty(), "tutorial still visible");
        } finally {
            frame.dispose();
            Messages.apply(initialLanguage);
            Theme.applyAppearance(initialTheme);
        }
    }

    private static void exercise(JDialog rules, AtomicReference<Throwable> failure) {
        SwingUtilities.invokeLater(() -> {
            var tutorial = visibleTutorials().get(0);
            try {
                var slider = all(tutorial, JSlider.class).get(0);
                var chapters = all(tutorial, JComboBox.class).stream()
                        .filter(box -> box.getItemCount() == TutorialScript.CHAPTERS.size()).findFirst().orElseThrow();
                button(tutorial, "Next chapter").doClick(0);
                check(chapters.getSelectedIndex() == 1, "next chapter");
                button(tutorial, "Previous chapter").doClick(0);
                check(chapters.getSelectedIndex() == 0, "previous chapter");
                chapters.setSelectedIndex(2);
                check(slider.getValue() == new TutorialScript().chapterStart(2), "chapter picker");
                slider.setValue(slider.getMaximum());
                check(chapters.getSelectedIndex() == chapters.getItemCount() - 1, "scrub");
                button(tutorial, "Restart").doClick(0);
                check(slider.getValue() == 0, "restart");

                Rectangle original = tutorial.getBounds();
                button(tutorial, "Fullscreen").doClick(0);
                check(tutorial.getBounds().equals(tutorial.getGraphicsConfiguration().getBounds()), "fullscreen");
                button(tutorial, "Exit fullscreen").doClick(0);
                check(tutorial.getBounds().equals(original), "restore window");
                for (boolean dark : new boolean[]{false, true}) {
                    Theme.applyAppearance(dark);
                    for (boolean hebrew : new boolean[]{true, false}) {
                        Messages.apply(hebrew);
                        check(tutorial.getComponentOrientation().isLeftToRight() != hebrew, "window orientation");
                        check(all(tutorial, BoardPanel.class).get(0).getComponentOrientation().isLeftToRight(),
                                "board geometry mirrored");
                        var captions = all(tutorial, JTextArea.class);
                        check(captions.get(0).getText().equals(new TutorialScript().shots().get(0).caption().in(hebrew)),
                                "localized subtitle");
                        var play = button(tutorial, hebrew ? "ניגון" : "Play");
                        check(play.isShowing(), "localized play button");
                    }
                }
                slider.setValue(new TutorialScript().chapterStart(2) + 1700);
                int start = slider.getValue();
                button(tutorial, "Play").doClick(0);
                later(180, () -> {
                    try {
                        button(tutorial, "Pause").doClick(0);
                        int paused = slider.getValue();
                        check(paused > start, "playback clock stood still");
                        later(120, () -> {
                            try {
                                check(slider.getValue() == paused, "pause did not freeze playback");
                                button(tutorial, "Play").doClick(0);
                                button(tutorial, "Close").doClick(0);
                            } catch (Throwable error) {
                                failure.set(error);
                                tutorial.dispose();
                            }
                        });
                    } catch (Throwable error) {
                        failure.set(error);
                        tutorial.dispose();
                    }
                });
            } catch (Throwable error) {
                failure.set(error);
                tutorial.dispose();
            }
        });
        button(rules, "Watch Tutorial").doClick(0);
    }

    private static void reopen(JDialog rules, AtomicReference<Throwable> failure) {
        SwingUtilities.invokeLater(() -> {
            var tutorial = visibleTutorials().get(0);
            try {
                check(all(tutorial, JSlider.class).get(0).getValue() == 0, "reopen preserved old position");
                button(tutorial, "Play");
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                button(tutorial, "Close").doClick(0);
            }
        });
        button(rules, "Watch Tutorial").doClick(0);
    }

    private static List<TutorialWindow> visibleTutorials() {
        return Arrays.stream(Window.getWindows()).filter(w -> w instanceof TutorialWindow && w.isShowing())
                .map(TutorialWindow.class::cast).toList();
    }

    private static JButton button(Container root, String text) {
        return all(root, JButton.class).stream().filter(b -> text.equals(b.getText())).findFirst().orElseThrow(
                () -> new AssertionError("Button missing: " + text));
    }

    private static <T extends Component> List<T> all(Component root, Class<T> type) {
        var result = new ArrayList<T>();
        if (type.isInstance(root)) result.add(type.cast(root));
        if (root instanceof Container parent) {
            for (var child : parent.getComponents()) result.addAll(all(child, type));
        }
        return result;
    }

    private static void later(int delay, Runnable action) {
        var timer = new Timer(delay, event -> action.run());
        timer.setRepeats(false);
        timer.start();
    }

    private static void check(boolean passed, String message) {
        if (!passed) throw new AssertionError(message);
    }
}
