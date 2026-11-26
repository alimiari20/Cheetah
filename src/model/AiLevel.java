package model;

/** Player control and, for computers, strategy strength. Independent of board difficulty. */
public enum AiLevel {
    HUMAN("Human Player"), EASY("Easy AI"), MEDIUM("Medium AI"), HARD("Hard AI");

    private final String label;
    AiLevel(String label) { this.label = label; }
    public boolean automated() { return this != HUMAN; }
    @Override public String toString() { return label; }
}
