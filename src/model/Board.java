package model;

import java.util.*;

/** Read-only board geometry and per-player movement tracks. */
public final class Board {
    public record Cell(int row, int column, String type, String color, String symbol,
                       String arrow, String specialColor) {
        public String id() { return "R" + row + "-C" + column; }
    }
    public record Position(int row, int column) {
        public static Position parse(String id) {
            int split = id.indexOf("-C");
            if (!id.startsWith("R") || split < 0) throw new IllegalArgumentException("Invalid square: " + id);
            return new Position(Integer.parseInt(id.substring(1, split)), Integer.parseInt(id.substring(split + 2)));
        }
    }

    private final Difficulty difficulty;
    private final int size;
    private final Map<String, Cell> cells;
    private final Map<PlayerColor, List<String>> paths;
    private final Map<PlayerColor, List<String>> homes;
    private final Map<PlayerColor, List<String>> castles;
    private final Map<PlayerColor, String> starts;

    Board(Difficulty difficulty, int size, Map<String, Cell> cells,
          Map<PlayerColor, List<String>> paths, Map<PlayerColor, List<String>> homes,
          Map<PlayerColor, List<String>> castles, Map<PlayerColor, String> starts) {
        this.difficulty = Objects.requireNonNull(difficulty);
        this.size = size;
        this.cells = Map.copyOf(cells);
        this.paths = freeze(paths);
        this.homes = freeze(homes);
        this.castles = freeze(castles);
        this.starts = Map.copyOf(starts);
        validate();
    }

    private static Map<PlayerColor, List<String>> freeze(Map<PlayerColor, List<String>> source) {
        EnumMap<PlayerColor, List<String>> copy = new EnumMap<>(PlayerColor.class);
        source.forEach((key, value) -> copy.put(key, List.copyOf(value)));
        return Collections.unmodifiableMap(copy);
    }

    public Difficulty difficulty() { return difficulty; }
    public int size() { return size; }
    public Cell cell(int row, int col) { return cells.get("R" + row + "-C" + col); }
    public Cell cell(String id) { return cells.get(id); }
    public Collection<Cell> cells() { return cells.values(); }
    public List<String> path(PlayerColor color) { return paths.get(color); }
    public List<String> home(PlayerColor color) { return homes.get(color); }
    public String start(PlayerColor color) { return starts.get(color); }
    public String castleSlot(PlayerColor color, int index) { return castles.get(color).get(index); }
    public boolean isHome(PlayerColor color, String squareId) { return homes.get(color).contains(squareId); }
    public boolean isSafe(String squareId) {
        Cell square = cell(squareId);
        return square != null && "star".equals(square.symbol());
    }
    public Map<String, Long> symbolCounts() {
        Map<String, Long> counts = new HashMap<>();
        for (Cell c : cells.values()) counts.merge(c.symbol(), 1L, Long::sum);
        return counts;
    }

    private void validate() {
        if (size != difficulty.boardSize() || cells.size() != size * size)
            throw new IllegalArgumentException("Board dimensions invalid for " + difficulty);
        for (PlayerColor color : PlayerColor.values()) {
            List<String> path = Objects.requireNonNull(paths.get(color), "path " + color);
            List<String> home = Objects.requireNonNull(homes.get(color), "home " + color);
            if (path.isEmpty() || home.isEmpty() || !path.get(0).equals(starts.get(color)) ||
                !path.subList(path.size() - home.size(), path.size()).equals(home) ||
                castles.get(color).size() != 4)
                throw new IllegalArgumentException("Invalid path/castle for " + color);
            for (String id : path) if (!cells.containsKey(id)) throw new IllegalArgumentException("Unknown path cell " + id);
        }
    }
}
