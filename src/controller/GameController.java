package controller;

import view.*;

import javax.swing.*;

import model.*;

import java.io.IOException;

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

    private final Random random;

    private final QuestionRepository questionRepository =

        new QuestionRepository();

    private QuestionBank questions;

    private GameSession session;

    private boolean matchSaved;

    private boolean resolvingDialog;
    private boolean selectingOpponentPiece = false;
    private Set<String> eligibleOpponentPieceIds = Set.of();
    private String pendingSurprisePieceId = null;
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

        this.random = Objects.requireNonNull(random);

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

            int firstPlayer) {

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

        if (resolvingDialog) {

            return;

        }

        tryAction(() -> {

            int first = FirstPlayerDialog.chooseStarter(

                view,

                config.players(),

                config.colors(),

                random

            );

            if (first >= 0) {

                startSession(config, first);

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

        return UiKit.confirm(

            view,

            "Exit current game",

            "LEAVE CURRENT MATCH?",

            prompt

                + "\nYour unfinished progress will not be saved.",

            "LEAVE GAME"

        );

    }

    @Override

    public void questionAdmin() {

        tryAction(() -> {

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

    @Override

    public void saveQuestion(Question question) {

        tryAction(() -> {

            try {

                List<Question> list =

                    new ArrayList<>(questionRepository.list());

                int old = -1;

                for (int i = 0; i < list.size(); i++) {

                    if (list.get(i).id().equals(question.id())) {

                        old = i;

                        break;

                    }

                }

                if (old >= 0) {

                    list.set(old, question);

                } else {

                    list.add(question);

                }

                questionRepository.saveAll(list);

                view.updateQuestionAdmin(

                    questionRepository.list()

                );

                JOptionPane.showMessageDialog(

                    view,

                    "Question saved successfully.",

                    "Question bank",

                    JOptionPane.INFORMATION_MESSAGE

                );

            } catch (IOException ex) {

                throw new IllegalStateException(

                    "Could not save question",

                    ex

                );

            }

        });

    }

    @Override

    public void deleteQuestion(String questionId) {

        tryAction(() -> {

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

                JOptionPane.showMessageDialog(

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

        if (resolvingDialog) {

            return;

        }

        tryAction(() -> {

            if (session.phase()

                    != GameSession.Phase.ROLL) {

                return;

            }

            engine.roll(session, random);

            refresh();

            postMatchCheck();

        });

    }

    @Override

    public void pass() {

    	if (resolvingDialog || selectingOpponentPiece) {
    	    return;
    	}

        tryAction(() -> {

            engine.pass(session);

            refresh();

            postMatchCheck();

        });

    }

    @Override

    public void selectPiece(String pieceId) {

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

            GameEngine.MoveFeedback move =

                engine.move(session, pieceId);

            refresh();


resolveSpecial(pieceId, move.squareId(), new HashSet<>(), 0);

if (selectingOpponentPiece) {
    return;
}

engine.completeMove(session);


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

    int die = random.nextInt(6) + 1;
    int stepsBackward = (die + 1) / 2;

    GameEngine.MoveFeedback result =
            engine.shiftPiece(session, opponent, -stepsBackward);

    selectingOpponentPiece = false;
    eligibleOpponentPieceIds = Set.of();
    pendingSurprisePieceId = null;
    view.setOpponentSelectionIds(Set.of());

    refresh();

    resolvingDialog = true;
    try {
        view.showSurprise(
                "Opponent Backward!\n"
                + "Die result: " + die + "\n"
                + opponent.color().label()
                + " piece " + (opponent.number() + 1)
                + " moved " + Math.abs(result.steps())
                + " squares backward."
        );
    } finally {
        resolvingDialog = false;
    }

    engine.completeMove(session);
    refresh();
    postMatchCheck();
}

    private void resolveSpecial(

            String pieceId,

            String squareId,

            Set<String> visited,

            int depth) {

        if (depth >= 4

                || squareId == null

                || session.phase()

                    == GameSession.Phase.FINISHED) {

            return;

        }

        Piece piece = session.findPiece(pieceId);

        if (piece == null

                || piece.state() == Piece.State.CASTLE

                || piece.state() == Piece.State.FINISHED) {

            return;

        }

        Board.Cell square =

            session.board().cell(squareId);

        if (square == null || !visited.add(squareId)) {

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

            boolean correct;

            try {

                correct = QuestionDialog.ask(

                    view,

                    question

                );

            } finally {

                resolvingDialog = false;

            }

            engine.resolveQuestion(

                session,

                pieceId,

                question,

                correct

            );

            refresh();

            Piece updated = session.findPiece(pieceId);

            if (updated != null

                    && updated.state() != Piece.State.CASTLE

                    && updated.state() != Piece.State.FINISHED) {

                resolveSpecial(

                    pieceId,

                    updated.squareId(session.board()),

                    visited,

                    depth + 1

                );

            }

        } else if ("surprise".equals(square.symbol())) {

} else if ("surprise".equals(square.symbol())) {
    SurpriseOutcome outcome = SurpriseOutcome.draw(random);

    if (outcome == SurpriseOutcome.OPPONENT_BACKWARD) {
        beginOpponentSelection(pieceId);

        resolvingDialog = true;
        try {
            if (selectingOpponentPiece) {
                view.showSurprise(
                    "Choose a red highlighted opponent piece on the board."
                );
            } else {
                view.showSurprise(
                    "No eligible opponent piece. Surprise canceled."
                );
            }
        } finally {
            resolvingDialog = false;
        }
        return;
    }

    GameEngine.SurpriseFeedback feedback =
            engine.applySurprise(session, pieceId, outcome, random);

    refresh();

    resolvingDialog = true;
    try {
        view.showSurprise(feedback.message());
    } finally {
        resolvingDialog = false;
    }

    if (feedback.landingSquare() != null
            && !feedback.landingSquare().equals(squareId)) {
        resolveSpecial(
            pieceId, feedback.landingSquare(), visited, depth + 1
        );
    }

        }


    }

    private void postMatchCheck() {

        if (session.phase() != GameSession.Phase.FINISHED

                || matchSaved) {

            return;

        }

        matchSaved = true;

        String info =

            "Game saved in your local match history.";

        try {

            history.save(session);

        } catch (IOException ex) {

            info = "Could not save local history: "

                + ex.getMessage();

        }

        refresh();

        view.showMatchEnd(session, info);

    }

    @Override

    public void history() {

        tryAction(() -> {

            try {

                view.showHistory(

                    history.readEntries()

                );

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

        JFileChooser chooser = new JFileChooser();

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

                JOptionPane.showConfirmDialog(

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

            JOptionPane.showMessageDialog(

                view,

                "Saved to:\n" + path.toAbsolutePath()

            );

        } catch (IOException ex) {

            view.showError(ex);

        }

    }

    private void refresh() {

        view.render(session);

    }

    private void tryAction(Runnable action) {

        try {

            action.run();

        } catch (RuntimeException ex) {

            ex.printStackTrace();

            view.showError(ex);

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
}