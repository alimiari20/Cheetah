package model;

import java.util.List;
import java.util.Random;

/** Capture observation follows the actual engine outcome across normal and chained moves. */
public final class CaptureRuleTests {
    public static void run() {
        GameEngine engine=new GameEngine(new BoardRepository());
        for(int operation=0;operation<5;operation++) {
            GameSession session=engine.start(Difficulty.EASY,List.of("Red","Blue","Green"),0,
                    List.of(PlayerColor.RED,PlayerColor.BLUE,PlayerColor.GREEN));
            Board board=session.board();
            Piece a=session.current().pieces().get(0);
            int landing=board.path(PlayerColor.RED).indexOf("R7-C4");
            int delta=switch(operation){case 1 -> 2; case 2 -> -3; default -> 1;};
            a.place(board,landing-delta);
            for(Player p:session.players().subList(1,3)) {
                int index=board.path(p.color()).indexOf("R7-C4");
                p.pieces().get(0).place(board,index);
            }
            CaptureMove.Before before=CaptureMove.observe(session,a.id());
            switch(operation) {
                case 0 -> {
                    session.dice(new DiceRoll(1,0,1,1,false));
                    session.mutableLegalMoves().add(a.id());session.phase(GameSession.Phase.SELECT_PIECE);
                    engine.move(session,a.id());
                }
                case 1 -> engine.resolveQuestion(session,a.id(),new Question("q",Difficulty.MEDIUM,"","",List.of("a"),0,""),true);
                case 2 -> engine.applySurprise(session,a.id(),SurpriseOutcome.BACKWARD_THREE,new Random(1));
                case 3 -> { // The engine may stop before an opponent double block.
                    Player other=session.players().get(1);
                    other.pieces().get(1).place(board,other.pieces().get(0).pathIndex());
                    engine.shiftPiece(session,a,1);
                }
                case 4 -> { // A stack on a protected start never emits a capture.
                    int start=board.path(a.color()).indexOf(board.start(PlayerColor.GREEN));
                    a.place(board,start-1);
                    session.players().get(2).pieces().get(0).place(board,0);
                    before=CaptureMove.observe(session,a.id());
                    engine.shiftPiece(session,a,1);
                }
            }
            CaptureMove capture=before.resolved();
            if (operation<3) {
                if(capture==null || capture.victims().size()!=2 || capture.route().size()!=Math.abs(delta)+1)
                    throw new AssertionError("Lost model capture for operation "+operation);
            } else if (capture!=null) throw new AssertionError("Invented capture");
        }
    }
}
