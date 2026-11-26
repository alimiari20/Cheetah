package model;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/** Chooses from authorized IDs. Evaluation reads public board state only. */
@FunctionalInterface
public interface AiMoveStrategy {
    String choose(GameEngine engine, GameSession game, Random decisionRandom);

    static AiMoveStrategy forLevel(AiLevel level) {
        return switch (level) {
            case EASY -> (engine, game, random) -> randomChoice(game.legalPieceIds(), random);
            case MEDIUM -> (engine, game, random) -> best(engine, game, false);
            case HARD -> (engine, game, random) -> best(engine, game, true);
            case HUMAN -> throw new IllegalArgumentException("A human has no AI strategy");
        };
    }

    static String opponent(AiLevel level, GameSession game, Set<String> eligible, Random random) {
        if (level == AiLevel.EASY) return randomChoice(eligible, random);
        return eligible.stream().max(Comparator.<String>comparingInt(id -> game.findPiece(id).pathIndex())
                .thenComparing(Comparator.naturalOrder())).orElse(null);
    }

    /** This policy receives only the displayed prompt and choices, never the answer key. */
    static int answer(AiLevel level, Difficulty difficulty, String prompt, List<String> choices, Random random) {
        int chance = switch (level) {
            case MEDIUM -> switch (difficulty) { case EASY -> 65; case MEDIUM -> 45; case HARD -> 25; };
            case HARD -> switch (difficulty) { case EASY -> 90; case MEDIUM -> 75; case HARD -> 55; };
            default -> 0;
        };
        if (random.nextInt(100) >= chance) return random.nextInt(choices.size());
        Set<String> words = words(prompt);
        int best = 0;
        var candidates = new java.util.ArrayList<Integer>();
        for (int i = 0; i < choices.size(); i++) {
            Set<String> answer = words(choices.get(i));
            int matches = (int) answer.stream().filter(words::contains).count();
            if (matches > best) {
                candidates.clear();
                best = matches;
            }
            if (matches == best) candidates.add(i);
        }
        return candidates.get(random.nextInt(candidates.size()));
    }

    private static Set<String> words(String text) {
        return new java.util.HashSet<>(List.of(text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")))
                .stream().filter(word -> word.length() >= 4).collect(java.util.stream.Collectors.toSet());
    }

    private static String randomChoice(Set<String> ids, Random random) {
        return ids.isEmpty() ? null : List.copyOf(ids).get(random.nextInt(ids.size()));
    }

    private static String best(GameEngine engine, GameSession game, boolean hard) {
        return game.legalPieceIds().stream()
                .max(Comparator.<String>comparingDouble(id -> score(engine, game, game.findPiece(id), hard))
                        .thenComparing(Comparator.naturalOrder())).orElse(null);
    }

    private static double score(GameEngine engine, GameSession game, Piece piece, boolean hard) {
        Board board = game.board();
        List<String> track = board.path(piece.color());
        int index = engine.previewLandingIndex(game, piece);
        String square = track.get(index);
        if (index == track.size() - 1) return 120;
        boolean safe = board.isSafe(square) || board.isHome(piece.color(), square);
        double value = (index - piece.pathIndex()) * 1.3 + index * .27;
        if (piece.state() == Piece.State.CASTLE) value += 16;
        if (safe) value += 10;
        if (board.isHome(piece.color(), square)) value += 10;
        long allies = occupying(game.current(), board, square, piece);
        if (hard && allies == 1) value += 14;
        if (hard && allies > 1) value -= 4;
        if (safe || allies > 0) return value + (hard ? finishPotential(board, piece, index) : 0);
        for (Player opponent : game.players()) {
            if (opponent.color() == piece.color()) continue;
            long occupying = occupying(opponent, board, square, null);
            if (occupying == 1) value += 30;
            value -= threat(game, opponent, square, hard) * (hard ? 55 : 20);
        }
        return value + (hard ? finishPotential(board, piece, index) : 0);
    }

    private static long occupying(Player player, Board board, String square, Piece exclude) {
        return player.pieces().stream().filter(p -> p != exclude && p.state() != Piece.State.CASTLE
                && p.state() != Piece.State.FINISHED && square.equals(p.squareId(board))).count();
    }

    private static double threat(GameSession game, Player opponent, String square, boolean hard) {
        Board board = game.board();
        List<String> path = board.path(opponent.color());
        int target = path.indexOf(square);
        if (target < 0 || board.isHome(opponent.color(), square)) return 0;
        Set<String> blocks = GameEngine.opponentDoubleBlocks(game, opponent.color());
        boolean[] reaches = new boolean[31];
        for (Piece foe : opponent.pieces()) {
            if (foe.state() != Piece.State.TRACK) continue;
            int distance = target - foe.pathIndex();
            if (distance <= 0 || distance >= reaches.length) continue;
            if (path.subList(foe.pathIndex() + 1, target + 1).stream().noneMatch(blocks::contains)) {
                reaches[distance] = true;
                if (!hard && distance <= 6) return .20;
            }
        }
        if (!hard) return 0;
        int outcomes = 0;
        int sides = Math.max(1, game.difficulty().specialSides());
        for (int regular = 1; regular <= 6; regular++) {
            for (int special = 1; special <= sides; special++) {
                int movement = sides == 1 ? regular : regular * (special - 1);
                if (reaches[movement]) outcomes++;
            }
        }
        return (double) outcomes / (6 * sides);
    }

    private static double finishPotential(Board board, Piece piece, int index) {
        List<String> path = board.path(piece.color());
        int remaining = path.size() - index - 1;
        return board.isHome(piece.color(), path.get(index)) && remaining <= 6 ? 8 + (6 - remaining) : 0;
    }
}
