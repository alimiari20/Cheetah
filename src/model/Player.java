package model;

import java.util.*;

public final class Player {
    private final String name;
    private final PlayerColor color;
    private final List<Piece> pieces;
    Player(String name, PlayerColor color) {
        this.name = Objects.requireNonNull(name);
        this.color = color;
        List<Piece> built = new ArrayList<>();
        for (int i = 0; i < 4; i++) built.add(new Piece(color, i));
        pieces = Collections.unmodifiableList(built);
    }
    public String name() { return name; }
    public PlayerColor color() { return color; }
    public List<Piece> pieces() { return pieces; }
    public long finishedCount() { return pieces.stream().filter(p -> p.state() == Piece.State.FINISHED).count(); }
    public long castleCount() { return pieces.stream().filter(p -> p.state() == Piece.State.CASTLE).count(); }
    public boolean hasFinished() { return finishedCount() == pieces.size(); }
}
