package view;

import model.*;
import java.awt.*;
import javax.swing.*;

/** Controls mounted only when Main receives --capture-lab. */
final class CaptureLab extends JPanel {
    private static final String[] HEROES = {"Knight","Wizard","Archer","Lancer"};
    private final JComboBox<String> attack = new JComboBox<>(HEROES), defend = new JComboBox<>(HEROES);
    private final JComboBox<PlayerColor> color = new JComboBox<>(PlayerColor.values()), other = new JComboBox<>();
    private final JComboBox<Difficulty> difficulty = new JComboBox<>(Difficulty.values());
    private final JComboBox<CaptureScenario.Direction> direction = new JComboBox<>(CaptureScenario.Direction.values());
    private final JComboBox<String> speed = new JComboBox<>(new String[]{"1×","0.5×","0.25×"});
    private final JButton play = new JButton("Play capture");
    private final BoardPanel board;
    private final GameEngine engine;
    private CaptureScenario scenario;

    CaptureLab(GameEngine engine,BoardPanel board) {
        super(new BorderLayout());
        this.engine=engine; this.board=board;
        JPanel controls = new JPanel(new GridLayout(0,1,4,4));
        controls.setBorder(BorderFactory.createEmptyBorder(12,12,12,12));
        field(controls,"Attacker",attack); field(controls,"Attacking color",color);
        field(controls,"Defender",defend); field(controls,"Defending color",other);
        field(controls,"Board",difficulty); field(controls,"Attack direction",direction);
        field(controls,"Playback speed",speed);
        controls.add(play);
        JPanel sidebar=new JPanel(new BorderLayout());
        sidebar.add(controls,BorderLayout.NORTH);
        add(sidebar,BorderLayout.WEST); add(board,BorderLayout.CENTER);
        defend.setSelectedIndex(1);
        direction.setSelectedItem(CaptureScenario.Direction.East);
        resetColors();
        for (JComboBox<?> box : new JComboBox<?>[]{attack,defend,other,difficulty,direction})
            box.addActionListener(e -> stage());
        color.addActionListener(e -> {resetColors(); stage();});
        play.addActionListener(e -> {
            stage();
            for(Component c:controls.getComponents()) c.setEnabled(false);
            try {
                CaptureMove move = scenario.capture(engine);
                if (board.playCapture(move, Math.scalb(1.0,-speed.getSelectedIndex())))
                    engine.completeMove(scenario.session());
            } finally {
                for(Component c:controls.getComponents()) c.setEnabled(true);
                play.setText("Replay capture");
            }
        });
        stage();
    }

    private void resetColors() {
        PlayerColor previous=(PlayerColor)other.getSelectedItem();
        DefaultComboBoxModel<PlayerColor> choices=new DefaultComboBoxModel<>();
        for (PlayerColor c:PlayerColor.values()) if(c!=color.getSelectedItem()) choices.addElement(c);
        if (previous!=null && previous!=color.getSelectedItem()) choices.setSelectedItem(previous);
        other.setModel(choices);
    }
    private static void field(JPanel panel,String name,JComboBox<?> control) {
        JLabel label=new JLabel(name);label.setLabelFor(control);panel.add(label);panel.add(control);
    }
    private void stage() {
        scenario=CaptureScenario.create(engine,(Difficulty)difficulty.getSelectedItem(),
                (PlayerColor)color.getSelectedItem(),attack.getSelectedIndex(),
                (PlayerColor)other.getSelectedItem(),defend.getSelectedIndex(),
                (CaptureScenario.Direction)direction.getSelectedItem());
        board.setSession(scenario.session());
        play.setText("Play capture");
    }
}
