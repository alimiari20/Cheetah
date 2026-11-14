package model;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Local-only, append-only history. Stored outside the source project. */
public final class HistoryRepository {
    private final Path historyFile;
    private static final String HEADER = "date,difficulty,players,winner,ranking,duration_seconds";

    public HistoryRepository() {
        this(Path.of(System.getProperty("user.home"), ".cheetah-ludo", "history.csv"));
    }
    public HistoryRepository(Path file) { this.historyFile = file; }

    public void save(GameSession game) throws IOException {
        if (game.phase() != GameSession.Phase.FINISHED || game.ranking().isEmpty())
            throw new IllegalStateException("Game is not complete");
        Path parent = historyFile.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        if (!Files.exists(historyFile)) Files.writeString(historyFile, HEADER + "\n", StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW);
        List<String> places = new ArrayList<>();
        for (PlayerColor c : game.ranking()) places.add(game.findPlayer(c).name());
        long duration = Duration.between(game.started(), Instant.now()).toSeconds();
        String line = String.join(",",
                csv(DateTimeFormatter.ISO_INSTANT.format(Instant.now())),
                csv(game.difficulty().name()),
                Integer.toString(game.players().size()),
                csv(places.get(0)),
                csv(String.join(" > ", places)),
                Long.toString(duration));
        Files.writeString(historyFile, line + "\n", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
    }

    public String displayHistory() throws IOException {
        if (!Files.exists(historyFile)) return "No completed games yet.\n\nFinish a match to save its result.";
        List<String> lines = Files.readAllLines(historyFile, StandardCharsets.UTF_8);
        if (lines.size() <= 1) return "No completed games yet.";
        StringBuilder sb = new StringBuilder("COMPLETED GAMES\n\n");
        for (int i = lines.size() - 1; i >= 1; i--) sb.append(lines.get(i)).append("\n\n");
        return sb.toString();
    }

    public record Entry(String date,String difficulty,int players,String winner,String ranking,long seconds) {}

    /** Structured history for the desktop table, with RFC-style quoted CSV parsing. */
    public List<Entry> readEntries() throws IOException {
        if (!Files.exists(historyFile)) return List.of();
        List<Entry> result=new ArrayList<>();
        List<String> lines=Files.readAllLines(historyFile,StandardCharsets.UTF_8);
        for(int i=1;i<lines.size();i++) {
            if(lines.get(i).isBlank())continue;
            List<String> f=parseCsv(lines.get(i));
            if(f.size()!=6)continue;
            try {result.add(new Entry(f.get(0),f.get(1),Integer.parseInt(f.get(2)),
                     f.get(3),f.get(4),Long.parseLong(f.get(5))));}
            catch(NumberFormatException ignored){ /* ignore malformed historic row */ }
        }
        Collections.reverse(result);return result;
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

    public void export(Path destination) throws IOException {
        if (Files.exists(historyFile)) Files.copy(historyFile, destination, StandardCopyOption.REPLACE_EXISTING);
        else Files.writeString(destination, HEADER + "\n", StandardCharsets.UTF_8);
    }

    private static String csv(String raw) {
        // Protect spreadsheet software against formula execution on exported player names.
        String sanitized = raw.matches("^[=+\\-@].*") ? "'" + raw : raw;
        return '"' + sanitized.replace("\"", "\"\"") + '"';
    }
}
