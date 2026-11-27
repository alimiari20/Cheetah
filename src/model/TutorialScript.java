package model;

import java.util.ArrayList;
import java.util.List;

/**
 * Fixed storyboard. Positions are indices into the shipped board paths.
 * Projections are private display copies with no engine, observers or persistence.
 */
public final class TutorialScript {
    public record Text(String english, String hebrew) {
        public String in(boolean rtl) { return rtl ? hebrew : english; }
    }

    public enum Visual {
        MENU, PLAYERS, DIFFICULTY, OPENING, DICE, THIRD_SIX, QUESTIONS, CORRECT, INCORRECT,
        SURPRISE, CLOCK, AI, WIN, HISTORY, REPLAY
    }

    public record Move(int piece, int destination, int captured) {}

    public record Shot(int chapter, int duration, Board board, Visual visual, Text caption, DiceRoll dice,
                       int active, List<Integer> positions, Move move, List<String> highlights) {
        public Shot {
            positions = List.copyOf(positions);
            highlights = List.copyOf(highlights);
        }

        public GameSession project(boolean afterMove) { return project(board, afterMove); }

        GameSession project(Board displayBoard, boolean afterMove) {
            var scene = new GameSession(displayBoard, List.of(new Player("Noa", PlayerColor.RED),
                    new Player("Ari", PlayerColor.GREEN)));
            scene.currentPlayer(active);
            for (int i = 0; i < positions.size(); i++) {
                int index = positions.get(i);
                if (afterMove && move != null) {
                    if (i == move.piece()) index = move.destination();
                    if (i == move.captured()) index = -1;
                }
                if (index >= 0) Replay.piece(scene, i).place(displayBoard, index);
            }
            if (!afterMove && move != null && dice != null && active == 0 && visual != Visual.INCORRECT)
                scene.mutableLegalMoves().add(Replay.piece(scene, move.piece()).id());
            return scene;
        }

        public List<String> route() {
            int piece = move.piece();
            var color = piece < 4 ? PlayerColor.RED : PlayerColor.GREEN;
            var path = board.path(color);
            int start = positions.get(piece);
            int end = move.destination();
            if (start < 0) return List.of(board.castleSlot(color, piece % 4), path.get(end));
            var route = new ArrayList<String>();
            int direction = Integer.compare(end, start);
            for (int i = start; i != end; i += direction) route.add(path.get(i));
            route.add(path.get(end));
            return List.copyOf(route);
        }
    }

    public static final List<Text> CHAPTERS = List.of(
            new Text("Start a game", "פתיחת משחק"),
            new Text("Roll and move", "הטלה ותנועה"),
            new Text("Captures and blocks", "לכידות וחסימות"),
            new Text("Question squares", "משבצות שאלה"),
            new Text("Surprise squares", "משבצות הפתעה"),
            new Text("Boost dice and sixes", "קוביות בוסט ורצף שש"),
            new Text("Human and AI turns", "תורות אדם ומחשב"),
            new Text("Home and victory", "הגעה לבית וניצחון"),
            new Text("History and replay", "היסטוריה והילוך חוזר"));

    private final List<Shot> shots = new ArrayList<>();
    private final int[] chapterStarts = new int[CHAPTERS.size()];
    private final BoardRepository boards = new BoardRepository();
    private int duration;
    private int chapter;
    private Board board;
    private static final List<Integer> CASTLE = positions(-1, -1, -1, -1);

    public TutorialScript() {
        Board easy = boards.load(Difficulty.EASY);
        Board medium = boards.load(Difficulty.MEDIUM);
        Board hard = boards.load(Difficulty.HARD);

        chapter(easy);
        cue(3, Visual.MENU, "Choose New Game from the main menu.",
                "בחרו ״משחק חדש״ בתפריט הראשי.");
        cue(5, Visual.PLAYERS, "Choose 2–4 players, names and colors. Set each player to Human or choose an AI level.",
                "בחרו 2–4 שחקנים, שמות וצבעים. הגדירו כל שחקן כאנושי או בחרו רמת מחשב.");
        cue(5, Visual.DIFFICULTY, "Preview Easy, Medium or Hard. Board difficulty and AI level are separate choices.",
                "הציגו את לוחות קל, בינוני או קשה. רמת הלוח ורמת המחשב נבחרות בנפרד.");
        add(4, Visual.OPENING, "Proceed to the opening roll. The highest roll decides who goes first.",
                "המשיכו להגרלת הפתיחה. ההטלה הגבוהה קובעת מי מתחיל.",
                new DiceRoll(5, 3, 1, 0, false), 0, CASTLE, null);

        chapter(easy);
        add(5, Visual.DICE, "Roll a regular six, then select a highlighted hero to leave the castle.",
                "הטילו שש בקובייה הרגילה ובחרו גיבור מסומן כדי לצאת מהטירה.",
                regular(6), 0, CASTLE, new Move(0, 0, -1), "R14-C7");
        add(4, Visual.DICE, "Choose one highlighted hero. Here, the next roll moves it three squares.",
                "בחרו גיבור מסומן אחד. כאן, ההטלה הבאה מקדמת אותו שלוש משבצות.",
                regular(3), 0, positions(0, -1, -1, -1), new Move(0, 3, -1));

        chapter(easy);
        int target = at(PlayerColor.GREEN, "R7-C4");
        add(5, Visual.DICE, "Land on a lone opponent on an unprotected square to send it back to its castle.",
                "נחיתה על יריב יחיד במשבצת שאינה מוגנת מחזירה אותו לטירה.",
                regular(2), 0, positions(13, -1, target, -1), new Move(0, 15, 4), "R7-C4");
        add(5, Visual.DICE, "A star protects both heroes from capture. Starts, Home lanes and Surprise squares are also safe.",
                "כוכב מגן על שני הגיבורים מלכידה. גם משבצות התחלה, מסלולי הבית ומשבצות הפתעה מוגנים.",
                regular(2), 0, positions(6, -1, at(PlayerColor.GREEN, "R9-C3"), -1),
                new Move(0, 8, -1), "R9-C3");
        add(6, Visual.DICE, "Two matching opponents form a Double Block. With no full move, you may stop just before it.",
                "שני יריבים מאותו צבע יוצרים חסימה כפולה. כשאין מהלך מלא, אפשר לעצור ממש לפניה.",
                regular(4), 0, positions(12, -1, target, target), new Move(0, 14, -1), "R7-C4");

        chapter(medium);
        add(5, Visual.QUESTIONS, "Question colors set the difficulty: green moves 1, yellow 2, and red 3.",
                "צבע השאלה קובע את רמת הקושי: ירוק מזיז משבצת אחת, צהוב שתיים ואדום שלוש.",
                null, 0, positions(2, 11, -1, -1), null, "R6-C17", "R1-C5", "R1-C13");
        add(4, Visual.DICE, "Land on a question square to answer a question of that color.",
                "נחתו על משבצת שאלה כדי לענות על שאלה בצבע שלה.",
                boost(2, 2, false), 0, positions(2, 11, -1, -1), new Move(0, 4, -1), "R1-C5");
        add(5, Visual.CORRECT, "A correct answer moves the hero forward. This yellow question awards two squares.",
                "תשובה נכונה מקדמת את הגיבור. השאלה הצהובה הזו מקדמת בשתי משבצות.",
                null, 0, positions(4, 11, -1, -1), new Move(0, 6, -1), "R1-C5");
        add(2, Visual.DICE, "A different hero lands on a red question.",
                "גיבור אחר נוחת על משבצת שאלה אדומה.",
                boost(1, 2, false), 0, positions(6, 11, -1, -1), new Move(1, 12, -1), "R1-C13");
        add(5, Visual.INCORRECT, "An incorrect answer moves the hero backward. Here, a red question sends it back three.",
                "תשובה שגויה מחזירה את הגיבור לאחור. כאן שאלה אדומה מחזירה אותו שלוש משבצות.",
                null, 0, positions(6, 12, -1, -1), new Move(1, 9, -1), "R1-C13");

        chapter(medium);
        add(4, Visual.DICE, "Land on a purple Surprise square to reveal an effect.",
                "נחתו על משבצת הפתעה סגולה כדי לחשוף השפעה.",
                boost(3, 2, false), 0, positions(23, -1, -1, -1), new Move(0, 26, -1), "R11-C17");
        add(5, Visual.SURPRISE, "Here the surprise moves the hero back three. It can also send a hero directly Home or back to the castle.",
                "כאן ההפתעה מחזירה את הגיבור שלוש משבצות. היא יכולה גם לשלוח גיבור ישירות לבית או בחזרה לטירה.",
                null, 0, positions(26, -1, -1, -1), new Move(0, 23, -1), "R11-C17");
        cue(6, Visual.SURPRISE, "Other surprises skip your next turn, give an immediate UltraBoost roll, or let you pick an opposing track hero to move back by a die roll.",
                "הפתעות אחרות מדלגות על התור הבא, מעניקות הטלת אולטרה־בוסט מיידית, או מאפשרות לבחור יריב במסלול שיזוז לאחור לפי הטלת קובייה.");

        chapter(medium);
        add(4, Visual.DICE, "On Medium, the Boost die has three faces. Move the regular roll times (Boost minus one).",
                "בבינוני, לקוביית הבוסט שלוש תוצאות. הכפילו את ההטלה הרגילה בבוסט פחות אחת.",
                boost(3, 3, false), 0, positions(27, -1, -1, -1), new Move(0, 33, -1));
        board = hard;
        add(4, Visual.DICE, "Hard uses UltraBoost, with six faces. Here, 2 × (5 − 1) moves eight squares.",
                "בקשה משתמשים באולטרה־בוסט עם שש תוצאות. כאן 2 × (5 − 1) מקדם שמונה משבצות.",
                boost(2, 5, true), 0, positions(8, -1, -1, -1), new Move(0, 16, -1));
        board = easy;
        add(3, Visual.DICE, "First regular six: move, then roll again.",
                "שש ראשון בקובייה הרגילה: הזיזו גיבור והטילו שוב.",
                regular(6), 0, positions(9, -1, -1, -1), new Move(0, 15, -1));
        add(3, Visual.DICE, "Second regular six: move, then roll again.",
                "שש שני בקובייה הרגילה: הזיזו גיבור והטילו שוב.",
                regular(6), 0, positions(15, -1, -1, -1), new Move(0, 21, -1));
        add(4, Visual.THIRD_SIX, "Third consecutive regular six: the roll is canceled and the turn ends.",
                "שש שלישי ברציפות בקובייה הרגילה: ההטלה מבוטלת והתור מסתיים.",
                regular(6), 0, positions(21, -1, -1, -1), null);

        chapter(easy);
        add(5, Visual.CLOCK, "The human player’s card counts down from 30 seconds. When time runs out, play passes to the next player.",
                "כרטיס השחקן האנושי סופר לאחור מ־30 שניות. כשהזמן נגמר, התור עובר לשחקן הבא.",
                null, 0, positions(21, -1, 12, -1), null);
        add(5, Visual.AI, "The AI rolls and chooses a move automatically. When the human turn returns, you control your pieces again.",
                "המחשב מטיל ובוחר מהלך בעצמו. כשהתור חוזר אליכם, אתם שוב שולטים בגיבורים שלכם.",
                regular(3), 1, positions(21, -1, 12, -1), new Move(4, 15, -1));

        chapter(medium);
        int home = board.path(PlayerColor.RED).size() - 1;
        add(6, Visual.WIN, "In your Home lane, only the regular die counts. An exact two brings the last hero Home. First to finish all four wins!",
                "במסלול הבית רק הקובייה הרגילה קובעת. שתיים בדיוק מכניסות את הגיבור האחרון לבית. הראשונים שמסיימים עם כל הארבעה מנצחים!",
                boost(2, 3, false), 0, List.of(home - 2, home, home, home, -1, -1, -1, -1),
                new Move(0, home, -1));

        chapter(medium);
        cue(4, Visual.HISTORY, "Open Game History from the main menu and select a completed match.",
                "פתחו את היסטוריית המשחקים מהתפריט הראשי ובחרו משחק שהסתיים.");
        add(4, Visual.REPLAY, "When a replay is available, select Watch Replay to revisit the recorded moves.",
                "כשיש הילוך חוזר זמין, בחרו ״צפייה בהילוך חוזר״ כדי לראות שוב את המהלכים.",
                boost(2, 3, false), 0, List.of(home - 2, home, home, home, -1, -1, -1, -1),
                new Move(0, home, -1));
    }

    public List<Shot> shots() { return List.copyOf(shots); }
    public int duration() { return duration; }
    public int chapterStart(int index) { return chapterStarts[index]; }
    public GameSession setupPreview(Difficulty difficulty) {
        return shots.get(0).project(boards.load(difficulty), false);
    }

    private void chapter(Board next) {
        chapter = shots.isEmpty() ? 0 : chapter + 1;
        chapterStarts[chapter] = duration;
        board = next;
    }

    private void cue(int seconds, Visual visual, String english, String hebrew) {
        add(seconds, visual, english, hebrew, null, 0, CASTLE, null);
    }

    private void add(int seconds, Visual visual, String english, String hebrew, DiceRoll dice, int active,
                     List<Integer> pieces, Move move, String... highlights) {
        shots.add(new Shot(chapter, seconds * 1000, board, visual, new Text(english, hebrew),
                dice, active, pieces, move, List.of(highlights)));
        duration += seconds * 1000;
    }

    private int at(PlayerColor color, String cell) {
        int index = board.path(color).indexOf(cell);
        if (index < 0) throw new IllegalArgumentException("Square is outside the tutorial path: " + cell);
        return index;
    }

    private static List<Integer> positions(int red, int redSecond, int green, int greenSecond) {
        return List.of(red, redSecond, -1, -1, green, greenSecond, -1, -1);
    }

    private static DiceRoll regular(int value) { return new DiceRoll(value, 0, 1, value, false); }

    private static DiceRoll boost(int regular, int special, boolean ultra) {
        return new DiceRoll(regular, special, special - 1, regular * (special - 1), ultra);
    }
}
