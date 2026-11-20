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
 * The number of squares per difficulty comes from BoardFactory (SRS 3.2.2.3.2-3.2.2.3.4).
 */
public final class SpecialSquareGenerator {
    private static final String STAR = "star";
    private static final String QUESTION = "question";
    private static final String SURPRISE = "surprise";

    /** The original 52-square track (Easy, Medium and Hard). */
    public static final int ORIGINAL_TRACK = 2;
    /** The additional 64-square track (Medium and Hard). */
    public static final int MIDDLE_TRACK = 1;
    /** The outer 72-square track (Hard only). */
    public static final int OUTER_TRACK = 0;

    /** How many squares of one kind are placed on one track. */
    public record Placement(int track, String symbol, String specialColor, int count) {
        public static Placement protection(int track, int count) {
            return new Placement(track, STAR, "-", count);
        }
        public static Placement question(int track, String levelColor, int count) {
            return new Placement(track, QUESTION, levelColor, count);
        }
        public static Placement surprise(int track, int count) {
            return new Placement(track, SURPRISE, "purple", count);
        }
    }

    /**
     * Places the given special squares randomly over the board template.
     * Which squares (and how many) is decided by the BoardFactory of each difficulty.
     */
    public Board generate(Board template, Random random, List<Placement> placements) {
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(random, "random");
        Objects.requireNonNull(placements, "placements");

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

        for (Placement placement : placements) {
            place(cells, available, placement.track(), placement.symbol(),
                placement.specialColor(), placement.count());
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