package model;

/** Immutable outcome of the selected DiceStrategy (normal and optional special die). */
public record DiceRoll(int regular, int special, int multiplier, int movement, boolean ultraBoost) {
    public boolean rolledSix() { return regular == 6; }
    public String description(Difficulty difficulty) {
        if (difficulty == Difficulty.EASY && !ultraBoost) return "Regular: " + regular + "    •    Move: " + movement;
        return "Regular: " + regular + "    •    " + (ultraBoost ? "Ultra" : "Boost") + ": " + special +
               "    •    ×" + multiplier + "    •    Move: " + movement;
    }
}
