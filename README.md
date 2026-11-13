# Cheetah Ludo — Prototype GUI Edition (Java Swing + MVC)

A playable, editable **cross-platform Java 17+ desktop game** for **Eclipse IDE**. The interface is based on the 24-page `Prototype_Cheetah.pdf` reference: royal-blue background, yellow calls to action, colorful Ludo board, and interactive views for the main menu, setup, first-player roll, gameplay, questions, surprises, history and question administration.

This is a **native Swing GUI**. It is not React, JavaFX, a browser page, or a static PDF screenshot. It uses standard Java only and the same source/JAR works on **Windows 10/11, macOS Intel and Apple Silicon**, and Linux when Java 17+ and a graphical desktop are present.

## How to RUN the actual game

### Eclipse (Windows or Mac)

1. Unzip the archive fully; keep the complete `CheetahLudoPrototypeGUI` folder together. If you imported an older version previously, **do not click its Run button**; select the new `Main.java` file and run that instead.
2. Open **Eclipse IDE for Java Developers**.
3. **File → Import → General → Existing Projects into Workspace**.
4. Select the extracted **`CheetahLudoPrototypeGUI`** project folder → Finish. This version uses a NEW project name so it can be imported alongside the old one.
5. Make sure **Project → Build Automatically** is checked and Eclipse has JDK 17 or newer.
6. Open `src/com/cheetahludo/Main.java`, right-click → **Run As → Java Application**.
7. The **CHEETAH LUDO** graphical main menu appears in a **separate desktop window**.

**Important:** run `CheetahLudo-Game.launch` or `Main.java` to open the window. The old `CheetahLudo-Check` launch option is not bundled in this version. The `--check` argument prints diagnostics and never opens the GUI.

### Windows (without Eclipse)

- Extract the ZIP, open `CheetahLudoPrototypeGUI`, double-click `RUN_ON_WINDOWS.bat`.
- Java 17+ must be installed unless you build the self-contained Windows app.
- For a bundled Java runtime, run `BUILD_WINDOWS_APP.bat` **on a Windows computer with a full JDK**. This uses `jpackage` to produce `release/CheetahLudo/CheetahLudo.exe` together with its runtime. Copy the **whole folder**; the `.exe` by itself is not portable.

### macOS (without Eclipse)

Extract the ZIP and run `RUN_ON_MAC.command`, or from Terminal:

```bash
cd /path/to/CheetahLudoEclipse
bash RUN_ON_MAC.command
```

If macOS warns about a downloaded script, run it with Terminal as above. Java 17+ is required. Apple Silicon uses the same `.jar` when a compatible Apple Silicon JDK is installed.

### Run the JAR directly

```bash
java -jar dist/CheetahLudo.jar
```

`java -jar dist/CheetahLudo.jar --check` tests Java/resources; it does **not** launch the game.

## Screens and functionality

| View | Implemented behavior |
|---|---|
| Main Menu | Large Cheetah Ludo brand, New Game, Game History, Exit, local administrator tools toggle |
| Game Setup | 2–4 players, editable names, **clickable unique player colors**, Easy/Medium/Hard selection and live board preview |
| First Player Roll | Individual dice clicks, highest result wins, automatic tie-break, starts clockwise |
| Easy / Medium / Hard Game | 15×15 / 17×17 / 19×19 board, player cards, dice, movement status, playable tokens, safe squares, captures, exact finish, blocked paths |
| Question Square | Multiple-choice modal, answer feedback, explanation and forward/backward movement |
| Surprise Square | Modal explaining the random effect; outcome applied in game engine |
| Match Complete | Winner and final ranking dialogue, local history save |
| Game History | Completed games table, individual ranking details and Export CSV |
| Question Administration | Search questions, add/edit/delete, Unicode support, local persistence; enforces **at least 30 questions** and at least one question per difficulty |

### Limits versus the prototype PDF

The PDF is a visual prototype with many demonstrations of specific SRS test scenarios. This edition reproduces the **actual interactive game screens**, not every frozen demo sequence or pixel of the web prototype. It does **not** include an authenticated remote administrator account, replayable named SRS demonstration scenes, question archive management, or web services. The administration toggle is **local demo editing access**, not an authentication or security boundary; do not use it to protect sensitive data.

The 32 original questions are bundled. Your locally edited question bank is read at the start of a new game.

## Where to edit (strict MVC)

- **Model**: `src/com/cheetahludo/model/` — game state, boards, turns, dice, moves, rules, piece captures, questions.
- **Controller**: `src/com/cheetahludo/controller/GameController.java` — user actions and modal orchestration.
- **View / GUI**: `src/com/cheetahludo/view/` — every screen and widget, with shared styles in `Theme.java` and `UiKit.java`.
- **Services**: `src/com/cheetahludo/service/` — local CSV match history and question bank persistence.

### Change the look

| Change | File |
|---|---|
| Blue background, gold buttons and component styling | `view/Theme.java`, `view/UiKit.java` |
| Main menu, in-game HUD and player cards | `view/GameFrame.java` |
| Player/setup screen | `view/SetupPage.java` |
| Interactive Ludo board and tokens | `view/BoardPanel.java` |
| First-player selection | `view/FirstPlayerDialog.java` |
| Question popup | `view/QuestionDialog.java` |
| Game history | `view/HistoryPage.java` |
| Question editor | `view/QuestionAdminPage.java` |

The Model and Controller do not depend on the specific theme, so you can redesign UI screens without editing game rules.

## Local data

Game history: `${user.home}/.cheetah-ludo/history.csv`

Edited questions: `${user.home}/.cheetah-ludo/questions.tsv`

`user.home` means your Windows/Mac home folder, **not** the project folder. Edits persist on the current computer; copying the project alone doesn't copy another PC's saved history or edited questions. The bundled question bank remains inside the source/JAR as a fallback.

## Build and tests

With Apache Ant installed:

```bash
ant clean jar
```

This compiles the project, runs **47 automated tests** (34 engine, 4 color assignment, 9 persistence/history), then rebuilds `dist/CheetahLudo.jar` with all board and question data.

```bash
ant run
```

The GUI and modal flow were also tested on a virtual desktop: main menu → setup → first-player competition → four-player game, and answering/applying a question. That is not a substitute for direct testing on your specific Windows or Mac machines.

## GUI preview images

View these files in `docs/screenshots/`:

- `01-main-menu.png`
- `02-setup.png`
- `03-first-player.png`
- `04-game-hard.png`
- `05-question.png`
- `06-game-history.png`
- `07-question-admin.png`

More mapping detail: `docs/PROTOTYPE_UI_MAPPING.md`.

## Troubleshooting

- **Console prints `CHECK PASSED`, but no window**: run `Main.java` / `CheetahLudo-Game`, not `CheetahLudo-Check`.
- **Java not found**: install Java/JDK 17 or newer. Run `java -version`.
- **Can't find class `Main`**: import the folder as an Existing Eclipse Project (don't create an unrelated blank project). Refresh and clean/rebuild.
- **No board data/questions**: keep the full ZIP extracted; check `java -jar dist/CheetahLudo.jar --check`.
- **Mac app hidden**: the Swing window opens separately from the Eclipse Console; use macOS window switcher.
- **Windows SmartScreen warning on a manually packaged EXE**: unsigned local builds may show a warning. Only run software you trust.

See also `docs/TROUBLESHOOTING.md`. No external Java libraries are required.
