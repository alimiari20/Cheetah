package view;

import javax.swing.*;
import javax.swing.border.EmptyBorder;

import model.*;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.BiFunction;

/** Full interactive player/difficulty setup screen from prototype page 2. */
public final class SetupPage extends UiKit.GradientPage {
    private int playerCount=4;
    private Difficulty difficulty=Difficulty.MEDIUM;
    private final JButton[] countButtons=new JButton[3];
    private final JButton[] modeButtons=new JButton[3];
    private final JTextField[] names=new JTextField[4];
    private final JPanel[] playerRows=new JPanel[4];
    private final PlayerColor[] selectedColors=PlayerColor.values().clone();
    private final ColorDotButton[][] colorButtons=new ColorDotButton[4][4];
    private final JLabel[] colorMarkers=new JLabel[4];
    private final JLabel previewTitle=UiKit.goldLabel("LIVE BOARD PREVIEW  •  MEDIUM 17 × 17");
    private final BoardPanel preview=new BoardPanel();
    private BiFunction<Difficulty,List<PlayerColor>,GameSession> previewProvider;
    private final Consumer<SetupDialog.Config> onStart;

    public SetupPage(Runnable onBack, Consumer<SetupDialog.Config> onStart) {
        super(new BorderLayout(12,14));this.onStart=onStart;
        setBorder(new EmptyBorder(17,28,22,28));
        JPanel header=UiKit.transparent(new BorderLayout());
        JButton back=Theme.button("← BACK TO MENU",false);back.addActionListener(e->onBack.run());
        back.setPreferredSize(new Dimension(192,45));header.add(back,BorderLayout.WEST);
        JPanel headline=UiKit.transparent(new GridLayout(2,1));
        headline.add(UiKit.title("GAME SETUP",24));headline.add(UiKit.sub("Choose players, colors and board difficulty"));
        header.add(headline,BorderLayout.CENTER);
        header.add(Box.createHorizontalStrut(170),BorderLayout.EAST);
        add(header,BorderLayout.NORTH);
        JPanel layout=UiKit.transparent(new GridBagLayout());
        GridBagConstraints c=new GridBagConstraints();c.gridy=0;c.fill=GridBagConstraints.BOTH;c.weighty=1;c.insets=new Insets(5,7,5,7);
        c.gridx=0;c.weightx=.54;layout.add(form(),c);
        c.gridx=1;c.weightx=.46;layout.add(previewPane(),c);
        JScrollPane scroll=new JScrollPane(layout);scroll.setOpaque(false);scroll.getViewport().setOpaque(false);
        scroll.setBorder(null);scroll.getVerticalScrollBar().setUnitIncrement(18);
        add(scroll,BorderLayout.CENTER);
        chooseCount(4);chooseDifficulty(Difficulty.MEDIUM);updateColorButtons();
    }
    public void setPreviewProvider(BiFunction<Difficulty,List<PlayerColor>,GameSession> fn){previewProvider=fn;updatePreview();}
    private JPanel form(){
        JPanel root=UiKit.transparent(new BorderLayout(0,13));root.setPreferredSize(new Dimension(525,610));
        JPanel stack=UiKit.transparent();stack.setLayout(new BoxLayout(stack,BoxLayout.Y_AXIS));
        JPanel counts=new UiKit.GlassCard(new BorderLayout(0,10));counts.setBorder(new EmptyBorder(16,18,16,18));
        counts.add(UiKit.goldLabel("♙  NUMBER OF PLAYERS (2–4)"),BorderLayout.NORTH);
        JPanel countRow=UiKit.transparent(new GridLayout(1,3,9,0));
        for(int i=0;i<3;i++){final int number=i+2;countButtons[i]=Theme.button(number+" Players",false);
            countButtons[i].addActionListener(e->chooseCount(number));countRow.add(countButtons[i]);}
        counts.add(countRow,BorderLayout.CENTER);counts.setMaximumSize(new Dimension(Integer.MAX_VALUE,100));
        stack.add(counts);stack.add(Box.createVerticalStrut(12));
        JPanel people=new UiKit.GlassCard(new BorderLayout(0,10));people.setBorder(new EmptyBorder(15,17,15,17));
        people.add(UiKit.goldLabel("PLAYER DETAILS & COLORS"),BorderLayout.NORTH);
        JPanel rows=UiKit.transparent(new GridLayout(4,1,0,9));
        PlayerColor[] colors=PlayerColor.values();
        for(int i=0;i<4;i++) {
            JPanel row=UiKit.transparent(new BorderLayout(10,0));row.setBorder(new EmptyBorder(3,4,3,4));
            JLabel circle=Theme.label("●",22,Font.BOLD,Theme.player(colors[i]));colorMarkers[i]=circle;row.add(circle,BorderLayout.WEST);
            names[i]=UiKit.field("Player "+(i+1));row.add(names[i],BorderLayout.CENTER);
            JPanel choices=UiKit.transparent(new FlowLayout(FlowLayout.RIGHT,2,0));
            for(int j=0;j<4;j++){
                final int rowIndex=i;final PlayerColor chosen=colors[j];
                ColorDotButton dot=new ColorDotButton(chosen);
                dot.addActionListener(e->chooseColor(rowIndex,chosen));
                colorButtons[i][j]=dot;choices.add(dot);
            }
            row.add(choices,BorderLayout.EAST);
            playerRows[i]=row;rows.add(row);
        }
        people.add(rows,BorderLayout.CENTER);people.setMaximumSize(new Dimension(Integer.MAX_VALUE,280));
        stack.add(people);stack.add(Box.createVerticalStrut(12));
        JPanel mode=new UiKit.GlassCard(new BorderLayout(0,9));mode.setBorder(new EmptyBorder(14,17,14,17));
        mode.add(UiKit.goldLabel("CHOOSE BOARD & DIFFICULTY"),BorderLayout.NORTH);
        JPanel modes=UiKit.transparent(new GridLayout(1,3,8,0));
        String[] texts={"EASY  •  15×15", "MEDIUM  •  17×17", "HARD  •  19×19"};
        for(int i=0;i<3;i++){final Difficulty diff=Difficulty.values()[i];
            modeButtons[i]=Theme.button(texts[i],false);modeButtons[i].setFont(Theme.font(11,Font.BOLD));
            modeButtons[i].addActionListener(e->chooseDifficulty(diff));modes.add(modeButtons[i]);}
        mode.add(modes,BorderLayout.CENTER);mode.setMaximumSize(new Dimension(Integer.MAX_VALUE,92));
        stack.add(mode);root.add(stack,BorderLayout.CENTER);
        JButton proceed=Theme.button("PROCEED TO FIRST PLAYER ROLL   →",true);
        proceed.setPreferredSize(new Dimension(400,50));
        proceed.addActionListener(e->submit());
        root.add(proceed,BorderLayout.SOUTH);return root;
    }
    private JPanel previewPane(){
        JPanel card=new UiKit.GlassCard(new BorderLayout(0,12));card.setBorder(new EmptyBorder(18,17,18,17));
        JPanel intro=UiKit.transparent(new GridLayout(2,1,0,3));previewTitle.setHorizontalAlignment(SwingConstants.CENTER);
        intro.add(previewTitle);intro.add(UiKit.sub("Board dimensions and piece positions update live"));
        card.add(intro,BorderLayout.NORTH);
        preview.setOpaque(false);preview.setBackground(Theme.SURFACE_2);
        preview.setPreferredSize(new Dimension(460,460));
        card.add(preview,BorderLayout.CENTER);
        JLabel hint=UiKit.sub("★ Safe squares      ? Questions      ◈ Surprises");card.add(hint,BorderLayout.SOUTH);
        return card;
    }
    private void chooseCount(int n) {
        playerCount=n;for(int i=0;i<3;i++)((UiKit.GlowButton)countButtons[i]).setActive(i==n-2);
        for(int i=0;i<4;i++){playerRows[i].setVisible(i<n);names[i].setEnabled(i<n);}
        for(int i=0;i<3;i++)countButtons[i].setText((i+2)+" PLAYERS"+(i==n-2?"  ✓":""));
        updatePreview();revalidate();repaint();
    }
    private void chooseColor(int row,PlayerColor chosen){
        int other=-1;for(int i=0;i<4;i++)if(selectedColors[i]==chosen){other=i;break;}
        if(other>=0 && other!=row)selectedColors[other]=selectedColors[row];
        selectedColors[row]=chosen;
        updateColorButtons();updatePreview();
    }
    private void updateColorButtons(){
        for(int i=0;i<4;i++){
            if(colorMarkers[i]!=null)colorMarkers[i].setForeground(Theme.player(selectedColors[i]));
            for(int j=0;j<4;j++)if(colorButtons[i][j]!=null)
                colorButtons[i][j].setSelected(selectedColors[i]==PlayerColor.values()[j]);
        }
    }
    private void chooseDifficulty(Difficulty d) {
        difficulty=d;
        for(int i=0;i<3;i++){
            modeButtons[i].setText(new String[]{"EASY", "MEDIUM", "HARD"}[i]+(d.ordinal()==i?"  ✓":""));
            ((UiKit.GlowButton)modeButtons[i]).setActive(d.ordinal()==i);
        }
        previewTitle.setText("LIVE BOARD PREVIEW  •  "+d.name()+"  "+d.boardSize()+" × "+d.boardSize());
        updatePreview();
    }
    private void updatePreview(){
        if(previewProvider!=null){
            List<PlayerColor> active=new ArrayList<>();for(int i=0;i<playerCount;i++)active.add(selectedColors[i]);
            preview.setSession(previewProvider.apply(difficulty,active));
        }
    }
    private void submit(){
        List<String> list=new ArrayList<>();
        for(int i=0;i<playerCount;i++){
            String name=names[i].getText().trim();
            if(name.isEmpty()){JOptionPane.showMessageDialog(this,"Player "+(i+1)+" needs a name.");names[i].requestFocus();return;}
            if(name.length()>24){JOptionPane.showMessageDialog(this,"Player names must be 24 characters or less.");return;}
            if(list.stream().anyMatch(name::equalsIgnoreCase)){
                JOptionPane.showMessageDialog(this,"Please choose different names for each player.");return;
            }
            list.add(name);
        }
        List<PlayerColor> active=new ArrayList<>();for(int i=0;i<playerCount;i++)active.add(selectedColors[i]);
        onStart.accept(new SetupDialog.Config(difficulty,list,active));
    }
    private static final class ColorDotButton extends JButton {
        private final PlayerColor color;
        ColorDotButton(PlayerColor color){
            this.color=color;setPreferredSize(new Dimension(23,25));setFocusPainted(false);
            setContentAreaFilled(false);setBorderPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText("Choose "+color.label());
        }
        @Override protected void paintComponent(Graphics g){
            Graphics2D d=(Graphics2D)g.create();d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            d.setColor(Theme.player(color));d.fillOval(4,5,15,15);
            if(isSelected()){d.setColor(Theme.WHITE);d.setStroke(new BasicStroke(2));d.drawOval(2,3,19,19);}
            d.dispose();
        }
    }
}
