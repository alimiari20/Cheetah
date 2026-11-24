package view;

import java.util.StringJoiner;
import javax.swing.*;
import javax.swing.border.*;

import model.*;
import java.util.Set;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.List;
import java.util.Objects;

/** Prototype-inspired desktop shell: real menu/setup/game/history/admin screens. */
public final class GameFrame extends JFrame {
    public interface Actions {
        void newGame();
        void startConfiguredGame(SetupDialog.Config config);
        GameSession preview(Difficulty difficulty,List<PlayerColor> colors);
        void showMenu();
        void roll();
        void pass();
        void selectPiece(String id);
        void history();
        void exportHistory();
        void questionAdmin();
        void saveQuestion(Question question);
        void deleteQuestion(String questionId);
    }
    private Actions actions;
    private final CardLayout screenLayout=new CardLayout();
    private final JPanel screens=new JPanel(screenLayout);
    private final SetupPage setupPage;
    private final HistoryPage historyPage;
    private final QuestionAdminPage adminPage;
    private final BoardPanel boardPanel=new BoardPanel();
    private final JLabel modeLabel=UiKit.goldLabel("CLASSIC MODE");
    private final JLabel turnLabel=Theme.label("Player 1",14,Font.BOLD,Theme.GOLD);
    private final JLabel turnCounter=Theme.label("TURN 01",11,Font.BOLD,Theme.MUTED);
    /** Live total game duration (SRS 3.2.2.3.8), refreshed every second while a match is running. */
    private final JLabel clockLabel=Theme.label("TIME  00:00",12,Font.BOLD,Theme.GOLD);
    private final Timer clock=new Timer(1000,e->updateClock());
    private GameSession clockSession;
    // pause: the screen is covered, buttons and keys do nothing, and the clock does not count the paused time
    private final PauseOverlay pauseOverlay=new PauseOverlay();
    private boolean paused;
    private long pausedMillis;                 // total paused time in the current game
    private long pauseStartedAt;
    private final JLabel diceDetails=Theme.label("Roll to begin",12,Font.BOLD,Theme.WHITE);
    private final JLabel movementTitle =
    	    Theme.label("AVAILABLE MOVES", 11, Font.BOLD, Theme.MUTED);
    private final JLabel resultValue =
    	    Theme.label("–", 30, Font.BOLD, Theme.GOLD);

    	private final JLabel movementValue =
    			Theme.label("READY", 14, Font.BOLD, Theme.GOLD);
    private final JLabel phaseLabel=Theme.label("READY TO ROLL",12,Font.BOLD,Theme.SUCCESS);
    private final JLabel instruction=Theme.label("Choose Roll Dice to begin",13,Font.BOLD,Theme.MUTED);
    private final JButton rollButton=Theme.button("ROLL DICE  ⚄",true);
    private final JButton passButton=Theme.button("PASS TURN",false);
    private final JPanel leftPlayers=UiKit.transparent();
    private final JPanel rightPlayers=UiKit.transparent();
    private final DieFace regularFace=new DieFace(),specialFace=new DieFace();
    private final JLabel multiplier=Theme.label("",12,Font.BOLD,Theme.GOLD);
    private final JLabel specialDieLabel =
    	    Theme.label("BOOST DIE", 11, Font.BOLD, Theme.MUTED);
    /** Move history: one short, coloured line per event, newest first, grouped by turn. */
    private final JPanel history=new JPanel();
    private final JButton adminSwitch=Theme.button("GUEST MODE  ▾",false);
    private final JButton adminTools=Theme.button("QUESTION MANAGEMENT",false);
    private final JLabel adminStatus=Theme.label("Guest view  •  Local multiplayer",11,Font.PLAIN,Theme.MUTED);
    private boolean adminMode;
    private String currentScreen="MENU";

    public GameFrame(){
        super("CHEETAH LUDO  •  Desktop MVC");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        Rectangle r=GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setSize(Math.min(1390,r.width),Math.min(910,r.height));
        setMinimumSize(new Dimension(Math.min(890,r.width),Math.min(670,r.height)));
        setLocationRelativeTo(null);
        screens.add(buildMainMenu(),"MENU");
        setupPage=new SetupPage(()->{if(actions!=null)actions.showMenu();},config->{if(actions!=null)actions.startConfiguredGame(config);});
        screens.add(setupPage,"SETUP");
        screens.add(buildGameScreen(),"GAME");
        historyPage=new HistoryPage(()->{if(actions!=null)actions.showMenu();},()->{if(actions!=null)actions.exportHistory();});
        screens.add(historyPage,"HISTORY");
        adminPage=new QuestionAdminPage(()->{if(actions!=null)actions.showMenu();},
                q->{if(actions!=null)actions.saveQuestion(q);},id->{if(actions!=null)actions.deleteQuestion(id);});
        screens.add(adminPage,"ADMIN");
        setContentPane(screens);
        boardPanel.setOnSelect(id->{if(actions!=null)actions.selectPiece(id);});
        rollButton.addActionListener(e->{if(actions!=null)actions.roll();});
        passButton.addActionListener(e->{if(actions!=null)actions.pass();});
        bindShortcut(KeyEvent.VK_R,"roll",()->{if(actions!=null && isGameVisible() && !paused)actions.roll();});
        bindShortcut(KeyEvent.VK_P,"pass",()->{if(actions!=null && isGameVisible() && !paused)actions.pass();});
        bindShortcut(KeyEvent.VK_ESCAPE,"pause",()->{if(isGameVisible())setPaused(!paused);});
        setGlassPane(pauseOverlay);
        buildMenu();
    }
    public void setActions(Actions a){actions=Objects.requireNonNull(a);}
    public boolean isGameVisible(){return "GAME".equals(currentScreen);}
    private void show(String key){if(!"GAME".equals(key)){clock.stop();if(paused)setPaused(false);}currentScreen=key;screenLayout.show(screens,key);screens.revalidate();screens.repaint();}
    public void showMainMenu(){show("MENU");}
    public void showSetup(){setupPage.setPreviewProvider((diff,colors)->actions.preview(diff,colors));show("SETUP");}
    public void showGame(){show("GAME");}
    public void showHistory(List<HistoryRepository.Entry> entries){historyPage.setEntries(entries);show("HISTORY");}
    public void showQuestionAdmin(List<Question> questions){adminPage.showQuestions(questions);show("ADMIN");}
    public void updateQuestionAdmin(List<Question> questions){adminPage.showQuestions(questions);}

    private JPanel buildMainMenu(){
        UiKit.GradientPage page=new UiKit.GradientPage(new BorderLayout(18,0));
        page.setBorder(new EmptyBorder(26,33,23,33));
        JPanel top=UiKit.transparent(new BorderLayout());
        JPanel account=UiKit.transparent(new GridLayout(2,1,0,8));account.setPreferredSize(new Dimension(254,112));
        adminSwitch.addActionListener(e->{adminMode=!adminMode;adminSwitch.setText(adminMode?"ADMINISTRATOR  ▴":"GUEST MODE  ▾");
            adminStatus.setText(adminMode?"Local administrator tools enabled":"Guest view  •  Local multiplayer");
            adminTools.setVisible(adminMode);});
        account.add(adminSwitch);account.add(adminStatus);top.add(account,BorderLayout.WEST);
        JPanel corner=UiKit.transparent(new BorderLayout());
        JLabel local=UiKit.chip("LOCAL MULTIPLAYER  •  2–4 PLAYERS",Theme.MUTED);corner.add(local,BorderLayout.NORTH);
        top.add(corner,BorderLayout.EAST);page.add(top,BorderLayout.NORTH);
        JPanel center=UiKit.transparent(new GridBagLayout());
        JPanel stack=UiKit.transparent();stack.setLayout(new BoxLayout(stack,BoxLayout.Y_AXIS));
        UiKit.Brand brand=new UiKit.Brand();brand.setPreferredSize(new Dimension(410,155));brand.setAlignmentX(Component.CENTER_ALIGNMENT);
        stack.add(brand);stack.add(Box.createVerticalStrut(18));
        // menu buttons stacked with equal gaps; a hidden button (question management for guests) leaves no hole
        JPanel panel=new UiKit.GlassCard(null);panel.setLayout(new BoxLayout(panel,BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(16,16,16,16));
        JButton play=Theme.button("▶   NEW GAME",true);play.addActionListener(e->{if(actions!=null)actions.newGame();});
        JButton history=Theme.button("↺   GAME HISTORY",false);history.addActionListener(e->{if(actions!=null)actions.history();});
        adminTools.addActionListener(e->{if(actions!=null)actions.questionAdmin();});adminTools.setVisible(false);
        JButton exit=Theme.button("EXIT",false);exit.addActionListener(e->{
            if(UiKit.confirm(this,"Exit","EXIT CHEETAH LUDO?","Are you sure you want to close the game?","EXIT GAME"))dispose();});
        JButton[] buttons={play,history,adminTools,exit};
        for(int i=0;i<buttons.length;i++){
            JButton b=buttons[i];
            b.setAlignmentX(Component.CENTER_ALIGNMENT);
            b.setPreferredSize(new Dimension(333,52));b.setMaximumSize(new Dimension(333,52));
            if(i>0){Component gap=Box.createVerticalStrut(10);panel.add(gap);
                if(b==adminTools){final Component g=gap;g.setVisible(false);                      // the gap shows and hides with the button
                    adminSwitch.addActionListener(ev->SwingUtilities.invokeLater(()->{g.setVisible(adminTools.isVisible());panel.revalidate();}));}}
            panel.add(b);
        }
        panel.setMaximumSize(new Dimension(365,400));panel.setAlignmentX(Component.CENTER_ALIGNMENT);
        // the four heroes stand on both sides of the menu
        JPanel row=UiKit.transparent(new BorderLayout(26,0));
        row.add(new HeroPair(0,PlayerColor.RED,1,PlayerColor.GREEN),BorderLayout.WEST);
        JPanel panelHolder=UiKit.transparent(new GridBagLayout());panelHolder.add(panel);   // keeps the card as tall as its buttons
        row.add(panelHolder,BorderLayout.CENTER);
        row.add(new HeroPair(2,PlayerColor.YELLOW,3,PlayerColor.BLUE),BorderLayout.EAST);
        row.setAlignmentX(Component.CENTER_ALIGNMENT);
        stack.add(row);center.add(stack);page.add(center,BorderLayout.CENTER);
        JLabel bottom=UiKit.sub("Cheetah Ludo  •  Local Multiplayer  •  Java Swing MVC  •  Windows & macOS");
        page.add(bottom,BorderLayout.SOUTH);return page;
    }
    private JPanel buildGameScreen(){
        UiKit.GradientPage root=new UiKit.GradientPage(new BorderLayout(10,6));
        root.setBorder(new EmptyBorder(8,14,10,14));
        JPanel head=UiKit.transparent(new BorderLayout(8,4));
        JButton menu=Theme.button("⌂  MENU",false);menu.addActionListener(e->{if(actions!=null)actions.showMenu();});
        JButton exit=Theme.button("EXIT GAME",false);exit.addActionListener(e->{if(actions!=null)actions.showMenu();});
        JButton pause=Theme.button("",false);pause.addActionListener(e->setPaused(true));
        pause.setIcon(new PauseIcon());pause.setToolTipText("Pause (Esc)");               // just the pause symbol
        pause.setPreferredSize(new Dimension(64,menu.getPreferredSize().height));
        JPanel leftButtons=UiKit.transparent(new FlowLayout(FlowLayout.LEFT,8,0));leftButtons.add(menu);leftButtons.add(pause);
        head.add(leftButtons,BorderLayout.WEST);head.add(exit,BorderLayout.EAST);
        JPanel title=UiKit.transparent(new GridLayout(1,3));                 // one compact line: mode | name | time
        modeLabel.setHorizontalAlignment(SwingConstants.CENTER);title.add(modeLabel);
        JLabel subtitle=UiKit.sub("CHEETAH LUDO ");title.add(subtitle);
        clockLabel.setHorizontalAlignment(SwingConstants.CENTER);title.add(clockLabel);
        head.add(title,BorderLayout.CENTER);
        root.add(head, BorderLayout.NORTH);

        // centre: the board itself, as big as possible (no extra frame around it)
        boardPanel.setOpaque(false);boardPanel.setPreferredSize(new Dimension(640,640));
        JPanel centre=UiKit.transparent(new BorderLayout(0,4));
        centre.add(boardPanel,BorderLayout.CENTER);centre.add(new BoardGuide(),BorderLayout.SOUTH);   // one-line legend under the board
        root.add(centre,BorderLayout.CENTER);

        // left side: players + the dice
        leftPlayers.setLayout(new BoxLayout(leftPlayers,BoxLayout.Y_AXIS));
        rightPlayers.setLayout(new BoxLayout(rightPlayers,BoxLayout.Y_AXIS));
        JPanel diceCard=new UiKit.GlassCard(new GridBagLayout());
        diceCard.setBorder(new EmptyBorder(12,12,12,12));
        GridBagConstraints c=new GridBagConstraints();
        c.gridx=0;c.fill=GridBagConstraints.HORIZONTAL;c.weightx=1;c.insets=new Insets(0,0,8,0);
        JLabel diceTitle=UiKit.goldLabel("REGULAR DIE");diceTitle.setHorizontalAlignment(SwingConstants.CENTER);
        turnLabel.setHorizontalAlignment(SwingConstants.CENTER);
        diceCard.add(turnLabel,c);
        JPanel faces=UiKit.transparent(new GridLayout(1,2,6,0));            // the two dice always side by side
        diceTitle.setFont(Theme.font(10,Font.BOLD));specialDieLabel.setFont(Theme.font(10,Font.BOLD));
        JPanel regularGroup=UiKit.transparent(new BorderLayout(0,4));
        JPanel regularHolder=UiKit.transparent(new FlowLayout(FlowLayout.CENTER,0,0));regularHolder.add(regularFace);
        regularGroup.add(regularHolder,BorderLayout.CENTER);regularGroup.add(diceTitle,BorderLayout.SOUTH);
        JPanel specialGroup=UiKit.transparent(new BorderLayout(0,4));
        specialDieLabel.setHorizontalAlignment(SwingConstants.CENTER);
        JPanel specialHolder=UiKit.transparent(new FlowLayout(FlowLayout.CENTER,0,0));specialHolder.add(specialFace);
        specialGroup.add(specialHolder,BorderLayout.CENTER);specialGroup.add(specialDieLabel,BorderLayout.SOUTH);
        faces.add(regularGroup);faces.add(specialGroup);
        faces.setMinimumSize(new Dimension(160,90));
        diceCard.add(faces,c);
        // one clear message under the dice (what to do now and how far you can move)
        instruction.setHorizontalAlignment(SwingConstants.CENTER);instruction.setFont(Theme.font(14,Font.BOLD));
        c.insets=new Insets(10,0,4,0);diceCard.add(instruction,c);
        rollButton.setPreferredSize(new Dimension(190,46));passButton.setPreferredSize(new Dimension(190,40));
        c.insets=new Insets(8,0,8,0);diceCard.add(rollButton,c);
        c.insets=new Insets(0,0,0,0);diceCard.add(passButton,c);
        c.weighty=1;diceCard.add(Box.createVerticalGlue(),c);
        root.add(sideColumn(leftPlayers,diceCard),BorderLayout.WEST);

        // right side: players + the history of the game, as long as there is room
        JPanel historyCard=new UiKit.GlassCard(new BorderLayout(0,6));
        historyCard.setBorder(new EmptyBorder(14,14,14,14));
        historyCard.add(UiKit.goldLabel("LAST MOVES"),BorderLayout.NORTH);
        history.setLayout(new BoxLayout(history,BoxLayout.Y_AXIS));history.setOpaque(false);
        JPanel historyTop=UiKit.transparent(new BorderLayout());historyTop.add(history,BorderLayout.NORTH);
        JScrollPane log=new JScrollPane(historyTop,ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        log.setOpaque(false);log.getViewport().setOpaque(false);log.setBorder(null);
        log.getVerticalScrollBar().setUnitIncrement(12);
        historyCard.add(log,BorderLayout.CENTER);
        root.add(sideColumn(rightPlayers,historyCard),BorderLayout.EAST);
        return root;
    }
    /** One-line legend of the special squares under the board, drawn with the same pixel icons as the board. */
    private static final class BoardGuide extends JComponent {
        private static final String[][] ITEMS={{"star","Protected"},{"question","Question"},{"surprise","Surprise"},{"start","Start"}};
        BoardGuide(){setPreferredSize(new Dimension(400,26));setMinimumSize(new Dimension(200,26));}
        @Override protected void paintComponent(Graphics g){
            Graphics2D d=(Graphics2D)g.create();
            d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            d.setFont(Theme.font(12,Font.BOLD));
            FontMetrics fm=d.getFontMetrics();
            int gap=26, total=0;
            for(String[] item:ITEMS) total+=22+fm.stringWidth(item[1]);
            total+=gap*(ITEMS.length-1);
            int x=(getWidth()-total)/2, y=getHeight()/2;
            for(String[] item:ITEMS){
                BoardPanel.paintLegendIcon(d,item[0],x+9,y,22);
                d.setColor(Theme.MUTED);d.drawString(item[1],x+22,y+5);
                x+=22+fm.stringWidth(item[1])+gap;
            }
            d.dispose();
        }
    }
    private static final String[] HERO_NAMES={"Knight","Wizard","Archer","Lancer"};
    private static final java.util.regex.Pattern EVENT=java.util.regex.Pattern.compile("#(\\d+)\\s+(.*)");
    /** Rebuilds the move history: a small "TURN n" header for each turn and one clear line per event. */
    private void updateHistory(GameSession session){
        history.removeAll();
        List<String> events=session.events();
        String lastTurn=null;
        for(int i=events.size()-1;i>=0;i--){
            java.util.regex.Matcher m=EVENT.matcher(events.get(i));
            String turnNo=m.matches()?m.group(1):"", body=m.matches()?m.group(2):events.get(i);
            if(!turnNo.equals(lastTurn)){
                JLabel head=Theme.label("TURN "+Integer.parseInt(turnNo.isEmpty()?"0":turnNo),10,Font.BOLD,Theme.GOLD);
                head.setBorder(new EmptyBorder(history.getComponentCount()==0?0:8,0,3,0));head.setAlignmentX(LEFT_ALIGNMENT);
                history.add(head);lastTurn=turnNo;
            }
            history.add(eventRow(session,body));
        }
        history.revalidate();history.repaint();
    }
    /** One history line: a coloured marker for the player and a short sentence about what happened. */
    private JLabel eventRow(GameSession session,String body){
        Player who=null;
        for(Player p:session.players())                           // longest matching name wins ("Raghad K" before "Raghad")
            if((body.startsWith(p.name()+" ")||body.startsWith(p.name()+"'")) && (who==null||p.name().length()>who.name().length())) who=p;
        String name=who==null?"":"<b style='color:"+hex(Theme.player(who.color()))+"'>"+esc(who.name())+"</b> ";
        String rest=who==null?body:body.substring(who.name().length()).trim();
        String text=describe(rest,body);
        JLabel row=new JLabel("<html><div style='width:118px'>"+name+text+"</div></html>");
        row.setFont(Theme.font(12,Font.PLAIN));row.setForeground(new Color(214,226,250));
        row.setIcon(new Marker(who==null?Theme.GOLD:Theme.player(who.color())));row.setIconTextGap(7);
        row.setVerticalTextPosition(SwingConstants.TOP);row.setAlignmentX(LEFT_ALIGNMENT);
        row.setBorder(new EmptyBorder(2,0,4,0));
        return row;
    }
    /** Turns the engine's long event text into a short, clear sentence. */
    private static String describe(String rest,String full){
        java.util.regex.Matcher m;
        if(full.startsWith("New ")) return "<b>New game</b> "+esc(full.substring(4).replace(" game with ",", ").replace(".",""));
        if((m=java.util.regex.Pattern.compile("(?:used an immediate surprise UltraBoost and )?rolled Regular: (\\d+).*?(?:×(\\d+).*?)?Move: (\\d+)\\. (No legal move.*|Select.*)").matcher(rest)).find()){
            String roll="rolled <b>"+m.group(1)+"</b>"+(m.group(2)!=null?" ×"+m.group(2)+" = <b>"+m.group(3)+"</b>":"");
            return roll+(m.group(4).startsWith("No")?" <span style='color:#ff9a9a'>— no move possible</span>":"");
        }
        if(rest.startsWith("has no legal move")) return "<span style='color:#ff9a9a'>passed the turn (no move)</span>";
        if((m=java.util.regex.Pattern.compile("entered the board with piece (\\d)(.*)").matcher(rest)).find())
            return "<b>"+hero(m.group(1))+"</b> left the castle"+extras(m.group(2));
        if((m=java.util.regex.Pattern.compile("moved piece (\\d) by (\\d+) squares(.*)").matcher(rest)).find())
            return "<b>"+hero(m.group(1))+"</b> moved "+m.group(2)+" squares"+extras(m.group(3));
        if(rest.startsWith("answered ")) return "<span style='color:"+(rest.contains("incorrectly")?"#ff9a9a":"#8ff0c0")+"'>"+esc(rest)+"</span>";
        if(full.startsWith("Surprise: ")) return "<b style='color:#c9a0ff'>Surprise!</b> "+esc(full.substring(10));
        if(rest.startsWith("rolled three consecutive")) return "rolled three 6s in a row — roll canceled";
        if(rest.startsWith("finished in place")) return "<b style='color:#ffd36b'>"+esc(rest)+"</b>";
        if(full.startsWith("Game over")) return "<b style='color:#ffd36b'>"+esc(full)+"</b>";
        return esc(rest);
    }
    /** Captures and reaching home, written after a move. */
    private static String extras(String tail){
        StringBuilder out=new StringBuilder();
        java.util.regex.Matcher c=java.util.regex.Pattern.compile("captured ([^—.]+)").matcher(tail);
        if(c.find()){
            StringBuilder victims=new StringBuilder();
            for(String v:c.group(1).split(",\\s*")){
                java.util.regex.Matcher pv=java.util.regex.Pattern.compile("(.+)'s (\\d)").matcher(v.trim());
                if(victims.length()>0)victims.append(", ");
                victims.append(pv.matches()?esc(pv.group(1))+"'s "+hero(pv.group(2)):esc(v.trim()));
            }
            out.append(" and <b style='color:#ff9a9a'>captured ").append(victims).append("</b> — sent back to the castle");
        }
        if(tail.contains("reached HOME")) out.append(" and <b style='color:#ffd36b'>reached HOME ★</b>");
        return out.toString();
    }
    private static String hero(String number){int n=Integer.parseInt(number)-1;return n>=0&&n<HERO_NAMES.length?HERO_NAMES[n]:"piece "+number;}
    private static String esc(String t){return t.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");}
    private static String hex(Color c){return String.format("#%02x%02x%02x",c.getRed(),c.getGreen(),c.getBlue());}
    /** Two big pixel heroes for the main menu, one a little higher than the other. */
    private static final class HeroPair extends JComponent {
        private final int first,second; private final PlayerColor firstColor,secondColor;
        HeroPair(int first,PlayerColor firstColor,int second,PlayerColor secondColor){
            this.first=first;this.second=second;this.firstColor=firstColor;this.secondColor=secondColor;
            setPreferredSize(new Dimension(200,250));
        }
        @Override protected void paintComponent(Graphics g){
            Graphics2D d=(Graphics2D)g.create();
            int w=getWidth(),h=getHeight();
            BoardPanel.paintHero(d,first,Theme.player(firstColor),w*.3,h*.42,104,false);
            BoardPanel.paintHero(d,second,Theme.player(secondColor),w*.7,h*.62,104,false);
            d.dispose();
        }
    }
    /** Pause symbol: two white rounded bars. */
    private static final class PauseIcon implements Icon {
        public int getIconWidth(){return 18;} public int getIconHeight(){return 20;}
        public void paintIcon(Component c,Graphics g,int x,int y){
            Graphics2D d=(Graphics2D)g.create();d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            d.setColor(Color.WHITE);d.fillRoundRect(x+1,y,6,20,3,3);d.fillRoundRect(x+11,y,6,20,3,3);d.dispose();
        }
    }
    /** Small round marker in the player's colour at the start of a history line. */
    private static final class Marker implements Icon {
        private final Color color; Marker(Color color){this.color=color;}
        public int getIconWidth(){return 10;} public int getIconHeight(){return 16;}
        public void paintIcon(Component c,Graphics g,int x,int y){
            Graphics2D d=(Graphics2D)g.create();d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            d.setColor(color);d.fillOval(x,y+4,9,9);d.dispose();
        }
    }
    /** Side column: player cards on top, the given card fills the rest of the height (no empty space). */
    private static JPanel sideColumn(JPanel players,JPanel card){
        JPanel column=UiKit.transparent(new BorderLayout(0,12));
        column.setPreferredSize(new Dimension(214,400));
        column.add(players,BorderLayout.NORTH);column.add(card,BorderLayout.CENTER);
        return column;
    }
    public void render(GameSession session){
        boardPanel.setSession(session);modeLabel.setText("★  "+session.difficulty().label().toUpperCase()+"  ★");
        turnCounter.setText("TURN  "+String.format("%02d",session.turn()));
        Player player=session.current();turnLabel.setText("● "+player.name().toUpperCase());turnLabel.setForeground(Theme.player(player.color()));
        phaseLabel.setText(switch(session.phase()){
            case ROLL->"ROLL THE DICE TO BEGIN YOUR TURN";
            case SELECT_PIECE->"CHOOSE A HIGHLIGHTED PIECE";
            case FINISHED->"GAME COMPLETED";
        });phaseLabel.setForeground(session.phase()==GameSession.Phase.FINISHED?Theme.GOLD:Theme.SUCCESS);
        rollButton.setEnabled(session.phase()==GameSession.Phase.ROLL);
        passButton.setEnabled(session.phase()==GameSession.Phase.SELECT_PIECE&&session.legalPieceIds().isEmpty());
        DiceRoll roll=session.dice();regularFace.setValue(roll==null?0:roll.regular());specialFace.setValue(roll==null?0:roll.special());
        boolean showSpecial=session.difficulty()!=Difficulty.EASY||(roll!=null&&roll.ultraBoost());
        specialDieLabel.setVisible(showSpecial);
        specialFace.setVisible(showSpecial);

        String boost=(roll != null && roll.ultraBoost()) || session.difficulty() == Difficulty.HARD ? "ULTRABOOST" : "BOOST";
        specialDieLabel.setText(showSpecial && roll != null ? boost+"  ×"+roll.multiplier() : boost);   // multiplier shown next to its die
        multiplier.setText(showSpecial && roll != null ? "×" + roll.multiplier() : "");
        diceDetails.setText(roll==null ? "READY TO ROLL" : "SHARED " + roll.movement() + "  |  HOME " + roll.regular());
        resultValue.setText(roll == null ? "–" : Integer.toString(roll.regular()));
        movementValue.setText(availableMovesText(session));

        // a single message for the player: what to do now, and how far the dice let them move
        String text; Color color;
        if (session.phase() == GameSession.Phase.FINISHED) { text = "Game completed!"; color = Theme.GOLD; }
        else if (session.phase() == GameSession.Phase.ROLL) { text = "Roll the dice!"; color = Theme.GOLD; }
        else if (session.legalPieceIds().isEmpty()) { text = "No legal moves. Pass your turn."; color = new Color(255,150,150); }
        else { text = "Move "+availableMovesText(session).toLowerCase()+". Pick a glowing hero."; color = Theme.SUCCESS; }
        instruction.setText(text);instruction.setForeground(color);
        wrap(instruction,135);

        updateHistory(session);
        updatePlayers(session);
        if(clockSession!=session){pausedMillis=0;if(paused)setPaused(false);}   // a new game starts with no paused time
        clockSession=session;updateClock();
        if(session.phase()==GameSession.Phase.FINISHED)clock.stop();else if(!clock.isRunning())clock.start();
        arenaRepaint();
    }
    /** Lets a centred label wrap onto two lines instead of being cut with "...". */
    private static void wrap(JLabel label){wrap(label,125);}
    private static void wrap(JLabel label,int width){
        String t=label.getText();
        if(!t.startsWith("<html>")) label.setText("<html><p style='width:"+width+"px' align=center>"+t+"</p></html>");
    }
    /** Pauses or resumes the game: the overlay blocks the screen and the clock stops counting. */
    public void setPaused(boolean pause){
        if(pause==paused)return;
        paused=pause;
        if(pause){pauseStartedAt=System.currentTimeMillis();clock.stop();}
        else{pausedMillis+=System.currentTimeMillis()-pauseStartedAt;
             if(clockSession!=null && clockSession.phase()!=GameSession.Phase.FINISHED)clock.start();}
        updateClock();
        pauseOverlay.setVisible(pause);
        if(pause)pauseOverlay.focusResume();
    }
    public boolean isPaused(){return paused;}
    /** Dark layer over the whole window with a "PAUSED" card and two choices: EXIT GAME or CONTINUE. It swallows all clicks. */
    private final class PauseOverlay extends JPanel {
        private final JButton resume=Theme.button("▶  CONTINUE",true);
        PauseOverlay(){
            super(new GridBagLayout());setOpaque(false);
            addMouseListener(new MouseAdapter(){});addMouseMotionListener(new MouseMotionAdapter(){});  // block the game underneath
            JPanel card=new UiKit.GlassCard(new GridLayout(3,1,0,12),22);
            card.setBorder(new EmptyBorder(26,40,26,40));
            card.add(UiKit.title("GAME PAUSED",30));
            card.add(UiKit.sub("The timer is stopped. Continue the game or leave it."));
            JButton leave=Theme.button("EXIT GAME",false);
            leave.addActionListener(e->{setPaused(false);if(actions!=null)actions.showMenu();});
            resume.addActionListener(e->setPaused(false));
            for(JButton b:new JButton[]{leave,resume})b.setPreferredSize(new Dimension(190,48));
            JPanel buttons=UiKit.transparent(new FlowLayout(FlowLayout.CENTER,14,0));buttons.add(leave);buttons.add(resume);
            card.add(buttons);
            add(card);
        }
        void focusResume(){SwingUtilities.invokeLater(resume::requestFocusInWindow);}
        @Override protected void paintComponent(Graphics g){
            g.setColor(new Color(4,12,40,175));g.fillRect(0,0,getWidth(),getHeight());super.paintComponent(g);
        }
    }
    private void updateClock(){
        if(clockSession==null)return;
        long now=System.currentTimeMillis(), pausedNow=paused?now-pauseStartedAt:0;
        long seconds=Math.max(0,(now-clockSession.started().toEpochMilli()-pausedMillis-pausedNow)/1000);
        long h=seconds/3600,m=(seconds%3600)/60,sec=seconds%60;
        clockLabel.setText(h>0?String.format("TIME  %d:%02d:%02d",h,m,sec):String.format("TIME  %02d:%02d",m,sec));
    }
    private void arenaRepaint(){screens.revalidate();screens.repaint();}
    private String availableMovesText(GameSession session) {
        if (session.dice() == null) return "-";
        boolean exit = false, shared = false, home = false;
        for (String id : session.legalPieceIds()) {
            Piece piece = session.findPiece(id);
            if (piece == null) continue;
            switch (piece.state()) {
                case CASTLE -> exit = true;
                case TRACK -> shared = true;
                case HOME -> home = true;
                default -> { }
            }
        }
        StringJoiner options = new StringJoiner(" / ");
        if (shared) options.add(session.dice().movement() + " TRACK");
        if (home) options.add(session.dice().regular() + " HOME");
        if (exit) options.add("EXIT");
        String result = options.toString();
        return result.isEmpty() ? "NO LEGAL MOVES" : result;
    }
    private void updatePlayers(GameSession session){
        leftPlayers.removeAll();rightPlayers.removeAll();
        for(int i=0;i<session.players().size();i++){
            Player p=session.players().get(i);
            boolean active=p==session.current()&&session.phase()!=GameSession.Phase.FINISHED;
            JPanel target=(i%2==0?leftPlayers:rightPlayers);
            target.add(new PlayerCard(p,active));target.add(Box.createVerticalStrut(12));
        }
        leftPlayers.revalidate();rightPlayers.revalidate();leftPlayers.repaint();rightPlayers.repaint();
    }
    /**
     * Player card: the name on a ribbon in the player's colour and the player's four heroes.
     * Heroes still in the castle are bright; a hero that left the castle is shown switched off (pale),
     * and one that reached home also gets a gold star.
     */
    private static final class PlayerCard extends JComponent {
        private final Player player; private final boolean active;
        PlayerCard(Player player,boolean active){
            this.player=player;this.active=active;
            setPreferredSize(new Dimension(212,96));setMaximumSize(new Dimension(214,96));setAlignmentX(LEFT_ALIGNMENT);
        }
        @Override protected void paintComponent(Graphics g){
            Graphics2D d=(Graphics2D)g.create();
            d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w=getWidth()-4,h=getHeight()-6,top=6;
            Color team=Theme.player(player.color());
            d.setColor(new Color(4,15,53,82));d.fill(new RoundRectangle2D.Double(1,top+4,w,h-2,16,16));
            d.setColor(new Color(24,57,132,225));d.fill(new RoundRectangle2D.Double(0,top,w,h-2,16,16));
            d.setColor(active?team:new Color(82,135,229,125));d.setStroke(new BasicStroke(active?3f:1f));
            d.draw(new RoundRectangle2D.Double(1,top+1,w-2,h-4,16,16));
            // name ribbon
            d.setColor(team);d.fill(new RoundRectangle2D.Double(10,top+8,w-20,24,10,10));
            d.setColor(Color.WHITE);d.setFont(Theme.font(13,Font.BOLD));
            String name=player.name();FontMetrics fm=d.getFontMetrics();
            int maxName=active?w-110:w-40;
            while(fm.stringWidth(name)>maxName && name.length()>3) name=name.substring(0,name.length()-2)+"…";
            d.drawString(name,20,top+25);
            if(active){                                                                   // "YOUR TURN" tag on the ribbon
                d.setFont(Theme.font(10,Font.BOLD));
                int tw=d.getFontMetrics().stringWidth("YOUR TURN")+14, tx=w-14-tw;
                d.setColor(Color.WHITE);d.fill(new RoundRectangle2D.Double(tx,top+11,tw,18,9,9));
                d.setColor(team);d.drawString("YOUR TURN",tx+7,top+24);
            }
            // the four heroes
            double slot=(w-20)/4.0;
            for(Piece piece:player.pieces()){
                double cx=10+slot*(piece.number()+.5), cy=top+52;
                boolean home=piece.state()==Piece.State.FINISHED, out=piece.state()!=Piece.State.CASTLE;
                // draw the hero on its own small image first, so a pale hero fades evenly
                java.awt.image.BufferedImage img=new java.awt.image.BufferedImage(64,64,java.awt.image.BufferedImage.TYPE_INT_ARGB);
                Graphics2D h2=img.createGraphics();BoardPanel.paintHero(h2,piece.number(),team,32,32,28,false);h2.dispose();
                Composite old=d.getComposite();
                if(out) d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,.3f));    // left the castle: shown switched off
                d.drawImage(img,(int)Math.round(cx-32),(int)Math.round(cy-32),null);
                d.setComposite(old);
                if(home){d.setColor(Theme.GOLD);d.setFont(Theme.font(15,Font.BOLD));d.drawString("★",(int)cx-6,(int)cy+6);}
            }
            // counts
            d.setFont(Theme.font(10,Font.BOLD));d.setColor(Theme.MUTED);
            String counts=player.finishedCount()+"/4 HOME   •   "+player.castleCount()+" IN CASTLE";
            d.drawString(counts,(w-d.getFontMetrics().stringWidth(counts))/2,top+h-9);
            d.dispose();
        }
    }
    public void showMatchEnd(GameSession session,String persistenceMessage){
        JDialog dialog=new JDialog(this,"CHEETAH LUDO  •  FINAL RANKINGS",true);
        UiKit.GradientPage root=new UiKit.GradientPage(new BorderLayout(0,16));
        root.setBorder(new EmptyBorder(25,30,25,30));
        JPanel title=UiKit.transparent(new GridLayout(2,1,0,6));
        title.add(UiKit.title("★  GAME COMPLETED!  ★",27));
        title.add(UiKit.sub(session.findPlayer(session.ranking().get(0)).name()+" won by finishing all four pieces!"));
        root.add(title,BorderLayout.NORTH);
        JPanel board=new UiKit.GlassCard(new BorderLayout(0,12));board.setBorder(new EmptyBorder(20,23,20,23));
        board.add(UiKit.goldLabel("FINAL RANKINGS"),BorderLayout.NORTH);
        JPanel scores=UiKit.transparent(new GridLayout(session.ranking().size(),1,0,8));int rank=1;
        for(PlayerColor color:session.ranking()){
            JLabel line=Theme.label("#"+rank+"     "+session.findPlayer(color).name()+"   •   "+color.label().toUpperCase()+
                         (rank==1?"   ★ WINNER":""),15,Font.BOLD,rank==1?Theme.GOLD:Theme.WHITE);
            scores.add(line);rank++;
        }
        board.add(scores,BorderLayout.CENTER);board.add(UiKit.sub(persistenceMessage),BorderLayout.SOUTH);
        root.add(board,BorderLayout.CENTER);
        JPanel controls=UiKit.transparent(new GridLayout(1,2,12,0));
        JButton again=Theme.button("↺  PLAY AGAIN",true);
        JButton menu=Theme.button("⌂  MAIN MENU",false);
        again.addActionListener(e->{dialog.dispose();if(actions!=null)actions.newGame();});
        menu.addActionListener(e->{dialog.dispose();if(actions!=null)actions.showMenu();});
        controls.add(again);controls.add(menu);root.add(controls,BorderLayout.SOUTH);
        dialog.setContentPane(root);dialog.setSize(570,500);dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }
    public void showInfo(String heading,String message){
        JTextArea area=UiKit.area(message,16);area.setEditable(false);
        JScrollPane s=new JScrollPane(area);s.setPreferredSize(new Dimension(625,380));
        JOptionPane.showMessageDialog(this,s,heading,JOptionPane.INFORMATION_MESSAGE);
    }
    public void showSurprise(String message){
        UiKit.notice(this,"SURPRISE EFFECT","✦  SURPRISE SQUARE  ✦",message,"APPLY SURPRISE EFFECT  →");
    }
    public void showError(Exception ex){JOptionPane.showMessageDialog(this,ex.getMessage(),"CHEETAH LUDO  •  ERROR",JOptionPane.ERROR_MESSAGE);}
    private void buildMenu(){
        JMenuBar b=new JMenuBar();JMenu game=new JMenu("Game");
        JMenuItem main=new JMenuItem("Main Menu");main.addActionListener(e->{if(actions!=null)actions.showMenu();});game.add(main);
        JMenuItem newGame=new JMenuItem("New Game");newGame.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N,Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        newGame.addActionListener(e->{if(actions!=null)actions.newGame();});game.add(newGame);
        JMenuItem hist=new JMenuItem("History");hist.addActionListener(e->{if(actions!=null)actions.history();});game.add(hist);
        JMenuItem exp=new JMenuItem("Export History");exp.addActionListener(e->{if(actions!=null)actions.exportHistory();});game.add(exp);
        JMenuItem admin=new JMenuItem("Question Administration");admin.addActionListener(e->{if(actions!=null)actions.questionAdmin();});game.add(admin);
        JMenuItem exit=new JMenuItem("Exit");exit.addActionListener(e->dispose());game.add(exit);
        JMenu help=new JMenu("Help");JMenuItem rules=new JMenuItem("Rules & Controls");
        rules.addActionListener(e->showInfo("HOW TO PLAY",
          "Roll 6 to bring a piece out of its castle. A six grants an extra roll, but three consecutive sixes end the turn.\n\n"+
          "Click one highlighted piece to move. You need an exact roll to reach Home.\n\n"+
          "Medium: regular die × (boost − 1). Hard: regular die × (UltraBoost − 1).\n\n"+
          "Protected star squares prevent captures. Double blocks stop opponent pieces from passing.\n\n"+
          "Question squares ask a question and move you forward/backward. Surprise squares have random effects.\n\n"+
          "Win by getting all four pieces Home. Keyboard: R rolls dice, P passes when permitted."));
        help.add(rules);b.add(game);b.add(help);setJMenuBar(b);
    }
    private void bindShortcut(int key,String id,Runnable action){
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key,0),id);
        getRootPane().getActionMap().put(id,new AbstractAction(){public void actionPerformed(ActionEvent e){action.run();}});
    }
    private static final class DieFace extends JComponent {
        private int value;
        DieFace(){setPreferredSize(new Dimension(62,62));}
        void setValue(int v){value=v;repaint();}
        @Override protected void paintComponent(Graphics g){
            Graphics2D d=(Graphics2D)g.create();d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            d.setColor(new Color(0,0,0,45));d.fillRoundRect(5,6,55,55,13,13);
            d.setPaint(new GradientPaint(0,0,new Color(255,255,255),55,55,new Color(195,227,255)));
            d.fillRoundRect(2,1,55,55,12,12);d.setColor(new Color(76,149,235));d.setStroke(new BasicStroke(2));d.drawRoundRect(2,1,55,55,12,12);
            d.setColor(new Color(24,87,196));
            if(value<=0){d.setFont(Theme.font(25,Font.BOLD));d.drawString("?",21,37);}
            else{int[][] pts={{15,14},{29,14},{43,14},{15,28},{29,28},{43,28},{15,42},{29,42},{43,42}};
                 int[][] index={{4},{0,8},{0,4,8},{0,2,6,8},{0,2,4,6,8},{0,2,3,5,6,8}};
                 for(int i:index[Math.min(6,value)-1])d.fillOval(pts[i][0]-3,pts[i][1]-4,8,8);}
            d.dispose();
        }
    }
    public void setOpponentSelectionIds(Set<String> ids) {
        boardPanel.setOpponentSelectionIds(ids);
    }
}