# Mapping from `Prototype_Cheetah.pdf` to interactive Java Swing screens

Reference: user-provided 24-page prototype document.

| Reference page | Prototype screen | Editable project implementation |
|---|---|---|
| 1 | Guest / Administrator main menu | `GameFrame.buildMainMenu` (local admin tools; no account authentication) |
| 2 | Player setup, colors, board preview | `SetupPage` (interactive player count, editable names, color swapping, difficulty and board preview) |
| 2–3 | First player roll, tie-break and result | `FirstPlayerDialog` (interactive dice contest, winner and start button) |
| 4–5 | Easy / Medium / Hard boards and exit | `GameFrame.buildGameScreen`, `BoardPanel`, confirm-leave via `GameController.showMenu` |
| 6–9 | Dice, extra roll, consecutive sixes, Boost and UltraBoost calculation | `GameEngine`, `GameFrame` dice HUD and phase status |
| 10–12 | Safe squares, captures, exact finish and double block | `BoardPanel`, `GameEngine`, `GameSession` |
| 14–17 | Question events and correct/incorrect outcomes | `QuestionDialog`, `QuestionBank`, `GameController.resolveSpecial` |
| 17–20 | Surprise events including skipped turn, roll, finish, castle, backtracking | `GameController.resolveSpecial`, `GameEngine.applySurprise`, `GameFrame.showSurprise` |
| 21–22 | Home, winner, rankings, history | `GameFrame.showMatchEnd`, `HistoryPage`, `HistoryRepository` |
| 23–24 | Question delete, 30-question validation, add/edit and save | `QuestionAdminPage`, `QuestionRepository` |

## Design choices

- Implemented as vector-based native Swing components; the PDF images are **not** embedded as background screenshots.
- Dark/royal-blue gradient, gold actions, colored player pieces and modal styling follow the PDF's visual language.
- Same compiled `.jar` works on Windows and macOS with compatible Java, including Apple Silicon.
- Game engine logic remains platform-neutral and independent of rendering.
- Screenshot assets are provided for comparison in `docs/screenshots`.

## Honest differences

This does **not** reproduce every static SRS demonstration or exact pixel measurements from the PDF. The original PDF's administrator appears as an authenticated profile; this Java version provides **local administration mode without authentication**, intended for a student desktop prototype, not secure remote multi-user operation. The original question archive, named scenario runners and audio UI have not been ported. The game screen is fully interactive for standard local play.
