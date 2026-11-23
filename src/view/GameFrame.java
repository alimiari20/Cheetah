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
    private final DieFace regularFace=new DieFace(false),specialFace=new DieFace(true);
    private final JLabel multiplier=Theme.label("",12,Font.BOLD,Theme.GOLD);
    private final JLabel specialDieLabel =
    	    Theme.label("BOOST DIE", 11, Font.BOLD, Theme.MUTED);
    private final JTextArea activity=UiKit.area("",4);
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
        bindShortcut(KeyEvent.VK_R,"roll",()->{if(actions!=null && isGameVisible())actions.roll();});
        bindShortcut(KeyEvent.VK_P,"pass",()->{if(actions!=null && isGameVisible())actions.pass();});
        buildMenu();
    }
    public void setActions(Actions a){actions=Objects.requireNonNull(a);}
    public boolean isGameVisible(){return "GAME".equals(currentScreen);}
    private void show(String key){if(!"GAME".equals(key))clock.stop();currentScreen=key;screenLayout.show(screens,key);screens.revalidate();screens.repaint();}
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
        JPanel panel=new UiKit.GlassCard(new GridLayout(0,1,0,10));panel.setBorder(new EmptyBorder(16,16,16,16));
        panel.setPreferredSize(new Dimension(365,247));panel.setMaximumSize(new Dimension(365,250));
        JButton play=Theme.button("▶   NEW GAME",true);play.addActionListener(e->{if(actions!=null)actions.newGame();});
        JButton history=Theme.button("↺   GAME HISTORY",false);history.addActionListener(e->{if(actions!=null)actions.history();});
        adminTools.addActionListener(e->{if(actions!=null)actions.questionAdmin();});adminTools.setVisible(false);
        JButton exit=Theme.button("EXIT",false);exit.addActionListener(e->{
            if(UiKit.confirm(this,"Exit","EXIT CHEETAH LUDO?","Are you sure you want to close the game?","EXIT GAME"))dispose();});
        panel.add(play);panel.add(history);panel.add(adminTools);panel.add(exit);
        stack.add(panel);center.add(stack);page.add(center,BorderLayout.CENTER);
        JLabel bottom=UiKit.sub("Cheetah Ludo  •  Local Multiplayer  •  Java Swing MVC  •  Windows & macOS");
        page.add(bottom,BorderLayout.SOUTH);return page;
    }
    private JPanel buildGameScreen(){
        UiKit.NightPage root=new UiKit.NightPage(new BorderLayout(16,10));
        root.setBorder(new EmptyBorder(12,20,14,20));

        // top bar: logo | mode + time | menu + exit
        JPanel head=UiKit.transparent(new BorderLayout(12,0));
        head.add(new PixelLogo(),BorderLayout.WEST);
        JPanel info=new UiKit.GlassCard(new FlowLayout(FlowLayout.CENTER,22,4));
        info.setBorder(new EmptyBorder(5,14,9,18));
        modeLabel.setFont(Theme.pixel(12));clockLabel.setFont(Theme.pixel(12));
        info.add(modeLabel);info.add(clockLabel);
        JPanel infoHolder=UiKit.transparent(new FlowLayout(FlowLayout.CENTER,0,0));infoHolder.add(info);
        head.add(infoHolder,BorderLayout.CENTER);
        JPanel exits=UiKit.transparent(new FlowLayout(FlowLayout.RIGHT,10,0));
        JButton menu=Theme.button("MENU",false);menu.addActionListener(e->{if(actions!=null)actions.showMenu();});
        JButton exit=Theme.button("EXIT",false);exit.addActionListener(e->{if(actions!=null)actions.showMenu();});
        for(JButton b:new JButton[]{menu,exit}){b.setFont(Theme.pixel(12));b.setPreferredSize(new Dimension(112,44));exits.add(b);}
        head.add(exits,BorderLayout.EAST);
        root.add(head,BorderLayout.NORTH);

        // left: player cards
        leftPlayers.setLayout(new BoxLayout(leftPlayers,BoxLayout.Y_AXIS));
        JPanel left=UiKit.transparent(new BorderLayout());left.setPreferredSize(new Dimension(250,400));
        left.add(leftPlayers,BorderLayout.NORTH);
        root.add(left,BorderLayout.WEST);

        // centre: the board, as big as the window allows
        boardPanel.setOpaque(false);boardPanel.setPreferredSize(new Dimension(640,640));
        root.add(boardPanel,BorderLayout.CENTER);

        // right: everything about the current turn
        JPanel turn=new UiKit.GlassCard(new GridBagLayout());
        turn.setBorder(new EmptyBorder(16,16,20,20));
        turn.setPreferredSize(new Dimension(270,400));
        GridBagConstraints c=new GridBagConstraints();
        c.gridx=0;c.fill=GridBagConstraints.HORIZONTAL;c.weightx=1;c.insets=new Insets(0,0,10,0);
        turnLabel.setHorizontalAlignment(SwingConstants.CENTER);turn.add(turnLabel,c);
        JPanel dice=UiKit.transparent(new FlowLayout(FlowLayout.CENTER,14,0));
        JPanel regularGroup=UiKit.transparent(new BorderLayout(0,4));
        JLabel regularDieLabel=Theme.label("DIE",11,Font.BOLD,Theme.MUTED);regularDieLabel.setFont(Theme.pixel(8));
        regularDieLabel.setHorizontalAlignment(SwingConstants.CENTER);
        regularGroup.add(regularFace,BorderLayout.CENTER);regularGroup.add(regularDieLabel,BorderLayout.SOUTH);
        JPanel specialGroup=UiKit.transparent(new BorderLayout(0,4));
        specialDieLabel.setFont(Theme.pixel(8));specialDieLabel.setHorizontalAlignment(SwingConstants.CENTER);
        specialGroup.add(specialFace,BorderLayout.CENTER);specialGroup.add(specialDieLabel,BorderLayout.SOUTH);
        dice.add(regularGroup);dice.add(specialGroup);
        dice.setMinimumSize(new Dimension(200,96));                   // never squash the dice
        turn.add(dice,c);
        multiplier.setHorizontalAlignment(SwingConstants.CENTER);multiplier.setFont(Theme.pixel(10));
        turn.add(multiplier,c);
        JPanel result=UiKit.transparent(new GridLayout(2,2,10,2));
        result.add(Theme.label("REGULAR ROLL",10,Font.BOLD,Theme.MUTED));result.add(movementTitle);
        result.add(resultValue);result.add(movementValue);
        movementTitle.setFont(Theme.font(10,Font.BOLD));resultValue.setFont(Theme.pixel(20));movementValue.setFont(Theme.font(12,Font.BOLD));
        turn.add(result,c);
        phaseLabel.setHorizontalAlignment(SwingConstants.CENTER);instruction.setHorizontalAlignment(SwingConstants.CENTER);
        phaseLabel.setFont(Theme.font(11,Font.BOLD));
        turn.add(phaseLabel,c);turn.add(instruction,c);
        rollButton.setText("ROLL DICE");rollButton.setFont(Theme.pixel(14));rollButton.setPreferredSize(new Dimension(220,56));
        passButton.setFont(Theme.pixel(10));passButton.setPreferredSize(new Dimension(220,44));
        c.insets=new Insets(8,0,10,0);turn.add(rollButton,c);
        c.insets=new Insets(0,0,0,0);turn.add(passButton,c);
        JLabel logTitle=Theme.label("LAST MOVES",10,Font.BOLD,Theme.GOLD);logTitle.setFont(Theme.pixel(9));
        c.insets=new Insets(22,0,6,0);turn.add(logTitle,c);
        activity.setEditable(false);activity.setFocusable(false);activity.setOpaque(false);
        activity.setFont(Theme.font(12,Font.PLAIN));activity.setForeground(new Color(200,215,245));activity.setBorder(null);
        activity.setColumns(1);                                       // width comes from the panel, text wraps
        // the list sits in a scroll pane of fixed size, so a long history never squeezes the dice and buttons
        JScrollPane log=new JScrollPane(activity,ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER,ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        log.setOpaque(false);log.getViewport().setOpaque(false);log.setBorder(null);
        log.setPreferredSize(new Dimension(220,120));log.setMinimumSize(new Dimension(100,40));
        c.insets=new Insets(0,0,0,0);c.weighty=1;c.fill=GridBagConstraints.BOTH;turn.add(log,c);
        root.add(turn,BorderLayout.EAST);
        return root;
    }
    /** "CHEETAH LUDO" written in the pixel font, gold and white with a hard shadow. */
    private static final class PixelLogo extends JComponent {
        PixelLogo(){setPreferredSize(new Dimension(300,44));}
        @Override protected void paintComponent(Graphics g){
            Graphics2D d=(Graphics2D)g.create();
            d.setFont(Theme.pixel(22));
            int y=32, x=8, gap=d.getFontMetrics().stringWidth("CHEETAH ");
            d.setColor(new Color(4,10,30));d.drawString("CHEETAH",x+3,y+3);d.drawString("LUDO",x+gap+3,y+3);
            d.setColor(Theme.GOLD);d.drawString("CHEETAH",x,y);
            d.setColor(Theme.WHITE);d.drawString("LUDO",x+gap,y);
            d.dispose();
        }
    }
    public void render(GameSession session){
        boardPanel.setSession(session);modeLabel.setText(session.difficulty().label().toUpperCase());
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

        specialDieLabel.setText(
            (roll != null && roll.ultraBoost()) || session.difficulty() == Difficulty.HARD
                ? "ULTRABOOST DIE"
                : "BOOST DIE"
        );

        multiplier.setText(
            showSpecial && roll != null
                ? "×" + roll.multiplier()
                : ""
        );
        diceDetails.setText(roll==null ? "READY TO ROLL"
                : "SHARED " + roll.movement() + "  |  HOME " + roll.regular());
        resultValue.setText(
        	    roll == null ? "–" : Integer.toString(roll.regular())
        	);

        movementValue.setText(availableMovesText(session));
        	boolean advanced = session.difficulty() != Difficulty.EASY;

        	movementTitle.setVisible(advanced);
        	movementValue.setVisible(advanced);

String text;

if (session.phase() == GameSession.Phase.FINISHED) {
    text = "Game completed!";
} else if (session.phase() == GameSession.Phase.ROLL) {
    text = "Roll the dice to begin.";
} else if (session.legalPieceIds().isEmpty()) {
    text = "NO LEGAL MOVES — PASS TURN";
} else {
    text = "Select a glowing piece to move.";
}

instruction.setText(text);

        List<String> events=session.events();                       // newest moves first
        StringBuilder log=new StringBuilder();
        for(int i=events.size()-1;i>=Math.max(0,events.size()-6);i--) log.append("> ").append(events.get(i)).append('\n');
        activity.setText(log.toString().trim());activity.setCaretPosition(0);   // keep the newest move in view
        updatePlayers(session);
        clockSession=session;updateClock();
        if(session.phase()==GameSession.Phase.FINISHED)clock.stop();else if(!clock.isRunning())clock.start();
        arenaRepaint();
    }
    private void updateClock(){
        if(clockSession==null)return;
        long seconds=Math.max(0,java.time.Duration.between(clockSession.started(),java.time.Instant.now()).getSeconds());
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
        leftPlayers.removeAll();
        for(Player p:session.players()){
            boolean active=p==session.current()&&session.phase()!=GameSession.Phase.FINISHED;
            leftPlayers.add(new PlayerCard(p,active));leftPlayers.add(Box.createVerticalStrut(12));
        }
        leftPlayers.revalidate();leftPlayers.repaint();
    }
    /** Player card: name, "YOUR TURN" tag, the player's four heroes and how many are home / in the castle. */
    private static final class PlayerCard extends JComponent {
        private final Player player; private final boolean active;
        PlayerCard(Player player,boolean active){
            this.player=player;this.active=active;
            setPreferredSize(new Dimension(246,140));setMaximumSize(new Dimension(250,140));setAlignmentX(LEFT_ALIGNMENT);
        }
        @Override protected void paintComponent(Graphics g){
            Graphics2D d=(Graphics2D)g.create();
            int w=getWidth()-6,h=getHeight()-8,top=8;
            Color team=Theme.player(player.color());
            d.setColor(new Color(4,10,30,170));d.fillRect(4,top+4,w,h);                       // hard shadow
            d.setColor(active?team:new Color(40,62,128));d.fillRect(0,top,w,h);                  // border
            d.setColor(new Color(16,36,96));d.fillRect(4,top+4,w-8,h-8);                         // fill
            d.setColor(team);d.fillRect(16,top+18,12,12);                                        // colour square
            d.setFont(Theme.pixel(12));d.setColor(Theme.WHITE);
            d.drawString(player.name().toUpperCase(),36,top+30);
            if(active){                                                                          // "YOUR TURN" tag
                d.setFont(Theme.pixel(8));
                int tw=d.getFontMetrics().stringWidth("YOUR TURN")+14, tx=w-tw-8;
                d.setColor(new Color(10,16,44));d.fillRect(tx,0,tw,22);
                d.setColor(Theme.GOLD);d.fillRect(tx+2,2,tw-4,18);
                d.setColor(new Color(10,16,44));d.drawString("YOUR TURN",tx+7,15);
            }
            for(Piece piece:player.pieces()){                                                  // the four heroes
                double cx=40+piece.number()*((w-44)/4.0), cy=top+72;
                boolean done=piece.state()==Piece.State.FINISHED;
                BoardPanel.paintHero(d,piece.number(),team,cx,cy,44,false,false);
                if(done){d.setColor(Theme.GOLD);d.setFont(Theme.pixel(10));d.drawString("\u2605",(int)cx-5,(int)cy+34);}
            }
            d.setFont(Theme.pixel(8));d.setColor(Theme.MUTED);
            d.drawString(player.finishedCount()+"/4 HOME   "+player.castleCount()+" CASTLE",16,top+h-14);
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
    /** Pixel-style die: white (or gold for the boost die) with square pips, solid outline and a hard shadow. */
    private static final class DieFace extends JComponent {
        private int value; private final boolean boost;
        DieFace(boolean boost){this.boost=boost;setPreferredSize(new Dimension(68,68));}
        void setValue(int v){value=v;repaint();}
        @Override protected void paintComponent(Graphics g){
            Graphics2D d=(Graphics2D)g.create();
            int s=60; Color ink=new Color(10,16,44);
            d.setColor(new Color(4,10,30,190));d.fillRect(6,6,s,s);                         // hard shadow
            d.setColor(ink);d.fillRect(0,0,s,s);                                             // outline
            d.setColor(boost?new Color(255,226,130):new Color(250,250,252));d.fillRect(4,4,s-8,s-8);
            d.setColor(boost?new Color(255,240,190):Color.WHITE);d.fillRect(4,4,s-8,4);     // top shine
            d.setColor(ink);
            if(value<=0){d.setFont(Theme.pixel(22));d.drawString("?",20,42);}
            else{int[][] pts={{17,17},{30,17},{43,17},{17,30},{30,30},{43,30},{17,43},{30,43},{43,43}};
                 int[][] index={{4},{0,8},{0,4,8},{0,2,6,8},{0,2,4,6,8},{0,2,3,5,6,8}};
                 for(int i:index[Math.min(6,value)-1])d.fillRect(pts[i][0]-5,pts[i][1]-5,10,10);}
            d.dispose();
        }
    }
    public void setOpponentSelectionIds(Set<String> ids) {
        boardPanel.setOpponentSelectionIds(ids);
    }
}