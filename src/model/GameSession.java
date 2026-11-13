package model;

import java.time.Instant;
import java.util.*;

/** The single source of truth for one match. UI components only read it. */
public final class GameSession {
    public enum Phase { ROLL, SELECT_PIECE, FINISHED }
    private final Board board;
    private final List<Player> players;
    private final Instant started = Instant.now();
    private final List<String> events = new ArrayList<>();
    private final List<PlayerColor> ranking = new ArrayList<>();
    private final EnumSet<PlayerColor> skips = EnumSet.noneOf(PlayerColor.class);
    private final Set<String> legalPieceIds = new LinkedHashSet<>();
    private int currentPlayer = 0;
    private int turn = 1;
    private int consecutiveSixes = 0;
    private Phase phase = Phase.ROLL;
    private DiceRoll dice;
    private boolean ultraNextRoll;
    private String status = "Roll the dice to begin!";

    GameSession(Board board, List<Player> players) {
        this.board = board;
        this.players = List.copyOf(players);
        record("New " + board.difficulty().label() + " game with " + players.size() + " players.");
    }

    public Board board() { return board; }
    public Difficulty difficulty() { return board.difficulty(); }
    public List<Player> players() { return players; }
    public Player current() { return players.get(currentPlayer); }
    public int currentIndex() { return currentPlayer; }
    public int turn() { return turn; }
    public int consecutiveSixes() { return consecutiveSixes; }
    public Phase phase() { return phase; }
    public DiceRoll dice() { return dice; }
    public boolean isUltraNextRoll() { return ultraNextRoll; }
    public String status() { return status; }
    public List<String> events() { return Collections.unmodifiableList(events); }
    public Set<String> legalPieceIds() { return Collections.unmodifiableSet(legalPieceIds); }
    public List<PlayerColor> ranking() { return Collections.unmodifiableList(ranking); }
    public Instant started() { return started; }

    void status(String value) { status = value; }
    void phase(Phase value) { phase = value; }
    void dice(DiceRoll value) { dice = value; }
    void currentPlayer(int index) { currentPlayer = index; }
    void turn(int value) { turn = value; }
    void consecutiveSixes(int value) { consecutiveSixes = value; }
    void ultraNextRoll(boolean value) { ultraNextRoll = value; }
    Set<String> mutableLegalMoves() { return legalPieceIds; }
    Set<PlayerColor> mutableSkips() { return skips; }
    List<PlayerColor> mutableRanking() { return ranking; }
    void record(String action) {
        events.add(String.format("#%02d  %s", turn, action));
        if (events.size() > 120) events.remove(0);
        status = action;
    }
    public Piece findPiece(String id) {
        for (Player player : players) for (Piece piece : player.pieces()) if (piece.id().equals(id)) return piece;
        return null;
    }
    public Player findPlayer(PlayerColor color) {
        for (Player p : players) if (p.color() == color) return p;
        return null;
    }
}
