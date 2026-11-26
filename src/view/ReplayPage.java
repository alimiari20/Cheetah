package view;

import model.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static view.Theme.Tone.*;

/** Plays immutable frames using a dedicated board with no live game actions. */
final class ReplayPage extends UiKit.GradientPage {
    private static final double[] SPEEDS = {.5, 1, 2, 4};
    private final BoardPanel board = new BoardPanel();
    private final JLabel position = UiKit.goldLabel("");
    private final JTextArea details = UiKit.area("",12);
    private final JTextArea players = UiKit.area("",5);
    private final JSlider timeline = new JSlider();
    private final JButton play = Theme.button("PLAY REPLAY",true);
    private final JButton pause = Theme.button("PAUSE REPLAY",false);
    private final JButton previous = Theme.button("PREVIOUS EVENT",false);
    private final JButton next = Theme.button("NEXT EVENT",false);
    private final JComboBox<String> speed = new JComboBox<>(new String[]{"0.5×","1×","2×","4×"});
    private final Timer timer = new Timer(0,event -> advance());
    private Replay replay;
    private GameSession projection;
    private int index;
    private boolean changingSlider;
    private boolean playing;

    ReplayPage(Runnable back) {
        super(new BorderLayout(10,8));
        setBorder(new EmptyBorder(10,18,12,18));
        JPanel top = UiKit.transparent(new BorderLayout(8,0));
        JButton leave = MenuStyle.button("BACK TO GAME HISTORY",false,back);
        top.add(leave,BorderLayout.LINE_START);
        top.add(UiKit.title("GAME REPLAY",23),BorderLayout.CENTER);
        add(top,BorderLayout.NORTH);
        board.setOpaque(false);
        JPanel arena = MenuStyle.card(new BorderLayout(),8);
        arena.add(board);
        add(arena,BorderLayout.CENTER);
        JPanel info = MenuStyle.card(new BorderLayout(0,8),10);
        info.setPreferredSize(new Dimension(300,350));
        details.setEditable(false);
        players.setEditable(false);
        info.add(position,BorderLayout.NORTH);
        info.add(new JScrollPane(details),BorderLayout.CENTER);
        info.add(new JScrollPane(players),BorderLayout.SOUTH);
        add(info,BorderLayout.LINE_END);

        speed.setSelectedIndex(1);
        timer.setRepeats(false);
        play.addActionListener(event -> {
            if (replay == null || playing) return;
            if (index == replay.frames().size()-1) display(0);
            playing = true;
            syncControls();
            schedule();
        });
        pause.addActionListener(event -> stop());
        previous.addActionListener(event -> seek(index-1));
        next.addActionListener(event -> seek(index+1));
        speed.addActionListener(event -> {
            if (timer.isRunning()) schedule();
        });
        timeline.addChangeListener(event -> {
            if (!changingSlider && replay != null) seek(timeline.getValue());
        });
        JButton restart = Theme.button("RESTART REPLAY",false);
        restart.addActionListener(event -> seek(0));

        JPanel transport = UiKit.transparent(new GridLayout(1,5,5,0));
        for (JButton button : List.of(play,pause,restart,previous,next)) transport.add(button);
        JPanel scrub = UiKit.transparent(new BorderLayout(10,0));
        scrub.add(timeline,BorderLayout.CENTER);
        JPanel rate = UiKit.transparent(new BorderLayout(6,0));
        JLabel label = UiKit.sub("Playback speed");
        label.setLabelFor(speed);
        rate.add(label,BorderLayout.LINE_START);
        rate.add(speed,BorderLayout.LINE_END);
        scrub.add(rate,BorderLayout.LINE_END);
        JPanel bottom = UiKit.transparent(new BorderLayout(0,4));
        bottom.add(scrub,BorderLayout.NORTH);
        bottom.add(transport,BorderLayout.SOUTH);
        add(bottom,BorderLayout.SOUTH);
        Messages.bind(this,() -> {
            timeline.getAccessibleContext().setAccessibleName(Messages.get("Replay timeline"));
            updateText();
        });
    }

    void open(Replay value) {
        stop();
        replay = value;
        changingSlider = true;
        timeline.setMaximum(replay.frames().size()-1);
        changingSlider = false;
        display(0);
    }

    void close() {
        stop();
        replay = null;
        projection = null;
        board.setSession(null);
    }

    private void seek(int frame) {
        stop();
        if (replay != null) display(Math.max(0,Math.min(frame,replay.frames().size()-1)));
    }

    private void stop() {
        playing = false;
        timer.stop();
        board.cancelCapture();
        syncControls();
    }

    private void schedule() {
        timer.setInitialDelay((int)(850 / SPEEDS[speed.getSelectedIndex()]));
        timer.restart();
    }

    private void advance() {
        if (!playing || replay == null) return;
        display(index+1);
        animateCapture();
        if (!playing || replay == null) return;
        if (index == replay.frames().size()-1) stop();
        else schedule();
    }

    private void animateCapture() {
        var motions = replay.frames().get(index).motions();
        var victims = new ArrayList<Piece>();
        Replay.Motion attacker = null;
        for (var motion : motions) {
            if (motion.captured()) victims.add(Replay.piece(projection,motion.piece()));
            else attacker = motion;
        }
        if (!victims.isEmpty() && attacker != null) {
            var route = attacker.route().stream().map(Board.Position::parse).toList();
            board.playCapture(new CaptureMove(Replay.piece(projection,attacker.piece()),route,victims),
                    SPEEDS[speed.getSelectedIndex()]);
        }
    }

    private void display(int frame) {
        index = frame;
        projection = replay.project(index);
        board.setSession(projection);
        changingSlider = true;
        timeline.setValue(index);
        changingSlider = false;
        updateText();
        syncControls();
    }

    private void syncControls() {
        play.setEnabled(replay != null && !playing);
        pause.setEnabled(playing);
        previous.setEnabled(replay != null && index>0);
        next.setEnabled(replay != null && index<replay.frames().size()-1);
    }

    private void updateText() {
        if (replay == null) return;
        var frame = replay.frames().get(index);
        position.setText(Messages.get("Event "+(index+1)+" of "+replay.frames().size()+" · Turn "+frame.turn()));
        var text = new StringBuilder(Messages.get(switch (frame.kind()) {
            case START -> "Match setup";
            case ROLL -> "Recorded dice roll";
            case PASS -> "Passed turn";
            case MOVE -> "Recorded move";
            case QUESTION -> "Recorded question";
            case SURPRISE -> "Recorded surprise";
            case OPPONENT -> "Recorded opponent move";
            case TURN -> "Turn continued";
            case FINISH -> "Final rankings";
        }));
        for (String message:frame.messages()) line(text,Messages.get(message));
        if (frame.kind()==Replay.Kind.ROLL && frame.dice()!=null) {
            var dice=frame.dice();
            if (replay.board().difficulty()==Difficulty.EASY && !dice.ultraBoost())
                line(text,Messages.get("Regular die: "+dice.regular()));
            else line(text,Messages.get((dice.ultraBoost()?"UltraBoost":"Boost")+" dice: "+dice.regular()
                    +" / "+dice.special()+" · ×"+dice.multiplier()+" · Move "+dice.movement()));
        }
        if (frame.opponentDie()>0) line(text,Messages.get("Opponent die: "+frame.opponentDie()));
        if (frame.extraRolls()>0) line(text,Messages.get("Extra rolls queued: "+frame.extraRolls()));
        if (frame.ultraBoostPending()) line(text,Messages.get("UltraBoost pending"));
        if (!frame.doubleBlocks().isEmpty())
            line(text,Messages.get("Double Blocks: "+String.join(", ",frame.doubleBlocks())));
        if (frame.surprise()==SurpriseOutcome.OPPONENT_BACKWARD && frame.opponentDie()==0)
            line(text,Messages.get("Surprise: opponent moves backward"));
        for (var motion:frame.motions()) {
            var player=replay.players().get(motion.piece()/4);
            line(text,Messages.get((motion.captured()?"Captured piece ":"Piece ")
                    +(motion.piece()%4+1)+" · "+player.name())+"\n"+String.join(" → ",motion.route()));
        }
        if (frame.answer()!=null) {
            var answer=frame.answer();
            line(text,answer.question().text());
            for (int i=0; i<answer.question().choices().size(); i++)
                line(text,(i+1)+". "+answer.question().choices().get(i));
            String selected=answer.selected()<0?Messages.get("No answer recorded"):answer.question().choices().get(answer.selected());
            line(text,Messages.get("Selected answer: "+selected));
            line(text,Messages.get(answer.correct()?"Correct answer selected":"Incorrect answer selected"));
            line(text,Messages.get("Correct answer: "+answer.question().choices().get(answer.question().correctIndex())));
            line(text,answer.question().explanation());
        }
        details.setText(text.toString());
        details.setCaretPosition(0);
        var roster=new StringBuilder(Messages.get("Replay players"));
        var shownPlayers=frame.kind()==Replay.Kind.FINISH ? frame.ranking().stream()
                .map(color -> replay.players().stream().filter(p -> p.color()==color).findFirst().orElseThrow()).toList()
                : replay.players();
        for (var player:shownPlayers) {
            int rank=frame.ranking().indexOf(player.color())+1;
            line(roster,(rank>0?"#"+rank+"  ":"")+player.name()+" · "+Messages.get(player.color().label())
                    +" · "+Messages.get(player.control().toString()));
        }
        players.setText(roster.toString());
        players.setCaretPosition(0);
    }

    private static void line(StringBuilder text, String value) { text.append('\n').append(value); }
}
