package model;

import java.util.*;

/**
 * All gameplay rules live here. No Swing imports, no files and no UI callbacks.
 * The controller handles timing/dialogs; the model enforces valid transitions.
 */
public final class GameEngine {
    public record MoveFeedback(String message, String squareId, int steps,
                               List<String> captures, boolean finished) {
        public MoveFeedback { captures = List.copyOf(captures); }
    }
    public record SurpriseFeedback(SurpriseOutcome outcome, String message, String landingSquare) {}

    private final BoardRepository boards;
    public GameEngine(BoardRepository boards) { this.boards = Objects.requireNonNull(boards); }

    public GameSession start(Difficulty difficulty, List<String> names) {
        return start(difficulty,names,0);
    }

    /** Default color order remains compatible with earlier projects and tests. */
    public GameSession start(Difficulty difficulty,List<String> names,int starterIndex) {
        return start(difficulty,names,starterIndex,
            Arrays.asList(PlayerColor.values()).subList(0,names.size()));
    }

    /** Rotates starting player while retaining selected colors from setup. */
    public GameSession start(Difficulty difficulty, List<String> names, int starterIndex,
                             List<PlayerColor> selectedColors) {
        if (names == null || names.size() < 2 || names.size() > 4)
            throw new IllegalArgumentException("Choose 2 to 4 players");
        if (selectedColors==null || selectedColors.size()!=names.size() ||
            new HashSet<>(selectedColors).size()!=selectedColors.size())
            throw new IllegalArgumentException("Each player must have a unique color");
        if (starterIndex < 0 || starterIndex >= names.size())throw new IllegalArgumentException("Invalid first player");
        List<Player> players=new ArrayList<>();
        for(int i=0;i<names.size();i++){
            int index=(starterIndex+i)%names.size();
            String name=names.get(index)==null?"":names.get(index).trim();
            if(name.isBlank())name=selectedColors.get(index).label();
            players.add(new Player(name,selectedColors.get(index)));
        }
        return new GameSession(boards.load(difficulty),players);
    }

    public DiceRoll roll(GameSession session, Random random) {
        requirePhase(session, GameSession.Phase.ROLL);
        int regular = random.nextInt(6) + 1;
        boolean ultra = session.isUltraNextRoll();
        int sides = ultra ? 6 : session.difficulty().specialSides();
        int special = sides == 1 ? 1 : random.nextInt(sides) + 1;
        int multiplier = session.difficulty() == Difficulty.EASY && !ultra ? 1 : special - 1;
        DiceRoll roll = new DiceRoll(regular, special, multiplier, regular * multiplier, ultra);
        session.ultraNextRoll(false);
        session.dice(roll);
        int count = regular == 6 ? session.consecutiveSixes() + 1 : 0;
        session.consecutiveSixes(count);
        session.mutableLegalMoves().clear();

        if (count >= 3) {
            session.record(session.current().name() + " rolled three sixes! Turn canceled.");
            advanceTurn(session);
        } else {
            session.mutableLegalMoves().addAll(calculateLegalMoves(session));
            session.phase(GameSession.Phase.SELECT_PIECE);
            session.record(session.current().name() + " rolled " + roll.description(session.difficulty()) +
                (session.legalPieceIds().isEmpty() ? ". No legal move — pass the turn." : ". Select a highlighted piece."));
        }
        return roll;
    }

    /** Full-move priority first, stop-before-opponent-double-block only as fallback. */
    public Set<String> calculateLegalMoves(GameSession session) {
        DiceRoll dice = session.dice();
        if (dice == null || session.consecutiveSixes() >= 3) return Set.of();
        Player current = session.current();
        Set<String> blocked = opponentDoubleBlocks(session, current.color());
        List<String> track = session.board().path(current.color());
        Set<String> full = new LinkedHashSet<>();
        Set<String> truncated = new LinkedHashSet<>();
        for (Piece piece : current.pieces()) {
            if (piece.state() == Piece.State.FINISHED) continue;
            if (piece.state() == Piece.State.CASTLE) {
                if (dice.regular() == 6 && !blocked.contains(session.board().start(current.color()))) full.add(piece.id());
                continue;
            }
            if (dice.movement() <= 0 || piece.pathIndex() + dice.movement() >= track.size()) continue;
            int firstBlocked = firstBlockedStep(track, piece.pathIndex(), dice.movement(), blocked);
            if (firstBlocked == 0) full.add(piece.id());
            else if (firstBlocked > 1) truncated.add(piece.id());
        }
        return full.isEmpty() ? truncated : full;
    }

    public MoveFeedback move(GameSession session, String pieceId) {
        requirePhase(session, GameSession.Phase.SELECT_PIECE);
        if (!session.legalPieceIds().contains(pieceId)) throw new IllegalArgumentException("Not a legal move: " + pieceId);
        Piece piece = session.findPiece(pieceId);
        Player player = session.current();
        if (piece == null || piece.color() != player.color()) throw new IllegalArgumentException("Wrong player");
        Board board = session.board();
        int steps = 0;
        List<String> captures = new ArrayList<>();
        boolean wasCastle = piece.state() == Piece.State.CASTLE;
        if (wasCastle) {
            piece.place(board, 0);
        } else {
            int requested = session.dice().movement();
            Set<String> blocked = opponentDoubleBlocks(session, player.color());
            int firstBlocked = firstBlockedStep(board.path(player.color()), piece.pathIndex(), requested, blocked);
            steps = firstBlocked > 0 ? firstBlocked - 1 : requested;
            if (steps < 1) throw new IllegalStateException("Move blocked at the first square");
            piece.place(board, piece.pathIndex() + steps);
            if (piece.state() != Piece.State.FINISHED && firstBlocked == 0 && !board.isSafe(piece.squareId(board))) {
                for (Player opponent : session.players()) {
                    if (opponent.color() == player.color()) continue;
                    List<Piece> occupying = new ArrayList<>();
                    for (Piece target : opponent.pieces()) {
                        if ((target.state() == Piece.State.TRACK || target.state() == Piece.State.HOME) &&
                            target.squareId(board).equals(piece.squareId(board))) occupying.add(target);
                    }
                    if (occupying.size() == 1) {
                        occupying.get(0).returnCastle();
                        captures.add(opponent.name() + "'s " + (occupying.get(0).number() + 1));
                    }
                }
            }
        }
        session.mutableLegalMoves().clear();
        String position = piece.squareId(board);
        String action = wasCastle ? player.name() + " entered the board with piece " + (piece.number() + 1) :
            player.name() + " moved piece " + (piece.number() + 1) + " by " + steps + " squares";
        if (!captures.isEmpty()) action += " and captured " + String.join(", ", captures);
        if (piece.state() == Piece.State.FINISHED) action += " — reached HOME!";
        session.record(action + ".");
        registerFinish(session, player);
        return new MoveFeedback(action, position, steps, captures, piece.state() == Piece.State.FINISHED);
    }

    /** Question answer and negative surprise movement; no random UI side effects. */
    public MoveFeedback shiftPiece(GameSession session, Piece piece, int delta) {
        if (piece == null || piece.state() == Piece.State.CASTLE || piece.state() == Piece.State.FINISHED)
            return new MoveFeedback("Piece cannot move", null, 0, List.of(), false);
        Board board = session.board();
        int old = piece.pathIndex();
        List<String> path = board.path(piece.color());
        Set<String> blocked = opponentDoubleBlocks(session, piece.color());
        int index = old;
        int direction = Integer.compare(delta, 0);
        for (int i = 0; i < Math.abs(delta); i++) {
            int candidate = index + direction;
            if (candidate < 0 || candidate >= path.size() || blocked.contains(path.get(candidate))) break;
            index = candidate;
            if (index == path.size() - 1) break;
        }
        piece.place(board, index);
        registerFinish(session, session.findPlayer(piece.color()));
        return new MoveFeedback("Shifted " + (index - old) + " squares", piece.squareId(board), index - old,
            List.of(), piece.state() == Piece.State.FINISHED);
    }

    public void resolveQuestion(GameSession session, String pieceId, Question question, boolean correct) {
        Piece piece = Objects.requireNonNull(session.findPiece(pieceId));
        if (piece.color() != session.current().color()) throw new IllegalArgumentException("Wrong player");
        int reward = question.movementReward() * (correct ? 1 : -1);
        MoveFeedback move = shiftPiece(session, piece, reward);
        session.record(session.current().name() + " answered " + (correct ? "correctly" : "incorrectly") +
            " (" + question.difficulty().name().toLowerCase() + "). " + move.message() + ".");
    }

    public SurpriseFeedback applySurprise(GameSession session, String pieceId, SurpriseOutcome outcome, Random random) {
        Piece piece = Objects.requireNonNull(session.findPiece(pieceId));
        if (piece.color() != session.current().color()) throw new IllegalArgumentException("Wrong player");
        String text;
        String square = null;
        switch (outcome) {
            case DIRECT_FINISH -> {
                piece.place(session.board(), session.board().path(piece.color()).size() - 1);
                registerFinish(session, session.current());
                text = "Surprise: Direct finish! Piece " + (piece.number() + 1) + " reached HOME.";
            }
            case OPPONENT_BACKWARD -> {
                List<Piece> available = new ArrayList<>();
                for (Player p : session.players()) if (p.color() != piece.color())
                    for (Piece other : p.pieces()) if (other.state() == Piece.State.TRACK || other.state() == Piece.State.HOME)
                        available.add(other);
                if (available.isEmpty()) text = "Surprise: No opponent piece to move backward.";
                else {
                    Piece other = available.get(random.nextInt(available.size()));
                    int moved = shiftPiece(session, other, -6).steps();
                    text = "Surprise: " + other.color().label() + " piece " + (other.number() + 1) +
                        " moved " + Math.abs(moved) + " squares backward.";
                }
            }
            case RETURN_TO_CASTLE -> {
                piece.returnCastle();
                text = "Surprise: Piece " + (piece.number() + 1) + " returned to the castle!";
            }
            case BACKWARD_THREE -> {
                MoveFeedback adjusted = shiftPiece(session, piece, -3);
                square = adjusted.steps() == 0 ? null : adjusted.squareId();
                text = "Surprise: " + Math.abs(adjusted.steps()) + " squares backward.";
            }
            case LOSE_NEXT_TURN -> {
                session.mutableSkips().add(piece.color());
                text = "Surprise: " + session.current().name() + " loses their next turn!";
            }
            case ULTRABOOST -> {
                session.ultraNextRoll(true);
                text = "Surprise: Immediate UltraBoost extra roll granted!";
            }
            default -> throw new IllegalArgumentException("Unknown outcome");
        }
        session.record(text);
        return new SurpriseFeedback(outcome, text, square);
    }

    /** Called after the controller has resolved popups/special-square effects. */
    public void completeMove(GameSession session) {
        if (session.phase() == GameSession.Phase.FINISHED) return;
        if (session.isUltraNextRoll() || (session.dice() != null && session.dice().rolledSix() && !session.current().hasFinished())) {
            session.phase(GameSession.Phase.ROLL);
            session.dice(null);
            session.mutableLegalMoves().clear();
            session.status(session.current().name() + " gets an extra roll!");
            return;
        }
        advanceTurn(session);
    }

    public void pass(GameSession session) {
        requirePhase(session, GameSession.Phase.SELECT_PIECE);
        if (!session.legalPieceIds().isEmpty()) throw new IllegalStateException("Select a legal piece instead of passing");
        session.record(session.current().name() + " has no legal move.");
        completeMove(session);
    }

    private void advanceTurn(GameSession session) {
        session.dice(null);
        session.mutableLegalMoves().clear();
        session.consecutiveSixes(0);
        int count = session.players().size();
        for (int offset = 1; offset <= count * 2; offset++) {
            int next = (session.currentIndex() + offset) % count;
            Player player = session.players().get(next);
            if (player.hasFinished()) continue;
            if (session.mutableSkips().remove(player.color())) {
                session.record(player.name() + " forfeits their turn (surprise penalty).");
                continue;
            }
            session.currentPlayer(next);
            session.turn(session.turn() + 1);
            session.phase(GameSession.Phase.ROLL);
            session.status(player.name() + "'s turn — roll the dice.");
            return;
        }
        // When everybody eligible was skipped, consume the penalties and try again next round.
        for (int offset = 1; offset <= count; offset++) {
            int next = (session.currentIndex() + offset) % count;
            if (!session.players().get(next).hasFinished()) {
                session.currentPlayer(next);
                session.turn(session.turn() + 1);
                session.phase(GameSession.Phase.ROLL);
                session.status(session.current().name() + "'s turn — roll the dice.");
                return;
            }
        }
        session.phase(GameSession.Phase.FINISHED);
    }

    private void registerFinish(GameSession session, Player player) {
        if (!player.hasFinished() || session.ranking().contains(player.color())) return;
        session.mutableRanking().add(player.color());
        session.record(player.name() + " finished in place " + session.ranking().size() + "!");
        if (session.ranking().size() == session.players().size() - 1) {
            for (Player last : session.players()) if (!session.ranking().contains(last.color()))
                session.mutableRanking().add(last.color());
            session.phase(GameSession.Phase.FINISHED);
            session.record("Game over! Winner: " + session.findPlayer(session.ranking().get(0)).name() + ".");
        }
    }

    private static void requirePhase(GameSession session, GameSession.Phase phase) {
        if (session.phase() != phase) throw new IllegalStateException("Expected " + phase + ", got " + session.phase());
    }

    private static int firstBlockedStep(List<String> path, int atIndex, int movement, Set<String> blocked) {
        for (int step = 1; step <= movement; step++) if (blocked.contains(path.get(atIndex + step))) return step;
        return 0;
    }

    public static Set<String> opponentDoubleBlocks(GameSession session, PlayerColor color) {
        Set<String> blocked = new HashSet<>();
        Board board = session.board();
        for (Player p : session.players()) {
            if (p.color() == color) continue;
            Map<String, Integer> counts = new HashMap<>();
            for (Piece piece : p.pieces()) {
                if (piece.state() != Piece.State.CASTLE && piece.state() != Piece.State.FINISHED)
                    counts.merge(piece.squareId(board), 1, Integer::sum);
            }
            counts.forEach((square, total) -> { if (total >= 2) blocked.add(square); });
        }
        return blocked;
    }
}
