package model;

import controller.TutorialPlayback;
import java.util.Random;

public final class TutorialScriptTests {
    public static void main(String[] args) {
        var script = new TutorialScript();
        check(script.duration() >= 90_000 && script.duration() <= 120_000, "tutorial duration");
        var engine = new GameEngine(new BoardRepository());
        for (var shot : script.shots()) {
            check(!shot.caption().english().isBlank() && !shot.caption().hebrew().isBlank(), "missing subtitle");
            var before = shot.project(false);
            var after = shot.project(true);
            for (String id : shot.highlights()) check(shot.board().cell(id) != null, "unknown highlight");
            if (shot.move() == null) continue;
            var move = shot.move();
            var hero = Replay.piece(before, move.piece());
            check(shot.route().get(0).equals(hero.squareId(shot.board())), "motion origin");
            check(shot.route().get(shot.route().size() - 1).equals(
                    Replay.piece(after, move.piece()).squareId(shot.board())), "motion destination");
            if (shot.dice() != null) {
                engine.roll(before, dice(shot.dice()));
                check(before.legalPieceIds().contains(hero.id()), "illegal demonstration roll: " + shot.caption().english());
                engine.move(before, hero.id());
            } else {
                int amount = switch (shot.visual()) {
                    case CORRECT -> 2;
                    case INCORRECT, SURPRISE -> -3;
                    default -> throw new AssertionError("Unknown effect");
                };
                engine.shiftPiece(before, hero, amount);
            }
            for (int i = 0; i < 8; i++) check(Replay.piece(before, i).pathIndex() == Replay.piece(after, i).pathIndex(),
                    "script differs from the game engine: " + shot.caption().english());
        }

        var player = new TutorialPlayback(script);
        player.play(0);
        player.tick(750_000_000);
        check(player.position() == 750 && player.frame().elapsed() == 750, "play clock");
        player.pause(1_000_000_000);
        player.tick(2_000_000_000);
        check(player.position() == 1000 && !player.playing(), "pause clock");
        for (int i = TutorialScript.CHAPTERS.size() - 1; i >= 0; i--) {
            player.chapter(i, 2_000_000_000);
            check(player.frame().shot().chapter() == i && player.frame().elapsed() == 0, "chapter boundary");
        }
        player.seek(script.duration() - 50, 0);
        player.play(0);
        player.tick(100_000_000);
        check(player.position() == script.duration() && !player.playing(), "end of playback");
        player.play(100_000_000);
        check(player.position() == 0 && player.playing(), "restart at end");
        System.out.println("Tutorial script and playback passed: " + script.duration() / 1000 + " seconds.");
    }

    private static Random dice(DiceRoll roll) {
        return new Random(0) {
            private boolean regular = true;
            @Override public int nextInt(int bound) {
                int value = regular ? roll.regular() : roll.special();
                regular = false;
                check(value >= 1 && value <= bound, "die value outside policy");
                return value - 1;
            }
        };
    }

    private static void check(boolean passed, String message) {
        if (!passed) throw new AssertionError(message);
    }
}
