package view;

import static view.Theme.Tone.*;

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
    private final List<JComboBox<AiLevel>> controls = new ArrayList<>();
    private final JPanel[] playerRows=new JPanel[4];
    private final PlayerColor[] selectedColors=PlayerColor.values().clone();
    private final ColorDotButton[][] colorButtons=new ColorDotButton[4][4];
    private final JLabel[] colorMarkers=new JLabel[4];
    private final JLabel previewTitle=UiKit.goldLabel("LIVE BOARD PREVIEW  •  MEDIUM 17 × 17");
    private final BoardPanel preview=new BoardPanel();
    private BiFunction<Difficulty,List<PlayerColor>,GameSession> previewProvider;
    private final Consumer<SetupDialog.Config> onStart;

    public SetupPage(Runnable onBack, Consumer<SetupDialog.Config> onStart) {
        super(new BorderLayout(8,8));this.onStart=onStart;
        setBorder(new EmptyBorder(8,16,10,16));
        JPanel header=UiKit.transparent(new BorderLayout());
        JButton back=Theme.button("← BACK TO MENU",false);back.addActionListener(e->onBack.run());
        back.setPreferredSize(new Dimension(170,36));header.add(back,BorderLayout.LINE_START);
        JPanel headline=UiKit.transparent(new GridLayout(2,1));
        headline.add(UiKit.title("GAME SETUP",21));headline.add(UiKit.sub("Choose players, colors and board difficulty"));
        header.add(headline,BorderLayout.CENTER);
        header.add(Box.createHorizontalStrut(140),BorderLayout.LINE_END);
        add(header,BorderLayout.NORTH);
        JPanel layout=UiKit.transparent(new GridBagLayout());
        GridBagConstraints c=new GridBagConstraints();c.gridy=0;c.fill=GridBagConstraints.BOTH;c.weighty=1;c.insets=new Insets(3,5,3,5);
        c.gridx=0;c.weightx=.54;layout.add(form(),c);
        c.gridx=1;c.weightx=.46;layout.add(previewPane(),c);
        JScrollPane scroll=new JScrollPane(layout);scroll.setOpaque(false);scroll.getViewport().setOpaque(false);
        scroll.setBorder(BorderFactory.createEmptyBorder());scroll.getVerticalScrollBar().setUnitIncrement(18);
        add(scroll,BorderLayout.CENTER);
        chooseCount(4);chooseDifficulty(Difficulty.MEDIUM);updateColorButtons();
    }
    public void setPreviewProvider(BiFunction<Difficulty,List<PlayerColor>,GameSession> fn){previewProvider=fn;updatePreview();}
    private JPanel form(){
        JPanel root=UiKit.transparent(new BorderLayout(0,7));root.setPreferredSize(new Dimension(430,490));
        JPanel stack=UiKit.transparent();stack.setLayout(new BoxLayout(stack,BoxLayout.Y_AXIS));
        JPanel counts=new UiKit.GlassCard(new BorderLayout(0,5));counts.setBorder(new EmptyBorder(9,12,9,12));
        counts.add(UiKit.goldLabel("♙  NUMBER OF PLAYERS (2–4)"),BorderLayout.NORTH);
        JPanel countRow=UiKit.transparent(new GridLayout(1,3,6,0));
        for(int i=0;i<3;i++){final int number=i+2;countButtons[i]=Theme.button(number+" Players",false);
            countButtons[i].addActionListener(e->chooseCount(number));countRow.add(countButtons[i]);}
        counts.add(countRow,BorderLayout.CENTER);counts.setMaximumSize(new Dimension(Integer.MAX_VALUE,78));
        stack.add(counts);stack.add(Box.createVerticalStrut(7));
        JPanel people=new UiKit.GlassCard(new BorderLayout(0,5));people.setBorder(new EmptyBorder(9,12,9,12));
        people.add(UiKit.goldLabel("PLAYER DETAILS & COLORS"),BorderLayout.NORTH);
        JPanel rows=UiKit.transparent(new GridLayout(4,1,0,4));
        PlayerColor[] colors=PlayerColor.values();
        for(int i=0;i<4;i++) {
            JPanel row=UiKit.transparent(new BorderLayout(6,0));row.setBorder(new EmptyBorder(1,2,1,2));
            JLabel circle=Theme.label("●",22,Font.BOLD,Theme.player(colors[i]));colorMarkers[i]=circle;row.add(circle,BorderLayout.LINE_START);
            names[i]=UiKit.field("Player "+(i+1));row.add(names[i],BorderLayout.CENTER);
            JPanel choices=UiKit.transparent(new FlowLayout(FlowLayout.TRAILING,2,0));
            for(int j=0;j<4;j++){
                final int rowIndex=i;final PlayerColor chosen=colors[j];
                ColorDotButton dot=new ColorDotButton(chosen);
                dot.addActionListener(e->chooseColor(rowIndex,chosen));
                colorButtons[i][j]=dot;choices.add(dot);
            }
            JComboBox<AiLevel> control = new JComboBox<>(AiLevel.values());
            Messages.renderCombo(control);
            control.setPreferredSize(new Dimension(114, 28));
            int player = i + 1;
            Messages.bind(control, () -> control.getAccessibleContext().setAccessibleName(
                    Messages.get("Player " + player + " control")));
            controls.add(control);
            JPanel options=UiKit.transparent(new FlowLayout(FlowLayout.TRAILING,4,1));
            options.add(choices);
            options.add(control);
            row.add(options,BorderLayout.LINE_END);
            playerRows[i]=row;rows.add(row);
        }
        people.add(rows,BorderLayout.CENTER);people.setMaximumSize(new Dimension(Integer.MAX_VALUE,225));
        stack.add(people);stack.add(Box.createVerticalStrut(7));
        JPanel mode=new UiKit.GlassCard(new BorderLayout(0,5));mode.setBorder(new EmptyBorder(9,12,9,12));
        mode.add(UiKit.goldLabel("CHOOSE BOARD & DIFFICULTY"),BorderLayout.NORTH);
        JPanel modes=UiKit.transparent(new GridLayout(1,3,8,0));
        String[] texts={"EASY  •  15×15", "MEDIUM  •  17×17", "HARD  •  19×19"};
        for(int i=0;i<3;i++){final Difficulty diff=Difficulty.values()[i];
            modeButtons[i]=Theme.button(texts[i],false);modeButtons[i].setFont(Theme.font(11,Font.BOLD));
            modeButtons[i].addActionListener(e->chooseDifficulty(diff));modes.add(modeButtons[i]);}
        mode.add(modes,BorderLayout.CENTER);mode.setMaximumSize(new Dimension(Integer.MAX_VALUE,75));
        stack.add(mode);root.add(stack,BorderLayout.CENTER);
        JButton proceed=Theme.button("PROCEED TO FIRST PLAYER ROLL   →",true);
        proceed.setPreferredSize(new Dimension(380,40));
        proceed.addActionListener(e->submit());
        root.add(proceed,BorderLayout.SOUTH);return root;
    }
    private JPanel previewPane(){
        JPanel card=new UiKit.GlassCard(new BorderLayout(0,6));card.setBorder(new EmptyBorder(10,10,10,10));
        JPanel intro=UiKit.transparent(new GridLayout(2,1,0,3));previewTitle.setHorizontalAlignment(SwingConstants.CENTER);
        intro.add(previewTitle);intro.add(UiKit.sub("Board dimensions and piece positions update live"));
        card.add(intro,BorderLayout.NORTH);
        preview.setOpaque(false);
        preview.setPreferredSize(new Dimension(330,330));
        card.add(preview,BorderLayout.CENTER);
        JLabel hint=UiKit.sub("★ Safe squares      ? Questions      ◈ Surprises");card.add(hint,BorderLayout.SOUTH);
        return card;
    }
    private void chooseCount(int n) {
        playerCount=n;for(int i=0;i<3;i++)((UiKit.GlowButton)countButtons[i]).setActive(i==n-2);
        for(int i=0;i<4;i++){playerRows[i].setVisible(i<n);names[i].setEnabled(i<n);}
        for(int i=0;i<3;i++)Messages.set(countButtons[i],(i+2)+" PLAYERS"+(i==n-2?"  ✓":""));
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
            Messages.set(modeButtons[i],new String[]{"EASY", "MEDIUM", "HARD"}[i]+(d.ordinal()==i?"  ✓":""));
            ((UiKit.GlowButton)modeButtons[i]).setActive(d.ordinal()==i);
        }
        Messages.set(previewTitle,"LIVE BOARD PREVIEW  •  "+d.name()+"  "+d.boardSize()+" × "+d.boardSize());
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
            if(name.isEmpty()){Dialogs.showMessageDialog(this,"Player "+(i+1)+" needs a name.");names[i].requestFocus();return;}
            if(name.length()>24){Dialogs.showMessageDialog(this,"Player names must be 24 characters or less.");return;}
            if(list.stream().anyMatch(name::equalsIgnoreCase)){
                Dialogs.showMessageDialog(this,"Please choose different names for each player.");return;
            }
            list.add(name);
        }
        List<PlayerColor> active=new ArrayList<>();for(int i=0;i<playerCount;i++)active.add(selectedColors[i]);
        List<AiLevel> levels=controls.subList(0,playerCount).stream()
                .map(box -> (AiLevel) box.getSelectedItem()).toList();
        onStart.accept(new SetupDialog.Config(difficulty,list,active,levels));
    }
    private static final class ColorDotButton extends JButton {
        private final PlayerColor color;
        ColorDotButton(PlayerColor color){
            this.color=color;setPreferredSize(new Dimension(23,25));setFocusPainted(false);
            setContentAreaFilled(false);setBorderPainted(false);setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            Messages.bind(this, () -> setToolTipText(Messages.get("Choose color: ") + Messages.get(color.label())));
        }
        @Override protected void paintComponent(Graphics g){
            Graphics2D d=(Graphics2D)g.create();d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            Color team=Theme.player(color);d.setColor(getModel().isRollover()?Theme.blend(team,Color.WHITE,.2f):team);d.fillOval(4,5,15,15);
            if(isSelected() || hasFocus()){d.setColor((isSelected()?ACCENT:TEXT).color());d.setStroke(new BasicStroke(2));d.drawOval(2,3,19,19);}
            d.dispose();
        }
    }
}
