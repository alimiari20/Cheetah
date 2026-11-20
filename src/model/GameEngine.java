
package model;

import java.util.*;

/**
 * All gameplay rules live here.
 * No Swing imports, files, or UI callbacks.
 */
public final class GameEngine {

    public record MoveFeedback(
            String message,
            String squareId,
            int steps,
            List<String> captures,
            boolean finished) {

        public MoveFeedback {
            captures = List.copyOf(captures);
        }
    }

    public record SurpriseFeedback(
            SurpriseOutcome outcome,
            String message,
            String landingSquare) {}

    private final BoardRepository boards;

    public GameEngine(BoardRepository boards) {
        this.boards = Objects.requireNonNull(boards);
    }

    public GameSession start(
            Difficulty difficulty,
            List<String> names) {

        return start(difficulty, names, 0);
    }

    public GameSession start(
            Difficulty difficulty,
            List<String> names,
            int starterIndex) {

        return start(
            difficulty,
            names,
            starterIndex,
            Arrays.asList(PlayerColor.values())
                  .subList(0, names.size())
        );
    }

    /**
     * STATIC BOARD: used for the setup-screen preview.
     */
    public GameSession start(
            Difficulty difficulty,
            List<String> names,
            int starterIndex,
            List<PlayerColor> selectedColors) {

        return createSession(
            boards.load(difficulty),
            names,
            starterIndex,
            selectedColors
        );
    }

    /**
     * DYNAMIC BOARD: called once when a real match begins.
     */
    public GameSession startNewGame(
            Difficulty difficulty,
            List<String> names,
            int starterIndex,
            List<PlayerColor> selectedColors,
            Random random) {

        validatePlayers(names, starterIndex, selectedColors);

        return createSession(
            boards.createGameBoard(difficulty, random),
            names,
            starterIndex,
            selectedColors
        );
    }

    private static void validatePlayers(
            List<String> names,
            int starterIndex,
            List<PlayerColor> selectedColors) {

        if (names == null || names.size() < 2
                || names.size() > 4) {
            throw new IllegalArgumentException(
                "Choose 2 to 4 players"
            );
        }

        if (selectedColors == null
                || selectedColors.size() != names.size()
                || new HashSet<>(selectedColors).size()
                    != selectedColors.size()
                || selectedColors.stream()
                    .anyMatch(Objects::isNull)) {

            throw new IllegalArgumentException(
                "Each player must have a unique color"
            );
        }

        if (starterIndex < 0 || starterIndex >= names.size()) {
            throw new IllegalArgumentException(
                "Invalid first player"
            );
        }
    }

    private GameSession createSession(
            Board board,
            List<String> names,
            int starterIndex,
            List<PlayerColor> selectedColors) {

        validatePlayers(names, starterIndex, selectedColors);

        List<Player> players = new ArrayList<>();

        for (int i = 0; i < names.size(); i++) {

            int index = (starterIndex + i) % names.size();

            String name = names.get(index) == null
                ? "" : names.get(index).trim();

            if (name.isBlank()) {
                name = selectedColors.get(index).label();
            }

            players.add(
                new Player(name, selectedColors.get(index))
            );
        }

        return new GameSession(board, players);
    }

    public DiceRoll roll(GameSession session, Random random) {
        requirePhase(session, GameSession.Phase.ROLL);
        Objects.requireNonNull(random, "random");

        // An awarded surprise roll is independent of the ordinary six counter.
        boolean surprise = session.isUltraNextRoll();
        DiceStrategy strategy = surprise
            ? DiceStrategies.surpriseUltraBoost()
            : DiceStrategies.forDifficulty(session.difficulty());
        DiceRoll roll = strategy.roll(random);
        session.ultraNextRoll(false);
        session.surpriseRollInProgress(surprise);
        session.dice(roll);

        if (!surprise) {
            int count = roll.rolledSix()
                ? session.consecutiveSixes() + 1 : 0;
            session.consecutiveSixes(count);

            if (count >= 3) {
                session.record(session.current().name()
                        + " rolled three consecutive regular sixes; third roll canceled.");
                // The third six is not played; no castle exit or movement occurs.
                advanceTurn(session);
                return roll;
            }
        }

        if (roll.rolledSix()) {
            // A six earns a normal extra roll even with zero movement or no legal piece.
            session.pendingExtraRolls(session.pendingExtraRolls() + 1);
        }

        session.mutableLegalMoves().clear();
        session.mutableLegalMoves().addAll(calculateLegalMoves(session));
        session.phase(GameSession.Phase.SELECT_PIECE);
        session.record(session.current().name()
                + (surprise ? " used an immediate surprise UltraBoost and rolled " : " rolled ")
                + roll.description(session.difficulty())
                + (session.legalPieceIds().isEmpty()
                    ? ". No legal move; pass to continue."
                    : ". Select a highlighted piece."));
        return roll;
    }

    /** Return the steps applicable to the SPECIFIC piece being considered. */
    public int stepsFor(GameSession session, Piece piece) {
        if (session.dice() == null) {
            throw new IllegalStateException("Dice have not been rolled");
        }
        DiceStrategy strategy = session.isSurpriseRollInProgress()
                ? DiceStrategies.surpriseUltraBoost()
                : DiceStrategies.forDifficulty(session.difficulty());
        return strategy.stepsFor(piece, session.dice());
    }

    /**
     * Full movement has priority over stopping at a block.
     */
    public Set<String> calculateLegalMoves(
            GameSession session) {

        DiceRoll dice = session.dice();

        if (dice == null || session.consecutiveSixes() >= 3) {
            return Set.of();
        }

        Player current = session.current();

        Set<String> blocked = opponentDoubleBlocks(
            session,
            current.color()
        );

        List<String> track =
            session.board().path(current.color());

        Set<String> full = new LinkedHashSet<>();
        Set<String> truncated = new LinkedHashSet<>();

        for (Piece piece : current.pieces()) {

            if (piece.state() == Piece.State.FINISHED) {
                continue;
            }

            if (piece.state() == Piece.State.CASTLE) {

                if (dice.regular() == 6
                        && !blocked.contains(
                            session.board().start(current.color())
                        )) {

                    full.add(piece.id());
                }

                continue;
            }

            int movement = stepsFor(session, piece);
            if (movement <= 0
                    || piece.pathIndex() + movement
                        >= track.size()) {

                continue;
            }

            int firstBlocked = firstBlockedStep(
                track,
                piece.pathIndex(),
                movement,
                blocked
            );

            if (firstBlocked == 0) {
                full.add(piece.id());
            } else if (firstBlocked > 1) {
                truncated.add(piece.id());
            }
        }

        return full.isEmpty() ? truncated : full;
    }

    public MoveFeedback move(
            GameSession session,
            String pieceId) {

        requirePhase(
            session,
            GameSession.Phase.SELECT_PIECE
        );

        if (!session.legalPieceIds().contains(pieceId)) {
            throw new IllegalArgumentException(
                "Not a legal move: " + pieceId
            );
        }

        Piece piece = session.findPiece(pieceId);
        Player player = session.current();

        if (piece == null || piece.color() != player.color()) {
            throw new IllegalArgumentException("Wrong player");
        }

        Board board = session.board();

        int steps = 0;
        List<String> captures = new ArrayList<>();

        boolean wasCastle =
            piece.state() == Piece.State.CASTLE;

        if (wasCastle) {

            piece.place(board, 0);

        } else {

            int requested = stepsFor(session, piece);

            Set<String> blocked = opponentDoubleBlocks(
                session,
                player.color()
            );

            int firstBlocked = firstBlockedStep(
                board.path(player.color()),
                piece.pathIndex(),
                requested,
                blocked
            );

            steps = firstBlocked > 0
                ? firstBlocked - 1 : requested;

            if (steps < 1) {
                throw new IllegalStateException(
                    "Move blocked at the first square"
                );
            }

            piece.place(
                board,
                piece.pathIndex() + steps
            );

            if (piece.state() != Piece.State.FINISHED
                    && !board.isSafe(piece.squareId(board))) {

                for (Player opponent : session.players()) {

                    if (opponent.color() == player.color()) {
                        continue;
                    }

                    List<Piece> occupying = new ArrayList<>();

                    for (Piece target : opponent.pieces()) {

                        if ((target.state() == Piece.State.TRACK
                                || target.state() == Piece.State.HOME)
                                && target.squareId(board).equals(
                                    piece.squareId(board))) {

                            occupying.add(target);
                        }
                    }

                    if (occupying.size() == 1) {

                        occupying.get(0).returnCastle();

                        captures.add(
                            opponent.name() + "'s "
                            + (occupying.get(0).number() + 1)
                        );
                    }
                }
            }
        }

        session.mutableLegalMoves().clear();

        String position = piece.squareId(board);

        String action = wasCastle
            ? player.name() + " entered the board with piece "
                + (piece.number() + 1)
            : player.name() + " moved piece "
                + (piece.number() + 1)
                + " by " + steps + " squares";

        if (!captures.isEmpty()) {
            action += " and captured "
                + String.join(", ", captures);
        }

        if (piece.state() == Piece.State.FINISHED) {
            action += " — reached HOME!";
        }

        session.record(action + ".");

        registerFinish(session, player);

        return new MoveFeedback(
            action,
            position,
            steps,
            captures,
            piece.state() == Piece.State.FINISHED
        );
    }

    /**
     * Movement from questions and surprise effects.
     */
    public MoveFeedback shiftPiece(
            GameSession session,
            Piece piece,
            int delta) {

        if (piece == null
                || piece.state() == Piece.State.CASTLE
                || piece.state() == Piece.State.FINISHED) {

            return new MoveFeedback(
                "Piece cannot move",
                null,
                0,
                List.of(),
                false
            );
        }

        Board board = session.board();

        int old = piece.pathIndex();

        List<String> path = board.path(piece.color());

        Set<String> blocked = opponentDoubleBlocks(
            session,
            piece.color()
        );

        int index = old;
        int direction = Integer.compare(delta, 0);

        for (int i = 0; i < Math.abs(delta); i++) {

            int candidate = index + direction;

            if (candidate < 0
                    || candidate >= path.size()
                    || blocked.contains(path.get(candidate))) {
                break;
            }

            index = candidate;

            if (index == path.size() - 1) {
                break;
            }
        }

        piece.place(board, index);

        List<String> captures =
            captureOpponents(session, piece);

        if (!captures.isEmpty()) {
            session.record(
                piece.color().label()
                + " captured "
                + String.join(", ", captures)
                + "."
            );
        }

        registerFinish(
            session,
            session.findPlayer(piece.color())
        );

        return new MoveFeedback(
            "Shifted " + (index - old) + " squares",
            piece.squareId(board),
            index - old,
            captures,
            piece.state() == Piece.State.FINISHED
        );
    }

    public void resolveQuestion(
            GameSession session,
            String pieceId,
            Question question,
            boolean correct) {

        Piece piece = Objects.requireNonNull(
            session.findPiece(pieceId)
        );

        if (piece.color() != session.current().color()) {
            throw new IllegalArgumentException("Wrong player");
        }

        int reward =
            question.movementReward() * (correct ? 1 : -1);

        MoveFeedback move = shiftPiece(
            session,
            piece,
            reward
        );

        session.record(
        	    session.findPlayer(piece.color()).name()
        	    + " answered "
            + (correct ? "correctly" : "incorrectly")
            + " ("
            + question.difficulty().name().toLowerCase()
            + "). "
            + move.message()
            + "."
        );
    }

    public SurpriseFeedback applySurprise(
            GameSession session,
            String pieceId,
            SurpriseOutcome outcome,
            Random random) {

        Piece piece = Objects.requireNonNull(
            session.findPiece(pieceId)
        );

        if (piece.color() != session.current().color()) {
            throw new IllegalArgumentException("Wrong player");
        }

        String text;
        String square = null;

        switch (outcome) {

        case DIRECT_FINISH -> {
            new DirectFinishCommand(this, session, piece).execute();

            text = "Surprise: Direct finish! Piece "
                + (piece.number() + 1)
                + " reached HOME.";
        }

        case OPPONENT_BACKWARD -> {
            throw new IllegalStateException(
                "OPPONENT_BACKWARD requires player selection in GameController"
            );
        }

            case RETURN_TO_CASTLE -> {
                new ReturnToCastleCommand(piece).execute();

                text = "Surprise: Piece "
                    + (piece.number() + 1)
                    + " returned to the castle!";
            }

            case BACKWARD_THREE -> {
                BackwardThreeCommand command =
                    new BackwardThreeCommand(this, session, piece);

                command.execute();

                MoveFeedback adjusted = command.getResult();

                square = adjusted.steps() == 0
                    ? null : adjusted.squareId();

                text = "Surprise: "
                    + Math.abs(adjusted.steps())
                    + " squares backward.";
            }

            case LOSE_NEXT_TURN -> {

            	new LoseNextTurnCommand(session, piece.color()).execute();
               

            	text = "Surprise: "
            		    + session.current().name()
            		    + " loses their next turn!";
            }

            case ULTRABOOST -> {

            	new UltraBoostCommand(session).execute();

                text = "Surprise: Immediate UltraBoost "
                    + "extra roll granted!";
            }

            default -> throw new IllegalArgumentException(
                "Unknown outcome"
            );
        }

        session.record(text);

        return new SurpriseFeedback(
            outcome,
            text,
            square
        );
    }

    /**
     * Completes a move after special effects are resolved.
     */
    public void completeMove(GameSession session) {
        if (session.phase() == GameSession.Phase.FINISHED) return;

        // A surprise UltraBoost must run before any queued regular bonus.
        if (session.isUltraNextRoll()) {
            prepareNextRoll(session, "Immediate surprise UltraBoost roll!");
            return;
        }
        if (session.pendingExtraRolls() > 0 && !session.current().hasFinished()) {
            session.pendingExtraRolls(session.pendingExtraRolls() - 1);
            prepareNextRoll(session, session.current().name() + " gets an extra roll!");
            return;
        }
        advanceTurn(session);
    }

    private void prepareNextRoll(GameSession session, String message) {
        session.phase(GameSession.Phase.ROLL);
        session.dice(null);
        session.surpriseRollInProgress(false);
        session.mutableLegalMoves().clear();
        session.status(message);
    }

    public void pass(GameSession session) {

        requirePhase(
            session,
            GameSession.Phase.SELECT_PIECE
        );

        if (!session.legalPieceIds().isEmpty()) {
            throw new IllegalStateException(
                "Select a legal piece instead of passing"
            );
        }

        session.record(
            session.current().name()
            + " has no legal move."
        );

        completeMove(session);
    }

    private void advanceTurn(GameSession session) {

        session.dice(null);
        session.ultraNextRoll(false);
        session.pendingExtraRolls(0);
        session.surpriseRollInProgress(false);
        session.mutableLegalMoves().clear();
        session.consecutiveSixes(0);

        int count = session.players().size();

        for (int offset = 1; offset <= count * 2; offset++) {

            int next =
                (session.currentIndex() + offset) % count;

            Player player = session.players().get(next);

            if (player.hasFinished()) {
                continue;
            }

            if (session.mutableSkips().remove(player.color())) {

                session.record(
                    player.name()
                    + " forfeits their turn (surprise penalty)."
                );

                continue;
            }

            session.currentPlayer(next);
            session.turn(session.turn() + 1);
            session.phase(GameSession.Phase.ROLL);

            session.status(
                player.name() + "'s turn — roll the dice."
            );

            return;
        }

        
        


     throw new IllegalStateException("No eligible player found");
    }
    void finishPieceDirectly(GameSession session, Piece piece) {
        piece.place(
            session.board(),
            session.board().path(piece.color()).size() - 1
        );

        registerFinish(session, session.findPlayer(piece.color()));
    }
    private void registerFinish(
            GameSession session,
            Player player) {

        if (!player.hasFinished()
                || session.ranking().contains(player.color())) {
            return;
        }

        session.mutableRanking().add(player.color());

        session.record(
            player.name()
            + " finished in place "
            + session.ranking().size()
            + "!"
        );

        if (session.ranking().size()
                == session.players().size() - 1) {

            for (Player last : session.players()) {

                if (!session.ranking().contains(last.color())) {
                    session.mutableRanking().add(last.color());
                }
            }

            session.phase(GameSession.Phase.FINISHED);

            session.record(
                "Game over! Winner: "
                + session.findPlayer(
                    session.ranking().get(0)
                  ).name()
                + "."
            );
        }
    }

    private static void requirePhase(
            GameSession session,
            GameSession.Phase phase) {

        if (session.phase() != phase) {

            throw new IllegalStateException(
                "Expected " + phase
                + ", got " + session.phase()
            );
        }
    }

    private static int firstBlockedStep(
            List<String> path,
            int atIndex,
            int movement,
            Set<String> blocked) {

        for (int step = 1; step <= movement; step++) {

            if (blocked.contains(path.get(atIndex + step))) {
                return step;
            }
        }

        return 0;
    }

    public static Set<String> opponentDoubleBlocks(
            GameSession session,
            PlayerColor color) {

        Set<String> blocked = new HashSet<>();
        Board board = session.board();

        for (Player player : session.players()) {

            if (player.color() == color) {
                continue;
            }

            Map<String, Integer> counts = new HashMap<>();

            for (Piece piece : player.pieces()) {

                if (piece.state() != Piece.State.CASTLE
                        && piece.state() != Piece.State.FINISHED) {

                    counts.merge(
                        piece.squareId(board),
                        1,
                        Integer::sum
                    );
                }
            }

            counts.forEach((square, total) -> {
                if (total >= 2) {
                    blocked.add(square);
                }
            });
        }

        return blocked;
    }

    private List<String> captureOpponents(
            GameSession session,
            Piece piece) {

        List<String> captures = new ArrayList<>();

        if (piece.state() == Piece.State.CASTLE
                || piece.state() == Piece.State.FINISHED) {
            return captures;
        }

        Board board = session.board();
        String square = piece.squareId(board);

        if (board.isSafe(square)) {
            return captures;
        }

        for (Player opponent : session.players()) {

            if (opponent.color() == piece.color()) {
                continue;
            }

            List<Piece> occupying = new ArrayList<>();

            for (Piece target : opponent.pieces()) {

                if ((target.state() == Piece.State.TRACK
                        || target.state() == Piece.State.HOME)
                        && square.equals(
                            target.squareId(board)
                        )) {

                    occupying.add(target);
                }
            }

            if (occupying.size() == 1) {

                Piece captured = occupying.get(0);
                captured.returnCastle();

                captures.add(
                    opponent.name() + "'s "
                    + (captured.number() + 1)
                );
            }
        }

        return captures;
    }
}
