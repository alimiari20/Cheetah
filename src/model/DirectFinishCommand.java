package model;

public class DirectFinishCommand implements SurpriseCommand {
	
	
	private final GameEngine engine;
	private final GameSession session;
	private final Piece piece;

	public DirectFinishCommand(
	        GameEngine engine,
	        GameSession session,
	        Piece piece) {

	    this.engine = engine;
	    this.session = session;
	    this.piece = piece;
	}
	
	@Override
	public void execute() {
	    engine.finishPieceDirectly(session, piece);
	}

}
