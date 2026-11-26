package model;

import java.util.*;

/** A completed game's immutable presentation data. No engine or repositories are needed to play it. */
public record Replay(Board board, List<Participant> players, List<Frame> frames) {
    public enum Kind { START, ROLL, PASS, MOVE, QUESTION, SURPRISE, OPPONENT, TURN, FINISH }
    public record Participant(String name, PlayerColor color, AiLevel control) {}
    public record Answer(Question question, int selected, boolean correct) {
        public Answer {
            if (selected < -1 || selected >= question.choices().size()
                    || correct != (selected == question.correctIndex())) {
                throw new IllegalArgumentException("Invalid replay answer");
            }
        }
    }
    /** Route includes the origin and destination. A jump contains only its endpoints. */
    public record Motion(int piece, List<String> route, boolean captured) {
        public Motion { route = List.copyOf(route); }
    }
    public record Frame(Kind kind, long elapsedMillis, int turn, int currentPlayer, GameSession.Phase phase,
                        DiceRoll dice, int extraRolls, boolean ultraBoostPending, int sixes,
                        List<Integer> positions, List<PlayerColor> ranking, List<String> doubleBlocks,
                        List<String> messages, List<Motion> motions, Answer answer,
                        SurpriseOutcome surprise, int opponentDie) {
        public Frame {
            positions = List.copyOf(positions);
            ranking = List.copyOf(ranking);
            doubleBlocks = List.copyOf(doubleBlocks);
            messages = List.copyOf(messages);
            motions = List.copyOf(motions);
        }
    }

    public Replay {
        players = List.copyOf(players);
        frames = List.copyOf(frames);
        if (players.size() < 2 || players.size() > 4
                || players.stream().map(Participant::color).distinct().count() != players.size()
                || frames.size() < 2 || frames.get(0).kind() != Kind.START
                || frames.get(frames.size()-1).kind() != Kind.FINISH
                || frames.get(frames.size()-1).phase() != GameSession.Phase.FINISHED
                || frames.get(frames.size()-1).ranking().size() != players.size()) {
            throw new IllegalArgumentException("Incomplete replay");
        }
        var colors = players.stream().map(Participant::color).toList();
        long previous = 0;
        for (Frame frame : frames) {
            if (frame.elapsedMillis() < previous || frame.currentPlayer() < 0
                    || frame.currentPlayer() >= players.size() || frame.turn() < 1
                    || frame.positions().size() != players.size()*4 || frame.extraRolls() < 0
                    || !colors.containsAll(frame.ranking())
                    || frame.ranking().stream().distinct().count() != frame.ranking().size()
                    || frame.opponentDie() < 0 || frame.opponentDie() > 6) {
                throw new IllegalArgumentException("Invalid replay frame");
            }
            previous = frame.elapsedMillis();
            for (int i=0; i<frame.positions().size(); i++) {
                int index = frame.positions().get(i);
                if (index < -1 || index >= board.path(players.get(i/4).color()).size()) {
                    throw new IllegalArgumentException("Invalid replay position");
                }
            }
            for (Motion motion : frame.motions()) {
                if (motion.piece() < 0 || motion.piece() >= players.size()*4 || motion.route().size() < 2
                        || motion.route().stream().anyMatch(id -> board.cell(id) == null)) {
                    throw new IllegalArgumentException("Invalid replay route");
                }
            }
        }
    }

    /** A fresh render projection. It shares only the immutable board with the recording. */
    public GameSession project(int index) {
        Frame frame = frames.get(index);
        var projection = new GameSession(board, players.stream().map(p -> new Player(p.name(), p.color())).toList());
        projection.currentPlayer(frame.currentPlayer());
        projection.turn(frame.turn());
        projection.phase(frame.phase());
        projection.dice(frame.dice());
        projection.pendingExtraRolls(frame.extraRolls());
        projection.ultraNextRoll(frame.ultraBoostPending());
        projection.consecutiveSixes(frame.sixes());
        projection.mutableRanking().addAll(frame.ranking());
        for (int i=0; i<frame.positions().size(); i++) {
            int position = frame.positions().get(i);
            if (position >= 0) piece(projection, i).place(board, position);
        }
        return projection;
    }

    public static Piece piece(GameSession projection, int index) {
        return projection.players().get(index / 4).pieces().get(index % 4);
    }
}
