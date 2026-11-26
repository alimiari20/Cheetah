package model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Visual description of one resolved move, including its actual forward or backward path. */
public record CaptureMove(Piece attacker, List<Board.Position> route, List<Piece> victims) {
    public CaptureMove {
        route = List.copyOf(route);
        victims = List.copyOf(victims);
    }

    /** Observe a model operation without changing its rules or relying on its log text. */
    public static Before observe(GameSession session, String attackerId) {
        Piece attacker = session.findPiece(attackerId);
        Map<Piece, String> occupied = new LinkedHashMap<>();
        for (Player player : session.players()) {
            if (player.color() == attacker.color()) continue;
            for (Piece piece : player.pieces()) {
                if (piece.state() == Piece.State.TRACK || piece.state() == Piece.State.HOME)
                    occupied.put(piece, piece.squareId(session.board()));
            }
        }
        return new Before(session.board(), attacker, attacker.pathIndex(), occupied);
    }

    public record Before(Board board, Piece attacker, int origin, Map<Piece, String> occupied) {
        public Before {
            occupied = Map.copyOf(occupied);
        }

        public CaptureMove resolved() {
            if (attacker.state() != Piece.State.TRACK || origin < 0) return null;
            String landing = attacker.squareId(board);
            List<Piece> victims = occupied.entrySet().stream()
                    .filter(e -> e.getKey().state() == Piece.State.CASTLE && landing.equals(e.getValue()))
                    .map(Map.Entry::getKey).sorted(java.util.Comparator.comparing(Piece::id)).toList();
            if (victims.isEmpty()) return null;
            List<Board.Position> route = new ArrayList<>();
            List<String> path = board.path(attacker.color());
            int end = attacker.pathIndex();
            for (int i = origin; ; i += Integer.compare(end, origin)) {
                route.add(Board.Position.parse(path.get(i)));
                if (i == end) break;
            }
            return new CaptureMove(attacker, route, victims);
        }
    }
}
