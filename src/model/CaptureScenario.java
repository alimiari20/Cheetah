package model;

import java.util.List;

/** Isolated developer fixture. Only the starting placements are staged. */
public record CaptureScenario(GameSession session, Piece attacker, Piece defender, int steps) {
    public enum Direction {
        North(0,-1), Northeast(1,-1), East(1,0), Southeast(1,1),
        South(0,1), Southwest(-1,1), West(-1,0), Northwest(-1,-1);
        final int dx,dy;
        Direction(int dx,int dy) { this.dx=dx; this.dy=dy; }
    }

    public static CaptureScenario create(GameEngine engine,Difficulty difficulty,PlayerColor attackColor,
                                         int hero,PlayerColor defenseColor,int defender,Direction direction) {
        GameSession session = engine.start(difficulty,List.of("Attacker","Defender"),0,List.of(attackColor,defenseColor));
        Board board = session.board();
        List<String> path = board.path(attackColor), other = board.path(defenseColor);
        Piece a = session.current().pieces().get(hero);
        Piece d = session.players().get(1).pieces().get(defender);
        for (int sign : new int[]{1,-1}) {
            for (int i=2;i<path.size()-2;i++) {
                String landing = path.get(i+sign);
                int opposite = other.indexOf(landing);
                if (opposite<0 || board.isSafe(landing) || board.isHome(attackColor,path.get(i-sign))) continue;
                Board.Position from = Board.Position.parse(path.get(i)), to = Board.Position.parse(landing);
                if (to.column()-from.column()!=direction.dx || to.row()-from.row()!=direction.dy) continue;
                a.place(board,i-sign);
                d.place(board,opposite);
                return new CaptureScenario(session,a,d,2*sign);
            }
        }
        throw new IllegalStateException("No shared capture square for "+direction+" on "+difficulty);
    }

    public CaptureMove capture(GameEngine engine) {
        CaptureMove.Before before = CaptureMove.observe(session,attacker.id());
        engine.shiftPiece(session,attacker,steps);
        CaptureMove move = before.resolved();
        if (move==null) throw new IllegalStateException("Expected a capture in this fixture");
        return move;
    }
}
