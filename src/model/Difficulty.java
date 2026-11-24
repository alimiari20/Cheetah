package model;

public enum Difficulty {
    EASY("Easy · Classic", 15, 1),
    MEDIUM("Medium · Boost", 17, 3),
    HARD("Hard · UltraBoost", 19, 6);

    private final String label;
    private final int boardSize;
    private final int specialSides;

    Difficulty(String label, int boardSize, int specialSides) {
        this.label = label;
        this.boardSize = boardSize;
        this.specialSides = specialSides;
    }

    public String label() { return label; }
    public int boardSize() { return boardSize; }
    public int specialSides() { return specialSides; }
    @Override public String toString() { return label; }
}
