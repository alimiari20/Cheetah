package model;

public class LoseNextTurnCommand implements SurpriseCommand {
	private final GameSession session;
	private final PlayerColor color;
	public LoseNextTurnCommand(GameSession session, PlayerColor color) {
	    this.session = session;
	    this.color = color;
	}
	@Override
	public void execute() {
		session.mutableSkips().add(color);

	}

}
