package model;

import java.util.Objects;
import java.util.Random;

/** Hard / surprise = normal d6 and an independent d6 UltraBoost die. */
public final class UltraBoostDiceStrategy implements DiceStrategy {
    @Override
    public DiceRoll roll(Random random) {
        Objects.requireNonNull(random);
        int regular = random.nextInt(6) + 1;
        int special = random.nextInt(6) + 1;
        int multiplier = special - 1;
        return new DiceRoll(regular, special, multiplier,
                regular * multiplier, true);
    }
}
