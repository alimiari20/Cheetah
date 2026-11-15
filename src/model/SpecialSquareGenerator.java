package model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/**
 * Creates a new, per-match assignment of special squares over the UNCHANGED
 * board geometry. The cached TSV-backed templates (used for setup previews)
 * are never changed.
 *
 * A track is identified by distance from the outermost board edge:
 * Easy:  the original common track only.
 * Medium: the newly added 64-cell perimeter is the outermost ring (distance 0).
 * Hard: the newly added 72-cell perimeter is the outermost ring (distance 0),
 *       the prior 64-cell track is distance 1, and the original track is inside.
 *
 * Starting squares, arrows, individual home paths and castles are excluded.
 */
public final class SpecialSquareGenerator {
    private static final String STAR = "star";
    private static final String QUESTION = "question";
    private static final String SURPRISE = "surprise";

    public Board generate(Board template, Random random) {
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(random, "random");

        // Work on a copy. In particular, NEVER mutate the cached preview board.
        Map<String, Board.Cell> cells = new HashMap<>();
        Map<Integer, List<String>> available = new HashMap<>();
        for (Board.Cell original : template.cells()) {
            Board.Cell cell = original;
            if (isMovableSpecial(original.symbol())) {
                cell = withSymbol(original, "-", "-");
            }
            cells.put(cell.id(), cell);

            // Only ordinary shared-track cells can ever be chosen. The board's
            // arrows, starts, home paths and geometry are immutable.
            if ("track".equals(cell.type()) && "-".equals(cell.symbol())) {
                int lane = lane(template.difficulty(), template.size(), cell);
                available.computeIfAbsent(lane, key -> new ArrayList<>()).add(cell.id());
            }
        }
        for (List<String> candidates : available.values()) {
            Collections.shuffle(candidates, random);
        }

        // 4 original protections on the original (52-step) shared track.
        place(cells, available, 2, STAR, "-", 4);
        if (template.difficulty() != Difficulty.EASY) {
            // 4 new protections and all six questions/two surprises
            // on the 64-square Medium track.
            place(cells, available, 1, STAR, "-", 4);
            place(cells, available, 1, QUESTION, "green", 2);
            place(cells, available, 1, QUESTION, "yellow", 2);
            place(cells, available, 1, QUESTION, "red", 2);
            place(cells, available, 1, SURPRISE, "purple", 2);
        }
        if (template.difficulty() == Difficulty.HARD) {
            // Only the additional Hard-level squares are placed on the
            // 72-square outer track; no Medium squares move out of their lane.
            place(cells, available, 0, STAR, "-", 4);
            place(cells, available, 0, QUESTION, "green", 1);
            place(cells, available, 0, QUESTION, "yellow", 1);
            place(cells, available, 0, QUESTION, "red", 1);
            place(cells, available, 0, SURPRISE, "purple", 1);
        }
        return template.withCells(cells);
    }

    private static int lane(Difficulty difficulty, int size, Board.Cell cell) {
        if (difficulty == Difficulty.EASY) return 2;
        int distance = Math.min(
            Math.min(cell.row() - 1, cell.column() - 1),
            Math.min(size - cell.row(), size - cell.column()));
        if (difficulty == Difficulty.MEDIUM) return distance == 0 ? 1 : 2;
        return distance == 0 ? 0 : distance == 1 ? 1 : 2;
    }

    private static boolean isMovableSpecial(String symbol) {
        return STAR.equals(symbol) || QUESTION.equals(symbol) || SURPRISE.equals(symbol);
    }

    private static Board.Cell withSymbol(Board.Cell c, String symbol, String specialColor) {
        return new Board.Cell(c.row(), c.column(), c.type(), c.color(),
            symbol, c.arrow(), specialColor);
    }

    private static void place(Map<String, Board.Cell> cells,
                              Map<Integer, List<String>> available,
                              int lane, String symbol, String specialColor, int count) {
        List<String> candidates = available.get(lane);
        if (candidates == null || candidates.size() < count) {
            throw new IllegalStateException("Not enough eligible ordinary track cells in lane " +
                lane + " for " + count + " " + symbol + " squares");
        }
        for (int i = 0; i < count; i++) {
            String id = candidates.remove(candidates.size() - 1);
            cells.put(id, withSymbol(cells.get(id), symbol, specialColor));
        }
    }
}
