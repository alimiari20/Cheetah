# Porting notes — React/TypeScript to Eclipse Java

The original upload was a React/TypeScript web app using Vite, Tailwind and localStorage. Eclipse is primarily a Java IDE, so we created a **native Java 17 Swing application** with `.project` and `.classpath`. This avoids fragile embedded-browser wrappers and extra node/npm setup.

## Preserved as model data or rules

- Original Easy (15×15), Medium (17×17), and Hard (19×19) **cell-by-cell maps**, castle positions, player tracks and home paths.
- Original special-square locations (stars, questions, surprises, directional arrows) and special counts.
- All 32 original software-engineering trivia questions, choices, correct answers and explanations.
- Classic, Boost and UltraBoost dice formulas, 3-sixes penalty, six-based castle exit/bonus roll.
- Full-move priority, double-block barriers and stop-before-block fallback.
- Captures, safe stars, exact home arrival, completion ranking and skipped turns.
- Question difficulty by landing-square color and ±1/2/3 reward/penalty.
- Surprise weights and six outcomes: direct finish, opponent -6, return to castle, self -3, skip next turn, immediate UltraBoost.
- Local completed-game history (reimplemented via CSV file, rather than browser localStorage).

## Intentionally different/new

- GUI is native Java Swing rather than React + CSS. It is responsive, visually styled and vector-rendered; it is not a pixel-perfect clone.
- Match setup and player names are provided in a desktop dialog. The original first-player roll and tie-breaker competition is also available as a Swing dialog.
- CSV file history replaces localStorage.
- Random dice are fair by default; the web application's SRS scripted demo rolls are not in normal gameplay.

## Deferred for future work

- Original web-specific SRS examiner/admin menu, question manager/editor, board validation screens, demo playback and scenario scripts.
- Audio effects and web animations.
- Special-arrow transport behavior (arrows display the original map's directions but have no movement effect).
- Persist/resume active games, undo/redo, online matchmaking and AI.

The original TypeScript source is not included in this Java ZIP; keep your original React ZIP as a reference when migrating additional requirements.
