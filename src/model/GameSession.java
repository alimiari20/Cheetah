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
    // Earned normal extra rolls wait while immediate surprise rolls resolve.
    private int pendingExtraRolls;
    private boolean surpriseRollInProgress;
    private final Deque<Integer> effectPlayerStack = new ArrayDeque<>();
    private record TurnState(
    	    int currentPlayer,
    	    int turn,
    	    int consecutiveSixes,
    	    Phase phase,
    	    DiceRoll dice,
    	    boolean ultraNextRoll,
    	    int pendingExtraRolls,
    	    boolean surpriseRollInProgress,
    	    Set<String> legalPieceIds,
    	    String status
    	) {}

    	private final Deque<TurnState> suspendedTurns = new ArrayDeque<>();void suspendCurrentTurn() {
    	    suspendedTurns.push(new TurnState(
    	            currentPlayer,
    	            turn,
    	            consecutiveSixes,
    	            phase,
    	            dice,
    	            ultraNextRoll,
    	            pendingExtraRolls,
    	            surpriseRollInProgress,
    	            new LinkedHashSet<>(legalPieceIds),
    	            status
    	        ));
    	    }
    	void resumeSuspendedTurn() {
    	    if (suspendedTurns.isEmpty()) {
    	        throw new IllegalStateException("No suspended turn to resume");
    	    }

    	    TurnState saved = suspendedTurns.pop();

    	    currentPlayer = saved.currentPlayer();
    	    turn = saved.turn();
    	    consecutiveSixes = saved.consecutiveSixes();
    	    phase = saved.phase();
    	    dice = saved.dice();
    	    ultraNextRoll = saved.ultraNextRoll();
    	    pendingExtraRolls = saved.pendingExtraRolls();
    	    surpriseRollInProgress = saved.surpriseRollInProgress();

    	    legalPieceIds.clear();
    	    legalPieceIds.addAll(saved.legalPieceIds());
    	    status = saved.status();
    	}
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
    public int pendingExtraRolls() { return pendingExtraRolls; }
    public boolean isSurpriseRollInProgress() { return surpriseRollInProgress; }
    public String status() { return status; }
    

    public boolean hasTemporaryEffectPlayer() {
        return !effectPlayerStack.isEmpty();
    }

    public Integer effectPlayerIndex() {
        return effectPlayerStack.peek();
    }
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
    void pendingExtraRolls(int value) {
        if (value < 0) throw new IllegalArgumentException("Negative extra rolls");
        pendingExtraRolls = value;
    }
    void surpriseRollInProgress(boolean value) {
        surpriseRollInProgress = value;
    }
   
    void beginTemporaryEffect(int playerIndex) {
        if (playerIndex < 0 || playerIndex >= players.size()) {
            throw new IllegalArgumentException("Invalid effect player");
        }

        suspendCurrentTurn();
        effectPlayerStack.push(playerIndex);

        currentPlayer = playerIndex;
        consecutiveSixes = 0;
        phase = Phase.ROLL;
        dice = null;
        ultraNextRoll = false;
        pendingExtraRolls = 0;
        surpriseRollInProgress = false;
        legalPieceIds.clear();
    }

    void endTemporaryEffect() {
        if (effectPlayerStack.isEmpty()) {
            throw new IllegalStateException(
                "No temporary effect to end"
            );
        }

        effectPlayerStack.pop();
        resumeSuspendedTurn();
    }
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
