package model;

import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Versioned, compressed sidecars associated with completed history entries. */
public final class ReplayRepository {
    private static final String MAGIC = "CHEETAH-REPLAY-1";
    private static final int MAX_FRAMES = 100_000;
    private final Path directory;

    public ReplayRepository(HistoryRepository history) { directory = history.replayDirectory(); }

    public Set<Integer> available(List<HistoryRepository.Entry> entries) {
        Set<Integer> found = new HashSet<>();
        for (var entry : entries) {
            if (entry.gameId() > 0 && Files.isRegularFile(path(entry.gameId()))) found.add(entry.gameId());
        }
        return Set.copyOf(found);
    }

    public void save(HistoryRepository.Entry entry, Replay replay) throws IOException {
        if (entry.gameId() <= 0 || !matches(entry, replay) || replay.frames().size() > MAX_FRAMES) {
            throw new IOException("Replay does not match completed history");
        }
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, "replay-", ".tmp");
        try {
            try (var out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(temporary))))) {
                out.writeUTF(MAGIC);
                out.writeInt(entry.gameId());
                out.writeUTF(entry.date());
                out.writeLong(entry.seconds());
                writeBoard(out, replay.board());
                out.writeInt(replay.players().size());
                for (var p : replay.players()) {
                    out.writeUTF(p.name());
                    out.writeUTF(p.color().name());
                    out.writeUTF(p.control().name());
                }
                out.writeInt(replay.frames().size());
                for (var frame : replay.frames()) writeFrame(out, frame);
            }
            try {
                Files.move(temporary, path(entry.gameId()), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, path(entry.gameId()), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public Optional<Replay> load(HistoryRepository.Entry entry) {
        if (entry.gameId() <= 0) return Optional.empty();
        try (var in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(path(entry.gameId())))))) {
            if (!MAGIC.equals(in.readUTF()) || in.readInt() != entry.gameId()
                    || !in.readUTF().equals(entry.date()) || in.readLong() != entry.seconds()) return Optional.empty();
            Board board = readBoard(in);
            var players = new ArrayList<Replay.Participant>();
            for (int n = count(in, 4); n > 0; n--) {
                players.add(new Replay.Participant(in.readUTF(), PlayerColor.valueOf(in.readUTF()), AiLevel.valueOf(in.readUTF())));
            }
            var frames = new ArrayList<Replay.Frame>();
            for (int n = count(in, MAX_FRAMES); n > 0; n--) frames.add(readFrame(in));
            Replay replay = new Replay(board, players, frames);
            if (in.read() != -1 || !matches(entry, replay)) return Optional.empty();
            return Optional.of(replay);
        } catch (IOException | IllegalArgumentException | SecurityException ex) {
            return Optional.empty();
        }
    }

    private Path path(int id) { return directory.resolve(id + ".replay.gz"); }

    private static boolean matches(HistoryRepository.Entry entry, Replay replay) {
        var names = replay.frames().get(replay.frames().size()-1).ranking().stream()
                .map(color -> replay.players().stream().filter(p -> p.color() == color).findFirst().orElseThrow().name().trim())
                .toList();
        return replay.board().difficulty().name().equals(entry.difficulty())
                && names.size() == entry.players() && names.get(0).equals(entry.winner())
                && String.join(" > ", names).equals(entry.ranking());
    }

    private static void writeBoard(DataOutputStream out, Board board) throws IOException {
        out.writeUTF(board.difficulty().name());
        for (int row=1; row<=board.size(); row++) for (int column=1; column<=board.size(); column++) {
            var cell = board.cell(row,column);
            out.writeUTF(cell.type());
            out.writeUTF(cell.color());
            out.writeUTF(cell.symbol());
            out.writeUTF(cell.arrow());
            out.writeUTF(cell.specialColor());
        }
        for (var color : PlayerColor.values()) {
            strings(out, board.path(color));
            strings(out, board.home(color));
            for (int i=0; i<4; i++) out.writeUTF(board.castleSlot(color,i));
            out.writeUTF(board.start(color));
        }
    }

    private static Board readBoard(DataInputStream in) throws IOException {
        Difficulty difficulty = Difficulty.valueOf(in.readUTF());
        int size = difficulty.boardSize();
        Map<String,Board.Cell> cells = new HashMap<>();
        for (int row=1; row<=size; row++) for (int column=1; column<=size; column++) {
            var cell = new Board.Cell(row,column,in.readUTF(),in.readUTF(),in.readUTF(),in.readUTF(),in.readUTF());
            cells.put(cell.id(),cell);
        }
        var paths = new EnumMap<PlayerColor,List<String>>(PlayerColor.class);
        var homes = new EnumMap<PlayerColor,List<String>>(PlayerColor.class);
        var castles = new EnumMap<PlayerColor,List<String>>(PlayerColor.class);
        var starts = new EnumMap<PlayerColor,String>(PlayerColor.class);
        for (var color : PlayerColor.values()) {
            paths.put(color,strings(in,400));
            homes.put(color,strings(in,400));
            castles.put(color,List.of(in.readUTF(),in.readUTF(),in.readUTF(),in.readUTF()));
            starts.put(color,in.readUTF());
            if (castles.get(color).stream().anyMatch(id -> !cells.containsKey(id))) {
                throw new IOException("Invalid replay castle");
            }
        }
        return new Board(difficulty,size,cells,paths,homes,castles,starts);
    }

    private static void writeFrame(DataOutputStream out, Replay.Frame f) throws IOException {
        out.writeUTF(f.kind().name()); out.writeLong(f.elapsedMillis()); out.writeInt(f.turn());
        out.writeInt(f.currentPlayer()); out.writeUTF(f.phase().name());
        out.writeBoolean(f.dice() != null);
        if (f.dice() != null) {
            var d = f.dice();
            out.writeInt(d.regular()); out.writeInt(d.special()); out.writeInt(d.multiplier());
            out.writeInt(d.movement()); out.writeBoolean(d.ultraBoost());
        }
        out.writeInt(f.extraRolls()); out.writeBoolean(f.ultraBoostPending()); out.writeInt(f.sixes());
        out.writeInt(f.positions().size());
        for (int position : f.positions()) out.writeInt(position);
        strings(out, f.ranking().stream().map(Enum::name).toList());
        strings(out, f.doubleBlocks()); strings(out, f.messages());
        out.writeInt(f.motions().size());
        for (var motion : f.motions()) {
            out.writeInt(motion.piece()); strings(out, motion.route()); out.writeBoolean(motion.captured());
        }
        out.writeBoolean(f.answer() != null);
        if (f.answer() != null) {
            var q = f.answer().question();
            out.writeUTF(q.id()); out.writeUTF(q.difficulty().name()); text(out,q.category()); text(out,q.text());
            out.writeInt(q.choices().size());
            for (String choice : q.choices()) text(out,choice);
            out.writeInt(q.correctIndex()); text(out,q.explanation());
            out.writeInt(f.answer().selected()); out.writeBoolean(f.answer().correct());
        }
        out.writeUTF(f.surprise() == null ? "" : f.surprise().name());
        out.writeInt(f.opponentDie());
    }

    private static Replay.Frame readFrame(DataInputStream in) throws IOException {
        var kind = Replay.Kind.valueOf(in.readUTF());
        long elapsed = in.readLong();
        int turn = in.readInt(), current = in.readInt();
        var phase = GameSession.Phase.valueOf(in.readUTF());
        DiceRoll dice = in.readBoolean()
                ? new DiceRoll(in.readInt(),in.readInt(),in.readInt(),in.readInt(),in.readBoolean()) : null;
        int extras = in.readInt();
        boolean ultra = in.readBoolean();
        int sixes = in.readInt();
        var positions = new ArrayList<Integer>();
        for (int n=count(in,16); n>0; n--) positions.add(in.readInt());
        var ranking = strings(in,4).stream().map(PlayerColor::valueOf).toList();
        var blocks = strings(in,400);
        var notes = strings(in,4096);
        var motions = new ArrayList<Replay.Motion>();
        for (int n=count(in,16); n>0; n--) {
            motions.add(new Replay.Motion(in.readInt(),strings(in,400),in.readBoolean()));
        }
        Replay.Answer answer = null;
        if (in.readBoolean()) {
            String id = in.readUTF();
            var difficulty = Difficulty.valueOf(in.readUTF());
            String category = text(in), prompt = text(in);
            var choices = new ArrayList<String>();
            for (int n=count(in,4); n>0; n--) choices.add(text(in));
            var question = new Question(id,difficulty,category,prompt,choices,in.readInt(),text(in));
            answer = new Replay.Answer(question,in.readInt(),in.readBoolean());
        }
        String effect = in.readUTF();
        return new Replay.Frame(kind,elapsed,turn,current,phase,dice,extras,ultra,sixes,positions,ranking,
                blocks,notes,motions,answer,effect.isEmpty()?null:SurpriseOutcome.valueOf(effect),in.readInt());
    }

    private static void strings(DataOutputStream out, List<String> list) throws IOException {
        out.writeInt(list.size());
        for (String value : list) out.writeUTF(value);
    }

    private static List<String> strings(DataInputStream in, int max) throws IOException {
        var values = new ArrayList<String>();
        for (int n=count(in,max); n>0; n--) values.add(in.readUTF());
        return values;
    }

    private static int count(DataInputStream in, int max) throws IOException {
        int n = in.readInt();
        if (n < 0 || n > max) throw new IOException("Invalid replay count");
        return n;
    }

    private static void text(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 1_048_576) throw new IOException("Replay text exceeds storage limit");
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static String text(DataInputStream in) throws IOException {
        byte[] bytes = new byte[count(in,1_048_576)];
        in.readFully(bytes);
        return new String(bytes,StandardCharsets.UTF_8);
    }
}
