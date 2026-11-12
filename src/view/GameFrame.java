package view;

import javax.swing.*;
import javax.swing.border.*;

import model.*;

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
    private final JLabel diceDetails=Theme.label("Roll to begin",12,Font.BOLD,Theme.WHITE);
    private final JLabel phaseLabel=Theme.label("READY TO ROLL",12,Font.BOLD,Theme.SUCCESS);
    private final JLabel instruction=Theme.label("Choose Roll Dice to begin",13,Font.BOLD,Theme.MUTED);
    private final JButton rollButton=Theme.button("ROLL DICE  ⚄",true);
    private final JButton passButton=Theme.button("PASS TURN",false);
    private final JPanel leftPlayers=UiKit.transparent();
    private final JPanel rightPlayers=UiKit.transparent();
    private final DieFace regularFace=new DieFace(),specialFace=new DieFace();
    private final JLabel multiplier=Theme.label("",12,Font.BOLD,Theme.GOLD);
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
    private void show(String key){currentScreen=key;screenLayout.show(screens,key);screens.revalidate();screens.repaint();}
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
        UiKit.GradientPage root=new UiKit.GradientPage(new BorderLayout(9,12));
        root.setBorder(new EmptyBorder(11,22,16,22));
        JPanel head=UiKit.transparent(new BorderLayout(8,4));
        JButton menu=Theme.button("⌂  MENU",false);menu.addActionListener(e->{if(actions!=null)actions.showMenu();});
        JButton exit=Theme.button("EXIT GAME",false);exit.addActionListener(e->{if(actions!=null)actions.showMenu();});
        head.add(menu,BorderLayout.WEST);head.add(exit,BorderLayout.EAST);
        JPanel title=UiKit.transparent(new GridLayout(2,1,0,1));
        modeLabel.setHorizontalAlignment(SwingConstants.CENTER);title.add(modeLabel);
        JLabel subtitle=UiKit.sub("CHEETAH LUDO  •  LIVE LOCAL MATCH");title.add(subtitle);
        head.add(title,BorderLayout.CENTER);
        JPanel ribbon=UiKit.transparent(new FlowLayout(FlowLayout.CENTER,12,1));
        ribbon.add(UiKit.chip("★ Safe Squares",Theme.GOLD));
        ribbon.add(UiKit.chip("? Question Squares",Theme.SUCCESS));
        ribbon.add(UiKit.chip("▣ Surprise Squares",new Color(235,122,255)));
        ribbon.add(turnCounter);
        JPanel headWrap=UiKit.transparent(new BorderLayout(0,5));headWrap.add(head,BorderLayout.NORTH);headWrap.add(ribbon,BorderLayout.SOUTH);
        root.add(headWrap,BorderLayout.NORTH);

        JPanel arena=UiKit.transparent(new BorderLayout(12,0));
        leftPlayers.setLayout(new BoxLayout(leftPlayers,BoxLayout.Y_AXIS));
        rightPlayers.setLayout(new BoxLayout(rightPlayers,BoxLayout.Y_AXIS));
        JPanel left=UiKit.transparent(new GridBagLayout());left.setPreferredSize(new Dimension(186,400));left.add(leftPlayers);
        JPanel right=UiKit.transparent(new GridBagLayout());right.setPreferredSize(new Dimension(186,400));right.add(rightPlayers);
        arena.add(left,BorderLayout.WEST);arena.add(right,BorderLayout.EAST);
        JPanel boardShell=new UiKit.GlassCard(new BorderLayout());boardShell.setBorder(new EmptyBorder(7,7,7,7));
        boardPanel.setOpaque(false);boardPanel.setPreferredSize(new Dimension(600,600));
        boardShell.add(boardPanel,BorderLayout.CENTER);arena.add(boardShell,BorderLayout.CENTER);
        root.add(arena,BorderLayout.CENTER);

        JPanel hud=new UiKit.GlassCard(new BorderLayout(13,0));hud.setBorder(new EmptyBorder(11,17,12,17));
        JPanel dice=UiKit.transparent(new FlowLayout(FlowLayout.LEFT,8,0));
        dice.add(regularFace);dice.add(multiplier);dice.add(specialFace);
        JPanel diceNumbers=UiKit.transparent(new GridLayout(3,1,0,3));
        diceNumbers.add(UiKit.goldLabel("DICE & MOVEMENT"));diceNumbers.add(diceDetails);diceNumbers.add(turnLabel);
        dice.add(diceNumbers);hud.add(dice,BorderLayout.WEST);
        JPanel status=UiKit.transparent(new GridLayout(2,1,0,5));
        phaseLabel.setHorizontalAlignment(SwingConstants.CENTER);instruction.setHorizontalAlignment(SwingConstants.CENTER);
        status.add(phaseLabel);status.add(instruction);hud.add(status,BorderLayout.CENTER);
        JPanel controls=UiKit.transparent(new GridLayout(2,1,0,7));
        controls.add(rollButton);controls.add(passButton);controls.setPreferredSize(new Dimension(184,91));hud.add(controls,BorderLayout.EAST);
        root.add(hud,BorderLayout.SOUTH);return root;
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
        specialFace.setVisible(showSpecial);multiplier.setText(showSpecial?"×":"CLASSIC");
        diceDetails.setText(roll==null?"READY TO ROLL":"MOVEMENT  "+roll.movement()+"  (×"+roll.multiplier()+")");
        String text=session.status();
        if(text.length()>100)text=text.substring(0,97)+"…";
        instruction.setText(text);
        updatePlayers(session);arenaRepaint();
    }
    private void arenaRepaint(){screens.revalidate();screens.repaint();}
    private void updatePlayers(GameSession session){
        leftPlayers.removeAll();rightPlayers.removeAll();
        for(int i=0;i<session.players().size();i++){
            Player p=session.players().get(i);
            JPanel card=new UiKit.GlassCard(new BorderLayout(4,8),16);
            card.setBorder(new EmptyBorder(12,12,11,12));
            card.setPreferredSize(new Dimension(182,105));
            card.setMaximumSize(new Dimension(185,115));
            boolean active=p==session.current()&&session.phase()!=GameSession.Phase.FINISHED;
            JLabel name=Theme.label("●  "+p.name(),12,Font.BOLD,active?Theme.GOLD:Theme.WHITE);
            name.setForeground(active?Theme.GOLD:Theme.player(p.color()));card.add(name,BorderLayout.NORTH);
            JPanel circles=UiKit.transparent(new FlowLayout(FlowLayout.LEFT,5,0));
            for(int j=0;j<4;j++)circles.add(Theme.label(j<p.finishedCount()?"★":"●",16,Font.BOLD,
                    j<p.finishedCount()?Theme.GOLD:Theme.player(p.color())));
            card.add(circles,BorderLayout.CENTER);
            card.add(Theme.label(p.finishedCount()+" / 4 HOME   •   "+p.castleCount()+" CASTLE",10,Font.PLAIN,Theme.MUTED),BorderLayout.SOUTH);
            JPanel target=(i%2==0?leftPlayers:rightPlayers);target.add(card);target.add(Box.createVerticalStrut(13));
        }
        leftPlayers.revalidate();rightPlayers.revalidate();
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
}
