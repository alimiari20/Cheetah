package model;

public class ReturnToCastleCommand implements SurpriseCommand {

    private final Piece piece;

    public ReturnToCastleCommand(Piece piece) {
        this.piece = piece;
    }

    @Override
    public void execute() {
        piece.returnCastle();
    }
}