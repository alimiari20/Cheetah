package view;

import controller.TutorialPlayback;
import model.Difficulty;
import model.Replay;
import model.TutorialScript;
import model.TutorialScript.Shot;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Locale;

import static view.Theme.Tone.*;

/** Dedicated player. The storyboard clock drives every animation and supports exact seeking. */
final class TutorialWindow extends JDialog {
    private final TutorialScript script = new TutorialScript();
    private final TutorialPlayback playback = new TutorialPlayback(script);
    private final BoardPanel board = new BoardPanel(false);
    private final Timer timer = new Timer(33, e -> tick());
    private final JSlider timeline = new JSlider(0, script.duration());
    private final JComboBox<TutorialScript.Text> chapters =
            new JComboBox<>(TutorialScript.CHAPTERS.toArray(TutorialScript.Text[]::new));
    private final JLabel heading = label(25, TEXT);
    private final JLabel chapterTitle = label(21, TEXT);
    private final JLabel time = label(12, ACCENT);
    private final JTextArea subtitle = UiKit.area("", 4);
    private final JButton play = Theme.button("", true);
    private final JButton previous = Theme.button("", false);
    private final JButton next = Theme.button("", false);
    private final JButton fullscreen = Theme.button("", false);
    private final JPanel demo = UiKit.transparent(new BorderLayout(0, 10));
    private final GameFrame.DieFace regular = new GameFrame.DieFace();
    private final GameFrame.DieFace special = new GameFrame.DieFace();
    private final JProgressBar countdown = new JProgressBar(0, 30);
    private Shot displayed;
    private int stage = -1;
    private int diceValue = -1;
    private int specialValue = -1;
    private int selectedChapter = -1;
    private boolean syncing;
    private Rectangle normalBounds;

    TutorialWindow(Window owner) {
        super(owner, ModalityType.APPLICATION_MODAL);
        setUndecorated(true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        var root = new UiKit.GradientPage(new BorderLayout(14, 12));
        root.setBorder(new EmptyBorder(14, 19, 15, 19));

        var header = UiKit.transparent(new BorderLayout(12, 0));
        heading.setHorizontalAlignment(SwingConstants.LEADING);
        header.add(heading, BorderLayout.CENTER);
        var tools = UiKit.transparent(new FlowLayout(FlowLayout.TRAILING, 7, 0));
        fullscreen.addActionListener(e -> fullscreen());
        Messages.bind(fullscreen, this::updateFullscreenText);
        tools.add(fullscreen);
        tools.add(button("Close", "סגירה", this::dispose));
        header.add(tools, BorderLayout.LINE_END);
        root.add(header, BorderLayout.NORTH);

        var arena = MenuStyle.card(new BorderLayout(), 5);
        board.setPreferredSize(new Dimension(540, 510));
        arena.add(board);
        root.add(arena, BorderLayout.CENTER);

        var panel = MenuStyle.card(new BorderLayout(0, 12), 14);
        panel.setPreferredSize(new Dimension(350, 455));
        chapterTitle.setHorizontalAlignment(SwingConstants.LEADING);
        var intro = UiKit.transparent(new BorderLayout(0, 9));
        intro.add(chapterTitle, BorderLayout.NORTH);
        subtitle.setOpaque(false);
        subtitle.setEditable(false);
        subtitle.setFocusable(false);
        subtitle.setFont(Theme.font(16, Font.PLAIN));
        subtitle.setBorder(null);
        intro.add(subtitle, BorderLayout.CENTER);
        panel.add(intro, BorderLayout.NORTH);
        panel.add(demo, BorderLayout.CENTER);
        root.add(panel, BorderLayout.LINE_END);

        var scrub = UiKit.transparent(new BorderLayout(10, 0));
        timeline.addChangeListener(e -> {
            if (!syncing) seek(timeline.getValue());
        });
        scrub.add(timeline, BorderLayout.CENTER);
        Messages.bind(time, () -> time.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT));
        scrub.add(time, BorderLayout.LINE_END);

        var controls = UiKit.transparent(new BorderLayout(8, 0));
        var transport = UiKit.transparent(new FlowLayout(FlowLayout.LEADING, 7, 0));
        play.addActionListener(e -> toggle());
        Messages.bind(play, this::updatePlayText);
        previous.addActionListener(e -> chapter(selectedChapter - 1));
        next.addActionListener(e -> chapter(selectedChapter + 1));
        Messages.bind(previous, () -> previous.setText(language("Previous chapter", "הפרק הקודם")));
        Messages.bind(next, () -> next.setText(language("Next chapter", "הפרק הבא")));
        transport.add(play);
        transport.add(button("Restart", "התחלה מחדש", () -> seek(0)));
        transport.add(previous);
        transport.add(next);
        controls.add(transport, BorderLayout.LINE_START);
        chapters.setPreferredSize(new Dimension(220, 34));
        chapters.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                                     boolean selected, boolean focused) {
                super.getListCellRendererComponent(list, value, index, selected, focused);
                if (value instanceof TutorialScript.Text text) setText(text.in(Messages.isHebrew()));
                setComponentOrientation(Messages.orientation());
                return this;
            }
        });
        chapters.addActionListener(e -> {
            if (!syncing) chapter(chapters.getSelectedIndex());
        });
        controls.add(chapters, BorderLayout.LINE_END);
        var bottom = UiKit.transparent(new BorderLayout(0, 5));
        bottom.add(scrub, BorderLayout.NORTH);
        bottom.add(controls, BorderLayout.SOUTH);
        root.add(bottom, BorderLayout.SOUTH);

        GlobalHeader.install(this, root);
        Messages.bind(root, this::localize);
        getRootPane().setDefaultButton(play);
        bind(KeyEvent.VK_ESCAPE, "exit", () -> {
            if (normalBounds != null) fullscreen();
            else dispose();
        });
        bind(KeyEvent.VK_F11, "fullscreen", this::fullscreen);
        var available = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setMinimumSize(new Dimension(Math.min(780, available.width), Math.min(560, available.height)));
        setSize(Math.min(1060, available.width), Math.min(765, available.height));
        setLocationRelativeTo(owner);
        render();
    }

    private void bind(int key, String name, Runnable action) {
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key, 0), name);
        getRootPane().getActionMap().put(name, new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { action.run(); }
        });
    }

    private JButton button(String english, String hebrew, Runnable action) {
        var button = Theme.button("", false);
        Messages.bind(button, () -> button.setText(language(english, hebrew)));
        button.addActionListener(e -> action.run());
        return button;
    }

    private void localize() {
        setTitle(language("Cheetah Ludo Tutorial", "הדרכת צ׳יטה לודו"));
        heading.setText(getTitle());
        chapters.getAccessibleContext().setAccessibleName(language("Chapters", "פרקים"));
        timeline.getAccessibleContext().setAccessibleName(language("Tutorial timeline", "ציר הזמן של ההדרכה"));
        displayed = null;
        render();
    }

    private void fullscreen() {
        if (normalBounds == null) {
            normalBounds = getBounds();
            setBounds(getGraphicsConfiguration().getBounds());
        } else {
            Rectangle restore = normalBounds;
            normalBounds = null;
            setBounds(restore);
        }
        updateFullscreenText();
    }

    private void updateFullscreenText() {
        fullscreen.setText(normalBounds == null ? language("Fullscreen", "מסך מלא")
                : language("Exit fullscreen", "יציאה ממסך מלא"));
    }

    private void updatePlayText() {
        play.setText(playback.playing() ? language("Pause", "השהיה") : language("Play", "ניגון"));
    }

    @Override public void dispose() {
        timer.stop();
        playback.pause(System.nanoTime());
        board.setTutorialMotion(null, null, List.of(), List.of());
        board.setSession(null);
        super.dispose();
    }

    private void toggle() {
        long now = System.nanoTime();
        if (playback.playing()) playback.pause(now);
        else playback.play(now);
        render();
    }

    private void seek(int value) {
        playback.seek(value, System.nanoTime());
        render();
    }

    private void chapter(int index) {
        playback.chapter(index, System.nanoTime());
        render();
    }

    private void tick() {
        playback.tick(System.nanoTime());
        render();
    }

    private void render() {
        var frame = playback.frame();
        Shot shot = frame.shot();
        int elapsed = frame.elapsed();
        int motionStart = Math.min(1250, shot.duration() / 3);
        int motionEnd = Math.min(shot.duration() - 180, motionStart + (shot.move() != null
                && shot.move().captured() >= 0 ? 1850 : 1050));
        double motion = (elapsed - motionStart) / (double)(motionEnd - motionStart);
        int nextStage = switch (shot.visual()) {
            case DIFFICULTY -> Math.min(2, elapsed * 3 / shot.duration());
            case PLAYERS, OPENING, CLOCK -> elapsed >= shot.duration() * .76 ? 1 : 0;
            case THIRD_SIX -> elapsed >= 800 ? 1 : 0;
            default -> shot.move() != null && motion >= 1 ? 1 : 0;
        };
        if (shot != displayed || stage != nextStage) {
            displayed = shot;
            stage = nextStage;
            chapterTitle.setText(TutorialScript.CHAPTERS.get(shot.chapter()).in(Messages.isHebrew()));
            subtitle.setText(shot.caption().in(Messages.isHebrew()));
            var scene = shot.visual() == TutorialScript.Visual.DIFFICULTY
                    ? script.setupPreview(Difficulty.values()[stage]) : shot.project(stage == 1);
            board.setSession(scene);
            if (shot.move() != null && stage == 0) {
                var move = shot.move();
                board.setTutorialMotion(Replay.piece(scene, move.piece()),
                        move.captured() < 0 ? null : Replay.piece(scene, move.captured()), shot.route(), shot.highlights());
            } else {
                board.setTutorialMotion(null, null, List.of(), shot.highlights());
            }
            updateDemo(shot);
        }
        board.setTutorialProgress(shot.move() == null || stage == 1 ? 1 : motion < 0 ? -1 : motion);
        updateDice(shot, elapsed);
        if (shot.visual() == TutorialScript.Visual.CLOCK && stage == 0) {
            int remaining = Math.max(1, 30 - elapsed * 30 / (shot.duration() * 76 / 100));
            countdown.setValue(remaining);
            countdown.setString(remaining + language(" s", " שנ׳"));
        }

        syncing = true;
        try {
            timeline.setValue(playback.position());
            if (selectedChapter != shot.chapter()) {
                selectedChapter = shot.chapter();
                chapters.setSelectedIndex(selectedChapter);
                previous.setEnabled(selectedChapter > 0);
                next.setEnabled(selectedChapter < TutorialScript.CHAPTERS.size() - 1);
            }
        } finally {
            syncing = false;
        }
        time.setText(clock(playback.position()) + " / " + clock(script.duration()));
        updatePlayText();
        if (playback.playing() && !timer.isRunning()) timer.start();
        if (!playback.playing()) timer.stop();
    }

    private void updateDemo(Shot shot) {
        demo.removeAll();
        var stack = UiKit.transparent(new GridLayout(0, 1, 0, 7));
        switch (shot.visual()) {
            case MENU -> {
                tile(stack, "Main Menu", "תפריט ראשי", TEXT);
                tile(stack, "New Game", "משחק חדש", ACCENT);
            }
            case PLAYERS -> {
                tile(stack, "Number of players   ·   2", "מספר שחקנים   ·   2", ACCENT);
                playerTile(stack, true, language("Human Player", "שחקן אנושי"));
                playerTile(stack, false, stage == 0 ? language("Human Player", "שחקן אנושי")
                        : language("Medium AI", "מחשב בינוני"));
            }
            case DIFFICULTY -> {
                tile(stack, "Easy   ·   15 × 15", "קל   ·   15 × 15", stage == 0 ? ACCENT : MUTED);
                tile(stack, "Medium   ·   17 × 17", "בינוני   ·   17 × 17", stage == 1 ? ACCENT : MUTED);
                tile(stack, "Hard   ·   19 × 19", "קשה   ·   19 × 19", stage == 2 ? ACCENT : MUTED);
            }
            case OPENING -> {
                var rolls = UiKit.transparent(new GridLayout(1, 2, 8, 0));
                openingDie(rolls, true, regular);
                openingDie(rolls, false, special);
                stack.add(rolls);
                tile(stack, stage == 0 ? "First player roll" : "Noa goes first",
                        stage == 0 ? "הגרלת השחקן הראשון" : "נועה מתחילה", ACCENT);
            }
            case QUESTIONS -> {
                tile(stack, "Green  ·  Easy  ·  1", "ירוק  ·  קל  ·  1", GREEN_TEXT);
                tile(stack, "Yellow  ·  Medium  ·  2", "צהוב  ·  בינוני  ·  2", YELLOW_TEXT);
                tile(stack, "Red  ·  Hard  ·  3", "אדום  ·  קשה  ·  3", RED_TEXT);
            }
            case CORRECT -> {
                tile(stack, "How many sides does a hexagon have?", "כמה צלעות יש למשושה?", TEXT);
                tile(stack, "5", "5", MUTED);
                tile(stack, stage == 0 ? "6" : "✓  6   ·   +2 squares",
                        stage == 0 ? "6" : "✓  6   ·   שתי משבצות קדימה", stage == 0 ? ACCENT : SUCCESS);
                tile(stack, "7", "7", MUTED);
                tile(stack, "8", "8", MUTED);
            }
            case INCORRECT -> {
                tile(stack, "What is the largest planet?", "מהו כוכב הלכת הגדול ביותר?", TEXT);
                tile(stack, stage == 0 ? "Saturn" : "✕  Saturn",
                        stage == 0 ? "שבתאי" : "✕  שבתאי", stage == 0 ? ACCENT : DANGER);
                tile(stack, stage == 0 ? "Jupiter" : "✓  Jupiter   ·   −3 squares",
                        stage == 0 ? "צדק" : "✓  צדק   ·   שלוש משבצות לאחור", stage == 0 ? MUTED : SUCCESS);
                tile(stack, "Mercury", "כוכב חמה", MUTED);
                tile(stack, "Neptune", "נפטון", MUTED);
            }
            case SURPRISE -> {
                if (shot.move() != null) {
                    tile(stack, stage == 0 ? "Surprise square" : "Move back 3",
                            stage == 0 ? "משבצת הפתעה" : "שלוש משבצות לאחור", SURPRISE);
                } else {
                    tile(stack, "Skip your next turn", "דילוג על התור הבא", SURPRISE);
                    tile(stack, "Immediate UltraBoost roll", "הטלת אולטרה־בוסט מיידית", SURPRISE);
                    tile(stack, "Move an opponent backward", "הזזת יריב לאחור", SURPRISE);
                }
            }
            case CLOCK -> {
                playerTile(stack, stage == 0, stage == 0 ? language("Human turn", "תור אנושי")
                        : language("AI turn", "תור מחשב"));
                if (stage == 0) {
                    countdown.setStringPainted(true);
                    Theme.foreground(countdown, PRIMARY);
                    stack.add(countdown);
                } else tile(stack, "Time expired", "הזמן נגמר", DANGER);
            }
            case HISTORY -> {
                tile(stack, "Main Menu → Game History", "תפריט ראשי ← היסטוריית משחקים", ACCENT);
                tile(stack, "Selected match   ·   Medium", "משחק נבחר   ·   בינוני", TEXT);
                playerTile(stack, true, language("Winner", "מנצחת"));
            }
            case REPLAY -> {
                tile(stack, "Watch Replay", "צפייה בהילוך חוזר", ACCENT);
                tile(stack, stage == 0 ? "Recorded move" : "Final rankings",
                        stage == 0 ? "מהלך מוקלט" : "דירוג סופי", TEXT);
                tile(stack, "Previous event   •   Next event", "אירוע קודם   •   אירוע הבא", MUTED);
            }
            case WIN -> {
                addDice(stack, shot);
                tile(stack, stage == 0 ? "3 / 4 Home" : "4 / 4 Home   ·   Noa wins!",
                        stage == 0 ? "3 מתוך 4 בבית" : "4 מתוך 4 בבית   ·   נועה מנצחת!", SUCCESS);
            }
            case AI, DICE, THIRD_SIX -> addDice(stack, shot);
        }
        demo.add(stack, BorderLayout.NORTH);
        Messages.refresh(demo);
        demo.revalidate();
        demo.repaint();
    }

    private void addDice(JPanel stack, Shot shot) {
        playerTile(stack, shot.active() == 0, shot.active() == 0 ? language("Human turn", "תור אנושי")
                : language("AI turn", "תור מחשב"));
        var dice = UiKit.transparent(new FlowLayout(FlowLayout.LEADING, 8, 3));
        dice.add(regular);
        if (shot.dice().special() != 0) dice.add(special);
        stack.add(dice);
        if (shot.dice().special() != 0) {
            String formula = shot.dice().regular() + " × (" + shot.dice().special() + " − 1) = " + shot.dice().movement();
            String title = shot.dice().ultraBoost() ? language("UltraBoost", "אולטרה־בוסט") : language("Boost", "בוסט");
            if (shot.visual() == TutorialScript.Visual.WIN) {
                tile(stack, "Regular die only", "קובייה רגילה בלבד", ACCENT);
            } else {
                var card = MenuStyle.card(new BorderLayout(10, 0), 8);
                var caption = label(14, ACCENT);
                caption.setText(title);
                var calculation = label(14, TEXT);
                calculation.setText(formula);
                Messages.bind(calculation, () -> calculation.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT));
                card.add(caption, BorderLayout.LINE_START);
                card.add(calculation, BorderLayout.LINE_END);
                stack.add(card);
            }
        } else if (shot.visual() == TutorialScript.Visual.THIRD_SIX && stage == 1) {
            tile(stack, "Third six   ·   Roll canceled", "שש שלישי   ·   ההטלה בוטלה", DANGER);
        } else if (shot.dice().rolledSix() && stage == 1) {
            tile(stack, "Extra roll earned", "זכיתם בהטלה נוספת", SUCCESS);
        }
    }

    private void updateDice(Shot shot, int elapsed) {
        if (shot.dice() == null) return;
        boolean opening = shot.visual() == TutorialScript.Visual.OPENING;
        int normal = dieAt(elapsed, shot.dice().regular(), 6, 2);
        int extra = dieAt(opening ? elapsed - 1250 : elapsed, shot.dice().special(),
                opening || shot.dice().ultraBoost() ? 6 : 3, 0);
        if (diceValue != normal) regular.setValue(diceValue = normal);
        if (specialValue != extra) special.setValue(specialValue = extra);
    }

    private static int dieAt(int elapsed, int result, int sides, int offset) {
        if (elapsed < 0 || result == 0) return 0;
        if (elapsed >= 650) return result;
        return (elapsed / 90 + offset) % sides + 1;
    }

    private void openingDie(JPanel rolls, boolean first, GameFrame.DieFace die) {
        var panel = UiKit.transparent(new FlowLayout(FlowLayout.LEADING, 4, 0));
        var name = label(14, first ? RED_TEXT : GREEN_TEXT);
        name.setText(first ? language("Noa", "נועה") : language("Ari", "ארי"));
        panel.add(name);
        panel.add(die);
        rolls.add(panel);
    }

    private void playerTile(JPanel stack, boolean first, String type) {
        String name = first ? language("Noa", "נועה") : language("Ari", "ארי");
        tile(stack, "●  " + name + "   ·   " + type, "●  " + name + "   ·   " + type,
                first ? RED_TEXT : GREEN_TEXT);
    }

    private void tile(JPanel stack, String english, String hebrew, Theme.Tone color) {
        var label = new JLabel(language(english, hebrew));
        label.setFont(Theme.font(14, Font.BOLD));
        Theme.foreground(label, color);
        var card = MenuStyle.card(new BorderLayout(), 8);
        card.add(label, BorderLayout.CENTER);
        stack.add(card);
    }

    private static String language(String english, String hebrew) {
        return Messages.isHebrew() ? hebrew : english;
    }

    private static JLabel label(int size, Theme.Tone color) {
        var label = new JLabel();
        label.setFont(Theme.font(size, Font.BOLD));
        Theme.foreground(label, color);
        return label;
    }

    private static String clock(int millis) {
        return String.format(Locale.ROOT, "%d:%02d", millis / 60000, millis / 1000 % 60);
    }
}
