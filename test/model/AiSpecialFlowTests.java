package model;

import controller.GameController;
import view.GameFrame;
import view.Theme;

import javax.swing.JDialog;
import javax.swing.SwingUtilities;
import java.awt.EventQueue;
import java.awt.Toolkit;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Desktop integration suite. Scripted dice are fixtures only; the production scheduler drives all AI actions. */
public final class AiSpecialFlowTests {
    private static final List<AiLevel> LEVELS = List.of(AiLevel.EASY, AiLevel.MEDIUM, AiLevel.HARD);
    private static final String PROMPT = "Which visible choice matches controller?";
    private static final List<String> CHOICES = List.of("Apple", "Controller", "Silver", "Blue");
    private static final long SEED = 431;
    private final GameEngine engine = new GameEngine(new BoardRepository());
    private final GameFrame frame = new GameFrame();
    private final Script dice = new Script();
    private final HistoryRepository history;
    private final GameController controller;
    private GameSession game;
    private AiLevel level;
    private int cases;

    private AiSpecialFlowTests(java.nio.file.Path directory) {
        history = new HistoryRepository(directory.resolve("history.csv"));
        controller = new GameController(frame, engine, history, dice);
        ((javax.swing.Timer)get(controller,"aiDelay")).setInitialDelay(15);
        frame.addNotify();
    }

    public static void main(String[] args) throws Exception {
        var directory = Files.createTempDirectory("ai-special-flow-");
        try {
            SwingUtilities.invokeAndWait(() -> {
                Theme.install();
                var suite = new AiSpecialFlowTests(directory);
                try { suite.run(); } finally { suite.frame.dispose(); }
            });
        } finally {
            try (var paths = Files.walk(directory)) {
                for (var path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }

    private void run() {
        for (Difficulty board : Difficulty.values()) for (int i=0;i<LEVELS.size();i++) {
            AiLevel ai = LEVELS.get(i);
            Difficulty color = Difficulty.values()[(board.ordinal()+i)%3];
            question(board,ai,color,true);
            question(board,ai,color,false);
            captureOrBlock(board,ai,false);
            captureOrBlock(board,ai,true);
            sixes(board,ai);
        }
        for (int i=0;i<LEVELS.size();i++) for (var outcome : SurpriseOutcome.values()) {
            surprise(Difficulty.values()[(i+outcome.ordinal())%3],LEVELS.get(i),outcome);
        }
        for (int i=0;i<LEVELS.size();i++) {
            Difficulty board = Difficulty.values()[i];
            AiLevel ai = LEVELS.get(i);
            chain(board,ai);
            forcedChain(board,ai,false);
            forcedChain(board,ai,true);
            finish(board,ai);
        }
        System.out.println("AI special-flow cases passed: "+cases+
                " (all 3 boards and levels; questions, 6 surprises, forced chains, captures, blocks, sixes and rankings).");
    }

    private void question(Difficulty board,AiLevel ai,Difficulty color,boolean correct) {
        var fixture = fixture(board);
        var red = fixture.path(PlayerColor.RED);
        fixture.mark(red.get(10),"question",switch(color){case EASY->"green";case MEDIUM->"yellow";case HARD->"red";});
        begin(fixture,ai,script(board,1), answers(ai,1,correct));
        hero(PlayerColor.RED,0).place(game.board(),9);
        handOff("question "+board+" "+ai+" "+color+" correct="+correct);
        int reward=color.ordinal()+1;
        check(hero(PlayerColor.RED,0).pathIndex()==10+(correct?reward:-reward),"Correct question displacement");
        var answer = frames().stream().filter(f->f.answer()!=null).findFirst().orElseThrow().answer();
        check(answer.correct()==correct && answer.question().difficulty()==color,"Actual answer and square color recorded");
    }

    private void surprise(Difficulty board,AiLevel ai,SurpriseOutcome effect) {
        var fixture = fixture(board);
        fixture.mark(fixture.path(PlayerColor.RED).get(10),"surprise","");
        int draw = switch(effect) {
            case DIRECT_FINISH -> 0; case OPPONENT_BACKWARD -> 10; case RETURN_TO_CASTLE -> 20;
            case BACKWARD_THREE -> 30; case LOSE_NEXT_TURN -> 60; case ULTRABOOST -> 90;
        };
        begin(fixture,ai,script(board,1,draw,0,1),answers(ai,1,true));
        hero(PlayerColor.RED,0).place(game.board(),9);
        if (effect==SurpriseOutcome.OPPONENT_BACKWARD) hero(PlayerColor.BLUE,0).place(game.board(),22);
        handOff("surprise "+effect+" "+board+" "+ai);
        Piece piece=hero(PlayerColor.RED,0);
        check(frames().stream().anyMatch(f->f.surprise()==effect),"Recorded actual surprise");
        switch(effect) {
            case DIRECT_FINISH -> check(piece.state()==Piece.State.FINISHED,"Direct finish");
            case RETURN_TO_CASTLE -> check(piece.state()==Piece.State.CASTLE,"Return to castle");
            case BACKWARD_THREE -> check(piece.pathIndex()==7,"Backward Three");
            case OPPONENT_BACKWARD -> {
                check(hero(PlayerColor.BLUE,0).pathIndex()==21,"Automatic opponent target and backward die");
                check(frames().stream().anyMatch(f->f.kind()==Replay.Kind.OPPONENT && f.opponentDie()==1),"Opponent die recorded");
            }
            case ULTRABOOST -> check(frames().stream().anyMatch(f->f.kind()==Replay.Kind.ROLL && f.dice().ultraBoost()
                    && f.dice().regular()==1 && f.dice().special()==2),"Immediate UltraBoost rolls automatically");
            case LOSE_NEXT_TURN -> {
                dice.set(script(board,1));
                controller.roll();
                controller.pass();
                check(game.current().color()==PlayerColor.BLUE && game.events().stream()
                        .anyMatch(s->s.contains("forfeits their turn")),"Skip used exactly at next turn");
            }
        }
    }

    private void chain(Difficulty board,AiLevel ai) {
        var fixture=fixture(board);
        fixture.mark(fixture.path(PlayerColor.RED).get(10),"question","green");
        fixture.mark(fixture.path(PlayerColor.RED).get(11),"surprise","");
        begin(fixture,ai,script(board,1,20),answers(ai,1,true));
        hero(PlayerColor.RED,0).place(game.board(),9);
        handOff("question into surprise");
        check(hero(PlayerColor.RED,0).state()==Piece.State.CASTLE && frames().stream()
                .anyMatch(f->f.kind()==Replay.Kind.QUESTION),"Question triggers surprise without interaction");

        fixture=fixture(board);
        fixture.mark(fixture.path(PlayerColor.RED).get(10),"surprise","");
        fixture.mark(fixture.path(PlayerColor.RED).get(7),"question","green");
        begin(fixture,ai,script(board,1,30),answers(ai,1,true));
        hero(PlayerColor.RED,0).place(game.board(),9);
        handOff("surprise into question");
        check(hero(PlayerColor.RED,0).pathIndex()==8,"Surprise triggers question without interaction");
    }

    private void forcedChain(Difficulty board,AiLevel ai,boolean bonus) {
        var fixture=fixture(board);
        fixture.mark(fixture.path(PlayerColor.RED).get(10),"surprise","");
        fixture.mark(fixture.path(PlayerColor.BLUE).get(21),bonus?"surprise":"question",bonus?"":"green");
        begin(fixture,ai,script(board,1,10,0,90,0,1),answers(ai,2,true));
        hero(PlayerColor.RED,0).place(game.board(),9);
        hero(PlayerColor.BLUE,0).place(game.board(),22);
        handOff("AI forced chain affecting human "+(bonus?"UltraBoost":"question"));
        check(!game.hasTemporaryEffectPlayer() && hero(PlayerColor.BLUE,0).pathIndex()==22,
                "Forced move resolved and human control handed back");
        check(frames().stream().anyMatch(f->bonus ? f.kind()==Replay.Kind.ROLL && f.dice().ultraBoost()
                && f.currentPlayer()==1 : f.answer()!=null && f.currentPlayer()==1 && f.answer().correct()),
                "AI resolves the human piece's temporary effect");
    }

    private void captureOrBlock(Difficulty board,AiLevel ai,boolean block) {
        var fixture=fixture(board);
        var red=fixture.path(PlayerColor.RED);
        var blue=fixture.path(PlayerColor.BLUE);
        int target=java.util.stream.IntStream.range(5,red.size()-8).filter(i->blue.contains(red.get(i))
                && !fixture.original.isSafe(red.get(i))).findFirst().orElseThrow();
        int other=blue.indexOf(red.get(target));
        begin(fixture,ai,script(board,block?3:1),answers(ai,1,true));
        hero(PlayerColor.RED,0).place(game.board(),target-(block?2:1));
        hero(PlayerColor.BLUE,0).place(game.board(),other);
        if (block) hero(PlayerColor.BLUE,1).place(game.board(),other);
        handOff(block?"Double Block":"capture");
        check(hero(PlayerColor.RED,0).pathIndex()==target-(block?1:0),"Legal landing recorded");
        check(hero(PlayerColor.BLUE,0).state()==(block?Piece.State.TRACK:Piece.State.CASTLE),"Capture or block");
        if (!block) check(frames().stream().flatMap(f->f.motions().stream()).anyMatch(Replay.Motion::captured),"Actual capture");
    }

    private void sixes(Difficulty board,AiLevel ai) {
        var fixture=fixture(board);
        begin(fixture,ai,script(board,6,script(board,1)),answers(ai,1,true));
        handOff("extra roll");
        var rolls=frames().stream().filter(f->f.kind()==Replay.Kind.ROLL).toList();
        check(rolls.size()==2 && rolls.get(0).extraRolls()==1 && rolls.get(1).dice().regular()==1,"AI takes earned extra roll");
        var first=rolls.get(0).dice();
        check(first.special()==(board==Difficulty.EASY?1:2) && first.ultraBoost()==(board==Difficulty.HARD),
                "Existing regular, Boost, or UltraBoost roll");
        begin(fixture,ai,script(board,6),answers(ai,1,true));
        game.consecutiveSixes(2);
        handOff("third consecutive six");
        check(game.events().stream().anyMatch(e->e.contains("third roll canceled")) && game.consecutiveSixes()==0,
                "Third six cancels AI turn");
    }

    private void finish(Difficulty board,AiLevel ai) {
        var fixture=fixture(board);
        begin(fixture,ai,script(board,1),answers(ai,1,true));
        int end=game.board().path(PlayerColor.RED).size()-1;
        for(int i=0;i<3;i++)hero(PlayerColor.RED,i).place(game.board(),end);
        hero(PlayerColor.RED,3).place(game.board(),end-1);
        frame.showGame();
        await(() -> {
            if (game.phase()!=GameSession.Phase.FINISHED) return false;
            for(var window:frame.getOwnedWindows()) {
                if(window instanceof JDialog dialog && dialog.isShowing() && dialog.getTitle().equals(view.Messages.get("CHEETAH LUDO  •  FINAL RANKINGS"))) {
                    // Rankings are terminal; no gameplay dialog is dismissed by the test.
                    dialog.dispose();
                    return true;
                }
            }
            return false;
        },"match completion and terminal rankings");
        check(game.ranking().equals(List.of(PlayerColor.RED,PlayerColor.BLUE)),"AI ranking order");
        try {
            var entry=history.readEntries().get(0);
            var replay=new ReplayRepository(history).load(entry).orElseThrow();
            if (board==Difficulty.EASY) {
                frame.showReplay(replay);
                Object page=get(frame,"replayPage");
                ((javax.swing.JSlider)get(page,"timeline")).setValue(1);
                String text=((javax.swing.JTextArea)get(page,"details")).getText();
                check(text.contains(view.Messages.get("Regular die: 1")),"Easy replay shows a single die");
            }
        } catch (java.io.IOException ex) { throw new AssertionError(ex); }
    }

    private void begin(Fixture fixture,AiLevel ai,int[] script,List<Question> questions) {
        frame.showMainMenu();
        level=ai;
        game=new GameSession(fixture.board(),List.of(new Player("Computer",PlayerColor.RED),new Player("Human",PlayerColor.BLUE)));
        @SuppressWarnings("unchecked") var roles=(Map<PlayerColor,AiLevel>)get(controller,"playerControls");
        roles.clear();
        roles.put(PlayerColor.RED,ai);
        ((Map<?,?>)get(controller,"delegatedEffects")).clear();
        ((Random)get(controller,"aiRandom")).setSeed(SEED);
        dice.set(script);
        set(controller,"session",game);
        set(controller,"matchSaved",false);
        set(controller,"recorder",new ReplayRecorder(game,roles));
        set(controller,"questions",new QuestionBank(new Random(1),questions));
        frame.setPlayerControls(roles);
        frame.render(game);
    }

    private void handOff(String scenario) {
        frame.showGame();
        await(() -> game.current().color()==PlayerColor.BLUE && game.phase()==GameSession.Phase.ROLL
                && !game.hasTemporaryEffectPlayer(),scenario);
        check(!((javax.swing.Timer)get(controller,"aiDelay")).isRunning(),"Human control restored after "+scenario);
    }

    private void await(BooleanSupplier done,String scenario) {
        var loop=Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();
        long timeout=System.nanoTime()+9_000_000_000L;
        boolean[] passed={false};
        var poll=new javax.swing.Timer(15,event->{
            if(done.getAsBoolean()) {passed[0]=true;loop.exit();}
            else if(System.nanoTime()>=timeout) {
                for(var window:frame.getOwnedWindows()) window.dispose();
                frame.showMainMenu();
                loop.exit();
            }
        });
        poll.start();
        loop.enter();
        poll.stop();
        check(passed[0],"AI did not complete automatically: "+scenario+" ("+level+") "+game.events());
        cases++;
    }

    private Piece hero(PlayerColor color,int index) {return game.findPlayer(color).pieces().get(index);}
    @SuppressWarnings("unchecked")
    private List<Replay.Frame> frames() {return (List<Replay.Frame>)get(get(controller,"recorder"),"frames");}

    private static List<Question> answers(AiLevel ai,int selections,boolean correct) {
        return Arrays.stream(Difficulty.values()).map(d->{
            var prediction=new Random(SEED);
            if(ai==AiLevel.EASY) for(int i=0;i<selections;i++) prediction.nextInt(1);
            int chosen=AiMoveStrategy.answer(ai,d,PROMPT,CHOICES,prediction);
            return new Question("q-"+d,d,"Fixture",PROMPT,CHOICES,correct?chosen:(chosen+1)%4,"Fixture explanation.");
        }).toList();
    }

    private static int[] script(Difficulty board,int regular,int... tail) {
        int prefix=board==Difficulty.EASY?1:2;
        int[] values=new int[prefix+tail.length];
        values[0]=regular-1;
        if(prefix==2) values[1]=1;
        System.arraycopy(tail,0,values,prefix,tail.length);
        return values;
    }

    private Fixture fixture(Difficulty difficulty) {
        return new Fixture(engine.start(difficulty,List.of("Computer","Human")).board());
    }

    private static final class Fixture {
        final Board original;
        final Map<String,Board.Cell> cells=new HashMap<>();
        Fixture(Board source) {
            original=source;
            for(var c:source.cells()) {
                cells.put(c.id(),c);
                if("question".equals(c.symbol())||"surprise".equals(c.symbol()))mark(c.id(),"","");
            }
        }
        List<String> path(PlayerColor color){return original.path(color);}
        void mark(String id,String symbol,String color) {
            var c=original.cell(id);
            cells.put(id,new Board.Cell(c.row(),c.column(),c.type(),c.color(),symbol,c.arrow(),color));
        }
        Board board(){return original.withCells(cells);}
    }

    private static final class Script extends Random {
        private int[] values={}; private int index;
        void set(int[] values){this.values=values;index=0;}
        @Override public int nextInt(int bound){
            int value=index<values.length?values[index++]:0;
            if(value>=bound)throw new IllegalStateException("Invalid scripted draw: "+value+" / "+bound);
            return value;
        }
    }
    private static Field field(Object object,String name) {
        try {var field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field;}
        catch(ReflectiveOperationException ex){throw new AssertionError(ex);}
    }
    private static Object get(Object object,String name){
        try{return field(object,name).get(object);}catch(IllegalAccessException ex){throw new AssertionError(ex);}
    }
    private static void set(Object object,String name,Object value){
        try{field(object,name).set(object,value);}catch(IllegalAccessException ex){throw new AssertionError(ex);}
    }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
