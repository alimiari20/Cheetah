package model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public final class AiOpponentTests {
    private static final GameEngine ENGINE = new GameEngine(new BoardRepository());

    public static void main(String[] args) {
        int turns = 0;
        for (Difficulty board : Difficulty.values()) {
            turns += play(board, List.of(AiLevel.HUMAN, AiLevel.EASY));
            turns += play(board, List.of(AiLevel.HUMAN, AiLevel.EASY, AiLevel.MEDIUM));
            turns += play(board, List.of(AiLevel.HUMAN, AiLevel.EASY, AiLevel.MEDIUM, AiLevel.HARD));
            turns += play(board, List.of(AiLevel.MEDIUM, AiLevel.HUMAN, AiLevel.HUMAN, AiLevel.HARD));
            turns += play(board, List.of(AiLevel.HARD, AiLevel.HUMAN));
        }
        check(turns > 100, "Exercise all strategies");
        checkQuestionPolicy();
        System.out.println("AI opponent tests passed: " + turns + " AI decisions across mixed configurations.");
    }

    private static int play(Difficulty difficulty, List<AiLevel> controls) {
        var names = new ArrayList<String>();
        for (int i = 0; i < controls.size(); i++) names.add("Player " + (i + 1));
        var dice = new Random(17);
        var decisions = new Random(95);
        var game = ENGINE.startNewGame(difficulty, names, 1,
                Arrays.asList(PlayerColor.values()).subList(0, controls.size()), dice);
        int selected = 0;
        for (int i = 0; i < 400 && game.phase() != GameSession.Phase.FINISHED; i++) {
            if (game.phase() == GameSession.Phase.ROLL) ENGINE.roll(game, dice);
            if (game.phase() != GameSession.Phase.SELECT_PIECE) continue;
            if (game.legalPieceIds().isEmpty()) {
                ENGINE.pass(game);
                continue;
            }
            var level = controls.get(game.current().color().ordinal());
            var before = game.players().stream().flatMap(p -> p.pieces().stream())
                    .map(piece -> piece.state() + ":" + piece.pathIndex()).toList();
            DiceRoll rolled = game.dice();
            String id = level.automated()
                    ? AiMoveStrategy.forLevel(level).choose(ENGINE, game, decisions)
                    : game.legalPieceIds().iterator().next();
            check(game.legalPieceIds().contains(id), "Strategy selected an authorized move");
            check(game.dice() == rolled && before.equals(game.players().stream().flatMap(p -> p.pieces().stream())
                    .map(piece -> piece.state() + ":" + piece.pathIndex()).toList()), "Evaluation is read-only");
            Piece piece = game.findPiece(id);
            int landing = ENGINE.previewLandingIndex(game, piece);
            ENGINE.move(game,id);
            check(piece.pathIndex() == landing, "Read-only preview agrees with engine movement");
            if (level.automated()) selected++;
            ENGINE.completeMove(game);
        }
        return selected;
    }

    private static void checkQuestionPolicy() {
        var choices = List.of("Apples", "Controller and model", "Mercury", "Fonts");
        for (Difficulty difficulty : Difficulty.values()) {
            for (AiLevel level : List.of(AiLevel.EASY, AiLevel.MEDIUM, AiLevel.HARD)) {
                var random = new Random(23);
                var observed = new java.util.HashSet<Integer>();
                for (int i = 0; i < 200; i++) {
                    int choice = AiMoveStrategy.answer(level,difficulty,"What does a controller do?",choices,random);
                    check(choice >= 0 && choice < choices.size(), "Visible answer index is valid");
                    observed.add(choice);
                }
                check(observed.size() == choices.size(), "Policy can guess any answer");
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
