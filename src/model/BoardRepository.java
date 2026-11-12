package model;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Imports the exact geometry exported from the original TypeScript project. */
public final class BoardRepository {
    private final Map<Difficulty, Board> cache = new EnumMap<>(Difficulty.class);

    public synchronized Board load(Difficulty difficulty) {
        return cache.computeIfAbsent(difficulty, this::parse);
    }

    private Board parse(Difficulty difficulty) {
        String path = "/com/cheetahludo/model/data/" + difficulty.name().toLowerCase() + ".tsv";
        InputStream input = BoardRepository.class.getResourceAsStream(path);
        if (input == null) throw new IllegalStateException("Missing board resource: " + path);
        Map<String, Board.Cell> cells = new HashMap<>();
        EnumMap<PlayerColor, List<String>> paths = new EnumMap<>(PlayerColor.class);
        EnumMap<PlayerColor, List<String>> homes = new EnumMap<>(PlayerColor.class);
        EnumMap<PlayerColor, List<String>> castles = new EnumMap<>(PlayerColor.class);
        EnumMap<PlayerColor, String> starts = new EnumMap<>(PlayerColor.class);
        int size = -1;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] fields = line.split("\t", -1);
                switch (fields[0]) {
                    case "SIZE" -> size = Integer.parseInt(fields[1]);
                    case "START" -> starts.put(color(fields[1]), fields[2]);
                    case "PATH" -> paths.put(color(fields[1]), List.of(fields[2].split(",")));
                    case "HOME" -> homes.put(color(fields[1]), List.of(fields[2].split(",")));
                    case "CASTLE" -> castles.put(color(fields[1]), List.of(fields[2].split(",")));
                    case "CELL" -> {
                        Board.Cell c = new Board.Cell(Integer.parseInt(fields[1]), Integer.parseInt(fields[2]),
                                fields[3], fields[4], fields[5], fields[6], fields[7]);
                        cells.put(c.id(), c);
                    }
                    case "DIFFICULTY" -> {
                        if (!fields[1].equalsIgnoreCase(difficulty.name())) throw new IOException("Wrong difficulty");
                    }
                    default -> throw new IOException("Unknown board record: " + fields[0]);
                }
            }
        } catch (IOException | RuntimeException ex) {
            throw new IllegalStateException("Could not load board " + difficulty, ex);
        }
        return new Board(difficulty, size, cells, paths, homes, castles, starts);
    }

    private static PlayerColor color(String value) { return PlayerColor.valueOf(value.toUpperCase(Locale.ROOT)); }
}
