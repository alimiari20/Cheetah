# On-board captures

Combat plays on the live board for 1.6 seconds. The knight lunges and swings a silver blade, the wizard casts an orb, the archer draws and releases an arrow, and the lancer charges with an extended spear. Each defender returns to its own castle position. The current board scale applies throughout playback, including when the window is resized.

## Developer capture lab

Run the application with `--capture-lab`. In Eclipse, add this flag under **Run Configurations → Arguments → Program arguments** for `com.cheetahludo.Main`. With the packaged game, run:

```sh
java -jar dist/CheetahLudo.jar --capture-lab
```

Choose the two heroes, their colors, the board, one of eight directions, and normal, half or quarter speed. **Play capture** stages a match on a shared, unprotected track square and uses the game engine to resolve a move. **Replay capture** repeats the setup. The controls and throwaway match exist only in this launch mode.

## Implementation

- `CaptureMove` compares opponent positions around one engine operation. It records a visual event only for opponents returned to their castles from the attacker's landing square. The approach follows the attacker's actual path indices, including backward movement.
- `CapturePlayback` owns one coalescing Swing timer and measures progress with a monotonic clock. Its secondary event loop dispatches input and paint events while the controller waits to resolve any chained effects. Leaving the board cancels the pending continuation.
- `BoardPanel` suppresses the participants' static sprites during playback. `CaptureAnimation` projects board coordinates into the current layout, including the same castle positions used for resting heroes. `CharacterAttackRenderer` draws weapons and effects procedurally. The transparent sprite cache is bounded by four colors, four heroes and two poses.
- `mvn test` runs the JUnit and standalone suites. `ant clean jar` runs the standalone suites and rebuilds the desktop JAR.
