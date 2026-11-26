package model;

import java.nio.file.Files;
import java.util.*;

public final class ReplayTests {
    public static void main(String[] args) throws Exception {
        var home = Files.createTempDirectory("cheetah-replay-");
        try {
            var csv = home.resolve("history.csv");
            Files.writeString(csv, HistoryRepository.HEADER + "\n5,easy,01/01/2025;01:02:03,Old,Match,,,00:03:00\n");
            var history = new HistoryRepository(csv);
            var repository = new ReplayRepository(history);
            var old = history.readEntries().get(0);
            check(old.gameId()==5 && repository.load(old).isEmpty(), "Old history remains readable without replay");
            var engine = new GameEngine(new BoardRepository());
            var scene = CaptureScenario.create(engine,Difficulty.HARD,PlayerColor.RED,0,
                    PlayerColor.BLUE,1,CaptureScenario.Direction.East);
            var game = scene.session();
            game.current().pieces().get(2).place(game.board(),0);
            game.current().pieces().get(3).place(game.board(),0);
            var recorder = new ReplayRecorder(game,Map.of(PlayerColor.BLUE,AiLevel.HARD));
            try {
                recorder.complete();
                throw new AssertionError("An unfinished game cannot become a replay");
            } catch (IllegalStateException expected) {}

            var random = new Random() {
                @Override public int nextInt(int bound) { return bound-1; }
            };
            var roll = engine.roll(game,random);
            recorder.roll(roll);
            var capture = scene.capture(engine);
            recorder.record(Replay.Kind.MOVE,capture,null,null,0);
            var question = new Question("fixture",Difficulty.MEDIUM,"קטגוריה","שאלה?",
                    List.of("א","ב","ג","ד"),1,"הסבר");
            var before = CaptureMove.observe(game,scene.attacker().id());
            engine.resolveQuestion(game,scene.attacker().id(),question,true);
            recorder.record(Replay.Kind.QUESTION,before.resolved(),new Replay.Answer(question,1,true),null,0);
            engine.applySurprise(game,scene.attacker().id(),SurpriseOutcome.LOSE_NEXT_TURN,random);
            recorder.record(Replay.Kind.SURPRISE,null,null,SurpriseOutcome.LOSE_NEXT_TURN,0);
            engine.completeMove(game);
            recorder.record(Replay.Kind.TURN);
            check(game.phase()==GameSession.Phase.ROLL,"Extra roll follows recorded result");
            for (var piece : game.current().pieces()) {
                engine.finishPieceDirectly(game,piece);
                recorder.record(Replay.Kind.SURPRISE,null,null,SurpriseOutcome.DIRECT_FINISH,0);
            }
            var replay = recorder.complete();
            var receipt = history.save(game);
            repository.save(receipt,replay);
            var csvBefore = Files.readString(csv);
            var reopened = new HistoryRepository(csv);
            var entries = reopened.readEntries();
            check(entries.get(0).equals(receipt) && entries.get(1).equals(old), "Existing schema and rows persist");
            var reader = new ReplayRepository(reopened);
            check(reader.available(entries).equals(Set.of(receipt.gameId())), "Only completed replay is offered");
            var loaded = reader.load(entries.get(0)).orElseThrow();
            check(loaded.players().equals(replay.players()) && loaded.frames().equals(replay.frames()), "Frames and AI roles round-trip");
            check(new HashSet<>(loaded.board().cells()).equals(new HashSet<>(game.board().cells())), "Exact board round-trips");
            check(loaded.frames().get(0).doubleBlocks().contains(game.board().start(PlayerColor.RED)), "Double Block recorded");
            check(loaded.frames().stream().flatMap(frame -> frame.messages().stream())
                    .anyMatch(message -> message.startsWith("Double Block cleared:")), "Double Block transition recorded");
            check(loaded.frames().get(1).dice().equals(roll), "Regular, special and multiplier retained");
            check(loaded.frames().get(2).motions().stream().anyMatch(Replay.Motion::captured), "Victim recorded");
            check(loaded.frames().get(2).motions().stream().anyMatch(motion -> !motion.captured()
                    && motion.route().equals(capture.route().stream().map(p -> "R"+p.row()+"-C"+p.column()).toList())),
                    "Exact capture route retained");
            check(loaded.frames().get(3).answer().question().equals(question), "Question is reconstructed without the bank");
            check(loaded.frames().get(4).surprise()==SurpriseOutcome.LOSE_NEXT_TURN, "Actual effect recorded");
            var end = loaded.project(loaded.frames().size()-1);
            var initial = loaded.project(0);
            check(end.ranking().equals(game.ranking()) && initial.ranking().isEmpty(), "Independent rankings after seeking");
            check(end != game && initial != game && Files.readString(csv).equals(csvBefore), "Seeking cannot write history or change live game");
            Files.writeString(csv.resolveSibling("history.csv.replays").resolve(receipt.gameId()+".replay.gz"),"broken");
            check(reader.load(receipt).isEmpty(), "Corrupt sidecar is unavailable");
            check(Files.readString(csv).equals(csvBefore), "Corrupt replay cannot modify history");
            System.out.println("Replay tests passed.");
        } finally {
            try (var paths = Files.walk(home)) {
                for (var path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }

    private static void check(boolean value,String message) {
        if (!value) throw new AssertionError(message);
    }
}
