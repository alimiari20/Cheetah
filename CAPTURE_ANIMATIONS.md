# Character capture animations

All four original pixel-art heroes retain their existing artwork and player colour. Knight: sword arc; Wizard: magic orb; Archer: arrow; Lancer: spear thrust. Captured heroes shake, rise and fade before appearing in their castle. Each capture sequence runs for 1,050 ms on a Swing timer. No external animation assets or JavaFX are required.

The controller compares opponent positions before and after model operations. Only a confirmed capture at the attacking hero's landing square triggers the effect. Regular moves, question shifts and surprise shifts are covered. The board temporarily hides the actual captured pieces and attacker while painting the effect; it does not modify model state. Roll, pass, board selection, pause and navigation are guarded during the animation. Special-square processing and completion continue once the effect ends.

## Eclipse
1. Back up your current branch or commit your changes.
2. Copy the changed files into the corresponding paths in your existing project (or import the full project as an existing Eclipse project).
3. Project > Clean; run `src/com/cheetahludo/Main.java` as a Java Application with Java 17 or later.
4. Start a match, capture an opponent with each of the four heroes, and check that the opponent returns to the castle and the next action works. Protected squares and blocked/non-capture moves should not show an attack.

## Changed files
- `src/view/CaptureAnimation.java` (new)
- `src/view/BoardPanel.java`
- `src/view/GameFrame.java`
- `src/controller/GameController.java`
- `test/view/CaptureAnimationTests.java` (new)
- `build.xml` (registers animation tests)

## Verification
`ant clean jar` passed: 105 existing tests plus a new animation test covering 16 hero/colour combinations, 80 rendered frames and the timer lifecycle. Manual interactive gameplay on macOS/Windows has not been verified.
