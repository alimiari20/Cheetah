package model;

import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Ali / Iteration 2 / US8 and US11.
 * Self-contained deterministic regression harness (works offline with Ant).
 * JUnit 5 equivalents are provided in test-junit/ for the course toolchain.
 */
public final class AliUS8Tests {
    private static final GameEngine engine = new GameEngine(new BoardRepository());
    private static int passed, failed;

    public static void main(String[] args) {
        test("R2 strategy selects correct implementation", () -> {
            truth(DiceStrategies.forDifficulty(Difficulty.EASY) instanceof EasyDiceStrategy);
            truth(DiceStrategies.forDifficulty(Difficulty.MEDIUM) instanceof BoostDiceStrategy);
            truth(DiceStrategies.forDifficulty(Difficulty.HARD) instanceof UltraBoostDiceStrategy);
        });
        test("SRS 3.2.3.3.2 Easy rolls only normal die", () -> {
            DiceRoll d = new EasyDiceStrategy().roll(new Fixed(5));
            equal(6, d.regular()); equal(1, d.special()); equal(6, d.movement());
            truth(!d.ultraBoost());
        });
        test("SRS 3.2.3.3.3 Boost d3 exact 1/2/3 and movement", () -> {
            for (int n=1; n<=3; n++) {
                DiceRoll d = new BoostDiceStrategy().roll(new Fixed(3,n-1));
                equal(4,d.regular()); equal(n,d.special()); equal(n-1,d.multiplier());
                equal(4*(n-1),d.movement());
            }
        });
        test("SRS 3.2.3.3.3 Ultra d6 exact 1..6 and movement", () -> {
            for (int n=1; n<=6; n++) {
                DiceRoll d = new UltraBoostDiceStrategy().roll(new Fixed(1,n-1));
                equal(2,d.regular()); equal(n,d.special()); equal(2*(n-1),d.movement());
            }
        });
        test("SRS 3.2.3.3.3 dice consume expected random values", () -> {
            Counting rng = new Counting();
            new EasyDiceStrategy().roll(rng); equal(List.of(6),rng.bounds);
            rng = new Counting(); new BoostDiceStrategy().roll(rng);
            equal(List.of(6,3),rng.bounds);
            rng = new Counting(); new UltraBoostDiceStrategy().roll(rng);
            equal(List.of(6,6),rng.bounds);
        });
        test("SRS 3.2.3.3.4 zero multiplier forbids track movement", () -> {
            GameSession s = game(Difficulty.MEDIUM);
            Piece p = piece(s,"red-0");p.place(s.board(),3);
            DiceRoll d = engine.roll(s,new Fixed(1,0));
            equal(0,d.movement()); truth(s.legalPieceIds().isEmpty());
            int previous=p.pathIndex();engine.pass(s);equal(previous,p.pathIndex());
        });
        test("SRS 3.2.3.3.4 zero multiplier still allows home d6 move", () -> {
            GameSession s=game(Difficulty.HARD);Piece p=piece(s,"red-0");
            int index=homeStart(s,PlayerColor.RED);p.place(s.board(),index);
            engine.roll(s,new Fixed(1,0));
            equal(0,s.dice().movement());truth(s.legalPieceIds().contains(p.id()));
            equal(2,engine.stepsFor(s,p));engine.move(s,p.id());equal(index+2,p.pathIndex());
        });
        test("SRS 3.2.4.3.7 mixed pieces get different steps", () -> {
            GameSession s=game(Difficulty.HARD);
            Piece track=piece(s,"red-0"),home=piece(s,"red-1");
            track.place(s.board(),1);home.place(s.board(),homeStart(s,PlayerColor.RED));
            engine.roll(s,new Fixed(1,3)); // d6=2, ultra N=4, shared=6, home=2
            equal(6,engine.stepsFor(s,track));equal(2,engine.stepsFor(s,home));
            truth(s.legalPieceIds().contains(track.id()));
            truth(s.legalPieceIds().contains(home.id()));
            engine.move(s,home.id());equal(homeStart(s,PlayerColor.RED)+2,home.pathIndex());
        });
        test("SRS 3.2.4.3.7 home exact entry unaffected by boost", () -> {
            GameSession s=game(Difficulty.HARD);Piece p=piece(s,"red-0");
            p.place(s.board(),s.board().path(p.color()).size()-2);
            engine.roll(s,new Fixed(0,5)); // d6=1; shared=5, HOME=1
            truth(s.legalPieceIds().contains(p.id()));
            engine.move(s,p.id());equal(Piece.State.FINISHED,p.state());
        });
        test("SRS 3.2.4.3.7 home overshoot checked on normal die", () -> {
            GameSession s=game(Difficulty.HARD);Piece p=piece(s,"red-0");
            p.place(s.board(),s.board().path(p.color()).size()-2);
            engine.roll(s,new Fixed(1,0)); // normal 2; multiplier 0
            truth(!s.legalPieceIds().contains(p.id()));
        });
        test("SRS 3.2.4.3.4 castle exit ignores multiplier", () -> {
            GameSession s=game(Difficulty.HARD);
            engine.roll(s,new Fixed(5,5)); // regular 6, Ultra 6, 30 shared steps
            engine.move(s,"red-0");equal(0,piece(s,"red-0").pathIndex());
        });
        test("SRS 3.2.3.3.5 six + zero multiplier earns bonus roll", () -> {
            GameSession s=game(Difficulty.MEDIUM);engine.roll(s,new Fixed(5,0));
            truth(s.legalPieceIds().contains("red-0"));
            engine.move(s,"red-0");engine.completeMove(s);
            equal(GameSession.Phase.ROLL,s.phase());equal(PlayerColor.RED,s.current().color());
        });
        test("SRS 3.2.3.3.5 six with no legal move still earns bonus", () -> {
            GameSession s=game(Difficulty.EASY);
            for(int i=0;i<4;i++) piece(s,"red-"+i).place(s.board(),s.board().path(PlayerColor.RED).size()-2);
            engine.roll(s,new Fixed(5));truth(s.legalPieceIds().isEmpty());
            engine.pass(s);equal(PlayerColor.RED,s.current().color());
            equal(GameSession.Phase.ROLL,s.phase());
        });
        test("SRS 3.2.3.3.6 third six canceled on Medium even with castle", () -> {
            GameSession s=game(Difficulty.MEDIUM);
            for(int i=0;i<2;i++) {
                engine.roll(s,new Fixed(5,0));engine.move(s,"red-"+i);engine.completeMove(s);
            }
            engine.roll(s,new Fixed(5,2));
            equal(Piece.State.CASTLE,piece(s,"red-2").state());
            equal(PlayerColor.GREEN,s.current().color());equal(0,s.consecutiveSixes());
        });
        test("SRS 3.2.3.3.6 third six canceled on Hard", () -> {
            GameSession s=game(Difficulty.HARD);
            for(int i=0;i<2;i++) {
                engine.roll(s,new Fixed(5,2));engine.move(s,"red-"+i);engine.completeMove(s);
            }
            engine.roll(s,new Fixed(5,0));
            equal(Piece.State.CASTLE,piece(s,"red-2").state());
            equal(PlayerColor.GREEN,s.current().color());
        });
        test("SRS 3.2.3.3.10 surprise Ultra runs before original 6 bonus", () -> {
            GameSession s=game(Difficulty.MEDIUM);
            engine.roll(s,new Fixed(5,2));engine.move(s,"red-0");
            equal(1,s.pendingExtraRolls());
            engine.applySurprise(s,"red-0",SurpriseOutcome.ULTRABOOST,new Fixed(0));
            engine.completeMove(s);
            equal(1,s.pendingExtraRolls());truth(s.isUltraNextRoll());
            DiceRoll ultra=engine.roll(s,new Fixed(1,5));
            truth(ultra.ultraBoost());equal(10,ultra.movement());
            equal(1,s.consecutiveSixes()); // the special roll did not reset the earlier six
            engine.move(s,"red-0");engine.completeMove(s);
            equal(GameSession.Phase.ROLL,s.phase());equal(0,s.pendingExtraRolls());
            truth(!s.isUltraNextRoll());
            DiceRoll ordinary=engine.roll(s,new Fixed(1,1));
            truth(!ordinary.ultraBoost());equal(2,ordinary.special());
        });
        test("SRS 3.2.3.3.9 special six adds bonus but not regular six count", () -> {
            GameSession s=game(Difficulty.EASY);
            engine.roll(s,new Fixed(5));engine.move(s,"red-0");
            engine.applySurprise(s,"red-0",SurpriseOutcome.ULTRABOOST,new Fixed(0));
            engine.completeMove(s);
            engine.roll(s,new Fixed(5,0)); // surprise 6, zero multiplier
            equal(1,s.consecutiveSixes()); // original normal six only
            engine.move(s,"red-1");engine.completeMove(s);
            equal(1,s.pendingExtraRolls()); // two pending, one consumed
            engine.roll(s,new Fixed(5)); // second consecutive regular six, NOT third
            equal(PlayerColor.RED,s.current().color());equal(2,s.consecutiveSixes());
        });
     
        test("SRS 3.2.3.3.9 special die never resets regular streak", () -> {
            GameSession s=game(Difficulty.MEDIUM);
            engine.roll(s,new Fixed(5,2));engine.move(s,"red-0");
            engine.applySurprise(s,"red-0",SurpriseOutcome.ULTRABOOST,new Fixed(0));
            engine.completeMove(s);
            engine.roll(s,new Fixed(1,0));engine.pass(s);
            equal(1,s.consecutiveSixes());
            engine.roll(s,new Fixed(5,1)); // ordinary second 6
            equal(2,s.consecutiveSixes());
        });
        test("US11 return to castle after zero multiplier cannot trigger square", () -> {
            GameSession s=game(Difficulty.HARD);
            Piece p=piece(s,"red-0");p.place(s.board(),3);
            engine.roll(s,new Fixed(2,0));
            truth(s.legalPieceIds().isEmpty());
            engine.pass(s);equal(3,p.pathIndex());
        });

        System.out.println("\nAli US8/US11: " + passed + " PASSED, " + failed + " FAILED");
        if (failed>0) System.exit(1);
    }

    private static int homeStart(GameSession s, PlayerColor color) {
        return s.board().path(color).indexOf(s.board().home(color).get(0));
    }
    private static Piece piece(GameSession s, String id) { return s.findPiece(id); }
    private static GameSession game(Difficulty d) { return engine.start(d,List.of("Ali","Teammate")); }
    private static void test(String label, Runnable test) {
        try {test.run();passed++;System.out.println("  PASS  " + label);}
        catch (Throwable ex) {failed++;System.err.println("  FAIL  " + label + ": " + ex);}
    }
    private static void truth(boolean ok) { if (!ok) throw new AssertionError("Assertion false"); }
    private static void equal(Object expected,Object actual) {
        if (!Objects.equals(expected,actual)) throw new AssertionError("Expected " + expected + " but got " + actual);
    }
    private static final class Fixed extends Random {
        private final int[] sequence; private int i;
        Fixed(int... sequence) { this.sequence=sequence; }
        @Override public int nextInt(int bound) { return Math.floorMod(sequence[(i++)%sequence.length],bound); }
    }
    private static final class Counting extends Random {
        private final java.util.ArrayList<Integer> bounds = new java.util.ArrayList<>();
        @Override public int nextInt(int bound) { bounds.add(bound); return 0; }
    }
}
