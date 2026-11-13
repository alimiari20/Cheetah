# Validation report

The Eclipse Java edition was compiled with Java 21 using `javac --release 17`.

Commands:

```
ant clean test
ant jar
java -jar dist/CheetahLudo.jar
```

`test/com/cheetahludo/model/EngineTests.java` runs without external libraries. Tests exercise board data, question cycling, dice modes, legal moves, sixes and rerolls, double blocks, capturing, safe stars, finish rules, quiz movement, surprises, skips, history CSV, and headless board graphics.

The GUI's primary window was also opened on an X virtual display and captured for visual inspection; game controls, board, player cards, and match activity panels were visible. User-platform runtime, all question/chain dialogs, and long full-match flows should still be manually checked in Eclipse.
