package model;

/** Game player identity; GUI styling is deliberately kept out of the model. */
public enum PlayerColor {
    RED, GREEN, YELLOW, BLUE;

    public String label() {
        return name().substring(0, 1) + name().substring(1).toLowerCase();
    }
}
