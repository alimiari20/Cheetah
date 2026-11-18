package model;

import java.util.Objects;
import java.util.Random;

/** Easy = a single fair six-sided die. */
public final class EasyDiceStrategy implements DiceStrategy {
    @Override
    public DiceRoll roll(Random random) {
        int regular = Objects.requireNonNull(random).nextInt(6) + 1;
        return new DiceRoll(regular, 1, 1, regular, false);
    }
}
