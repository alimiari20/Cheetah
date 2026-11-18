package model;

import java.util.Random;

/**
 * Strategy (GoF): encapsulates difficulty-specific dice policies without
 * coupling game/turn management to the selected difficulty.
 */
public interface DiceStrategy {
    /** Rolls the normal die and any extra die provided by this strategy. */
    DiceRoll roll(Random random);

    /**
     * The selected piece determines movement: a HOME piece uses only the
     * normal die, while a shared-track piece uses the dice policy.
     */
    default int stepsFor(Piece piece, DiceRoll roll) {
        if (piece.state() == Piece.State.HOME) {
            return roll.regular();
        }
        return roll.movement();
    }
}
