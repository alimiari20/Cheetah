package model;

public class UltraBoostCommand implements SurpriseCommand {

    private final GameSession session;

    public UltraBoostCommand(GameSession session) {
        this.session = session;
    }

    @Override
    public void execute() {
        session.ultraNextRoll(true);
    }
}