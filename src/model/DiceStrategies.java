package model;

import java.util.Objects;

/** Composition root for selecting interchangeable dice strategies. */
public final class DiceStrategies {
    private DiceStrategies() {}

    public static DiceStrategy forDifficulty(Difficulty difficulty) {
        return switch (Objects.requireNonNull(difficulty)) {
            case EASY -> new EasyDiceStrategy();
            case MEDIUM -> new BoostDiceStrategy();
            case HARD -> new UltraBoostDiceStrategy();
        };
    }

    /** The surprise always uses UltraBoost, even in Medium games. */
    public static DiceStrategy surpriseUltraBoost() {
        return new UltraBoostDiceStrategy();
    }
}
