package model;

/** Mutable state belongs to the model; only GameEngine should make gameplay changes. */
public final class Piece {
    public enum State { CASTLE, TRACK, HOME, FINISHED }
    private final PlayerColor color;
    private final int number;
    private State state = State.CASTLE;
    private int pathIndex = -1;

    Piece(PlayerColor color, int number) { this.color = color; this.number = number; }
    public PlayerColor color() { return color; }
    public int number() { return number; }
    public String id() { return color.name().toLowerCase() + "-" + number; }
    public State state() { return state; }
    public int pathIndex() { return pathIndex; }
    public String squareId(Board board) {
        return switch (state) {
            case CASTLE -> board.castleSlot(color, number);
            case TRACK, HOME -> board.path(color).get(pathIndex);
            case FINISHED -> board.home(color).get(board.home(color).size() - 1);
        };
    }
    void place(Board board, int index) {
        if (index < 0 || index >= board.path(color).size()) throw new IllegalArgumentException("Out of bounds");
        pathIndex = index;
        state = index == board.path(color).size() - 1 ? State.FINISHED
                : board.isHome(color, board.path(color).get(index)) ? State.HOME : State.TRACK;
    }
    void returnCastle() { pathIndex = -1; state = State.CASTLE; }
}
