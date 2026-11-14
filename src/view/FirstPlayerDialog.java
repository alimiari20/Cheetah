package view;

import javax.swing.*;
import javax.swing.border.*;

import model.PlayerColor;

import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

/** Prototype first-player selection, including interactive roll cards and tie-break rounds. */
public final class FirstPlayerDialog extends JDialog {
    private final List<String> names;
    private final List<PlayerColor> colors;
    private final Random random;
    private final List<Integer> contenders=new ArrayList<>();
    private final int[] rolls;
    private final JLabel[] resultLabels;
    private final JPanel[] cards;
    private final JButton rollButton=Theme.button("ROLL DICE  ⚄",true);
    private final JButton continueButton=Theme.button("START GAME ON BOARD  →",true);
    private final JLabel title=UiKit.title("FIRST PLAYER SELECTION",25);
    private final JLabel subtitle=UiKit.sub("Each player rolls individually. Highest roll takes the first turn.");
    private final JLabel notice=Theme.label("PLAYER 1 OF 4",12,Font.BOLD,Theme.GOLD);
    private int round=1,turn=0,winner=-1;
    private FirstPlayerDialog(JFrame owner,List<String> names,List<PlayerColor> colors,Random random){
        super(owner,"CHEETAH LUDO  •  First Player Selection",true);
        this.names=List.copyOf(names);this.colors=List.copyOf(colors);this.random=random;this.rolls=new int[names.size()];
        this.resultLabels=new JLabel[names.size()];this.cards=new JPanel[names.size()];
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);setResizable(true);
        setContentPane(build());
        for(int i=0;i<names.size();i++)contenders.add(i);
        rollButton.addActionListener(e->roll());continueButton.addActionListener(e->dispose());
        continueButton.setEnabled(false);getRootPane().setDefaultButton(rollButton);
        setSize(Math.max(680,names.size()*155+115),490);setLocationRelativeTo(owner);update();
    }
    private JPanel build(){
        UiKit.GradientPage root=new UiKit.GradientPage(new BorderLayout(0,18));root.setBorder(new EmptyBorder(30,35,28,35));
        JPanel heading=UiKit.transparent(new GridLayout(3,1,0,9));
        heading.add(title);heading.add(subtitle);notice.setHorizontalAlignment(SwingConstants.CENTER);heading.add(notice);
        root.add(heading,BorderLayout.NORTH);
        JPanel line=UiKit.transparent(new GridLayout(1,names.size(),12,0));
        for(int i=0;i<names.size();i++){
            final int index=i;
            JPanel card=new UiKit.GlassCard(new BorderLayout(0,13));card.setBorder(new EmptyBorder(16,10,16,10));
            JLabel initial=Theme.label(initials(names.get(i)),18,Font.BOLD,Theme.player(colors.get(i)));
            initial.setHorizontalAlignment(SwingConstants.CENTER);
            card.add(initial,BorderLayout.NORTH);
            JPanel details=UiKit.transparent(new GridLayout(2,1,0,3));
            JLabel name=UiKit.title(names.get(i),12);
            JLabel color=UiKit.sub(colors.get(i).name());
            details.add(name);details.add(color);card.add(details,BorderLayout.CENTER);
            JLabel result=UiKit.title("?",31);result.setForeground(Theme.GOLD);
            resultLabels[i]=result;card.add(result,BorderLayout.SOUTH);
            cards[i]=card;card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            card.addMouseListener(new MouseAdapter(){public void mouseClicked(MouseEvent e){
                if(winner<0 && turn<contenders.size() && contenders.get(turn)==index)roll();
            }});
            line.add(card);
        }
        root.add(line,BorderLayout.CENTER);
        JPanel actions=UiKit.transparent(new GridLayout(1,3,12,0));
        JButton cancel=Theme.button("CANCEL",false);cancel.addActionListener(e->{winner=-1;dispose();});
        actions.add(cancel);actions.add(rollButton);actions.add(continueButton);
        root.add(actions,BorderLayout.SOUTH);return root;
    }
    private void roll(){
        if(winner>=0 || turn>=contenders.size())return;
        int current=contenders.get(turn);int value=random.nextInt(6)+1;
        rolls[current]=value;resultLabels[current].setText("⚄ " + value);turn++;
        if(turn<contenders.size()){update();return;}
        int high=0;for(int i:contenders)high=Math.max(high,rolls[i]);
        List<Integer> ties=new ArrayList<>();for(int i:contenders)if(rolls[i]==high)ties.add(i);
        if(ties.size()==1){
            winner=ties.get(0);title.setText("★  "+names.get(winner).toUpperCase()+" STARTS!");
            notice.setText("HIGHEST ROLL: "+high+"  •  CLOCKWISE TURN ORDER");
            subtitle.setText("All players have rolled. Start your match on the board.");
            continueButton.setEnabled(true);rollButton.setEnabled(false);
            getRootPane().setDefaultButton(continueButton);
        }else{
            round++;contenders.clear();contenders.addAll(ties);turn=0;
            title.setText("TIE BREAKER  •  ROUND "+round);
            subtitle.setText("Only the tied players reroll. Highest roll wins.");
            for(int i:ties){rolls[i]=0;resultLabels[i].setText("?");}
            update();
        }
    }
    private void update(){
        if(winner>=0||turn>=contenders.size())return;
        int index=contenders.get(turn);
        notice.setText("ROUND "+round+"  •  CLICK "+names.get(index).toUpperCase()+"'S CARD OR ROLL BUTTON");
        rollButton.setText("ROLL FOR "+names.get(index).toUpperCase());
        for(int i=0;i<cards.length;i++){
            cards[i].setBorder(new CompoundBorder(new LineBorder(i==index?Theme.GOLD:new Color(55,103,194),i==index?2:1,true),
                               new EmptyBorder(15,9,15,9)));
        }
    }
    private static String initials(String name){String[] p=name.trim().split("\\s+");return p.length>=2?
        (p[0].substring(0,1)+p[1].substring(0,1)).toUpperCase(Locale.ROOT):name.substring(0,Math.min(2,name.length())).toUpperCase(Locale.ROOT);}
    public static int chooseStarter(JFrame owner,List<String> names,Random random){
        return chooseStarter(owner,names,Arrays.asList(PlayerColor.values()).subList(0,names.size()),random);
    }
    public static int chooseStarter(JFrame owner,List<String> names,List<PlayerColor> colors,Random random){
        FirstPlayerDialog dlg=new FirstPlayerDialog(owner,names,colors,random);dlg.setVisible(true);return dlg.winner;
    }
}
