# Eclipse import conflicts (multiple downloaded versions)

The older packages were all named `CheetahLudoEclipse`, which could cause Eclipse to select the old code or the old `CheetahLudo-Check` configuration. This build uses `CheetahLudoPrototypeGUI` as its project ID, and includes only a game launch file.

To start the real GUI, right-click `src/com/cheetahludo/Main.java` in the **new** project and choose **Run As > Java Application**. The green Run button may continue relaunching an old configuration; do not use it until you have selected the new game.

----

# Cheetah Ludo - Eclipse / Mac / Windows troubleshooting

## Fastest way to try the game

- Extract the downloaded ZIP FIRST (do not import or launch straight from inside the ZIP).
- **Mac:** Open `CheetahLudoEclipse/RUN_ON_MAC.command` after installing Java 17+.
- **Windows:** Open `CheetahLudoEclipse/RUN_ON_WINDOWS.bat` after installing Java 17+. To create a self-contained Windows `.exe` app, run `BUILD_WINDOWS_APP.bat` from Windows with a full JDK 17+.
- Or open a Terminal / Command Prompt in `CheetahLudoEclipse` and run:

    java -jar dist/CheetahLudo.jar --check
    java -jar dist/CheetahLudo.jar

Both commands are intended to be run from a desktop that supports graphical windows. `--check` runs without a GUI.

## Eclipse step-by-step

1. Make sure you have **Eclipse IDE for Java Developers** and **JDK 17 or newer** installed.
2. Extract the archive and look for a folder named **CheetahLudoEclipse** containing `src`, `.project`, `.classpath`, `CheetahLudo-Game.launch`.
3. In Eclipse select **File > Import > General > Existing Projects into Workspace > Next**.
4. Select the extracted `CheetahLudoEclipse` folder as root, then **Finish**.
5. If the project shows a red `X`, go to **Eclipse > Settings (macOS)** / **Window > Preferences (Windows) > Java > Installed JREs**. Select or **Add > Standard VM** a locally installed **JDK 17+**, and set it as the default.
6. Right-click project > **Properties > Java Compiler**, and check that the compliance level is **17** or later. It is fine to use Java 21 to compile Java 17 language features.
7. Right-click project > **Refresh**, then **Project > Clean...** and make sure **Build Automatically** is enabled.
8. Open `src/com/cheetahludo/Main.java`, right-click > **Run As > Java Application**.
9. Alternative: right-click `CheetahLudo-Game.launch` > **Run As > Java Application** (if offered), or use **Run > Run Configurations > Java Application > CheetahLudo-Game**. **Do not use `CheetahLudo-Check` to play:** that one just prints "CHECK PASSED".
10. If it still fails, right-click `CheetahLudo-Check.launch` > **Run As > CheetahLudo-Check** and copy the **Console** output.

## Frequent errors

**"No projects found"** — select the folder directly containing `.project`. On macOS use Finder > View > Show View Options / Cmd+Shift+. to inspect hidden dotfiles; import a fully *extracted* folder.

**"Project already exists"** — Eclipse already has a project of the same name. Open the existing project or choose a different workspace. Don't import a duplicate into the same workspace.

**"JRE System Library [JavaSE-17] is unbound"** — configure an installed JDK 17+ under Eclipse's installed JREs, then use Project > Properties > Java Build Path > Libraries to point to it. This revised ZIP uses a generic JRE container to reduce such errors.

**"Unsupported class file major version 61" / "UnsupportedClassVersionError"** — Java older than 17 is launching the app. Use JDK 17+ for the run configuration. `61` refers to Java 17 bytecode.

**"Could not find or load main class"** — build the imported project and right-click `src/com/cheetahludo/Main.java` (NOT the ZIP or the top-level package); check it has no red errors. For the JAR, use `java -jar dist/CheetahLudo.jar` from the project folder.

**"Missing board resource" / "Missing questions"** — check the `src/com/cheetahludo/model/data/*.tsv` files are present. Eclipse should copy source resources into `bin/` during a normal Java build; ensure `src` is a Source Folder in Java Build Path.

**"Exception in thread AWT-EventQueue"** — read the error in Eclipse > Console. Newer versions also write the startup stack trace to `~/.cheetah-ludo/last-startup-error.txt`.

**Mac shows "cannot be opened because the developer cannot be verified"** — the downloaded `.command` might be quarantined. Instead of bypassing protection globally, open Terminal, change to the extracted project directory and run `bash RUN_ON_MAC.command`. If macOS security settings restrict downloaded scripts, use Eclipse to run the Java `Main.java` directly.

**Mac shows a message about a missing Java runtime** — install a JDK (not just Eclipse). Check `java -version` in Terminal and Eclipse's Installed JREs. If several Java versions exist, make Eclipse's JDK 17+ the default.

**No window appears** — a graphical display must be available. Inspect Eclipse's Console for error lines; use `--check` to test resources. If a setup popup is behind the main window, bring the Cheetah Ludo application to the foreground. Do not run with `-Djava.awt.headless=true`.

## Information to send if you still cannot start it

1. Are you using macOS or Windows? (Include version if known.)
2. Are you importing in Eclipse or opening the `.jar`?
3. Paste the *first complete error* from the **Eclipse Console** or terminal (or attach a screenshot).
4. Send the output of `java -version` and `java -jar dist/CheetahLudo.jar --check`.

The project requires no libraries beyond the JDK; you do NOT need Maven, Gradle, Node.js, JavaFX, or npm.

## Creating an app for Windows users without Java installed

On a **Windows 10/11 development PC** with a full JDK 17+, double-click `BUILD_WINDOWS_APP.bat`. It invokes `jpackage --type app-image` to create `release\CheetahLudo\CheetahLudo.exe` and an embedded Java runtime. Distribute the entire `release\CheetahLudo` folder. The script is an app-image builder, **not** an installer builder; packaging on macOS does not create Windows executables.
