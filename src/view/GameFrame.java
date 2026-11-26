package view;

import static view.Theme.Tone.*;

import java.util.StringJoiner;
import javax.swing.*;
import javax.swing.border.*;

import model.*;
import security.AdminAudit;
import java.util.Set;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.List;
import java.util.Objects;

/** Prototype-inspired desktop shell: real menu/setup/game/history/admin screens. */
public final class GameFrame extends JFrame {
    public interface Actions {
        void openAdminDashboard();
        void showAdminSecurity();
        void showSecurityDashboard();
    	void logoutAdmin();
        void newGame();
        void startConfiguredGame(SetupDialog.Config config);
        GameSession preview(Difficulty difficulty,List<PlayerColor> colors);
        void showMenu();
        void editAdminAccount();
        void roll();
        void pass();
        void selectPiece(String id);
        void history();
        void exportHistory();
        void questionAdmin();
        void saveQuestion(Question question);
        void deleteQuestion(String questionId);
        default void gameAvailabilityChanged() {}
    }
    private Actions actions;
    private java.util.Map<PlayerColor,AiLevel> playerControls = java.util.Map.of();
    private final CardLayout screenLayout=new CardLayout();
    private final JPanel screens=new JPanel(screenLayout);
    private final GlobalHeader header=new GlobalHeader();
    private final SecurityDashboardPage securityDashboard = new SecurityDashboardPage(this::showMainMenu);
    private final SetupPage setupPage;
    private final HistoryPage historyPage;
    private final QuestionAdminPage adminPage;
    private final BoardPanel boardPanel=new BoardPanel();
    private final JLabel modeLabel=UiKit.goldLabel("CLASSIC MODE");
    private final JLabel turnLabel=Theme.label("Player 1",14,Font.BOLD,ACCENT);
    private final JLabel turnCounter=Theme.label("TURN 01",11,Font.BOLD,MUTED);
    /** Live total game duration (SRS 3.2.2.3.8), refreshed every second while a match is running. */
    private final JLabel clockLabel=Theme.label("TIME  00:00",12,Font.BOLD,ACCENT);
    private final Timer clock=new Timer(1000,e->updateClock());
    private GameSession clockSession;
    // pause: the screen is covered, buttons and keys do nothing, and the clock does not count the paused time
    private final PauseOverlay pauseOverlay=new PauseOverlay();
    private boolean paused;
    private long pausedMillis;                 // total paused time in the current game
    private long pauseStartedAt;
    private final JLabel diceDetails=Theme.label("Roll to begin",12,Font.BOLD,TEXT);
    private final JLabel movementTitle =
            Theme.label("AVAILABLE MOVES", 11, Font.BOLD, MUTED);
    private final JLabel resultValue =
            Theme.label("–", 30, Font.BOLD, ACCENT);

    	private final JLabel movementValue =
            Theme.label("READY", 14, Font.BOLD, ACCENT);
    private final JLabel phaseLabel=Theme.label("READY TO ROLL",12,Font.BOLD,SUCCESS);
    private final JLabel instruction=Theme.label("Choose Roll Dice to begin",13,Font.BOLD,MUTED);
    private final JButton rollButton=Theme.button("ROLL DICE  ⚄",true);
    private final JButton passButton=Theme.button("PASS TURN",false);
    private final JPanel leftPlayers=UiKit.transparent();
    private final JPanel rightPlayers=UiKit.transparent();
    private final DieFace regularFace=new DieFace(),specialFace=new DieFace();
    private final JLabel multiplier=Theme.label("",12,Font.BOLD,ACCENT);
    private final JLabel specialDieLabel =
            Theme.label("BOOST DIE", 11, Font.BOLD, MUTED);
    /** Move history: one short, coloured line per event, newest first, grouped by turn. */
    private final JPanel history=new JPanel();
    private final AdminDashboardPage dashboard;
    private JMenuItem homeMenuItem;
    private JMenuItem newGameMenuItem;
    private boolean adminMode;
    private String currentScreen="MENU";

    public GameFrame(){
        super("Cheetah Ludo");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        Rectangle r=GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setSize(Math.min(1390,r.width),Math.min(910,r.height));
        setMinimumSize(new Dimension(Math.min(890,r.width),Math.min(670,r.height)));
        setLocationRelativeTo(null);
        screens.add(new PlayerMenuPage(
                () -> actions.newGame(), () -> actions.history(),
                () -> actions.openAdminDashboard(),
                this::showRules, this::confirmExit), "MENU");
        dashboard = new AdminDashboardPage(
                () -> actions.logoutAdmin(), () -> actions.questionAdmin(),
                () -> actions.history(), () -> actions.editAdminAccount(),
                () -> actions.showAdminSecurity(), () -> actions.showSecurityDashboard());
        screens.add(dashboard, "DASHBOARD");
        screens.add(securityDashboard, "SECURITY");
        setupPage=new SetupPage(()->{if(actions!=null)actions.showMenu();},config->{if(actions!=null)actions.startConfiguredGame(config);});
        screens.add(setupPage,"SETUP");
        screens.add(buildGameScreen(),"GAME");
        historyPage=new HistoryPage(()->{if(actions!=null)actions.showMenu();},()->{if(actions!=null)actions.exportHistory();});
        screens.add(historyPage,"HISTORY");
        adminPage=new QuestionAdminPage(()->{if(actions!=null)actions.showMenu();},
                q->{if(actions!=null)actions.saveQuestion(q);},id->{if(actions!=null)actions.deleteQuestion(id);});
        screens.add(adminPage,"ADMIN");
        JPanel shell=GlobalHeader.wrap(screens,header);
        Messages.bind(shell, () -> setTitle(Messages.get("Cheetah Ludo")));
        setContentPane(shell);
        boardPanel.setOnSelect(id->{if(actions!=null)actions.selectPiece(id);});
        rollButton.addActionListener(e->{if(actions!=null)actions.roll();});
        passButton.addActionListener(e->{if(actions!=null)actions.pass();});
        bindShortcut(KeyEvent.VK_R,"roll",()->{if(actions!=null && isGameVisible() && !paused)actions.roll();});
        bindShortcut(KeyEvent.VK_P,"pass",()->{if(actions!=null && isGameVisible() && !paused)actions.pass();});
        bindShortcut(KeyEvent.VK_ESCAPE,"pause",()->{if(isGameVisible())setPaused(!paused);});
        setGlassPane(pauseOverlay);
        buildMenu();
        Messages.refresh(this);
    }
    public void openCaptureLab(GameEngine engine) {
        setJMenuBar(null);
        JPanel shell=GlobalHeader.wrap(new CaptureLab(engine, boardPanel),header);
        Messages.bind(shell, () -> setTitle(Messages.get("Cheetah Ludo — Capture Lab")));
        setContentPane(shell);
        Messages.refresh(this);
        setVisible(true);
    }
    @Override public void dispose() {
        boardPanel.cancelCapture();
        clock.stop();
        currentScreen = "MENU";
        if (actions != null) actions.gameAvailabilityChanged();
        super.dispose();
    }
    public void setActions(Actions a){actions=Objects.requireNonNull(a);}
    public boolean isGameVisible(){return "GAME".equals(currentScreen);}
    private void show(String key){if(!"GAME".equals(key)){boardPanel.cancelCapture();clock.stop();if(paused)setPaused(false);}currentScreen=key;screenLayout.show(screens,key);screens.revalidate();screens.repaint();if(actions!=null)actions.gameAvailabilityChanged();}
    public void showMainMenu(){show(adminMode ? "DASHBOARD" : "MENU");}
    public void showSetup(){setupPage.setPreviewProvider((diff,colors)->actions.preview(diff,colors));show("SETUP");}
    public void showGame(){show("GAME");}
    public void showHistory(List<HistoryRepository.Entry> entries){historyPage.setEntries(entries);show("HISTORY");}
    public void showQuestionAdmin(List<Question> questions){adminPage.showQuestions(questions);show("ADMIN");}
    public void updateQuestionAdmin(List<Question> questions){adminPage.showQuestions(questions);}

    private void confirmExit() {
        if (UiKit.confirm(this, "Exit", "EXIT CHEETAH LUDO?",
                "Are you sure you want to close the game?", "EXIT GAME")) dispose();
    }
    private JPanel buildGameScreen(){
        UiKit.GradientPage root=new UiKit.GradientPage(new BorderLayout(10,6));
        root.setBorder(new EmptyBorder(8,14,10,14));
        JPanel head=UiKit.transparent(new BorderLayout(8,4));
        JButton menu=Theme.button("⌂  MENU",false);menu.addActionListener(e->{if(actions!=null)actions.showMenu();});
        JButton exit=Theme.button("EXIT GAME",false);exit.addActionListener(e->{if(actions!=null)actions.showMenu();});
        JButton pause=Theme.button("",false);pause.addActionListener(e->setPaused(true));
        pause.setIcon(new PauseIcon());Messages.bind(pause, () -> pause.setToolTipText(Messages.get("Pause (Esc)")));               // just the pause symbol
        pause.setPreferredSize(new Dimension(64,menu.getPreferredSize().height));
        JPanel leftButtons=UiKit.transparent(new FlowLayout(FlowLayout.LEADING,8,0));leftButtons.add(menu);leftButtons.add(pause);
        head.add(leftButtons,BorderLayout.LINE_START);head.add(exit,BorderLayout.LINE_END);
        JPanel title=UiKit.transparent(new BorderLayout(12,0));
        title.add(modeLabel,BorderLayout.LINE_START);
        title.add(UiKit.sub("CHEETAH LUDO"),BorderLayout.CENTER);
        title.add(clockLabel,BorderLayout.LINE_END);
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
        root.add(sideColumn(leftPlayers,diceCard),BorderLayout.LINE_START);

        // right side: players + the history of the game, as long as there is room
        JPanel historyCard=new UiKit.GlassCard(new BorderLayout(0,6));
        historyCard.setBorder(new EmptyBorder(14,14,14,14));
        historyCard.add(UiKit.goldLabel("LAST MOVES"),BorderLayout.NORTH);
        history.setLayout(new BoxLayout(history,BoxLayout.Y_AXIS));history.setOpaque(false);
        JPanel historyTop=UiKit.transparent(new BorderLayout());historyTop.add(history,BorderLayout.NORTH);
        JScrollPane log=new JScrollPane(historyTop,ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        log.setOpaque(false);log.getViewport().setOpaque(false);log.setBorder(BorderFactory.createEmptyBorder());
        log.getVerticalScrollBar().setUnitIncrement(12);
        historyCard.add(log,BorderLayout.CENTER);
        root.add(sideColumn(rightPlayers,historyCard),BorderLayout.LINE_END);
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
            for(String[] item:ITEMS) total+=22+fm.stringWidth(Messages.get(item[1]));
            total+=gap*(ITEMS.length-1);
            int x=(getWidth()-total)/2, y=getHeight()/2;
            if (!Theme.isDarkMode()) {
                d.setColor(SECONDARY.color());
                d.fillRoundRect(Math.max(0,x-7),0,Math.min(getWidth(),total+14),getHeight(),12,12);
            }
            for(String[] item:ITEMS){
                BoardPanel.paintLegendIcon(d,item[0],x+9,y,22);
                d.setColor(MUTED.color());d.drawString(Messages.get(item[1]),x+22,y+5);
                x+=22+fm.stringWidth(Messages.get(item[1]))+gap;
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
                JLabel head=Theme.label("TURN "+Integer.parseInt(turnNo.isEmpty()?"0":turnNo),10,Font.BOLD,ACCENT);
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
        Player player = who;
        String rest=player==null?body:body.substring(player.name().length()).trim();
        JLabel row=new JLabel();
        row.setFont(Theme.font(12,Font.PLAIN));Theme.foreground(row,TEXT);
        Runnable update = () -> {
            String name=player==null?"":"<b style='color:"+hex(Theme.playerText(player.color()).color())+"'>"+esc(player.name())+"</b> ";
            String text=Messages.isHebrew() ? esc(Messages.get(body)) : name+describe(rest,body);
            row.setText("<html><div dir='"+(Messages.isHebrew()?"rtl":"ltr")
                    +"' style='width:118px;text-align:"+(Messages.isHebrew()?"right":"left")+"'>"+text+"</div></html>");
            row.setIcon(new Marker(player==null?ACCENT.color():Theme.player(player.color())));
        };
        Theme.onAppearance(row, update);
        Messages.bind(row, update);
        row.setIconTextGap(7);
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
            return roll+(m.group(4).startsWith("No")?" <span style='color:" + hex(DANGER.color()) + "'>— no move possible</span>":"");
        }
        if(rest.startsWith("has no legal move")) return "<span style='color:" + hex(DANGER.color()) + "'>passed the turn (no move)</span>";
        if((m=java.util.regex.Pattern.compile("entered the board with piece (\\d)(.*)").matcher(rest)).find())
            return "<b>"+hero(m.group(1))+"</b> left the castle"+extras(m.group(2));
        if((m=java.util.regex.Pattern.compile("moved piece (\\d) by (\\d+) squares(.*)").matcher(rest)).find())
            return "<b>"+hero(m.group(1))+"</b> moved "+m.group(2)+" squares"+extras(m.group(3));
        if(rest.startsWith("answered ")) return "<span style='color:"+hex((rest.contains("incorrectly")?DANGER:SUCCESS).color())+"'>"+esc(rest)+"</span>";
        if(full.startsWith("Surprise: ")) return "<b style='color:" + hex(SURPRISE.color()) + "'>Surprise!</b> "+esc(full.substring(10));
        if(rest.startsWith("rolled three consecutive")) return "rolled three 6s in a row — roll canceled";
        if(rest.startsWith("finished in place")) return "<b style='color:" + hex(ACCENT.color()) + "'>"+esc(rest)+"</b>";
        if(full.startsWith("Game over")) return "<b style='color:" + hex(ACCENT.color()) + "'>"+esc(full)+"</b>";
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
            out.append(" and <b style='color:" + hex(DANGER.color()) + "'>captured ").append(victims).append("</b> — sent back to the castle");
        }
        if(tail.contains("reached HOME")) out.append(" and <b style='color:" + hex(ACCENT.color()) + "'>reached HOME ★</b>");
        return out.toString();
    }
    private static String hero(String number){int n=Integer.parseInt(number)-1;return n>=0&&n<HERO_NAMES.length?HERO_NAMES[n]:"piece "+number;}
    private static String esc(String t){return t.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");}
    private static String hex(Color c){return String.format("#%02x%02x%02x",c.getRed(),c.getGreen(),c.getBlue());}
    /** Pause symbol: two white rounded bars. */
    private static final class PauseIcon implements Icon {
        public int getIconWidth(){return 18;} public int getIconHeight(){return 20;}
        public void paintIcon(Component c,Graphics g,int x,int y){
            Graphics2D d=(Graphics2D)g.create();d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            d.setColor(TEXT.color());d.fillRoundRect(x+1,y,6,20,3,3);d.fillRoundRect(x+11,y,6,20,3,3);d.dispose();
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
        boardPanel.setSession(session);Messages.set(modeLabel,"★  "+session.difficulty().label().toUpperCase()+"  ★");
        Messages.set(turnCounter,"TURN  "+String.format("%02d",session.turn()));
        Player player=session.current();
        Messages.bind(turnLabel, () -> turnLabel.setText("● "+player.name()));
        Theme.foreground(turnLabel,Theme.playerText(player.color()));
        Messages.set(phaseLabel,switch(session.phase()){
            case ROLL->"ROLL THE DICE TO BEGIN YOUR TURN";
            case SELECT_PIECE->"CHOOSE A HIGHLIGHTED PIECE";
            case FINISHED->"GAME COMPLETED";
        });Theme.foreground(phaseLabel,session.phase()==GameSession.Phase.FINISHED?ACCENT:SUCCESS);
        boolean automated=playerControls.getOrDefault(player.color(),AiLevel.HUMAN).automated();
        rollButton.setEnabled(!automated && session.phase()==GameSession.Phase.ROLL);
        passButton.setEnabled(!automated && session.phase()==GameSession.Phase.SELECT_PIECE&&session.legalPieceIds().isEmpty());
        DiceRoll roll=session.dice();regularFace.setValue(roll==null?0:roll.regular());specialFace.setValue(roll==null?0:roll.special());
        boolean showSpecial=session.difficulty()!=Difficulty.EASY||(roll!=null&&roll.ultraBoost());
        specialDieLabel.setVisible(showSpecial);
        specialFace.setVisible(showSpecial);

        String boost=(roll != null && roll.ultraBoost()) || session.difficulty() == Difficulty.HARD ? "ULTRABOOST" : "BOOST";
        Messages.set(specialDieLabel,showSpecial && roll != null ? boost+"  ×"+roll.multiplier() : boost);   // multiplier shown next to its die
        multiplier.setText(showSpecial && roll != null ? "×" + roll.multiplier() : "");
        Messages.set(diceDetails,roll==null ? "READY TO ROLL" : "SHARED " + roll.movement() + "  |  HOME " + roll.regular());
        resultValue.setText(roll == null ? "–" : Integer.toString(roll.regular()));
        Messages.bind(movementValue, () -> movementValue.setText(localizeMoves(availableMovesText(session))));

        // a single message for the player: what to do now, and how far the dice let them move
        String text; Theme.Tone color;
        if (session.phase() == GameSession.Phase.FINISHED) { text = "Game completed!"; color = ACCENT; }
        else if (automated) { text = "AI is thinking…"; color = ACCENT; }
        else if (session.phase() == GameSession.Phase.ROLL) { text = "Roll the dice!"; color = ACCENT; }
        else if (session.legalPieceIds().isEmpty()) { text = "No legal moves. Pass your turn."; color = DANGER; }
        else { text = null; color = SUCCESS; }
        Messages.bind(instruction, () -> {
            String moves=availableMovesText(session);
            instruction.setText(text == null
                    ? Messages.get("Move "+(Messages.isHebrew()?localizeMoves(moves):moves.toLowerCase(java.util.Locale.ROOT))
                            +". Pick a glowing hero.")
                    : Messages.get(text));
            wrap(instruction,135);
        });
        Theme.foreground(instruction,color);

        updateHistory(session);
        updatePlayers(session);
        if(clockSession!=session){pausedMillis=0;if(paused)setPaused(false);}   // a new game starts with no paused time
        clockSession=session;updateClock();
        if(session.phase()==GameSession.Phase.FINISHED)clock.stop();else if(!clock.isRunning())clock.start();
        arenaRepaint();
    }
    /** Lets a centred label wrap onto two lines instead of being cut with "...". */
    private static void wrap(JLabel label,int width){
        String t=label.getText();
        if(!t.startsWith("<html>")) label.setText("<html><p style='width:"+width+"px' align=center>"+t+"</p></html>");
    }
    /** Pauses or resumes the game: the overlay blocks the screen and the clock stops counting. */
    public boolean playCapture(CaptureMove move){
        boolean rollEnabled=rollButton.isEnabled(),passEnabled=passButton.isEnabled();
        rollButton.setEnabled(false);passButton.setEnabled(false);
        try{return boardPanel.playCapture(move, 1.0);}
        finally{rollButton.setEnabled(rollEnabled);passButton.setEnabled(passEnabled);}
    }
    public boolean isCapturePlaying(){return boardPanel.isCapturePlaying();}
    public void setPaused(boolean pause){
        if(isCapturePlaying())return;
        if(pause==paused)return;
        paused=pause;
        if(pause){pauseStartedAt=System.currentTimeMillis();clock.stop();}
        else{pausedMillis+=System.currentTimeMillis()-pauseStartedAt;
             if(clockSession!=null && clockSession.phase()!=GameSession.Phase.FINISHED)clock.start();}
        updateClock();
        pauseOverlay.setVisible(pause);
        if(pause)pauseOverlay.focusResume();
        if(actions!=null)actions.gameAvailabilityChanged();
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
        private Rectangle headerBounds() {
            return SwingUtilities.convertRectangle(header, new Rectangle(header.getSize()), this);
        }
        @Override public boolean contains(int x,int y) {
            return !headerBounds().contains(x,y) && super.contains(x,y);
        }
        @Override protected void paintComponent(Graphics g){
            Rectangle bounds=headerBounds();
            g.setColor(SCRIM.color());
            g.fillRect(0,0,getWidth(),bounds.y);
            int bottom=bounds.y+bounds.height;
            g.fillRect(0,bottom,getWidth(),getHeight()-bottom);
            super.paintComponent(g);
        }
    }
    private void updateClock(){
        if(clockSession==null)return;
        long now=System.currentTimeMillis(), pausedNow=paused?now-pauseStartedAt:0;
        long seconds=Math.max(0,(now-clockSession.started().toEpochMilli()-pausedMillis-pausedNow)/1000);
        long h=seconds/3600,m=(seconds%3600)/60,sec=seconds%60;
        Messages.set(clockLabel,h>0?String.format("TIME  %d:%02d:%02d",h,m,sec):String.format("TIME  %02d:%02d",m,sec));
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
    private String localizeMoves(String text) {
        return java.util.Arrays.stream(text.split(" / ")).map(Messages::get)
                .collect(java.util.stream.Collectors.joining(" / "));
    }
    private void updatePlayers(GameSession session){
        leftPlayers.removeAll();rightPlayers.removeAll();
        for(int i=0;i<session.players().size();i++){
            Player p=session.players().get(i);
            boolean active=p==session.current()&&session.phase()!=GameSession.Phase.FINISHED;
            JPanel target=(i%2==0?leftPlayers:rightPlayers);
            target.add(new PlayerCard(p,active,playerControls.getOrDefault(p.color(),AiLevel.HUMAN)));
            target.add(Box.createVerticalStrut(12));
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
        private final AiLevel control;
        PlayerCard(Player player,boolean active,AiLevel control){
            this.player=player;this.active=active;this.control=control;
            setPreferredSize(new Dimension(212,96));setMaximumSize(new Dimension(214,96));setAlignmentX(LEFT_ALIGNMENT);
        }
        @Override protected void paintComponent(Graphics g){
            Graphics2D d=(Graphics2D)g.create();
            d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w=getWidth()-4,h=getHeight()-6,top=6;
            Color team=Theme.player(player.color());
            d.setColor(SHADOW.color());d.fill(new RoundRectangle2D.Double(1,top+4,w,h-2,16,16));
            d.setColor(SURFACE.color());d.fill(new RoundRectangle2D.Double(0,top,w,h-2,16,16));
            d.setColor(active?team:BORDER.color());d.setStroke(new BasicStroke(active?3f:1f));
            d.draw(new RoundRectangle2D.Double(1,top+1,w-2,h-4,16,16));
            // name ribbon
            d.setColor(team);d.fill(new RoundRectangle2D.Double(10,top+8,w-20,24,10,10));
            d.setColor(Theme.teamInk(team));d.setFont(Theme.font(13,Font.BOLD));
            String name=player.name();FontMetrics fm=d.getFontMetrics();
            int maxName=active?w-110:w-40;
            while(fm.stringWidth(name)>maxName && name.length()>3) name=name.substring(0,name.length()-2)+"…";
            d.drawString(name,20,top+25);
            if(active){                                                                   // "YOUR TURN" tag on the ribbon
                d.setFont(Theme.font(10,Font.BOLD));
                int tw=d.getFontMetrics().stringWidth(Messages.get("YOUR TURN"))+14, tx=w-14-tw;
                d.setColor(Color.WHITE);d.fill(new RoundRectangle2D.Double(tx,top+11,tw,18,9,9));
                d.setColor(team);d.drawString(Messages.get("YOUR TURN"),tx+7,top+24);
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
                if(home){d.setColor(ACCENT.color());d.setFont(Theme.font(15,Font.BOLD));d.drawString("★",(int)cx-6,(int)cy+6);}
            }
            // counts
            d.setFont(Theme.font(10,Font.BOLD));d.setColor(MUTED.color());
            String counts=player.finishedCount()+"/4 HOME   •   "+player.castleCount()+" IN CASTLE";
            counts=Messages.get(counts);
            if (control.automated()) counts=Messages.get(control.toString())+"  ·  "+counts;
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
                         (rank==1?"   ★ WINNER":""),15,Font.BOLD,rank==1?ACCENT:TEXT);
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
        GlobalHeader.install(dialog,root);dialog.setSize(570,540);dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }
    public void showInfo(String heading,String message){
        JTextArea area=UiKit.area("",16);Messages.set(area,message);area.setEditable(false);
        JScrollPane s=new JScrollPane(area);s.setPreferredSize(new Dimension(625,380));
        Dialogs.showMessageDialog(this,s,heading,JOptionPane.INFORMATION_MESSAGE);
    }
    public void showSurprise(String message){
        UiKit.notice(this,"SURPRISE EFFECT","✦  SURPRISE SQUARE  ✦",message,"APPLY SURPRISE EFFECT  →");
    }
    public void showSurprise(String message, boolean automated){
        if (!automated) { showSurprise(message); return; }
        UiKit.notice(this,"SURPRISE EFFECT","✦  SURPRISE SQUARE  ✦",message,"APPLY SURPRISE EFFECT  →",1250);
    }
    public void setPlayerControls(java.util.Map<PlayerColor,AiLevel> controls) {
        playerControls=java.util.Map.copyOf(controls);
    }
    public void showError(Exception ex){Dialogs.showMessageDialog(this,ex.getMessage(),"CHEETAH LUDO  •  ERROR",JOptionPane.ERROR_MESSAGE);}
    private static <T extends AbstractButton> T localizedMenu(T item, String text) {
        Messages.set(item,text);
        return item;
    }
    private void buildMenu(){
        JMenuBar b=new JMenuBar();JMenu game=localizedMenu(new JMenu(),"Game");
        homeMenuItem = localizedMenu(new JMenuItem(),"Main Menu");
        homeMenuItem.addActionListener(e -> { if (actions != null) actions.showMenu(); });
        game.add(homeMenuItem);
        newGameMenuItem = localizedMenu(new JMenuItem(),"New Game");
        newGameMenuItem.setAccelerator(KeyStroke.getKeyStroke(
                KeyEvent.VK_N, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        newGameMenuItem.addActionListener(e -> {
            if (actions != null && !adminMode) actions.newGame();
        });
        game.add(newGameMenuItem);
        JMenuItem hist=localizedMenu(new JMenuItem(),"History");hist.addActionListener(e->{if(actions!=null)actions.history();});game.add(hist);
        JMenuItem exp=localizedMenu(new JMenuItem(),"Export History");exp.addActionListener(e->{if(actions!=null)actions.exportHistory();});game.add(exp);
        JMenuItem exit=localizedMenu(new JMenuItem(),"Exit");exit.addActionListener(e->dispose());game.add(exit);
        JMenu help=localizedMenu(new JMenu(),"Help");JMenuItem rules=localizedMenu(new JMenuItem(),"Rules & Controls");
        rules.addActionListener(e -> { if (!isCapturePlaying()) showRules(); });
        help.add(rules);b.add(game);b.add(help);setJMenuBar(b);
    }

    private void showRules() {
        showInfo("HOW TO PLAY",
          "Roll 6 to bring a piece out of its castle. A six grants an extra roll, but three consecutive sixes end the turn.\n\n"+
          "Click one highlighted piece to move. You need an exact roll to reach Home.\n\n"+
          "Medium: regular die × (boost − 1). Hard: regular die × (UltraBoost − 1).\n\n"+
          "Protected star squares prevent captures. Double blocks stop opponent pieces from passing.\n\n"+
          "Question squares ask a question and move you forward/backward. Surprise squares have random effects.\n\n"+
          "Win by getting all four pieces Home. Keyboard: R rolls dice, P passes when permitted.");
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
            d.setColor(SHADOW.color());d.fillRoundRect(5,6,55,55,13,13);
            d.setPaint(new GradientPaint(0,0,new Color(255,255,255),55,55,Theme.blend(PRIMARY.color(),Color.WHITE,.24f)));
            d.fillRoundRect(2,1,55,55,12,12);d.setColor(ACCENT.color());d.setStroke(new BasicStroke(2));d.drawRoundRect(2,1,55,55,12,12);
            d.setColor(ON_PRIMARY.color());
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


    public void setAdminMode(boolean enabled) {
        adminMode = enabled;
        Messages.set(homeMenuItem,enabled ? "Admin Dashboard" : "Main Menu");
        newGameMenuItem.setEnabled(!enabled);
        if (!enabled) {
            dashboard.setEmail(null);
            securityDashboard.clear();
        }
        showMainMenu();
    }

    public void showAdminEmail(String email) {
        dashboard.setEmail(email);
    }

    public void showAdminSecurity(int attempts, long minutes) {
        AdminAccountDialog.showSecurity(this, attempts, minutes);
    }

    public void showSecurityDashboard(AdminAudit.Snapshot snapshot) {
        if (!adminMode) return;
        securityDashboard.setSnapshot(snapshot);
        show("SECURITY");
    }
}
