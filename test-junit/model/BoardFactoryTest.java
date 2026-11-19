package model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Raghad Tarabeh / Iteration 2 / US7 + US11.
 * Tests BoardFactory (Factory Method) and the random board generation (SDP risk R1):
 * 100 generated boards for every difficulty.
 */
class BoardFactoryTest {

    private static final int BOARDS = 100;
    private final BoardRepository repository = new BoardRepository();

    @Test
    void factoryCreatesBoardOfRequestedDifficulty() {
        for (Difficulty d : Difficulty.values()) {
            BoardFactory factory = BoardFactory.forDifficulty(d, repository);
            assertEquals(d, factory.difficulty());
            assertEquals(d, factory.createBoard(new Random(1)).difficulty());
        }
    }

    @Test
    void boardSizesMatchTheDiagrams() {
        assertEquals(15, create(Difficulty.EASY, 1).size());
        assertEquals(17, create(Difficulty.MEDIUM, 1).size());
        assertEquals(19, create(Difficulty.HARD, 1).size());
    }

    /** SRS 3.2.2.3.2 */
    @Test
    void easyBoardsHaveRequiredSquares() {
        checkBoards(Difficulty.EASY, 4, 0, 0);
    }

    /** SRS 3.2.2.3.3: 8 protection, 6 questions (2 per level), 4 surprises. */
    @Test
    void mediumBoardsHaveRequiredSquares() {
        checkBoards(Difficulty.MEDIUM, 8, 2, 4);
    }

    /** SRS 3.2.2.3.4: 12 protection, 9 questions (3 per level), 3 surprises. */
    @Test
    void hardBoardsHaveRequiredSquares() {
        checkBoards(Difficulty.HARD, 12, 3, 3);
    }

    /** SRS 3.2.2.3.6: a new random layout at the start of each game. */
    @Test
    void everyGameGetsANewLayout() {
        for (Difficulty d : Difficulty.values()) {
            Set<Set<String>> layouts = new HashSet<>();
            for (int seed = 0; seed < BOARDS; seed++) {
                layouts.add(specialSquares(create(d, seed)));
            }
            assertTrue(layouts.size() > 1, d + " always creates the same layout");
        }
    }

    /** The fixed board used for the setup preview must never change. */
    @Test
    void templateBoardIsNotChanged() {
        for (Difficulty d : Difficulty.values()) {
            Map<String, Long> before = repository.load(d).symbolCounts();
            Set<String> layoutBefore = specialSquares(repository.load(d));
            for (int seed = 0; seed < 20; seed++) {
                create(d, seed);
            }
            assertEquals(before, repository.load(d).symbolCounts());
            assertEquals(layoutBefore, specialSquares(repository.load(d)));
        }
    }

    @Test
    void sameSeedGivesSameBoard() {
        for (Difficulty d : Difficulty.values()) {
            assertEquals(specialSquares(create(d, 42)), specialSquares(create(d, 42)));
        }
    }

    private void checkBoards(Difficulty d, long protection, long questionsPerLevel, long surprises) {
        Board template = repository.load(d);
        for (int seed = 0; seed < BOARDS; seed++) {
            Board board = create(d, seed);
            String name = d + " board #" + seed;

            Map<String, Long> counts = board.symbolCounts();
            assertEquals(protection, (long) counts.getOrDefault("star", 0L), name + ": protection squares");
            assertEquals(3 * questionsPerLevel, (long) counts.getOrDefault("question", 0L), name + ": question squares");
            assertEquals(surprises, (long) counts.getOrDefault("surprise", 0L), name + ": surprise squares");
            assertEquals(template.symbolCounts().get("arrow"), counts.get("arrow"), name + ": arrows");
            assertEquals(4L, (long) counts.get("start"), name + ": starting squares");

            // SRS 3.2.2.3.9: question levels are green / yellow / red
            Map<String, Long> levels = new HashMap<>();
            for (Board.Cell cell : board.cells()) {
                if ("question".equals(cell.symbol())) {
                    levels.merge(cell.specialColor(), 1L, Long::sum);
                }
            }
            if (questionsPerLevel > 0) {
                assertEquals(Map.of("green", questionsPerLevel, "yellow", questionsPerLevel,
                    "red", questionsPerLevel), levels, name + ": question colors");
            }

            for (Board.Cell cell : board.cells()) {
                Board.Cell original = template.cell(cell.id());
                // SRS 3.2.2.3.5: starting squares and arrows keep their positions
                if ("start".equals(original.symbol()) || "arrow".equals(original.symbol())) {
                    assertEquals(original, cell, name + ": fixed square moved " + cell.id());
                }
                // SRS 3.2.2.3.6: special squares only on ordinary shared-track squares
                if (isSpecial(cell.symbol())) {
                    assertEquals("track", cell.type(), name + ": special square off the track " + cell.id());
                    for (PlayerColor color : PlayerColor.values()) {
                        assertTrue(!board.home(color).contains(cell.id()),
                            name + ": special square on a home path " + cell.id());
                    }
                }
            }
            // The board geometry (paths) does not change
            for (PlayerColor color : PlayerColor.values()) {
                assertEquals(template.path(color), board.path(color), name + ": path of " + color);
            }
        }
    }

    private Board create(Difficulty d, long seed) {
        return BoardFactory.forDifficulty(d, repository).createBoard(new Random(seed));
    }

    private static boolean isSpecial(String symbol) {
        return "star".equals(symbol) || "question".equals(symbol) || "surprise".equals(symbol);
    }

    private static Set<String> specialSquares(Board board) {
        Set<String> result = new HashSet<>();
        for (Board.Cell cell : board.cells()) {
            if (isSpecial(cell.symbol())) {
                result.add(cell.id() + ":" + cell.symbol() + ":" + cell.specialColor());
            }
        }
        return result;
    }
}