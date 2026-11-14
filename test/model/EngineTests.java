package model;

import java.awt.image.BufferedImage;
import java.awt.event.MouseEvent;
import javax.swing.SwingUtilities;

import model.Board;
import model.BoardRepository;
import model.DiceRoll;
import model.Difficulty;
import model.GameEngine;
import model.GameSession;
import model.HistoryRepository;
import model.Piece;
import model.Player;
import model.PlayerColor;
import model.Question;
import model.QuestionBank;
import model.SurpriseOutcome;
import view.BoardPanel;

import java.nio.file.*;
import java.util.*;

/** Self-contained deterministic test runner: no external test dependencies. */
public final class EngineTests {
    private static int passed=0;
    private static int failed=0;
    private static final BoardRepository boards=new BoardRepository();
    private static final GameEngine engine=new GameEngine(boards);

    public static void main(String[] args) {
        checkTest("all 3 original board resources",()->{
            for(Difficulty d:Difficulty.values()) {
                Board b=boards.load(d);
                eq(d.boardSize(),b.size());
                eq(d.boardSize()*d.boardSize(),b.cells().size());
            }
        });
        checkTest("SRS symbol counts",()->{
            verifyCounts(Difficulty.EASY,4,4,0,0);
            verifyCounts(Difficulty.MEDIUM,8,8,6,2);
            verifyCounts(Difficulty.HARD,12,12,9,3);
        });
        checkTest("player paths start at exact start squares",()->{
            for(Difficulty d:Difficulty.values()) {
                Board b=boards.load(d);
                for(PlayerColor c:PlayerColor.values()) {
                    eq(b.start(c),b.path(c).get(0));
                    eq(b.home(c).get(b.home(c).size()-1),b.path(c).get(b.path(c).size()-1));
                    eq(4, countCastleSlots(b,c));
                }
            }
        });
        checkTest("32 original trivia questions imported",()->eq(32,new QuestionBank(new Random(1)).total()));
        checkTest("question cycle has no repeats before refill",()->{
            QuestionBank bank=new QuestionBank(new Random(5));
            Set<String> seen=new HashSet<>();
            for(int i=0;i<11;i++) {
                Question q=bank.next(Difficulty.EASY);
                yes(seen.add(q.id()),"Repeated question early: "+q.id());
            }
            yes(bank.next(Difficulty.EASY)!=null,"Should refill cycle");
        });
        checkTest("all board difficulties paint headlessly",()->{
            for(Difficulty d:Difficulty.values()) {
                BoardPanel panel=new BoardPanel();
                panel.setSize(760,760);
                panel.setSession(engine.start(d,List.of("A","B","C","D")));
                BufferedImage surface=new BufferedImage(760,760,BufferedImage.TYPE_INT_ARGB);
                panel.paint(surface.createGraphics());
                yes(surface.getRGB(380,380)!=0,"Empty render for "+d);
            }
        });
        checkTest("GUI piece hit-testing selects a legal castle piece",()->{
            GameSession s=game();engine.roll(s,new Fixed(5));
            BoardPanel panel=new BoardPanel();
            panel.setSize(700,700);
            panel.setSession(s);
            java.util.concurrent.atomic.AtomicReference<String> clicked=new java.util.concurrent.atomic.AtomicReference<>();
            panel.setOnSelect(clicked::set);
            BufferedImage image=new BufferedImage(700,700,BufferedImage.TYPE_INT_ARGB);
            panel.paint(image.createGraphics());
            Board.Position pos=Board.Position.parse(s.board().castleSlot(PlayerColor.RED,0));
            double cell=660.0/s.board().size();
            int x=(int)Math.round(20+(pos.column()-.5)*cell);
            int y=(int)Math.round(20+(pos.row()-.5)*cell);
            MouseEvent event=new MouseEvent(panel,MouseEvent.MOUSE_CLICKED,System.currentTimeMillis(),0,x,y,1,false);
            panel.dispatchEvent(event);
            eq("red-0",clicked.get());
        });
        checkTest("first player rotates clockwise order",()->{
            GameSession s=engine.start(Difficulty.MEDIUM,List.of("R","G","Y","B"),2);
            eq(PlayerColor.YELLOW,s.current().color());
            eq(List.of(PlayerColor.YELLOW,PlayerColor.BLUE,PlayerColor.RED,PlayerColor.GREEN),
                    s.players().stream().map(Player::color).toList());
            eq("Y",s.players().get(0).name());
        });
        checkTest("default 2 player session with four pieces each",()->{
            GameSession s=game();eq(2,s.players().size());
            eq(PlayerColor.RED,s.current().color());
            for(Player p:s.players()) {
                eq(4,p.pieces().size());
                for(Piece piece:p.pieces()) eq(Piece.State.CASTLE,piece.state());
            }
        });
        checkTest("easy die is regular 1-6",()->{
            GameSession s=game();DiceRoll r=engine.roll(s,new Fixed(3));
            eq(4,r.regular());eq(1,r.special());eq(1,r.multiplier());eq(4,r.movement());
        });
        checkTest("medium boost multiplier zero",()->{
            GameSession s=engine.start(Difficulty.MEDIUM,List.of("A","B"));
            DiceRoll r=engine.roll(s,new Fixed(2,0));eq(3,r.regular());eq(1,r.special());eq(0,r.movement());
        });
        checkTest("hard ultraboost multiplier five",()->{
            GameSession s=engine.start(Difficulty.HARD,List.of("A","B"));
            DiceRoll r=engine.roll(s,new Fixed(3,5));eq(4,r.regular());eq(6,r.special());eq(20,r.movement());
        });
        checkTest("cannot exit castle without rolling 6",()->{
            GameSession s=game();engine.roll(s,new Fixed(3));
            yes(s.legalPieceIds().isEmpty(),"Castle should remain locked");
            engine.pass(s);eq(PlayerColor.GREEN,s.current().color());
        });
        checkTest("roll 6 exits castle and rewards reroll",()->{
            GameSession s=game();engine.roll(s,new Fixed(5));
            eq(4,s.legalPieceIds().size());
            GameEngine.MoveFeedback move=engine.move(s,"red-0");
            eq(0,move.steps());eq(0,s.findPiece("red-0").pathIndex());
            engine.completeMove(s);eq(GameSession.Phase.ROLL,s.phase());
            eq(PlayerColor.RED,s.current().color());
        });
        checkTest("three consecutive 6s lose third turn",()->{
            GameSession s=game();
            for(int i=0;i<2;i++) {
                engine.roll(s,new Fixed(5));engine.move(s,"red-"+i);engine.completeMove(s);
            }
            engine.roll(s,new Fixed(5));
            eq(PlayerColor.GREEN,s.current().color());eq(0,s.consecutiveSixes());
            eq(GameSession.Phase.ROLL,s.phase());
        });
        checkTest("medium 6 exits castle despite zero multiplier",()->{
            GameSession s=engine.start(Difficulty.MEDIUM,List.of("A","B"));
            engine.roll(s,new Fixed(5,0));
            eq(0,s.dice().movement());
            yes(s.legalPieceIds().contains("red-0"),"Six should unlock castle");
        });
        checkTest("track move advances exact number of steps",()->{
            GameSession s=game();s.findPiece("red-0").place(s.board(),0);
            engine.roll(s,new Fixed(2));engine.move(s,"red-0");eq(3,s.findPiece("red-0").pathIndex());
        });
        checkTest("opponent double block stops just before",()->{
            GameSession s=game();Board b=s.board();
            s.findPiece("red-0").place(b,0);
            placeAtSharedSquare(s,"green-0",b.path(PlayerColor.RED).get(3));
            placeAtSharedSquare(s,"green-1",b.path(PlayerColor.RED).get(3));
            engine.roll(s,new Fixed(3)); // 4 steps, blocked at 3 => may advance 2
            yes(s.legalPieceIds().contains("red-0"),"Truncated move should be offered");
            engine.move(s,"red-0");eq(2,s.findPiece("red-0").pathIndex());
        });
        checkTest("full move has priority over stop-before-block",()->{
            GameSession s=game();Board b=s.board();
            s.findPiece("red-0").place(b,0);
            s.findPiece("red-1").place(b,8);
            placeAtSharedSquare(s,"green-0",b.path(PlayerColor.RED).get(3));
            placeAtSharedSquare(s,"green-1",b.path(PlayerColor.RED).get(3));
            engine.roll(s,new Fixed(3));
            yes(!s.legalPieceIds().contains("red-0"),"Truncated move must be excluded");
            yes(s.legalPieceIds().contains("red-1"),"Full move should remain legal");
        });
        checkTest("capture lone opponent on unsafe square",()->{
            GameSession s=game();Board b=s.board();
            s.findPiece("red-0").place(b,0);
            String target=b.path(PlayerColor.RED).get(2);
            placeAtSharedSquare(s,"green-0",target);
            engine.roll(s,new Fixed(1));
            GameEngine.MoveFeedback result=engine.move(s,"red-0");
            eq(1,result.captures().size());eq(Piece.State.CASTLE,s.findPiece("green-0").state());
        });
        checkTest("star square prevents capture",()->{
            GameSession s=game();Board b=s.board();
            String star=null;
            for(String id:b.path(PlayerColor.RED)) if(b.isSafe(id)) {star=id;break;}
            yes(star!=null,"Expected safe track square");
            int index=b.path(PlayerColor.RED).indexOf(star);
            yes(index>0,"Safe square should be reachable");
            s.findPiece("red-0").place(b,index-1);
            placeAtSharedSquare(s,"green-0",star);
            engine.roll(s,new Fixed(0));engine.move(s,"red-0");
            eq(Piece.State.TRACK,s.findPiece("green-0").state());
        });
        checkTest("overshoot exact home is not legal",()->{
            GameSession s=game();Piece p=s.findPiece("red-0");
            p.place(s.board(),s.board().path(PlayerColor.RED).size()-2);
            engine.roll(s,new Fixed(1));
            yes(!s.legalPieceIds().contains("red-0"),"Overshoot must be blocked");
        });
        checkTest("exact finish marks piece home",()->{
            GameSession s=game();Piece p=s.findPiece("red-0");
            p.place(s.board(),s.board().path(PlayerColor.RED).size()-2);
            engine.roll(s,new Fixed(0));engine.move(s,p.id());
            eq(Piece.State.FINISHED,p.state());eq(1L,s.current().finishedCount());
        });
        checkTest("question reward and penalty update path",()->{
            GameSession s=game();Piece p=s.findPiece("red-0");p.place(s.board(),6);
            Question q=new Question("id",Difficulty.MEDIUM,"c","x",List.of("yes","no"),0,"e");
            engine.resolveQuestion(s,p.id(),q,true);eq(8,p.pathIndex());
            engine.resolveQuestion(s,p.id(),q,false);eq(6,p.pathIndex());
        });
        checkTest("question penalty clamps to start",()->{
            GameSession s=game();Piece p=s.findPiece("red-0");p.place(s.board(),1);
            Question q=new Question("id",Difficulty.HARD,"c","x",List.of("yes","no"),0,"e");
            engine.resolveQuestion(s,p.id(),q,false);eq(0,p.pathIndex());
        });
        checkTest("surprise return to castle",()->{
            GameSession s=game();Piece p=s.findPiece("red-0");p.place(s.board(),5);
            engine.applySurprise(s,p.id(),SurpriseOutcome.RETURN_TO_CASTLE,new Fixed(0));
            eq(Piece.State.CASTLE,p.state());
        });
        checkTest("surprise direct finish",()->{
            GameSession s=game();Piece p=s.findPiece("red-0");p.place(s.board(),5);
            engine.applySurprise(s,p.id(),SurpriseOutcome.DIRECT_FINISH,new Fixed(0));
            eq(Piece.State.FINISHED,p.state());
        });
        checkTest("surprise opponent moves backwards 6",()->{
            GameSession s=game();Piece red=s.findPiece("red-0");red.place(s.board(),3);
            Piece green=s.findPiece("green-0");green.place(s.board(),10);
            engine.applySurprise(s,red.id(),SurpriseOutcome.OPPONENT_BACKWARD,new Fixed(0));
            eq(4,green.pathIndex());
        });
        checkTest("surprise ultra boost sets extra roll flag",()->{
            GameSession s=game();Piece p=s.findPiece("red-0");p.place(s.board(),1);
            engine.applySurprise(s,p.id(),SurpriseOutcome.ULTRABOOST,new Fixed(0));
            yes(s.isUltraNextRoll(),"Ultra next roll should be set");
        });
        checkTest("surprise roll is a true ultra boost",()->{
            GameSession s=game();Piece p=s.findPiece("red-0");p.place(s.board(),1);
            engine.applySurprise(s,p.id(),SurpriseOutcome.ULTRABOOST,new Fixed(0));
            engine.roll(s,new Fixed(1,5));
            yes(s.dice().ultraBoost(),"Should use 6-sided ultra die");
            eq(10,s.dice().movement());
            yes(!s.isUltraNextRoll(),"Ultra flag should be consumed");
        });
        checkTest("surprise weighted thresholds",()->{
            eq(SurpriseOutcome.DIRECT_FINISH,SurpriseOutcome.draw(new Fixed(0)));
            eq(SurpriseOutcome.OPPONENT_BACKWARD,SurpriseOutcome.draw(new Fixed(10)));
            eq(SurpriseOutcome.RETURN_TO_CASTLE,SurpriseOutcome.draw(new Fixed(20)));
            eq(SurpriseOutcome.BACKWARD_THREE,SurpriseOutcome.draw(new Fixed(30)));
            eq(SurpriseOutcome.LOSE_NEXT_TURN,SurpriseOutcome.draw(new Fixed(60)));
            eq(SurpriseOutcome.ULTRABOOST,SurpriseOutcome.draw(new Fixed(90)));
        });
        checkTest("skip next turn penalty consumed once",()->{
            GameSession s=game();
            Piece p=s.findPiece("red-0");p.place(s.board(),1);
            engine.applySurprise(s,p.id(),SurpriseOutcome.LOSE_NEXT_TURN,new Fixed(0));
            engine.roll(s,new Fixed(0));engine.move(s,p.id());engine.completeMove(s);
            eq(PlayerColor.GREEN,s.current().color());
            engine.roll(s,new Fixed(0));engine.pass(s);
            eq(PlayerColor.GREEN,s.current().color()); // Red skipped, so Green gets next turn
            engine.roll(s,new Fixed(0));engine.pass(s);
            eq(PlayerColor.RED,s.current().color()); // Penalty consumed
        });
        checkTest("match finish creates ranking",()->{
            GameSession s=game();Board b=s.board();
            for(int i=0;i<3;i++)s.findPiece("red-"+i).place(b,b.path(PlayerColor.RED).size()-1);
            Piece fourth=s.findPiece("red-3");fourth.place(b,b.path(PlayerColor.RED).size()-2);
            engine.roll(s,new Fixed(0));engine.move(s,fourth.id());
            eq(GameSession.Phase.FINISHED,s.phase());
            eq(List.of(PlayerColor.RED,PlayerColor.GREEN),s.ranking());
        });
        checkTest("completed history saves CSV record",()->{
            try {
                GameSession s=game();Board b=s.board();
                for(int i=0;i<3;i++)s.findPiece("red-"+i).place(b,b.path(PlayerColor.RED).size()-1);
                Piece p=s.findPiece("red-3");p.place(b,b.path(PlayerColor.RED).size()-2);
                engine.roll(s,new Fixed(0));engine.move(s,p.id());
                Path dest=Files.createTempDirectory("cheetah-tests").resolve("match.csv");
                HistoryRepository h=new HistoryRepository(dest);h.save(s);
                String txt=Files.readString(dest);
                yes(txt.contains("difficulty,players,winner"),"CSV header missing");
                yes(txt.contains("Player 1"),"Player missing");
                Path exported=dest.resolveSibling("export.csv");h.export(exported);
                eq(txt,Files.readString(exported));
            }catch(Exception ex){throw new AssertionError(ex);}
        });
        System.out.println("\n"+passed+" PASSED, "+failed+" FAILED");
        if(failed>0)System.exit(1);
    }
    private static void verifyCounts(Difficulty d,long stars,long arrows,long questions,long surprises) {
        Map<String,Long> counts=boards.load(d).symbolCounts();
        eq(stars,counts.getOrDefault("star",0L));
        eq(arrows,counts.getOrDefault("arrow",0L));
        eq(questions,counts.getOrDefault("question",0L));
        eq(surprises,counts.getOrDefault("surprise",0L));
    }
    private static int countCastleSlots(Board b,PlayerColor c) {
        Set<String> positions=new HashSet<>();
        for(int i=0;i<4;i++)positions.add(b.castleSlot(c,i));
        return positions.size();
    }
    private static GameSession game(){return engine.start(Difficulty.EASY,List.of("Player 1","Player 2"));}
    private static void placeAtSharedSquare(GameSession s,String pieceId,String squareId) {
        Piece piece=s.findPiece(pieceId);
        int index=s.board().path(piece.color()).indexOf(squareId);
        yes(index>=0,"Square should be in the opponent's track: "+squareId);
        piece.place(s.board(),index);
    }
    private static final class Fixed extends Random {
        private final int[] values;private int index;
        Fixed(int... values){this.values=values;}
        @Override public int nextInt(int bound){return Math.floorMod(values[index++%values.length],bound);}
    }
    private static void checkTest(String title,Runnable test) {
        try {test.run();passed++;System.out.println("  PASS  "+title);}
        catch(Throwable ex){failed++;System.err.println("  FAIL  "+title+" -> "+ex);}
    }
    private static void yes(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void eq(Object a,Object b){if(!Objects.equals(a,b))throw new AssertionError("Expected "+a+" but got "+b);}
}
