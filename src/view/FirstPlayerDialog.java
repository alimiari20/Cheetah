package view;

import static view.Theme.Tone.*;

import javax.swing.*;
import javax.swing.border.*;

import model.PlayerColor;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;

/**
 * First-player selection: every player rolls one die (with a short rolling animation), highest roll starts,
 * ties are re-rolled by the tied players only. Each player is shown as a card with their knight and a big die.
 */
public final class FirstPlayerDialog extends JDialog {
    private final List<String> names;
    private final List<PlayerColor> colors;
    private final Random random;
    private final Random animation=new Random();          // only for the flicker while rolling, never for the result
    private final List<Integer> contenders=new ArrayList<>();
    private final int[] rolls;
    private final int[] shown;                               // face currently shown on each card (0 = "?")
    private final RollCard[] cards;
    private final JButton rollButton=Theme.button("ROLL DICE",true);
    private final JButton continueButton=Theme.button("START GAME  →",true);
    private final JLabel title=UiKit.title("WHO STARTS?",28);
    private final JLabel subtitle=UiKit.sub("Each player rolls one die. The highest roll takes the first turn.");
    private final JLabel notice=Theme.label("",15,Font.BOLD,ACCENT);
    private int round=1,turn=0,winner=-1,rollingCard=-1;
    private boolean tie;

    private FirstPlayerDialog(JFrame owner,List<String> names,List<PlayerColor> colors,Random random){
        super(owner,"CHEETAH LUDO  •  First Player Selection",true);
        this.names=List.copyOf(names);this.colors=List.copyOf(colors);this.random=random;
        this.rolls=new int[names.size()];this.shown=new int[names.size()];this.cards=new RollCard[names.size()];
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);setResizable(true);
        GlobalHeader.install(this,build());
        for(int i=0;i<names.size();i++)contenders.add(i);
        rollButton.addActionListener(e->roll());continueButton.addActionListener(e->dispose());
        continueButton.setEnabled(false);getRootPane().setDefaultButton(rollButton);
        setSize(Math.max(720,names.size()*200+120),620);setLocationRelativeTo(owner);update();
    }
    private JPanel build(){
        UiKit.GradientPage root=new UiKit.GradientPage(new BorderLayout(0,18));root.setBorder(new EmptyBorder(26,35,26,35));
        JPanel heading=UiKit.transparent(new GridLayout(3,1,0,8));
        notice.setHorizontalAlignment(SwingConstants.CENTER);
        heading.add(title);heading.add(subtitle);heading.add(notice);
        root.add(heading,BorderLayout.NORTH);
        JPanel line=UiKit.transparent(new GridLayout(1,names.size(),16,0));
        for(int i=0;i<names.size();i++){
            final int index=i;
            RollCard card=new RollCard(i);cards[i]=card;
            card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
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
    /** Rolls for the current player: the die flickers for a moment, then shows the real result. */
    private void roll(){
        if(winner>=0 || turn>=contenders.size() || rollingCard>=0)return;
        int current=contenders.get(turn);int value=random.nextInt(6)+1;
        rollingCard=current;rollButton.setEnabled(false);
        final int[] frames={0};
        javax.swing.Timer timer=new javax.swing.Timer(70,null);
        timer.addActionListener(e->{
            frames[0]++;
            if(frames[0]<10){shown[current]=animation.nextInt(6)+1;cards[current].repaint();return;}
            timer.stop();rollingCard=-1;shown[current]=value;rolls[current]=value;afterRoll();
        });
        timer.start();
    }
    private void afterRoll(){
        turn++;
        if(turn<contenders.size()){rollButton.setEnabled(true);update();return;}
        int high=0;for(int i:contenders)high=Math.max(high,rolls[i]);
        List<Integer> ties=new ArrayList<>();for(int i:contenders)if(rolls[i]==high)ties.add(i);
        if(ties.size()==1){
            winner=ties.get(0);tie=false;
            Messages.set(title,"★  "+names.get(winner).toUpperCase()+" STARTS!  ★");
            Theme.foreground(title,Theme.playerText(colors.get(winner)));
            Messages.set(subtitle,"Highest roll: "+high+". Turns go clockwise from here.");
            Messages.set(notice,"PRESS START GAME TO GO TO THE BOARD");Theme.foreground(notice,ACCENT);
            continueButton.setEnabled(true);rollButton.setEnabled(false);Messages.set(rollButton,"ROLL DICE");
            getRootPane().setDefaultButton(continueButton);
            for(RollCard c:cards)c.repaint();
        }else{
            round++;contenders.clear();contenders.addAll(ties);turn=0;tie=true;
            Messages.set(title,"IT'S A TIE!");
            Messages.set(subtitle,"Round "+round+": only the tied players roll again.");
            for(int i:ties){rolls[i]=0;shown[i]=0;}
            rollButton.setEnabled(true);update();
        }
    }
    private void update(){
        if(winner>=0||turn>=contenders.size())return;
        int index=contenders.get(turn);
        Messages.set(notice,names.get(index).toUpperCase()+", ROLL THE DIE!");
        Theme.foreground(notice,Theme.playerText(colors.get(index)));
        Messages.set(rollButton,"ROLL FOR "+names.get(index).toUpperCase());
        for(RollCard c:cards)c.repaint();
    }
    private int currentRoller(){return winner<0 && turn<contenders.size()?contenders.get(turn):-1;}

    /** One player's card: name ribbon, their knight and a big die. Highlights who rolls now, ties and the winner. */
    private final class RollCard extends JComponent {
        private final int index;
        RollCard(int index){this.index=index;setPreferredSize(new Dimension(180,280));}
        @Override protected void paintComponent(Graphics g){
            Graphics2D d=(Graphics2D)g.create();
            d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w=getWidth(),h=getHeight(),top=18;
            Color team=Theme.player(colors.get(index));
            boolean current=currentRoller()==index, isWinner=winner==index;
            boolean out=(winner>=0&&!isWinner)||(tie&&!contenders.contains(index));   // lost, or not part of the tie-break
            // card body
            d.setColor(SHADOW.color());d.fill(new RoundRectangle2D.Double(3,top+5,w-6,h-top-6,22,22));
            d.setColor(SURFACE.color());d.fill(new RoundRectangle2D.Double(2,top,w-6,h-top-6,22,22));
            Color edge=isWinner?ACCENT.color():current?team:BORDER.color();
            d.setColor(edge);d.setStroke(new BasicStroke(isWinner||current?4f:1.2f));
            d.draw(new RoundRectangle2D.Double(2,top,w-6,h-top-6,22,22));
            // name ribbon in the team colour
            d.setColor(team);d.fill(new RoundRectangle2D.Double(14,top+14,w-34,34,12,12));
            d.setColor(Theme.teamInk(team));d.setFont(Theme.font(15,Font.BOLD));
            String name=names.get(index);
            FontMetrics fm=d.getFontMetrics();
            while(fm.stringWidth(name)>w-50 && name.length()>3) name=name.substring(0,name.length()-2)+"…";
            d.drawString(name,(w-6)/2-fm.stringWidth(name)/2,top+37);
            // the player's knight
            BoardPanel.paintHero(d,0,team,(w-6)/2.0,top+122,98,false);
            // the die
            int s=64, dx=(w-6)/2-s/2, dy=h-s-30;
            drawDie(d,dx,dy,s,shown[index],rollingCard==index);
            if(out){                                                                     // dim players who are out
                d.setColor(SCRIM.color());d.fill(new RoundRectangle2D.Double(2,top,w-6,h-top-6,22,22));
            }
            // tag above the card
            String tag=isWinner?"★ STARTS FIRST ★":current?(rollingCard==index?"ROLLING...":"YOUR ROLL"):(tie&&contenders.contains(index)&&rolls[index]==0?"TIE - ROLL AGAIN":null);
            if(tag!=null){
                d.setFont(Theme.font(12,Font.BOLD));
                int tw=d.getFontMetrics().stringWidth(Messages.get(tag))+22, tx=(w-6)/2-tw/2;
                d.setColor(isWinner?PRIMARY.color():team);d.fill(new RoundRectangle2D.Double(tx,2,tw,28,14,14));
                d.setColor(isWinner?ON_PRIMARY.color():Theme.teamInk(team));d.drawString(Messages.get(tag),tx+11,21);
            }
            d.dispose();
        }
        private void drawDie(Graphics2D d,int x,int y,int s,int value,boolean rolling){
            Color ink=ON_PRIMARY.color();
            d.setColor(SHADOW.color());d.fill(new RoundRectangle2D.Double(x+4,y+5,s,s,14,14));
            d.setColor(rolling?Theme.blend(PRIMARY.color(),Color.WHITE,.24f):Color.WHITE);d.fill(new RoundRectangle2D.Double(x,y,s,s,14,14));
            d.setColor(ink);d.setStroke(new BasicStroke(2.5f));d.draw(new RoundRectangle2D.Double(x,y,s,s,14,14));
            if(value<=0){
                d.setFont(Theme.font(32,Font.BOLD));
                FontMetrics fm=d.getFontMetrics();d.drawString("?",x+s/2-fm.stringWidth("?")/2,y+s/2+12);return;
            }
            int[][] pts={{1,1},{2,1},{3,1},{1,2},{2,2},{3,2},{1,3},{2,3},{3,3}};
            int[][] index={{4},{0,8},{0,4,8},{0,2,6,8},{0,2,4,6,8},{0,2,3,5,6,8}};
            double step=s/4.0, r=s*.085;
            for(int i:index[Math.min(6,value)-1]) d.fill(new Ellipse2D.Double(x+pts[i][0]*step-r,y+pts[i][1]*step-r,2*r,2*r));
        }
    }

    public static int chooseStarter(JFrame owner,List<String> names,Random random){
        return chooseStarter(owner,names,Arrays.asList(PlayerColor.values()).subList(0,names.size()),random);
    }
    public static int chooseStarter(JFrame owner,List<String> names,List<PlayerColor> colors,Random random){
        FirstPlayerDialog dlg=new FirstPlayerDialog(owner,names,colors,random);dlg.setVisible(true);return dlg.winner;
    }
}