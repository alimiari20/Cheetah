package model;

import java.util.Objects;
import java.util.Random;

/** Medium = normal d6 and a d3 Boost multiplier (N - 1). */
public final class BoostDiceStrategy implements DiceStrategy {
    @Override
    public DiceRoll roll(Random random) {
        Objects.requireNonNull(random);
        int regular = random.nextInt(6) + 1;
        int special = random.nextInt(3) + 1;
        int multiplier = special - 1;
        return new DiceRoll(regular, special, multiplier,
                regular * multiplier, false);
    }
}
