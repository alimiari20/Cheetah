package view;

import java.awt.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

import model.Difficulty;
import model.PlayerColor;

/** Returns configuration only; no model mutations happen inside a dialog. */
public final class SetupDialog {
    public record Config(Difficulty difficulty, List<String> players, List<PlayerColor> colors) {
        public Config {
            players = List.copyOf(players); colors = List.copyOf(colors);
            if(players.size()!=colors.size() || players.size()<2 || players.size()>4 ||
                  new HashSet<>(colors).size()!=colors.size())
                throw new IllegalArgumentException("Choose 2–4 players with distinct colors.");
        }
        public Config(Difficulty difficulty,List<String> players) {
            this(difficulty,players,Arrays.asList(PlayerColor.values()).subList(0,players.size()));
        }
    }
    private SetupDialog() {}

    public static Config show(JFrame owner) {
        JComboBox<Difficulty> difficulty = new JComboBox<>(Difficulty.values());
        difficulty.setSelectedItem(Difficulty.EASY);
        JComboBox<Integer> count = new JComboBox<>(new Integer[]{2,3,4});
        JTextField[] names = new JTextField[4];
        JPanel fields = new JPanel(new GridBagLayout());
        fields.setBackground(Color.WHITE);
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(7,7,7,7);
        g.fill = GridBagConstraints.HORIZONTAL;
        g.gridy = 0; g.gridx = 0; g.weightx = 0;
        fields.add(Theme.label("DIFFICULTY",12,Font.BOLD,Theme.MUTED),g);
        g.gridx = 1;g.weightx=1; fields.add(difficulty,g);
        g.gridy++;g.gridx=0;g.weightx=0;
        fields.add(Theme.label("PLAYERS",12,Font.BOLD,Theme.MUTED),g);
        g.gridx=1;g.weightx=1;fields.add(count,g);
        String[] defaults={"Player 1","Player 2","Player 3","Player 4"};
        for(int i=0;i<4;i++) {
            g.gridy++;g.gridx=0;g.weightx=0;
            fields.add(Theme.label("Player "+(i+1),12,Font.PLAIN,Theme.NAVY),g);
            names[i] = new JTextField(defaults[i],18);
            names[i].setFont(Theme.font(14,Font.PLAIN));
            g.gridx=1;g.weightx=1;fields.add(names[i],g);
        }
        Runnable refresh = () -> {
            int n=(Integer)count.getSelectedItem();
            for(int i=0;i<4;i++) names[i].setEnabled(i<n);
        };
        count.addActionListener(e->refresh.run());
        refresh.run();
        JPanel outer = new JPanel(new BorderLayout(0,12));
        outer.setBackground(Color.WHITE);
        outer.setBorder(new EmptyBorder(12,12,12,12));
        outer.add(Theme.label("START A NEW MATCH",20,Font.BOLD,Theme.NAVY),BorderLayout.NORTH);
        outer.add(fields,BorderLayout.CENTER);
        int result=JOptionPane.showConfirmDialog(owner,outer,"Cheetah Ludo · Setup",
                JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE);
        if(result!=JOptionPane.OK_OPTION) return null;
        int n=(Integer)count.getSelectedItem();
        List<String> players=new ArrayList<>();
        for(int i=0;i<n;i++) {
            String name=names[i].getText().trim();
            players.add(name.isEmpty() ? defaults[i] : name);
        }
        return new Config((Difficulty)difficulty.getSelectedItem(),players);
    }
}
