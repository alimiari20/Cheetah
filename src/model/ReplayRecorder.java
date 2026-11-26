package model;

import java.util.*;
import java.util.concurrent.TimeUnit;

/** Captures state at controller action boundaries, after the engine resolves the real outcome. */
public final class ReplayRecorder {
    private final GameSession game;
    private final List<Replay.Participant> players;
    private final List<Replay.Frame> frames = new ArrayList<>();
    private final List<String> messages = new ArrayList<>();
    private final long started = System.nanoTime();

    public ReplayRecorder(GameSession game, Map<PlayerColor,AiLevel> controls) {
        this(game,controls,List.of());
    }

    public ReplayRecorder(GameSession game, Map<PlayerColor,AiLevel> controls, List<String> openingRolls) {
        this.game = game;
        players = game.players().stream().map(p -> new Replay.Participant(p.name(), p.color(),
                controls.getOrDefault(p.color(), AiLevel.HUMAN))).toList();
        messages.addAll(openingRolls);
        messages.add(game.status());
        game.observeReplay(messages::add);
        record(Replay.Kind.START);
    }

    public void record(Replay.Kind kind) { record(kind, game.dice(), null, null, null, 0); }
    public void roll(DiceRoll actual) { record(Replay.Kind.ROLL, actual, null, null, null, 0); }
    public void note(String message) { messages.add(message); }

    public void record(Replay.Kind kind, CaptureMove capture, Replay.Answer answer,
                       SurpriseOutcome surprise, int opponentDie) {
        record(kind, game.dice(), capture, answer, surprise, opponentDie);
    }

    private void record(Replay.Kind kind, DiceRoll dice, CaptureMove capture, Replay.Answer answer,
                        SurpriseOutcome surprise, int opponentDie) {
        var positions = game.players().stream().flatMap(p -> p.pieces().stream()).map(Piece::pathIndex).toList();
        List<Replay.Motion> motions = new ArrayList<>();
        if (!frames.isEmpty()) {
            List<Integer> before = frames.get(frames.size()-1).positions();
            for (int i=0; i<positions.size(); i++) {
                int from = before.get(i), to = positions.get(i);
                if (from == to) continue;
                Piece piece = Replay.piece(game, i);
                boolean captured = capture != null && capture.victims().contains(piece);
                boolean directFinish = surprise == SurpriseOutcome.DIRECT_FINISH;
                motions.add(new Replay.Motion(i, route(piece, from, to, directFinish), captured));
            }
        }
        var blocks = new TreeSet<String>();
        for (var player : game.players()) blocks.addAll(GameEngine.opponentDoubleBlocks(game, player.color()));
        if (!frames.isEmpty()) {
            var priorBlocks = frames.get(frames.size()-1).doubleBlocks();
            for (String block : blocks) if (!priorBlocks.contains(block)) note("Double Block formed: " + block);
            for (String block : priorBlocks) if (!blocks.contains(block)) note("Double Block cleared: " + block);
        }
        frames.add(new Replay.Frame(kind, TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started),
                game.turn(), game.currentIndex(), game.phase(), dice, game.pendingExtraRolls(), game.isUltraNextRoll(),
                game.consecutiveSixes(), positions, game.ranking(), List.copyOf(blocks), messages, motions, answer,
                surprise, opponentDie));
        messages.clear();
    }

    private List<String> route(Piece piece, int from, int to, boolean jump) {
        var board = game.board();
        var path = board.path(piece.color());
        String castle = board.castleSlot(piece.color(), piece.number());
        if (from < 0 || to < 0 || jump) {
            return List.of(from < 0 ? castle : path.get(from), to < 0 ? castle : path.get(to));
        }
        int step = Integer.compare(to, from);
        var result = new ArrayList<String>();
        for (int i=from; ; i+=step) {
            result.add(path.get(i));
            if (i == to) return result;
        }
    }

    public Replay complete() {
        if (game.phase() != GameSession.Phase.FINISHED) throw new IllegalStateException("Game is not complete");
        record(Replay.Kind.FINISH);
        game.observeReplay(null);
        return new Replay(game.board(), players, frames);
    }
}
