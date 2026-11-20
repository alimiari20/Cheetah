package model;

import java.util.List;
import java.util.Objects;
import java.util.Random;

import static model.SpecialSquareGenerator.MIDDLE_TRACK;
import static model.SpecialSquareGenerator.ORIGINAL_TRACK;
import static model.SpecialSquareGenerator.OUTER_TRACK;
import static model.SpecialSquareGenerator.Placement.protection;
import static model.SpecialSquareGenerator.Placement.question;
import static model.SpecialSquareGenerator.Placement.surprise;

import model.SpecialSquareGenerator.Placement;

/**
 * Design pattern: Factory Method (Iteration 2, US7).
 *
 * Each difficulty has its own factory that creates the game board for that
 * difficulty. The caller only asks for a board; the concrete factory decides
 * which tracks and how many special squares the board gets
 * (SRS 3.2.2.3.2 - 3.2.2.3.4 and the board diagrams of the project specification).
 */
public abstract class BoardFactory {

    private final BoardRepository repository;
    private final SpecialSquareGenerator generator = new SpecialSquareGenerator();

    protected BoardFactory(BoardRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    /** Returns the factory that creates boards of the given difficulty. */
    public static BoardFactory forDifficulty(Difficulty difficulty, BoardRepository repository) {
        return switch (Objects.requireNonNull(difficulty, "difficulty")) {
            case EASY -> new EasyBoardFactory(repository);
            case MEDIUM -> new MediumBoardFactory(repository);
            case HARD -> new HardBoardFactory(repository);
        };
    }

    public abstract Difficulty difficulty();

    /** The factory method: creates a new board with randomly placed special squares. */
    public abstract Board createBoard(Random random);

    /** Shared step: loads the fixed board diagram and places the given special squares. */
    protected final Board randomBoard(Random random, Placement... placements) {
        return generator.generate(repository.load(difficulty()), random, List.of(placements));
    }

    private static final class EasyBoardFactory extends BoardFactory {
        EasyBoardFactory(BoardRepository repository) { super(repository); }

        @Override public Difficulty difficulty() { return Difficulty.EASY; }

        /** SRS 3.2.2.3.2: 4 protection squares, no question or surprise squares. */
        @Override public Board createBoard(Random random) {
            return randomBoard(random,
                protection(ORIGINAL_TRACK, 4));
        }
    }

    private static final class MediumBoardFactory extends BoardFactory {
        MediumBoardFactory(BoardRepository repository) { super(repository); }

        @Override public Difficulty difficulty() { return Difficulty.MEDIUM; }

        /** Medium board diagram (project specification): 8 protection, 6 question (2 per level) and 2 surprise squares. */
        @Override public Board createBoard(Random random) {
            return randomBoard(random,
                protection(ORIGINAL_TRACK, 4),
                protection(MIDDLE_TRACK, 4),
                question(MIDDLE_TRACK, "green", 2),
                question(MIDDLE_TRACK, "yellow", 2),
                question(MIDDLE_TRACK, "red", 2),
                surprise(MIDDLE_TRACK, 2));
        }
    }

    private static final class HardBoardFactory extends BoardFactory {
        HardBoardFactory(BoardRepository repository) { super(repository); }

        @Override public Difficulty difficulty() { return Difficulty.HARD; }

        /** SRS 3.2.2.3.4: 12 protection, 9 question (3 per level) and 3 surprise squares. */
        @Override public Board createBoard(Random random) {
            return randomBoard(random,
                protection(ORIGINAL_TRACK, 4),
                protection(MIDDLE_TRACK, 4),
                protection(OUTER_TRACK, 4),
                question(MIDDLE_TRACK, "green", 2),
                question(MIDDLE_TRACK, "yellow", 2),
                question(MIDDLE_TRACK, "red", 2),
                question(OUTER_TRACK, "green", 1),
                question(OUTER_TRACK, "yellow", 1),
                question(OUTER_TRACK, "red", 1),
                surprise(MIDDLE_TRACK, 2),
                surprise(OUTER_TRACK, 1));
        }
    }
}