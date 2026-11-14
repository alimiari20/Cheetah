# Cheetah Ludo — MVC architecture (GUI edition)

**Model** (pure Java) → `src/model`: boards, dice, players, GameSession, GameEngine, exact-finish, capture, double-block, special-square rules and question DTOs.

**Controller** (Swing event orchestration) → `src/controller/GameController.java`: subscribes to GameFrame.Actions; chooses screen flow, handles dialog results, delegates move/turn rules to GameEngine, delegates file storage to repositories, refreshes view after state changes.

**View** (Swing only) → `src/view`: GameFrame hosts a CardLayout of main menu, setup, game board, history and administration; separate FirstPlayerDialog and QuestionDialog are modal; `BoardPanel` paints the state and emits piece-ID selections; Theme/UiKit share visual components.

**Repositories** → `src/model` (HistoryRepository, QuestionRepository): HistoryRepository saves/results/exports local history; QuestionRepository persists editable question bank and enforces 30-question minimum.

## A typical move

1. Player clicks Roll in `GameFrame`.
2. View dispatches action to `GameController.roll()`.
3. Controller calls `GameEngine.roll(session,random)`.
4. Model calculates dice and legal piece IDs.
5. Controller calls `view.render(session)`.
6. Player clicks a highlighted token in BoardPanel, which emits its piece ID.
7. Controller calls GameEngine.move, optionally presents QuestionDialog or Surprise, completes move and refreshes.

Rendering methods never decide if a move is legal. GameEngine never draws the board or imports Swing. File persistence remains separate from engine logic. Local question editing loads into a new game through QuestionBank.

## Running

Java 17+, import into Eclipse and run `src/com/cheetahludo/Main.java`. Running `--check` intentionally prints diagnostics instead of showing the GUI. See root README.
