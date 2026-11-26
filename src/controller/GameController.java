package controller;

import view.Dialogs;

import view.*;

import javax.swing.*;

import model.*;
import security.AdminAuthService;
import security.AdminAudit;
import view.AdminAccountDialog;
import security.AdminEmailRepository;
import security.AdminAuthService.Factor;
import security.AdminSession;
import java.awt.Component;
import java.io.IOException;
import java.io.UncheckedIOException;

import java.nio.file.*;

import java.util.*;

/**

 * Handles GUI actions and connects the View to the Model.

 */

public final class GameController

        implements GameFrame.Actions {

    private final GameFrame view;

    private final GameEngine engine;

    private final HistoryRepository history;
    private final ReplayRepository replays;
    private ReplayRecorder recorder;
    private long replayRequest;

    private final Random random;
    private final Random aiRandom = new Random();
    private final Map<PlayerColor, AiLevel> playerControls = new EnumMap<>(PlayerColor.class);
    private final Map<Integer, AiLevel> delegatedEffects = new HashMap<>();
    private final javax.swing.Timer aiDelay = new javax.swing.Timer(550, event -> runAiTurn());
    private int actionDepth;

    private final QuestionRepository questionRepository =

        new QuestionRepository();
    private final AdminSession adminSession = new AdminSession();
    private final AdminAuthService adminAuthService = new AdminAuthService();
    private final AdminAudit adminAudit = new AdminAudit();
    private final AdminEmailRepository adminEmailRepository =
            new AdminEmailRepository();
    private QuestionBank questions;

    private GameSession session;

    private boolean matchSaved;

    private boolean resolvingDialog;
    private boolean selectingOpponentPiece = false;
    private Set<String> eligibleOpponentPieceIds = Set.of();
    private String pendingSurprisePieceId = null;
    
 // Tracks which player's move completion is waiting
 // for an opponent-selection surprise to finish.
 
    private void beginOpponentSelection(String currentPieceId) {
        Set<String> eligible = findEligibleOpponentPieces(currentPieceId);

        if (eligible.isEmpty()) {
            return;
        }

        pendingSurprisePieceId = currentPieceId;
        eligibleOpponentPieceIds = eligible;
        selectingOpponentPiece = true;

        view.setOpponentSelectionIds(eligible);
    }
    public GameController(

            GameFrame view,

            GameEngine engine,

            HistoryRepository history,

            Random random) {

        this.view = Objects.requireNonNull(view);

        this.engine = Objects.requireNonNull(engine);

        this.history = Objects.requireNonNull(history);
        this.replays = new ReplayRepository(history);

        this.random = Objects.requireNonNull(random);
        aiDelay.setRepeats(false);

        this.questions = new QuestionBank(random);

        view.setActions(this);

    }

    /**

     * Show the original main menu.

     * Do not randomize a board here.

     */

    public void start() {

        view.showMainMenu();

        view.setVisible(true);

    }

    /**

     * Starts an actual match.

     * Special squares are randomized ONLY here.

     */

    private void startSession(

            SetupDialog.Config config,

            int firstPlayer, List<String> openingRolls) {

        session = engine.startNewGame(

            config.difficulty(),

            config.players(),

            firstPlayer,

            config.colors(),

            random

        );

        try {

            questions = new QuestionBank(

                random,

                questionRepository.list()

            );

        } catch (IOException ex) {

            throw new IllegalStateException(

                "Unable to load local questions",

                ex

            );

        }

        matchSaved = false;
        playerControls.clear();
        delegatedEffects.clear();
        for (int i = 0; i < config.colors().size(); i++) {
            playerControls.put(config.colors().get(i), config.controls().get(i));
        }
        view.setPlayerControls(playerControls);
        recorder = new ReplayRecorder(session, playerControls, openingRolls);
        

selectingOpponentPiece = false;
eligibleOpponentPieceIds = Set.of();
pendingSurprisePieceId = null;

view.setOpponentSelectionIds(Set.of());


        refresh();

    }

    @Override

    public void newGame() {

        if (resolvingDialog || selectingOpponentPiece) {
            return;
        }

        if (view.isGameVisible()

                && session != null

                && session.turn() > 1

                && session.phase()

                    != GameSession.Phase.FINISHED

                && !confirmLeave("Start a new match?")) {

            return;

        }

        view.showSetup();

    }

    /**

     * IMPORTANT:

     * Setup preview uses the original STATIC board.

     */

    @Override

    public GameSession preview(

            Difficulty difficulty,

            List<PlayerColor> colors) {

        List<String> names = new ArrayList<>();

        for (int i = 0; i < colors.size(); i++) {

            names.add("Player " + (i + 1));

        }

        return engine.start(

            difficulty,

            names,

            0,

            colors

        );

    }

    /**

     * Starting a real game triggers board randomization.

     */

    @Override

    public void startConfiguredGame(

            SetupDialog.Config config) {

    	if (view.isCapturePlaying() || resolvingDialog || selectingOpponentPiece) {
    	    return;
    	}

        tryAction(() -> {

            var selection = FirstPlayerDialog.chooseRecordedStarter(

                view,

                config.players(),

                config.colors(),

                config.controls(),

                random

            );

            if (selection.starter() >= 0) {

                var rolls = selection.rolls().stream()
                        .map(roll -> "First-player roll: " + roll.player() + " rolled " + roll.die() + ".").toList();
                startSession(config, selection.starter(), rolls);

                view.showGame();

            }

        });

    }

    @Override

    public void showMenu() {

        if (resolvingDialog) {

            return;

        }

        if (view.isGameVisible()

                && session != null

                && session.turn() > 1

                && session.phase()

                    != GameSession.Phase.FINISHED

                && !confirmLeave("Leave this match?")) {

            return;

        }
        
        view.showMainMenu();

    }

    private boolean confirmLeave(String prompt) {
        resolvingDialog = true;
        aiDelay.stop();
        try {
            return UiKit.confirm(view, "Exit current game", "LEAVE CURRENT MATCH?",
                    prompt + "\nYour unfinished progress will not be saved.", "LEAVE GAME");
        } finally {
            resolvingDialog = false;
            gameAvailabilityChanged();
        }
    }

    private AiLevel control(PlayerColor color) {
        return playerControls.getOrDefault(color, AiLevel.HUMAN);
    }

    private AiLevel effectControl(PlayerColor color) {
        return delegatedEffects.getOrDefault(session.temporaryEffectDepth(), control(color));
    }

    private void completeTurnEffect() {
        engine.completeMove(session);
        delegatedEffects.keySet().removeIf(depth -> depth > session.temporaryEffectDepth());
        recorder.record(Replay.Kind.TURN);
    }

    private AiLevel activeControl() {
        if (session == null) return AiLevel.HUMAN;
        Piece trigger = selectingOpponentPiece ? session.findPiece(pendingSurprisePieceId) : null;
        return effectControl(trigger == null ? session.current().color() : trigger.color());
    }

    private boolean readyForAi() {
        return actionDepth == 0 && !resolvingDialog && view.isGameVisible() && !view.isPaused()
                && !view.isCapturePlaying() && session != null && session.phase() != GameSession.Phase.FINISHED
                && activeControl().automated();
    }

    @Override public void gameAvailabilityChanged() {
        if (!view.isHistoryVisible()) replayRequest++;
        aiDelay.stop();
        if (readyForAi()) aiDelay.restart();
    }

    private void runAiTurn() {
        if (!readyForAi()) return;
        AiLevel level = activeControl();
        if (selectingOpponentPiece) {
            String target = AiMoveStrategy.opponent(level, session, eligibleOpponentPieceIds, aiRandom);
            performSelection(target);
        } else if (session.phase() == GameSession.Phase.ROLL) {
            performRoll();
        } else if (session.legalPieceIds().isEmpty()) {
            performPass();
        } else {
            performSelection(AiMoveStrategy.forLevel(level).choose(engine, session, aiRandom));
        }
    }








    private boolean loginAdmin() {
        try {
            if (!adminAuthService.isProvisioned()) {
                Dialogs.showMessageDialog(view,
                        "No local administrator account exists. Run the following command in a terminal:\n"
                                + "java -jar CheetahLudo.jar --setup-admin",
                        "Admin Setup", JOptionPane.INFORMATION_MESSAGE);
                return false;
            }

            boolean passwordAccepted = verifyAdminFactor(view, Factor.PASSWORD, true);
            boolean signedIn = adminSession.signIn(passwordAccepted, () -> {
                try {
                    if (adminAuthService.codeVersion() == null) {
                        // Existing password-only installations enroll after proving the saved password.
                        return changeAdminCode(view, null, false);
                    }
                    return verifyAdminFactor(view, Factor.CODE, true);
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            });
            if (signedIn) adminAudit.signedIn();
            return signedIn;
        } catch (IOException | RuntimeException ex) {
            view.showError(ex);
            return false;
        }
    }

    private boolean verifyAdminFactor(Component parent, Factor factor, boolean login) throws IOException {
        var attempts = adminAuthService.attempts(factor);
        if (attempts.lockedSeconds() > 0) {
            adminAudit.blocked(factor);
            Dialogs.showMessageDialog(parent,
                    "Administrator authentication is locked. Try again in "
                            + attempts.lockedSeconds() + " seconds.",
                    "Admin Account Locked", JOptionPane.WARNING_MESSAGE);
            return false;
        }

        boolean code = factor == Factor.CODE;
        return AdminLoginDialog.showLogin(parent,
                code ? "Authentication Code" : "Admin Login",
                code ? "Authentication Code" : "Administrator password",
                code ? "code" : "password",
                value -> {
                    try {
                        return adminAudit.verify(adminAuthService, factor, value, login);
                    } catch (IOException ex) {
                        throw new UncheckedIOException(ex);
                    }
                }, attempts.remaining(), AdminAuthService.LOCKOUT_MINUTES);
    }

    private boolean changeAdminCode(Component parent, byte[] version, boolean generate) throws IOException {
        char[] code = generate ? adminAuthService.generateCode()
                : AdminLoginDialog.askNewCode(parent, version == null);
        if (code == null) {
            return false;
        }

        try {
            adminAuthService.replaceCode(version, code);
            if (generate) {
                AdminAccountDialog.showGeneratedCode(parent, code);
            }
            return true;
        } finally {
            Arrays.fill(code, '\0');
        }
    }

    @Override
    public void logoutAdmin() {
        adminSession.logout();
        view.setAdminMode(false);
    }

    @Override
    public void openAdminDashboard() {
        if (!adminSession.isAuthenticated() && !loginAdmin()) {
            return;
        }
        view.setAdminMode(true);
        view.showAdminEmail(loadAdminProfileEmail());
    }

    private String loadAdminProfileEmail() {
        try {
            return adminEmailRepository.loadOrInitialize();
        } catch (IOException ex) {
            view.showError(ex);
            return "";
        }
    }

    @Override
    public void showAdminSecurity() {
        tryAction(() -> {
            requireAdmin();
            view.showAdminSecurity(AdminAuthService.MAX_ATTEMPTS, AdminAuthService.LOCKOUT_MINUTES);
        });
    }

    @Override
    public void showSecurityDashboard() {
        tryAction(() -> {
            try {
                view.showSecurityDashboard(adminAudit.read(adminSession));
            } catch (IOException ex) {
                view.showInfo("Security Dashboard", "Security activity could not be read.");
            }
        });
    }

    private void requireAdmin() {
        adminSession.requireAuthenticated();
    }


    @Override
    public void questionAdmin() {
        if(view.isCapturePlaying()) return;

        tryAction(() -> {
            requireAdmin();

            try {

                view.showQuestionAdmin(

                    questionRepository.list()

                );

            } catch (IOException ex) {

                throw new IllegalStateException(

                    "Cannot read local questions",

                    ex

                );

            }

        });

    }

    private final QuestionAnalysis questionAnalysis = new QuestionAnalysis();
    private boolean questionAnalysisBusy;
    private record QuestionSave(List<Question> baseline,List<Question> update,
                                Optional<QuestionAnalysis.Match> duplicate) {}

    @Override
    public void suggestQuestionDifficulty(String editingId,String prompt,List<String> choices,int correctIndex) {
        tryAction(() -> {
            requireAdmin();
            if(questionAnalysisBusy || !view.isQuestionAdminVisible()) return;
            setQuestionAnalysisBusy(true);
            new SwingWorker<QuestionAnalysis.DifficultySuggestion,Void>() {
                @Override protected QuestionAnalysis.DifficultySuggestion doInBackground() throws IOException {
                    return questionAnalysis.suggest(editingId,prompt,choices,correctIndex,questionRepository.list());
                }

                @Override protected void done() {
                    try {
                        if (!view.isQuestionAdminVisible() || !adminSession.isAuthenticated()) return;
                        var suggestion=get();
                        if (QuestionReviewDialog.confirmDifficulty(view,suggestion)
                                && view.isQuestionAdminVisible() && adminSession.isAuthenticated()) {
                            view.applySuggestedDifficulty(suggestion.difficulty());
                        }
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    } catch (java.util.concurrent.ExecutionException ex) {
                        view.showError(new IllegalStateException(ex.getCause().getMessage(),ex.getCause()));
                    } finally {
                        setQuestionAnalysisBusy(false);
                    }
                }
            }.execute();
        });
    }

    @Override
    public void saveQuestion(Question question) {
        tryAction(() -> {
            requireAdmin();
            if (questionAnalysisBusy || !view.isQuestionAdminVisible()) return;
            setQuestionAnalysisBusy(true);
            new SwingWorker<QuestionSave,Void>() {
                @Override protected QuestionSave doInBackground() throws IOException {
                    var baseline = questionRepository.list();
                    var updated = new ArrayList<>(baseline);
                    int index = -1;
                    for(int i=0;i<updated.size();i++) if (updated.get(i).id().equals(question.id())) {
                        index=i;
                        break;
                    }
                    if (index<0) updated.add(question);
                    else updated.set(index,question);
                    QuestionRepository.validate(updated);
                    var candidates = new LinkedHashSet<>(baseline);
                    candidates.addAll(questionRepository.readExcel());
                    return new QuestionSave(baseline,updated,questionAnalysis.duplicate(question,List.copyOf(candidates)));
                }

                @Override protected void done() {
                    try {
                        if (!view.isQuestionAdminVisible() || !adminSession.isAuthenticated()) return;
                        QuestionSave pending=get();
                        if (pending.duplicate().isPresent()
                                && !QuestionReviewDialog.confirmDuplicate(view,question,pending.duplicate().get())) return;
                        requireAdmin();
                        if (!questionRepository.list().equals(pending.baseline())) {
                            Dialogs.showMessageDialog(view,"The question bank changed during analysis. Please save again.");
                            return;
                        }
                        questionRepository.saveAll(pending.update());
                        view.updateQuestionAdmin(questionRepository.list());
                        Dialogs.showMessageDialog(view,"Question saved successfully.","Question bank",JOptionPane.INFORMATION_MESSAGE);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    } catch (java.util.concurrent.ExecutionException ex) {
                        view.showError(new IllegalStateException(ex.getCause().getMessage(),ex.getCause()));
                    } catch (IOException | RuntimeException ex) {
                        view.showError(ex);
                    } finally {
                        setQuestionAnalysisBusy(false);
                    }
                }
            }.execute();
        });
    }

    private void setQuestionAnalysisBusy(boolean busy) {
        questionAnalysisBusy=busy;
        view.setQuestionAnalysisBusy(busy);
    }

    @Override
    public void deleteQuestion(String questionId) {
        if (questionAnalysisBusy) return;
        tryAction(() -> {
            requireAdmin();

          

            try {

                List<Question> list =

                    new ArrayList<>(questionRepository.list());

                if (!list.removeIf(

                        question -> question.id().equals(questionId))) {

                    throw new IllegalArgumentException(

                        "Question not found."

                    );

                }

                questionRepository.saveAll(list);

                view.updateQuestionAdmin(

                    questionRepository.list()

                );

                Dialogs.showMessageDialog(

                    view,

                    "Question deleted.",

                    "Question bank",

                    JOptionPane.INFORMATION_MESSAGE

                );

            } catch (IOException ex) {

                throw new IllegalStateException(

                    "Could not delete question",

                    ex

                );

            }

        });

    }

    @Override

    public void roll() {
        if (session == null || activeControl().automated()) return;
        performRoll();
    }

    private void performRoll() {
    	if (view.isCapturePlaying() || resolvingDialog || selectingOpponentPiece) {
    	    return;
    	}

        tryAction(() -> {

            if (session.phase()

                    != GameSession.Phase.ROLL) {

                return;

            }

            recorder.roll(engine.roll(session, random));
            delegatedEffects.keySet().removeIf(depth -> depth > session.temporaryEffectDepth());

            refresh();

            postMatchCheck();

        });

    }

    @Override

    public void pass() {
        if (!canRunHumanTimerAction()) return;
        performPass();
    }

    private void performPass() {
    	if (view.isCapturePlaying() || resolvingDialog || selectingOpponentPiece) {
    	    return;
    	}

        tryAction(() -> {

            engine.pass(session);
            delegatedEffects.keySet().removeIf(depth -> depth > session.temporaryEffectDepth());
            recorder.record(Replay.Kind.PASS);

            refresh();

            postMatchCheck();

        });

    }

    private boolean canRunHumanTimerAction() {
        return actionDepth == 0 && !resolvingDialog && !selectingOpponentPiece
                && view.isGameVisible() && !view.isPaused() && !view.isCapturePlaying()
                && session != null && session.phase() != GameSession.Phase.FINISHED
                && !activeControl().automated();
    }

    /** The current player's 30 seconds are over: skip the rest of the turn. */
    @Override
    public void timeOut() {
        if (!canRunHumanTimerAction()) return;
        tryAction(() -> {
            engine.timeOut(session);
            delegatedEffects.keySet().removeIf(depth -> depth > session.temporaryEffectDepth());
            recorder.record(Replay.Kind.TURN);
            refresh();
            postMatchCheck();
        });
    }

    @Override

    public void selectPiece(String pieceId) {
        if (session == null || activeControl().automated()) return;
        performSelection(pieceId);
    }

    private void performSelection(String pieceId) {
        if(view.isCapturePlaying()) return;

if (selectingOpponentPiece) {
    if (!eligibleOpponentPieceIds.contains(pieceId)) {
        return;
    }

    tryAction(() -> resolveOpponentSelection(pieceId));
    return;
}


        if (resolvingDialog

                || session.phase()

                    != GameSession.Phase.SELECT_PIECE

                || !session.legalPieceIds().contains(pieceId)) {

            return;

        }

        tryAction(() -> {

            CaptureMove.Before before = CaptureMove.observe(session, pieceId);
            GameEngine.MoveFeedback move =

                engine.move(session, pieceId);

            refreshCapture(before, Replay.Kind.MOVE, null, null, 0);


            resolveSpecial(pieceId, move.squareId());

if (selectingOpponentPiece) {
    return;
}


completeTurnEffect();
refresh();
postMatchCheck();

        });

    }

    /**

     * Reads special squares from the SAME board

     * that the player sees in GameFrame.

     */

private void resolveOpponentSelection(String opponentPieceId) {

    if (!selectingOpponentPiece
            || !eligibleOpponentPieceIds.contains(opponentPieceId)) {
        return;
    }

    Piece opponent = session.findPiece(opponentPieceId);
    if (opponent == null || opponent.state() != Piece.State.TRACK) {
        return;
    }

    AiLevel initiator = activeControl();
    int die = random.nextInt(6) + 1;

    OpponentBackwardCommand command =
        new OpponentBackwardCommand(engine, session, opponent, die);

    CaptureMove.Before before = CaptureMove.observe(session, opponentPieceId);
    command.execute();

    GameEngine.MoveFeedback result = command.getResult();
    boolean opponentMoved =
            result.steps() != 0
            && result.squareId() != null;
    selectingOpponentPiece = false;
    eligibleOpponentPieceIds = Set.of();
    pendingSurprisePieceId = null;
    view.setOpponentSelectionIds(Set.of());

    refreshCapture(before, Replay.Kind.OPPONENT, null, SurpriseOutcome.OPPONENT_BACKWARD, die);

    resolvingDialog = true;
    try {
        view.showSurprise(
                "Opponent Backward!\n"
                + "Die result: " + die + "\n"
                + opponent.color().label()
                + " piece " + (opponent.number() + 1)
                + " moved " + Math.abs(result.steps())
                + " squares backward.", initiator.automated()
        );
    } finally {
        resolvingDialog = false;
    }
    if (opponentMoved) {
        Board.Cell landing = session.board().cell(result.squareId());

        if (landing != null
                && ("question".equals(landing.symbol())
                    || "surprise".equals(landing.symbol()))) {
            engine.beginOpponentEffect(session, opponentPieceId);
            if (initiator.automated()) {
                // A forced chain belongs to the AI that triggered it, even on a human's piece.
                delegatedEffects.put(session.temporaryEffectDepth(), initiator);
            }
            recorder.record(Replay.Kind.TURN);
        }
    }
    if (opponentMoved) {
        Board.Cell landingCell =
                session.board().cell(result.squareId());

        if (landingCell != null) {
            String symbol = landingCell.symbol();

            if ("question".equals(symbol)
                    || "surprise".equals(symbol)) {

                // The opponent's special square must be resolved
                // before the original player's turn continues.
            	resolveSpecial(opponentPieceId, result.squareId());

            	// Another OPPONENT_BACKWARD is waiting for a piece selection.
            	// Do not complete any suspended turn yet.
            	if (selectingOpponentPiece) {
            	    refresh();
            	    return;
            	}
            }
        }
    }

 // Complete the current effect.
 // GameEngine handles earned rolls and suspended turns.
 completeTurnEffect();

 refresh();
 postMatchCheck();
}

private void resolveSpecial(
        String pieceId,
        String squareId) {

	if (squareId == null
	        || session.phase() == GameSession.Phase.FINISHED) {
	    return;
	}

	while (squareId != null
	        && session.phase() != GameSession.Phase.FINISHED) {

	    String nextSquare = null;

	    Piece piece = session.findPiece(pieceId);

        if (piece == null

                || piece.state() == Piece.State.CASTLE

                || piece.state() == Piece.State.FINISHED) {

            return;

        }

        Board.Cell square =

            session.board().cell(squareId);

        if (square == null) {
            return;
        

        }

        if ("question".equals(square.symbol())) {

            Difficulty questionDifficulty =

                switch (square.specialColor()) {

                    case "green" -> Difficulty.EASY;

                    case "yellow" -> Difficulty.MEDIUM;

                    case "red" -> Difficulty.HARD;

                    default -> throw new IllegalStateException(

                        "Unknown question square color: "

                        + square.specialColor()

                    );

                };

            Question question =

                questions.next(questionDifficulty);

            resolvingDialog = true;

            QuestionDialog.Result answerResult;

            try {

                AiLevel level = effectControl(piece.color());
                if (level.automated()) {
                    int answer = AiMoveStrategy.answer(level, question.difficulty(), question.text(),
                            question.choices(), aiRandom);
                    answerResult = QuestionDialog.askRecorded(view, question, answer);
                } else {
                    answerResult = QuestionDialog.askRecorded(view, question, -1);
                }

            } finally {

                resolvingDialog = false;

            }
            boolean correct = answerResult.correct();

            CaptureMove.Before before = CaptureMove.observe(session, pieceId);
            if (piece.color() != session.current().color()) {
                engine.resolveOpponentQuestion(
                    session,
                    pieceId,
                    question,
                    correct
                );
            } else {
                engine.resolveQuestion(
                    session,
                    pieceId,
                    question,
                    correct
                );
            }

            refreshCapture(before, Replay.Kind.QUESTION,
                    new Replay.Answer(question, answerResult.selected(), correct), null, 0);

            Piece updated = session.findPiece(pieceId);
            if (updated != null
                    && updated.state() != Piece.State.CASTLE
                    && updated.state() != Piece.State.FINISHED
                    && !updated.squareId(session.board()).equals(squareId)) {

            	nextSquare = updated.squareId(session.board());

            }

        

} else if ("surprise".equals(square.symbol())) {
    SurpriseOutcome outcome = SurpriseOutcome.draw(random);

    if (outcome == SurpriseOutcome.OPPONENT_BACKWARD) {
        beginOpponentSelection(pieceId);
        recorder.note(selectingOpponentPiece ? "Opponent selection started."
                : "No eligible opponent piece. Surprise canceled.");
        recorder.record(Replay.Kind.SURPRISE,null,null,outcome,0);

        resolvingDialog = true;
        try {
            if (selectingOpponentPiece) {
                view.showSurprise(
                    effectControl(piece.color()).automated() ? "AI is choosing an opponent piece."
                            : "Choose a red highlighted opponent piece on the board.",
                    effectControl(piece.color()).automated()
                );
            } else {
                view.showSurprise(
                    "No eligible opponent piece. Surprise canceled.", effectControl(piece.color()).automated()
                );
            }
        } finally {
            resolvingDialog = false;
        }
        return;
    }

    CaptureMove.Before before = CaptureMove.observe(session, pieceId);
    GameEngine.SurpriseFeedback feedback =
            engine.applySurprise(session, pieceId, outcome, random);

    refreshCapture(before, Replay.Kind.SURPRISE, null, outcome, 0);

    resolvingDialog = true;
    try {
        view.showSurprise(feedback.message(), effectControl(piece.color()).automated());
    } finally {
        resolvingDialog = false;
    }

    if (feedback.landingSquare() != null
            && !feedback.landingSquare().equals(squareId)) {
        nextSquare = feedback.landingSquare();
    }

    } // End surprise

    squareId = nextSquare;

    } // End while

    } // End resolveSpecial

    private void postMatchCheck() {

        if (session.phase() != GameSession.Phase.FINISHED

                || matchSaved) {

            return;

        }

        matchSaved = true;

        String info =

            "Game saved in your local match history.";

        try {

            var entry = history.save(session);
            try {
                replays.save(entry, recorder.complete());
            } catch (IOException | RuntimeException ex) {
                info = "Game saved, but its replay could not be saved.";
            }

        } catch (IOException ex) {

            info = "Could not save local history: "

                + ex.getMessage();

        }

        refresh();

        view.showMatchEnd(session, info);
    
    }

    @Override

    public void history() {
        if(view.isCapturePlaying()) return;

        tryAction(() -> {

            try {

                var entries = history.readEntries();
                view.showHistory(entries, replays.available(entries));

            } catch (IOException ex) {

                throw new IllegalStateException(

                    "Could not read history",

                    ex

                );

            }

        });

    }

    @Override

    public void exportHistory() {
        if(view.isCapturePlaying()) return;

        JFileChooser chooser = Dialogs.fileChooser();

        chooser.setDialogTitle(

            "Export Cheetah Ludo results as CSV"

        );

        chooser.setSelectedFile(

            new java.io.File("cheetah-ludo-history.csv")

        );

        if (chooser.showSaveDialog(view)

                != JFileChooser.APPROVE_OPTION) {

            return;

        }

        Path path =

            chooser.getSelectedFile().toPath();

        if (!path.getFileName()

                .toString()

                .toLowerCase(Locale.ROOT)

                .endsWith(".csv")) {

            path = path.resolveSibling(

                path.getFileName() + ".csv"

            );

        }

        if (Files.exists(path)) {

            int confirm =

                Dialogs.showConfirmDialog(

                    view,

                    "Replace existing file?",

                    "Export",

                    JOptionPane.YES_NO_OPTION

                );

            if (confirm != JOptionPane.YES_OPTION) {

                return;

            }

        }

        try {

            history.export(path);

            Dialogs.showMessageDialog(

                view,

                "Saved to:\n" + path.toAbsolutePath()

            );

        } catch (IOException ex) {

            view.showError(ex);

        }

    }

    @Override public void watchReplay(HistoryRepository.Entry entry) {
        if (!view.isHistorySelection(entry)) return;
        long request = ++replayRequest;
        new SwingWorker<Optional<Replay>,Void>() {
            @Override protected Optional<Replay> doInBackground() { return replays.load(entry); }
            @Override protected void done() {
                if (request != replayRequest || !view.isHistorySelection(entry)) return;
                try {
                    var replay = get();
                    if (replay.isPresent()) view.showReplay(replay.get());
                    else view.markReplayUnavailable(entry.gameId());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (java.util.concurrent.ExecutionException ex) {
                    view.markReplayUnavailable(entry.gameId());
                }
            }
        }.execute();
    }

    private void refreshCapture(CaptureMove.Before before, Replay.Kind kind,
                                Replay.Answer answer, SurpriseOutcome surprise, int opponentDie) {
        CaptureMove capture = before.resolved();
        recorder.record(kind,capture,answer,surprise,opponentDie);
        refresh();
        if (capture != null && !view.playCapture(capture)) {
            throw new java.util.concurrent.CancellationException();
        }
    }

    private void refresh() {

        view.render(session, activeControl().automated());

    }

    private void tryAction(Runnable action) {
        actionDepth++;
        boolean finished = false;
        try {

            action.run();
            finished = true;

        } catch (java.util.concurrent.CancellationException abandonedMatch) {
            // Leaving the board cancels the rest of this move, including chained dialogs.
        } catch (RuntimeException ex) {

            ex.printStackTrace();

            view.showError(ex);

        } finally {
            if (--actionDepth == 0 && finished) gameAvailabilityChanged();
        }

    }
    private Set<String> findEligibleOpponentPieces(String currentPieceId) {
        Set<String> eligible = new HashSet<>();

        Piece currentPiece = session.findPiece(currentPieceId);

        if (currentPiece == null) {
            return eligible;
        }

        for (Player player : session.players()) {
            if (player.color() == currentPiece.color()) {
                continue;
            }

            for (Piece piece : player.pieces()) {
            	if (piece.state() == Piece.State.TRACK) {
            	    eligible.add(piece.id());
            	}
            }
        }

        return eligible;
    }
    @Override
    public void editAdminAccount() {
        tryAction(() -> {
            requireAdmin();
            try {
                new AdminAccountDialog(view, loadAdminProfileEmail(),
                        adminAuthService.codeVersion() != null, this::updateAdminCode).setVisible(true);
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        });
    }

    private boolean updateAdminCode(Component parent, boolean generate) {
        try {
            requireAdmin();
            byte[] version = adminAuthService.codeVersion();
            if (!verifyAdminFactor(parent, Factor.PASSWORD, false)
                    || (version != null && !verifyAdminFactor(parent, Factor.CODE, false))) {
                return false;
            }
            requireAdmin();
            return changeAdminCode(parent, version, generate);
        } catch (IOException | RuntimeException ex) {
            Dialogs.showMessageDialog(parent, ex.getMessage(),
                    "Admin Security", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }
}
