package model;

public class OpponentBackwardCommand implements SurpriseCommand {

    private final GameEngine engine;
    private final GameSession session;
    private final Piece opponentPiece;
    private final int die;

    private GameEngine.MoveFeedback result;

    public OpponentBackwardCommand(
            GameEngine engine,
            GameSession session,
            Piece opponentPiece,
            int die) {

        this.engine = engine;
        this.session = session;
        this.opponentPiece = opponentPiece;
        this.die = die;
    }

    @Override
    public void execute() {
        int stepsBackward = (die + 1) / 2;

        result = engine.shiftPiece(
            session,
            opponentPiece,
            -stepsBackward
        );
    }

    public GameEngine.MoveFeedback getResult() {
        return result;
    }
}