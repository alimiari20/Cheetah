package model;

public class BackwardThreeCommand implements SurpriseCommand {

    private final GameEngine engine;
    private final GameSession session;
    private final Piece piece;

    private GameEngine.MoveFeedback result;

    public BackwardThreeCommand(
            GameEngine engine,
            GameSession session,
            Piece piece) {

        this.engine = engine;
        this.session = session;
        this.piece = piece;
    }

    @Override
    public void execute() {
        result = engine.shiftPiece(session, piece, -3);
    }

    public GameEngine.MoveFeedback getResult() {
        return result;
    }
}