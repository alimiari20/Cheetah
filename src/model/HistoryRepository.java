package model;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Local-only, append-only game history, stored outside the source project.
 * The file uses the course's CSV template (History-DataBase_Template from Moodle):
 *
 *   gameID,difficulty,timestamp(DD/MM/YYYY; HH:MM:SS),1st_place_name,2nd_place_name,
 *   3rd_place_name,4rd_place_name,game_duration(HH:MM:SS)
 *   1,hard,19/10/2026;12:01:00,Mor,Noy,Safa,Muhammed,01:05:04
 *
 * Unused places (games with 2 or 3 players) are left empty.
 * A history file written by an older version of the game is converted once,
 * and the original is kept next to it as history.csv.bak.
 */
public final class HistoryRepository {
    private final Path historyFile;

    /** Header exactly as in the course template (including its "4rd" spelling). */
    public static final String HEADER = "gameID,difficulty,timestamp(DD/MM/YYYY; HH:MM:SS),"
            + "1st_place_name,2nd_place_name,3rd_place_name,4rd_place_name,game_duration(HH:MM:SS)";
    private static final String OLD_HEADER = "date,difficulty,players,winner,ranking,duration_seconds";
    private static final int COLUMNS = 8;
    private static final Object SAVE_LOCK = new Object();
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("dd/MM/yyyy;HH:mm:ss");

    public HistoryRepository() {
        this(Path.of(System.getProperty("user.home"), ".cheetah-ludo", "history.csv"));
    }
    public HistoryRepository(Path file) { this.historyFile = file; }

    Path replayDirectory() { return historyFile.toAbsolutePath().resolveSibling(historyFile.getFileName() + ".replays"); }

    public Entry save(GameSession game) throws IOException {
        if (game.phase() != GameSession.Phase.FINISHED || game.ranking().isEmpty())
            throw new IllegalStateException("Game is not complete");
        Path parent = historyFile.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        synchronized (SAVE_LOCK) {
            Path lock = historyFile.resolveSibling(historyFile.getFileName() + ".lock");
            try (var channel = FileChannel.open(lock, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 var ignored = channel.lock()) {
                return appendCompleted(game);
            }
        }
    }

    private Entry appendCompleted(GameSession game) throws IOException {
        convertOldFormat();
        if (!Files.exists(historyFile))
            Files.writeString(historyFile, HEADER + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);

        List<String> places = new ArrayList<>();
        for (PlayerColor c : game.ranking()) places.add(game.findPlayer(c).name());
        while (places.size() < 4) places.add("");

        long seconds = Math.max(0, Duration.between(game.started(), Instant.now()).toSeconds());
        int id = nextGameId();
        String date = LocalDateTime.now().format(TIMESTAMP);
        String line = String.join(",",
                Integer.toString(id),
                game.difficulty().name().toLowerCase(Locale.ROOT),
                date,
                csv(places.get(0)), csv(places.get(1)), csv(places.get(2)), csv(places.get(3)),
                duration(seconds));
        Files.writeString(historyFile, line + "\n", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        return entry(parseCsv(line));
    }

    public String displayHistory() throws IOException {
        convertOldFormat();
        if (!Files.exists(historyFile)) return "No completed games yet.\n\nFinish a match to save its result.";
        List<String> lines = Files.readAllLines(historyFile, StandardCharsets.UTF_8);
        if (lines.size() <= 1) return "No completed games yet.";
        StringBuilder sb = new StringBuilder("COMPLETED GAMES\n\n");
        for (int i = lines.size() - 1; i >= 1; i--) sb.append(lines.get(i)).append("\n\n");
        return sb.toString();
    }

    public record Entry(String date,String difficulty,int players,String winner,String ranking,long seconds,int gameId) {
        public Entry(String date,String difficulty,int players,String winner,String ranking,long seconds) {
            this(date,difficulty,players,winner,ranking,seconds,0);
        }
    }

    private static Entry entry(List<String> fields) {
        List<String> names = new ArrayList<>();
        for (int p = 3; p <= 6; p++) if (!fields.get(p).isBlank()) names.add(unprotect(fields.get(p).trim()));
        if (names.isEmpty()) throw new IllegalArgumentException("Missing ranking");
        int id;
        try { id = Integer.parseInt(fields.get(0).trim()); }
        catch (NumberFormatException ex) { id = 0; }
        return new Entry(fields.get(2).trim(), fields.get(1).trim().toUpperCase(Locale.ROOT), names.size(),
                names.get(0), String.join(" > ", names), parseDuration(fields.get(7)), id);
    }

    /** Structured history for the desktop table, newest first. */
    public List<Entry> readEntries() throws IOException {
        convertOldFormat();
        if (!Files.exists(historyFile)) return List.of();
        List<Entry> result = new ArrayList<>();
        List<String> lines = Files.readAllLines(historyFile, StandardCharsets.UTF_8);
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).isBlank()) continue;
            List<String> f = parseCsv(lines.get(i));
            if (f.size() != COLUMNS) continue;
            try {
                result.add(entry(f));
            } catch (RuntimeException ignored) { /* ignore a malformed historic row */ }
        }
        Collections.reverse(result);
        return result;
    }

    public void export(Path destination) throws IOException {
        convertOldFormat();
        if (Files.exists(historyFile)) Files.copy(historyFile, destination, StandardCopyOption.REPLACE_EXISTING);
        else Files.writeString(destination, HEADER + "\n", StandardCharsets.UTF_8);
    }

    // ---------- helpers ----------

    private int nextGameId() throws IOException {
        int max = 0;
        List<String> lines = Files.readAllLines(historyFile, StandardCharsets.UTF_8);
        for (int i = 1; i < lines.size(); i++) {
            List<String> f = parseCsv(lines.get(i));
            try { max = Math.max(max, Integer.parseInt(f.get(0).trim())); }
            catch (RuntimeException ignored) { /* skip a row without a numeric ID */ }
        }
        return max + 1;
    }

    /** Converts a history file written in the previous format to the course template, once. */
    private void convertOldFormat() throws IOException {
        if (!Files.exists(historyFile)) return;
        List<String> lines = Files.readAllLines(historyFile, StandardCharsets.UTF_8);
        if (lines.isEmpty() || !lines.get(0).trim().equals(OLD_HEADER)) return;
        Files.copy(historyFile, historyFile.resolveSibling(historyFile.getFileName() + ".bak"),
                StandardCopyOption.REPLACE_EXISTING);
        StringBuilder out = new StringBuilder(HEADER).append('\n');
        int id = 0;
        for (int i = 1; i < lines.size(); i++) {
            List<String> f = parseCsv(lines.get(i));
            if (f.size() != 6) continue;
            try {
                String when = LocalDateTime.ofInstant(Instant.parse(f.get(0)), ZoneId.systemDefault()).format(TIMESTAMP);
                List<String> names = new ArrayList<>(Arrays.asList(f.get(4).split(" > ")));
                while (names.size() < 4) names.add("");
                out.append(++id).append(',').append(f.get(1).toLowerCase(Locale.ROOT)).append(',').append(when);
                for (int p = 0; p < 4; p++) out.append(',').append(csv(names.get(p)));
                out.append(',').append(duration(Long.parseLong(f.get(5).trim()))).append('\n');
            } catch (RuntimeException ignored) { /* drop a malformed historic row */ }
        }
        Files.writeString(historyFile, out, StandardCharsets.UTF_8);
    }

    /** 3725 -> "01:02:05" */
    static String duration(long seconds) {
        return String.format("%02d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60);
    }

    /** "01:02:05" -> 3725 */
    static long parseDuration(String text) {
        String[] p = text.trim().split(":");
        if (p.length != 3) throw new IllegalArgumentException("Bad duration: " + text);
        return Long.parseLong(p[0]) * 3600 + Long.parseLong(p[1]) * 60 + Long.parseLong(p[2]);
    }

    private static List<String> parseCsv(String text){
        List<String> fields=new ArrayList<>();StringBuilder s=new StringBuilder();boolean quote=false;
        for(int i=0;i<text.length();i++){
            char ch=text.charAt(i);
            if(ch=='\"'){
                if(quote && i+1<text.length() && text.charAt(i+1)=='\"'){s.append('\"');i++;}
                else quote=!quote;
            } else if(ch==','&&!quote){fields.add(s.toString());s.setLength(0);}
            else s.append(ch);
        }
        fields.add(s.toString());return fields;
    }

    /** Writes a name like the template (no quotes), quoting only when the name needs it. */
    private static String csv(String raw) {
        // Protect spreadsheet software against formula execution on exported player names.
        String value = raw.matches("^[=+\\-@].*") ? "'" + raw : raw;
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r"))
            return '"' + value.replace("\"", "\"\"") + '"';
        return value;
    }

    private static String unprotect(String name) {
        return name.matches("^'[=+\\-@].*") ? name.substring(1) : name;
    }
}
