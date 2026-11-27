package view;

import javax.swing.*;

import model.*;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

/** HiDPI-friendly scalable, clickable Ludo board. Painting never changes game state. */
public final class BoardPanel extends JPanel {
    private static final Color TRACK = new Color(251, 252, 255);
    private GameSession session;
    private CapturePlayback playback;
    private final boolean interactive;
    private List<String> tutorialHighlights = List.of();
    private Piece tutorialActor;
    private List<CaptureAnimation.Point> tutorialRoute = List.of();
    private CaptureAnimation tutorialCapture;
    private Piece tutorialVictim;
    private double tutorialProgress = 1;

    public boolean isCapturePlaying() { return playback != null; }

    boolean playCapture(CaptureMove move, double speed) {
        if (playback != null) throw new IllegalStateException("A capture is already playing");
        playback = new CapturePlayback(new CaptureAnimation(move, session.board()), speed, this::repaint);
        try {
            return playback.play();
        } finally {
            playback = null;
            repaint();
        }
    }

    void cancelCapture() {
        if (playback != null) playback.cancel();
    }

    @Override public void addNotify() {
        super.addNotify();
        if (interactive) {
            lifeTimer.start();
            glowTimer.start();
        }
    }

    @Override public void removeNotify() {
        cancelCapture();
        lifeTimer.stop();
        glowTimer.stop();
        hopTimer.stop();
        super.removeNotify();
    }

    private float glowPhase = 0f;
    private int lifeTick;                                  // drives the blinking and the bouncing "move me" arrow
    private final javax.swing.Timer lifeTimer = new javax.swing.Timer(150, e -> { if (session != null) { lifeTick++; repaint(); } });
    private Set<String> opponentSelectionIds = Set.of();
    private final javax.swing.Timer glowTimer =
        new javax.swing.Timer(40, e -> {
        	if (session != null &&
        	        (!session.legalPieceIds().isEmpty()
        	        || !opponentSelectionIds.isEmpty())) {
                glowPhase += 0.12f;
                repaint();
            }
        });
    private Consumer<String> onSelect = id -> {};
    private final List<Hit> hitTargets = new ArrayList<>();
    private record Hit(String id, double x, double y, double radius) {
        boolean contains(double px, double py) { return Math.hypot(px - x, py - y) <= radius; }
    }
    public BoardPanel() {
        this(true);
    }

    BoardPanel(boolean interactive) {
        this.interactive = interactive;
        setOpaque(false);
        setBackground(new Color(21, 49, 116));
        setPreferredSize(new Dimension(710,710));
        if (!interactive) return;
        MouseAdapter mouse = new MouseAdapter() {
        	@Override
        	public void mouseClicked(MouseEvent e) {
                if(isCapturePlaying()) return;
        	    for (int i = hitTargets.size() - 1; i >= 0; i--) {
        	        if (hitTargets.get(i).contains(e.getX(), e.getY())) {

        	            String selectedId = hitTargets.get(i).id();

        	            if (!opponentSelectionIds.isEmpty()
        	                    && !opponentSelectionIds.contains(selectedId)) {
        	                return;
        	            }

        	            onSelect.accept(selectedId);
        	            return;
        	        }
        	    }
        	
            }
            @Override public void mouseMoved(MouseEvent e) {
                boolean legal=false;
                for (Hit h : hitTargets) if (h.contains(e.getX(),e.getY()) &&
                		session != null &&
                		(opponentSelectionIds.isEmpty()
                		    ? session.legalPieceIds().contains(h.id())
                		    : opponentSelectionIds.contains(h.id()))) { legal=true; break; }
                setCursor(Cursor.getPredefinedCursor(legal ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }
    public void setOnSelect(Consumer<String> callback) { onSelect=Objects.requireNonNull(callback); }

    void setTutorialMotion(Piece actor, Piece victim, List<String> route, List<String> highlights) {
        tutorialActor = actor;
        tutorialVictim = victim;
        tutorialHighlights = highlights;
        var squares = route.stream().map(Board.Position::parse).toList();
        tutorialRoute = squares.stream()
                .map(p -> new CaptureAnimation.Point(p.column() - .5, p.row() - .5)).toList();
        if (actor != null && actor.state() == Piece.State.CASTLE) {
            var yard = castleArea(session.board(), actor.color(), 0, 0, 1);
            var slot = yardSpot(yard, actor.number());
            tutorialRoute = List.of(new CaptureAnimation.Point(slot[0], slot[1]), tutorialRoute.get(1));
        }
        tutorialCapture = victim == null ? null
                : new CaptureAnimation(new CaptureMove(actor, squares, List.of(victim)), session.board());
        repaint();
    }

    void setTutorialProgress(double progress) {
        if (progress == tutorialProgress) return;
        tutorialProgress = progress;
        repaint();
    }

    private boolean hidesTutorialPiece(Piece piece) {
        return tutorialProgress >= 0 && tutorialProgress < 1 && (piece == tutorialActor || piece == tutorialVictim);
    }

    private void paintTutorial(Graphics2D graphics, double x, double y, double tile) {
        if (tutorialActor == null || tutorialProgress < 0 || tutorialProgress >= 1) return;
        if (tutorialCapture != null) {
            tutorialCapture.paint(graphics, x, y, tile, tutorialProgress);
            return;
        }
        double along = tutorialProgress * (tutorialRoute.size() - 1);
        int step = (int) along;
        double fraction = along - step;
        var point = tutorialRoute.get(step).toward(tutorialRoute.get(step + 1), fraction);
        paintPiece(graphics, tutorialActor, x + point.x() * tile,
                y + (point.y() - Math.sin(Math.PI * fraction) * .45) * tile, tile * .69, false, false);
    }
    // hop animation: when a hero moves forward it jumps square by square to its new place (quickly)
    private GameSession watchedSession;
    private final Map<String,Integer> lastPathIndex=new HashMap<>();   // piece id -> path index at the previous update
    private String hopPieceId;                                        // hero that is hopping right now (null = none)
    private List<String> hopSquares=List.of();                        // squares it hops over, start to end
    private long hopStartedAt;
    private double hopStepMillis;
    private final javax.swing.Timer hopTimer=new javax.swing.Timer(15,e->repaint());
    public void setSession(GameSession value) {
        if (session != value) cancelCapture();
        if(value!=watchedSession){watchedSession=value;lastPathIndex.clear();hopPieceId=null;}
        else if(value!=null) startHopIfMoved(value);
        if(value!=null) for(Player player:value.players()) for(Piece piece:player.pieces()) lastPathIndex.put(piece.id(),piece.pathIndex());
        session=value;repaint();
    }
    /** Finds a hero that moved forward since the last update and starts its hop animation. */
    private void startHopIfMoved(GameSession value){
        Board board=value.board();
        for(Player player:value.players()) for(Piece piece:player.pieces()) {
            Integer before=lastPathIndex.get(piece.id());
            int now=piece.pathIndex();
            if(before==null || now<=before) continue;
            List<String> path=board.path(piece.color());
            List<String> squares=new ArrayList<>();
            if(before<0) { squares.add(board.castleSlot(piece.color(),piece.number())); squares.addAll(path.subList(0,now+1)); }
            else squares.addAll(path.subList(before,now+1));
            hopPieceId=piece.id();hopSquares=squares;hopStartedAt=System.currentTimeMillis();
            hopStepMillis=Math.max(45,Math.min(110,650.0/(squares.size()-1)));   // fast: never longer than about 0.65 s
            hopTimer.start();
            return;
        }
    }
    /** Draws the hopping hero between two squares with a small jump; returns false when the hop has finished. */
    private boolean drawHop(Graphics2D d,double x,double y,double s){
        int steps=hopSquares.size()-1;
        double progress=(System.currentTimeMillis()-hopStartedAt)/hopStepMillis;
        Piece piece=session.findPiece(hopPieceId);
        if(piece==null || steps<1 || progress>=steps){hopPieceId=null;hopTimer.stop();return false;}
        int k=(int)progress; double t=progress-k;
        Board.Position a=Board.Position.parse(hopSquares.get(k)), b=Board.Position.parse(hopSquares.get(k+1));
        double ax=x+(a.column()-.5)*s, ay=y+(a.row()-.5)*s, bx=x+(b.column()-.5)*s, by=y+(b.row()-.5)*s;
        double cx=ax+(bx-ax)*t, cy=ay+(by-ay)*t-Math.sin(Math.PI*t)*s*.45;          // arc: up and down on every square
        paintPiece(d,piece,cx,cy,s*.69,false,false);
        return true;
    }
    public void setOpponentSelectionIds(Set<String> ids) {
        opponentSelectionIds = Set.copyOf(ids);
        repaint();
    }
    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D d=(Graphics2D)g.create();
        d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        hitTargets.clear();
        if(session==null) { d.dispose(); return; }
        Board board=session.board();
        int n=board.size();
        double square=Math.min((getWidth()-22.0)/n,(getHeight()-22.0)/n);   // small margin: the board uses almost all its space
        if(square<=0){d.dispose();return;}
        double size=square*n;
        double x=(getWidth()-size)/2;
        double y=(getHeight()-size)/2;
        d.setColor(new Color(0,0,0,50));
        d.fill(new RoundRectangle2D.Double(x-6,y-2,size+18,size+19,16,16));
        d.setColor(new Color(255,184,11));
        d.fill(new RoundRectangle2D.Double(x-9,y-9,size+18,size+18,14,14));
        d.setColor(new Color(26,44,91));
        d.fill(new RoundRectangle2D.Double(x-4,y-4,size+8,size+8,7,7));
        d.setColor(Color.WHITE);
        d.fill(new RoundRectangle2D.Double(x,y,size,size,3,3));
        for(int row=1;row<=n;row++)for(int col=1;col<=n;col++) {
            Board.Cell cell=board.cell(row,col);
            double xx=x+(col-1)*square, yy=y+(row-1)*square;
            Color fill=cellFill(cell);
            double bevel=Math.max(1,Math.round(square*.1));
            d.setColor(fill); d.fill(new Rectangle2D.Double(xx,yy,square,square));
            d.setColor(Theme.blend(fill,Color.WHITE,.65f));                     // pixel bevel: light top-left
            d.fill(new Rectangle2D.Double(xx,yy,square,bevel)); d.fill(new Rectangle2D.Double(xx,yy,bevel,square));
            d.setColor(Theme.blend(fill,new Color(20,30,70),.08f));             // soft shade bottom-right
            d.fill(new Rectangle2D.Double(xx,yy+square-bevel,square,bevel)); d.fill(new Rectangle2D.Double(xx+square-bevel,yy,bevel,square));
            d.setColor(new Color(120,134,170));
            d.setStroke(new BasicStroke(.8f));
            d.draw(new Rectangle2D.Double(xx,yy,square,square));
        }
        drawCastles(d,board,x,y,square);
        drawGoal(d,board,x,y,square);
        for(int row=1;row<=n;row++)for(int col=1;col<=n;col++) {
            Board.Cell cell=board.cell(row,col);
            if("-".equals(cell.symbol())) continue;
            drawSymbol(d,cell,x+(col-.5)*square,y+(row-.5)*square,square);
        }
        if (!tutorialHighlights.isEmpty()) {
            d.setStroke(new BasicStroke((float)Math.max(1.5, square * .10)));
            for (String id : tutorialHighlights) {
                var position = Board.Position.parse(id);
                double left = x + (position.column() - .93) * square;
                double top = y + (position.row() - .93) * square;
                var outline = new RoundRectangle2D.Double(left, top, square * .86, square * .86, 6, 6);
                d.setColor(new Color(24, 27, 47, 100));
                d.fill(outline);
                d.setColor(Theme.Tone.PRIMARY.color());
                d.draw(outline);
            }
        }
        drawPieces(d,board,x,y,square);
        paintTutorial(d,x,y,square);
        if (playback != null) playback.animation.paint(d, x, y, square, playback.progress());
        d.setColor(new Color(66,87,114));
        d.setStroke(new BasicStroke(2f));
        d.draw(new RoundRectangle2D.Double(x-1,y-1,size+2,size+2,6,6));
        d.dispose();
    }

    private Color cellFill(Board.Cell c) {
        if ("homeCenter".equals(c.type())) return new Color(246,247,251);
        if ("castle".equals(c.type())) return new Color(251,252,255);
        if ("homePath".equals(c.type())) {
            PlayerColor color=parseColor(c.color());
            if(color!=null)return Theme.blend(Theme.player(color),Color.WHITE,.45f);
        }
        if ("start".equals(c.symbol())) {
            PlayerColor color = parseColor(c.color());
            if (color!=null) return Theme.blend(Theme.player(color),Color.WHITE,.27f);
        }
        return TRACK;
    }
    private static PlayerColor parseColor(String value) {
        try { return PlayerColor.valueOf(value.toUpperCase(Locale.ROOT)); }
        catch(IllegalArgumentException ex) { return null; }
    }




private void drawCastles(Graphics2D d, Board board,
                         double ox, double oy, double s) {

    if (board.difficulty() == Difficulty.EASY ||
        board.difficulty() == Difficulty.MEDIUM ||
        board.difficulty() == Difficulty.HARD) {

        PlayerColor[] colors;
        int[][] corners;

        if (board.difficulty() == Difficulty.EASY) {

            // Easy - unchanged
            colors = new PlayerColor[] {
                PlayerColor.GREEN, PlayerColor.YELLOW,
                PlayerColor.RED, PlayerColor.BLUE
            };

            corners = new int[][] {
                {0, 0}, {0, 9}, {9, 0}, {9, 9}
            };

        } else if (board.difficulty() == Difficulty.MEDIUM) {

            // Medium - unchanged
            colors = new PlayerColor[] {
                PlayerColor.RED, PlayerColor.GREEN,
                PlayerColor.BLUE, PlayerColor.YELLOW
            };

            corners = new int[][] {
                {1, 1}, {1, 10}, {10, 1}, {10, 10}
            };

        } else {

            // Hard - new
            colors = new PlayerColor[] {
                PlayerColor.RED, PlayerColor.GREEN,
                PlayerColor.BLUE, PlayerColor.YELLOW
            };

            corners = new int[][] {
                {2, 2}, {2, 11}, {11, 2}, {11, 11}
            };
        }

        for (int i = 0; i < colors.length; i++) {

            Color color = Theme.player(colors[i]);

            double x = ox + corners[i][1] * s;
            double y = oy + corners[i][0] * s;

            // Outer castle: 6x6
            d.setColor(color);
            d.fill(new Rectangle2D.Double(
                x, y, 6 * s, 6 * s
            ));

            // White inner castle
            double margin = 0.35 * s;

            d.setColor(Color.WHITE);
            d.fill(new RoundRectangle2D.Double(
                x + margin, y + margin,
                6 * s - 2 * margin,
                6 * s - 2 * margin,
                s * 0.25, s * 0.25
            ));
        }

        return;
    }

    // Original fallback rendering - unchanged
    for (PlayerColor color : PlayerColor.values()) {

        int minR = Integer.MAX_VALUE, minC = Integer.MAX_VALUE;
        int maxR = 0, maxC = 0;

        for (int i = 0; i < 4; i++) {

            Board.Position spot =
                Board.Position.parse(board.castleSlot(color, i));

            minR = Math.min(minR, spot.row());
            minC = Math.min(minC, spot.column());
            maxR = Math.max(maxR, spot.row());
            maxC = Math.max(maxC, spot.column());
        }

        double bx = ox + (minC - 1.45) * s;
        double by = oy + (minR - 1.45) * s;
        double width = (maxC - minC + 1.9) * s;
        double height = (maxR - minR + 1.9) * s;

        d.setColor(Theme.blend(
            Theme.player(color), Color.WHITE, .86f));

        d.fill(new RoundRectangle2D.Double(
            bx, by, width, height, s * .65, s * .65));

        d.setColor(Theme.blend(
            Theme.player(color), Color.WHITE, .15f));

        d.setStroke(new BasicStroke(
            Math.max(2.7f, (float)(s * .115))));

        d.draw(new RoundRectangle2D.Double(
            bx, by, width, height, s * .65, s * .65));
    }
}




    private void drawGoal(Graphics2D d,Board board,double x,double y,double s) {
        int mid=board.size()/2;
        double cx=x+(mid+.5)*s,cy=y+(mid+.5)*s, r=s*1.48;
        Polygon north=new Polygon(new int[]{(int)(cx-r),(int)(cx+r),(int)cx},
                                  new int[]{(int)(cy-r),(int)(cy-r),(int)cy},3);
        Polygon east=new Polygon(new int[]{(int)(cx+r),(int)(cx+r),(int)cx},
                                 new int[]{(int)(cy-r),(int)(cy+r),(int)cy},3);
        Polygon south=new Polygon(new int[]{(int)(cx-r),(int)(cx+r),(int)cx},
                                  new int[]{(int)(cy+r),(int)(cy+r),(int)cy},3);
        Polygon west=new Polygon(new int[]{(int)(cx-r),(int)(cx-r),(int)cx},
                                 new int[]{(int)(cy-r),(int)(cy+r),(int)cy},3);
        Polygon[] quadrants={north,east,south,west};
        PlayerColor[] colors=goalColors(board.difficulty());
        for(int i=0;i<4;i++) {d.setColor(Theme.player(colors[i]));d.fill(quadrants[i]);}
        d.setStroke(new BasicStroke(1.5f));
        d.setColor(new Color(96,109,134));
        for(Polygon triangle:quadrants) d.draw(triangle);
        d.setColor(new Color(244, 249, 255));
        d.setFont(Theme.font(Math.max(10f,(float)(s*.37)),Font.BOLD));
        centerText(d, !interactive && Messages.isHebrew() ? "בית" : "HOME", cx, cy + s * .12);
    }
    /** Colour of each HOME triangle, in the order top, right, bottom, left. */
    private static PlayerColor[] goalColors(Difficulty difficulty) {
        return difficulty==Difficulty.EASY
            ? new PlayerColor[]{PlayerColor.YELLOW,PlayerColor.BLUE,PlayerColor.RED,PlayerColor.GREEN}
            : new PlayerColor[]{PlayerColor.GREEN,PlayerColor.YELLOW,PlayerColor.BLUE,PlayerColor.RED};
    }

    /*
     * Board icons drawn as pixel art, in the same style as the hero pieces.
     * Letters: O outline, Y gold, H gold highlight, C icon colour, L white, G gift, P ribbon, X filled (outline added automatically).
     */
    private static final String[] STAR_ART={".....O.....","....OHO....","....OYO....","OOOOYYYOOOO","OHYYYYYYYYO",".OYYYYYYYO.",
        "..OYYYYYO..","..OYYOYYO..",".OYYO.OYYO.",".OYO...OYO.",".OO.....OO."};
    private static final String[] QUESTION_ART={"...OOOOO...","..OCCCCCO..",".OCCLLLCCO.","OCCLLCLLCCO","OCCCCCLLCCO","OCCCCLLCCCO",
        "OCCCCLLCCCO","OCCCCCCCCCO",".OCCCLLCCO.","..OCCCCCO..","...OOOOO..."};
    private static final String[] GIFT_ART={"..OO...OO..",".OPPO.OPPO.","..OOPOPOO..","OOOOOOOOOOO","OGGGGPGGGGO","OOOOOOOOOOO",
        ".OGGGPGGGO.",".OGGGPGGGO.",".OGGGPGGGO.",".OGGGPGGGO.",".OOOOOOOOO."};
    private static final String[] HASH_ART={".X.X.","XXXXX",".X.X.","XXXXX",".X.X."};
    private static final String[] ARROW_ART={"....X...","....XX..","XXXXXXX.","XXXXXXXX","XXXXXXX.","....XX..","....X..."};   // points right

    /** Draws one board icon ("star", "question", "surprise" or "start") outside the board, e.g. in a legend. */
    static void paintLegendIcon(Graphics2D d,String symbol,double cx,double cy,double s) {
        Map<Character,Color> colors=new HashMap<>();
        colors.put('O',new Color(14,18,40)); colors.put('L',Color.WHITE);
        colors.put('Y',new Color(244,174,45)); colors.put('H',new Color(255,230,140));
        colors.put('C',new Color(221,72,99)); colors.put('G',new Color(147,89,211)); colors.put('P',new Color(255,205,60));
        colors.put('X',new Color(77,88,104));
        Object aa=d.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_OFF);
        switch(symbol) {
            case "star" -> drawPixelIcon(d,STAR_ART,colors,cx,cy,s*.8/13,0,false);
            case "question" -> drawPixelIcon(d,QUESTION_ART,colors,cx,cy,s*.8/13,0,false);
            case "surprise" -> drawPixelIcon(d,GIFT_ART,colors,cx,cy,s*.8/13,0,false);
            case "start" -> drawPixelIcon(d,HASH_ART,colors,cx,cy,s*.62/7,0,false);
            default -> {}
        }
        d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,aa);
    }
    private void drawSymbol(Graphics2D d,Board.Cell cell,double cx,double cy,double s) {
        Color ink=new Color(14,18,40);
        Map<Character,Color> colors=new HashMap<>();
        colors.put('O',ink); colors.put('L',Color.WHITE);
        colors.put('Y',new Color(244,174,45)); colors.put('H',new Color(255,230,140));
        Object aa=d.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_OFF);
        double u=s*.8/13;
        switch(cell.symbol()) {
            case "star" -> drawPixelIcon(d,STAR_ART,colors,cx,cy,u,0,false);
            case "question" -> {
                colors.put('C',switch (cell.specialColor()) {
                    case "yellow" -> new Color(218,151,16);
                    case "green" -> new Color(20,143,109);
                    default -> new Color(221,72,99);
                });
                drawPixelIcon(d,QUESTION_ART,colors,cx,cy,u,0,false);
            }
            case "surprise" -> {
                colors.put('G',new Color(147,89,211)); colors.put('P',new Color(255,205,60));
                drawPixelIcon(d,GIFT_ART,colors,cx,cy,u,0,false);
            }
            case "start" -> {
                colors.put('X',new Color(77,88,104));
                drawPixelIcon(d,HASH_ART,colors,cx,cy,s*.62/7,0,false);
            }
            case "arrow" -> {
                PlayerColor color=parseColor(cell.specialColor());
                colors.put('X',color==null?new Color(173, 202, 250):Theme.player(color));
                int turns=switch(cell.arrow()) { case "down" -> 1; case "left" -> 2; case "up" -> 3; default -> 0; };
                drawPixelIcon(d,ARROW_ART,colors,cx,cy,s*.72/10,turns,true);
            }
            default -> {}
        }
        d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,aa);
    }
    /** Paints square pixel art centred on (cx,cy), rotated by quarter turns; optionally adds a dark outline around X pixels. */
    private static void drawPixelIcon(Graphics2D d,String[] art,Map<Character,Color> colors,double cx,double cy,double u,int turns,boolean outline) {
        int h=art.length, w=art[0].length(), size=Math.max(w,h);
        char[][] grid=new char[size+2][size+2];
        for(char[] row:grid) Arrays.fill(row,'.');
        for(int r=0;r<h;r++) for(int c=0;c<w;c++) {
            int rr=r+(size-h)/2, cc=c+(size-w)/2;
            for(int t=0;t<turns;t++) { int tmp=rr; rr=cc; cc=size-1-tmp; }     // quarter turn clockwise
            grid[rr+1][cc+1]=art[r].charAt(c);
        }
        if(outline) for(int r=0;r<size+2;r++) for(int c=0;c<size+2;c++) {
            if(grid[r][c]!='.') continue;
            for(int dr=-1;dr<=1;dr++) for(int dc=-1;dc<=1;dc++) {
                int nr=r+dr, nc=c+dc;
                if(Math.abs(dr)+Math.abs(dc)==1&&nr>=0&&nc>=0&&nr<size+2&&nc<size+2&&grid[nr][nc]=='X') grid[r][c]='O';
            }
        }
        double ox=cx-(size+2)*u/2, oy=cy-(size+2)*u/2;
        for(int r=0;r<size+2;r++) for(int c=0;c<size+2;c++) {
            Color color=colors.get(grid[r][c]);
            if(color==null) continue;
            d.setColor(color);
            d.fill(new Rectangle2D.Double(Math.floor(ox+c*u),Math.floor(oy+r*u),Math.ceil(u)+.5,Math.ceil(u)+.5));
        }
    }

    private void drawPieces(Graphics2D d,Board board,double x,double y,double s) {
        Map<String,List<Piece>> grouped=new LinkedHashMap<>();
        // colours that are not playing in this game: a faded stone castle instead of an empty white yard
        for(PlayerColor color:PlayerColor.values()) {
            boolean playing=false;
            for(Player player:session.players()) if(player.color()==color) playing=true;
            if(playing) continue;
            double[] yard=castleArea(board,color,x,y,s);
            if(yard!=null) drawClosedCastle(d,color,yard,s);
        }
        boolean hopping=hopPieceId!=null;
        if(hopping && playback!=null && playback.animation.hides(session.findPiece(hopPieceId))) {
            hopPieceId=null;hopTimer.stop();hopping=false;            // a capture is playing: its animation moves this hero
        }
        for(Player player:session.players()) {
            double[] yard=castleArea(board,player.color(),x,y,s);
            for(Piece piece:player.pieces()) {
                if(piece.state()!=Piece.State.CASTLE && yard!=null) drawCastleShadow(d,piece,yard,s);
                if(hopping && piece.id().equals(hopPieceId)) continue;      // drawn by the hop animation
                if ((playback != null && playback.animation.hides(piece)) || hidesTutorialPiece(piece)) continue;
                if(piece.state()==Piece.State.CASTLE && yard!=null) {
                    // heroes waiting at home stand in a neat 2x2 group in the middle of their castle
                    double[] c=yardSpot(yard,piece.number());
                    double diameter=Math.min(Math.min(yard[2],yard[3])*.29,s*1.7);
                    paintPiece(d,piece,c[0],c[1],diameter,
                        opponentSelectionIds.isEmpty() && session.legalPieceIds().contains(piece.id()),
                        opponentSelectionIds.contains(piece.id()));
                    hitTargets.add(new Hit(piece.id(),c[0],c[1],diameter*.6));
                } else if(piece.state()!=Piece.State.FINISHED) {
                    grouped.computeIfAbsent(piece.squareId(board),id->new ArrayList<>()).add(piece);
                }
            }
        }
        for(Map.Entry<String,List<Piece>> entry:grouped.entrySet()) {
            Board.Position pos=Board.Position.parse(entry.getKey());
            List<Piece> occupied=entry.getValue();
            for(int i=0;i<occupied.size();i++) {
                Piece piece=occupied.get(i);
                double cx=x+(pos.column()-.5)*s,cy=y+(pos.row()-.5)*s;
                double diameter=s*.69;
                if(occupied.size()>1) {
                    diameter=s*.46;
                    cx+=((i%2==0)?-.22:.22)*s;
                    cy+=((i/2%2==0)?-.22:.22)*s;
                }
                paintPiece(d, piece, cx, cy, diameter,
                        opponentSelectionIds.isEmpty()
                                && session.legalPieceIds().contains(piece.id()),
                        opponentSelectionIds.contains(piece.id()));
                hitTargets.add(new Hit(piece.id(),cx,cy,Math.max(diameter*.53,9)));
            }
        }
        // Finished pieces stand in a row inside the HOME triangle of their own colour.
        int mid=board.size()/2;
        double centerX=x+(mid+.5)*s,centerY=y+(mid+.5)*s;
        PlayerColor[] goal=goalColors(board.difficulty());
        double[][] side={{0,-1},{1,0},{0,1},{-1,0}};                  // top, right, bottom, left
        for(Player player:session.players()) {
<<<<<<< HEAD
            int count=0;
            for(Piece piece:player.pieces()) if(piece.state()==Piece.State.FINISHED
                    && !(hopping && piece.id().equals(hopPieceId)) && !hidesTutorialPiece(piece)) {
                double angle=Math.PI*2*(colorNumber+.25)/4;
                double cx=centerX+Math.cos(angle)*s*(.55+count*.14);
                double cy=centerY+Math.sin(angle)*s*(.55+count*.14);
                paintPiece(d, piece, cx, cy, s * .25, false, false);
=======
            int k=Arrays.asList(goal).indexOf(player.color());
            if(k<0) continue;
            int count=0, total=0;
            for(Piece piece:player.pieces()) if(piece.state()==Piece.State.FINISHED && !(hopping && piece.id().equals(hopPieceId))) total++;
            for(Piece piece:player.pieces()) if(piece.state()==Piece.State.FINISHED && !(hopping && piece.id().equals(hopPieceId))) {
                double out=s*.85, along=(count-(total-1)/2.0)*s*.32;     // into the triangle, side by side, centred
                double cx=centerX+side[k][0]*out-side[k][1]*along;
                double cy=centerY+side[k][1]*out+side[k][0]*along;
                paintPiece(d, piece, cx, cy, s * .3, false, false);
>>>>>>> main
                count++;
            }
        }
        if(hopping && !drawHop(d,x,y,s)) repaint();   // hop finished: draw the hero in its real place next time
    }
    // dark silhouettes of the heroes, cached by hero number and size
    private final Map<String,java.awt.image.BufferedImage> shadowCache=new HashMap<>();
    /** Leaves a soft dark shadow in the castle spot of a hero that has left the castle. */
    private void drawCastleShadow(Graphics2D d,Piece piece,double[] yard,double s) {
        double[] c=yardSpot(yard,piece.number());
        double diameter=Math.min(Math.min(yard[2],yard[3])*.29,s*1.7);
        int box=(int)Math.ceil(diameter*2);
        if(box<4) return;
        if(shadowCache.size()>64) shadowCache.clear();              // window was resized many times
        java.awt.image.BufferedImage shadow=shadowCache.computeIfAbsent(piece.number()+"/"+box,key->{
            java.awt.image.BufferedImage img=new java.awt.image.BufferedImage(box,box,java.awt.image.BufferedImage.TYPE_INT_ARGB);
            Graphics2D g=img.createGraphics();
            paintHero(g,piece.number(),Color.GRAY,box/2.0,box/2.0,diameter,false);
            g.setComposite(AlphaComposite.SrcIn);                     // keep the hero's outline, paint it one dark colour
            g.setColor(new Color(20,24,45));
            g.fillRect(0,0,box,box);
            g.dispose();
            return img;
        });
        Composite old=d.getComposite();
        d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,.28f));
        d.drawImage(shadow,(int)Math.round(c[0]-box/2.0),(int)Math.round(c[1]-box/2.0),null);
        d.setComposite(old);
    }
    /** Bounds of a player's castle in pixels {left, top, width, height}, or null if the board has none. */
    static double[] castleArea(Board board,PlayerColor color,double x,double y,double s) {
        int minR=Integer.MAX_VALUE,minC=Integer.MAX_VALUE,maxR=-1,maxC=-1;
        String name=color.name().toLowerCase(Locale.ROOT);
        for(Board.Cell cell:board.cells()) {
            if(!"castle".equals(cell.type())||!name.equals(cell.color())) continue;
            Board.Position p=Board.Position.parse(cell.id());
            minR=Math.min(minR,p.row());maxR=Math.max(maxR,p.row());
            minC=Math.min(minC,p.column());maxC=Math.max(maxC,p.column());
        }
        if(maxR<0) return null;
        return new double[]{x+(minC-1)*s,y+(minR-1)*s,(maxC-minC+1)*s,(maxR-minR+1)*s};
    }
    static double[] yardSpot(double[] a,int number) {
        double cx=a[0]+a[2]/2,cy=a[1]+a[3]/2,dx=a[2]*.2,dy=a[3]*.2;
        return new double[]{cx+(number%2==0?-dx:dx),cy+(number<2?-dy:dy)};
    }
    /**
     * Each player's four pieces are four pixel heroes: 1 knight (sword + shield), 2 wizard (staff),
     * 3 archer (bow), 4 lancer (spear). Knights wear black armour; their clothes are the player's colour.
     * Letters: O outline, E eyes, X clothes, H/D clothes light/dark, S/T/s black armour (mid/shine/dark),
     * K/k skin, M hair, G gold, B boots, W wood, N/n dark cloth, L white, P magic glow, R mouth.
     */
    private static final String[][] HEROES = {
        // Knight
        {"................................",
         "................................",
         "................................",
         "................................",
         ".........OOOOOOOOOO.............",
         ".......OOOOOOOOOOOOOO...........",
         ".......OOMMMMMMMMMMOO...........",
         ".....OOMMMMMMMMMMMMMMOO.........",
         ".....OOMMMMMMMMMMMMMMOO.........",
         "....OOMMMMMMMMMMMMMMMMOO........",
         "....OOXXXXXXXXXXXXXXXXOO........",
         "....OOXXXXXXXXXXXXXXXXOO........",
         "....OOMMKKEEKKKKEEKKMMOO....OO..",
         "....OOMMKKEEKKKKEEKKMMOO...OOOO.",
         "....OOMMKKKKRRRRKKKKMMOO...OTTO.",
         ".....OOMKKKKRRRRKKKKMOO...OOTTOO",
         "......OOOKKKKKKKKKKOOO....OOTTOO",
         "......OOOOKKKKKKKKOOOO....OOTTOO",
         ".....OGGXXXXXXXXXXXXGGO...OOTTOO",
         "...OOOGGXXXXXXXXXXXXGGOO..OOTTOO",
         "...OSSOOXXHHXXXXDDXXOOSSOOOOTTOO",
         ".OOSSSSOOXHHXXXXDDXXOOSSOOOOTTOO",
         ".OOSTTSOOXXXXGGXXXXXOOSSOOOOTTOO",
         "OOSSTTSSOOXXGGGGXXXXOOSSOOOOTTOO",
         "OOSSXXSSOOXXGGGGDXXXOOKKOOGGGGGG",
         "OOSSXXSSOOXXXGGGDDXXOOKKOOGGGGGG",
         "OOSSXXSSOOXXXXXXDDXXOOOOOOOOWWOO",
         "OOSSXXSSOOXXXXXDDDDXOOOOOOOOWWO.",
         "OOsSSSSsOODDDDDDDDDDOOO..OOOOOO.",
         ".OOsSSsOOODDDDDDDDDDOO.....OO...",
         ".OOOOOOOOOGGGGGGGGGGOO..........",
         "...OOOOOOOGGGGGGGGGGOO..........",
         ".....OOOOSSSOOOOSSSSOO..........",
         "......OOSSSSOOOOOSSSOOO.........",
         "......OOSSSsOO..OSSSsOO.........",
         ".....OOOSSssOO..OOSSssOOO.......",
         ".....OBBBBBBOO..OOBBBBBBO.......",
         "....OOBBBBBOOO..OOOBBBBBOO......",
         "....OOOOOOOOOO..OOOOOOOOOO......",
         ".....OOOOOOOO....OOOOOOOO......."},
        // Wizard
        {"..................OO..............",
         ".................OOOO.............",
         ".................OXXO.............",
         "...............OOXXXOO............",
         "...............OOXXHOO............",
         ".............OOXXXHHOO............",
         ".............OOXXXHHOO............",
         "...........OOXXXXXXHOOO...........",
         "...........OOXXXXXXXDOO...........",
         ".........OOXXXXXXXXXXDDOO.........",
         ".........OXXXXGGXXXXXDDDO.........",
         ".......OOOXXXXGGXXXXXXDDOOO.......",
         "....OOOOOOOOOOOOOOOOOOOOOOOO......",
         "....OOOOOOOOOOOOOOOOOOOOOOOOO.....",
         ".......OOOOKKKKKKKKKkOOOO...OO....",
         "........OOKKKKKKKKKKkkOO....OOO...",
         "........OOKKEEKKKKEEkkOO...OPPO...",
         "........OOKKEEKKKKEEkkOO..OOPPOO..",
         "........OOLLKKKKKKKKLLOO..OOPPOO..",
         "........OOLLLKKKKKKLLLOO...OPPOOO.",
         "........OOLLLLLLLLLLLLOO...OOOWWO.",
         ".......OOOLLLLLLLLLLLLOOO...OOWWOO",
         ".......OOXXXOLLLLLLOXXXOO...OOWWOO",
         ".....OOXXXXXXOLLLLOXXXXXXO..OOWWO.",
         ".....OOXXXHHXXLLLLXXXXDDXOOOKKOOO.",
         "....OOXXXHHHXXLLLLXXXXDDXXOOKKOO..",
         "....OOXXHHHXXXGGXXXXXXDDXXOOWWOO..",
         "....OOXXHHXXXXGGXXXXXXDDXOOOWWWOO.",
         "....OOKKOOXXXXGGXXXXXXDDOO..OWWWO.",
         ".....OKKOOXXXXGGXXXXXDDOO...OOWWOO",
         ".....OOOOOXXXXGGXXXXDDOO....OOWWOO",
         "......OOOXXXXXGGXXXXDDOO....OOWWOO",
         "......OOOXXXXXXXXXXXXDDOO...OOWWOO",
         ".....OOXXXXXXXXXXXXXXXXDDO..OOWWOO",
         ".....OXXXXXXXXXXXXXXXXXDDDOOOOWWOO",
         "....OOXXXXXXXXXXXXXXXXXXDDOOOOWWO.",
         "....OOOOBBOOOOOOOOOOBBOOOO..OOOOO.",
         ".....OOOBBOOOOOOOOOOBBOOO....OO..."},
        // Archer
        {"...........OOOOOOOO.................",
         ".........OOOOOOOOOOOO...............",
         ".........OOXXXXXXXXOO...............",
         ".......OOXXXXXXXXXXXOOO.............",
         ".......OOXXXHHXXXXXXDOO.............",
         "......OOXXXXHHXXXXXXXDOO............",
         "......OOXXXOKKKKKKOXXXOO....OO......",
         "......OOXXOKKKKKKKKOXXOO...OOOO.....",
         "......OOXXKKEEKKEEKKXXOO...OWWO.....",
         "......OOXXKKEEKKEEKKXXOO..OOWWOOO...",
         "......OOXXKKKKKKkkKKXXOO..OOWWOOO...",
         "......OOXXKKKKKKkkKKXXOO..OOWWOOOO..",
         "......OODDOKKKKKKKKODDOO..OOWW.OOOLL",
         ".......ODDOOKKKKKKOODDO...OOWW..OOLL",
         "........OOOOXXXXXXOOOO....OOWW..OOLL",
         "........OOOXXXXXXXXOOO....OOWW..OOLL",
         ".......OOXXXXNNXXXXXXOO...OOWW..OOLL",
         ".....OOXXXXXNNNNXXXXXXXO..OOWW..OOLL",
         ".....OOXHHXXNNNNXXXXDDXXOOOOWW..OOLL",
         "....OOXXHHXXNNNNXXXXDDXXOOOOWW..OOLL",
         "....OOKKOOXXGGGGGGXXDDOOKKOOWW..OOLL",
         "....OOKKOOXXXGGGGGXXDDOOKKOOWW..OOLL",
         "....OOKKOOXXXXNNXXXXDDOO..OOWW..OOLL",
         ".....OKKOOXXXXNNXXXXDDOO..OOWW.OOOLL",
         ".....OOOOOXXXXNNXXXXDDOO..OOWWOOOO..",
         ".......OOOXXXXNNXXXXDDOO...OWWOOO...",
         ".......OOOXXXXXXXXXXDDOO...OOOO.....",
         "........OOXXXXXXXXXDXDOO....OO......",
         "........OODXXXXOOODXDXOO............",
         "........OODDXXOOOOODXXOOO...........",
         "........OONNNNOO..OONNNOO...........",
         ".......OOONNNNOO..OONNNNOOO.........",
         ".......OBBBBBBOO..OOBBBBBBO.........",
         "......OOBBBBBOOO..OOOBBBBBOO........",
         "......OOOOOOOOOO..OOOOOOOOOO........",
         ".......OOOOOOOO....OOOOOOOO........."},
        // Lancer
        {"........................................",
         "........................................",
         "..................................OO....",
         ".................................OOOO...",
         ".............OOOOOOOO............OTTO...",
         "...........OOOOOOOOOOOO.........OOTTOO..",
         "...........OOMMMMMMMMOO.........OOSSOO..",
         "..........OOMMMMMMMMMMOO.........OSSO...",
         "..........OOXXXXXXXXXXOO..........WW....",
         "..........OOXXXXXXXXXXOO..........WW....",
         "..........OOKKEEKKEEKKOO..........WW....",
         "..........OOKKEEKKEEKKOO..........WW....",
         "..........OOKKKKRRKKKKOO..........WW....",
         "...........OOKKKRRKKKOO...........WW....",
         "...........OOKKKKKKKKO............WW....",
         ".............OOKKKKOOO............WW....",
         "..............OOOOOOOOO...........WW....",
         "..............OOOOOOOOOO..........WW....",
         "...........OOOOXXXXXXXXXXXOOO.....WW....",
         ".........OOOOOXXXXXXXXXXXXOOOOO...WW....",
         ".........OSSOOXXHHXXXXXXXDOOSSO...WW....",
         ".......OOSSSOOXXHHXXXXXXDDOOSSSOO.WW....",
         ".......OOSSSOOXXXXXGGXXXDDOOSSSOOOWW....",
         "......OOSSSSOOXXXGGGGGGXDDOOSSSSOOWW....",
         "......OOsSSSOOXXGGGGGGGGDDOOSSSsOOWW....",
         "......OOssSOOOXXGGGGGGGGDDOOOSssOOOW....",
         "......OOKKOOOOXXXGGGGGGXDDOOOOKKOOOO....",
         ".......OKKOOOOXXXXXGGXXXDDOOOOKKOOO.....",
         ".......OOO..OOXXXXXXXXXXDDOO..OOO.......",
         "........OO..OOXXXXXXXXXXDDOO..OO........",
         "............OOGGGGGGGGGGGGOO............",
         "............OOGGGGGGGGGGGGOO............",
         "............OOSSSSOOOOSSSSOO............",
         "............OOSSSSOOOOOSSSOOO...........",
         "............OOSSSsOO..OSSSsOO...........",
         "...........OOOSSssOO..OOSSssOOO.........",
         "...........OBBBBBBOO..OOBBBBBBO.........",
         "..........OOBBBBBOOO..OOOBBBBBOO........",
         "..........OOOOOOOOOO..OOOOOOOOOO........",
         "...........OOOOOOOO....OOOOOOOO........."}};
    /** Column of each sprite's body centre (the weapon makes the sprites different widths). */
    private static final int[] HERO_CENTRE = {14,16,15,19};

    /** Faded pixel stone castle (closed gate, team flags) for a colour nobody plays. Grid: 26 x 24 pixels. */
    private void drawClosedCastle(Graphics2D d,PlayerColor color,double[] yard,double s) {
        double u=Math.max(1,Math.floor(Math.min(yard[2],yard[3]-0)*.86/28));
        double ox=Math.round(yard[0]+yard[2]/2-13*u), oy=Math.round(yard[1]+yard[3]/2-12*u);
        Color team=Theme.player(color), stone=new Color(168,176,196), brick=new Color(196,203,220);
        Color ink=new Color(14,18,40), gate=new Color(60,64,84), bars=new Color(110,116,140);
        Object aa=d.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        Composite oldComposite=d.getComposite();
        d.setColor(new Color(255,255,255,130));                                   // fade the whole yard: colour not in the game
        d.fill(new Rectangle2D.Double(yard[0],yard[1],yard[2],yard[3]));
        d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,.45f));  // faded castle
        d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_OFF);
        int[][] blocks={{6,10,14,13},{10,4,6,8},{1,7,5,16},{20,7,5,16}};      // wall, keep, left tower, right tower
        for(int[] b:blocks) {
            castleBox(d,ox,oy,u,b[0],b[1],b[2],b[3],stone,ink);
            for(int i=0;i<b[2];i+=2) castleBox(d,ox,oy,u,b[0]+i,b[1]-1,1,1,stone,ink);   // battlements
        }
        int[][] bricks={{8,13},{15,15},{2,12},{22,17},{3,18},{12,7},{17,12}};
        for(int[] b:bricks) castlePixel(d,ox,oy,u,b[0],b[1],2,1,brick);
        int[][] flags={{3,3,6},{22,3,6},{13,0,3}};                               // pole x, top, bottom
        for(int[] f:flags) { castlePixel(d,ox,oy,u,f[0],f[1],1,f[2]-f[1],ink); castlePixel(d,ox,oy,u,f[0]+1,f[1],3,2,team); }
        castlePixel(d,ox,oy,u,12,5,2,2,ink); castlePixel(d,ox,oy,u,2,10,1,2,ink); castlePixel(d,ox,oy,u,23,10,1,2,ink);   // windows
        castleBox(d,ox,oy,u,10,16,6,7,gate,ink);                                   // closed gate
        for(int i=10;i<16;i+=2) castlePixel(d,ox,oy,u,i,16,1,7,bars);
        castlePixel(d,ox,oy,u,10,18,6,1,bars); castlePixel(d,ox,oy,u,10,21,6,1,bars);
        castleBox(d,ox,oy,u,11,11,4,4,team,ink);                                   // team shield
        castlePixel(d,ox,oy,u,12,12,2,2,Theme.blend(team,Color.WHITE,.5f));
        d.setComposite(oldComposite);
        d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,aa);
    }
    private void castlePixel(Graphics2D d,double ox,double oy,double u,int x,int y,int w,int h,Color c) {
        d.setColor(c); d.fill(new Rectangle2D.Double(ox+x*u,oy+y*u,w*u,h*u));
    }
    private void castleBox(Graphics2D d,double ox,double oy,double u,int x,int y,int w,int h,Color fill,Color ink) {
        castlePixel(d,ox,oy,u,x-1,y-1,w+2,h+2,ink); castlePixel(d,ox,oy,u,x,y,w,h,fill);
    }

    private void drawHero(Graphics2D d,Piece p,double cx,double cy,double diameter) {
        // a little life: the heroes blink now and then (each one at its own moment)
        int phase=Math.floorMod(p.id().hashCode(),40);
        paintHero(d,p.number(),Theme.player(p.color()),cx,cy,diameter,(lifeTick+phase)%30==0);
    }
    /** Paints hero number {@code number} (0 knight, 1 wizard, 2 archer, 3 lancer) centred on (cx,cy); also used outside the board. */
    static void paintHero(Graphics2D d,int number,Color outfit,double cx,double cy,double diameter,boolean blink) {
        paintHero(d, number, outfit, cx, cy, diameter, blink, true);
    }

    // In combat the moving weapon is drawn at the hand. Omit its resting artwork.
    private static final int[] WEAPON_COLUMN = {26, 28, 27, 33};
    static void paintHero(Graphics2D d,int number,Color outfit,double cx,double cy,double diameter,
                          boolean blink, boolean restingWeapon) {
        int who=Math.floorMod(number,HEROES.length);
        String[] art=HEROES[who];
        Color ink=new Color(14,18,40), skin=new Color(246,200,156);
        double u=diameter*1.3/38.0;                        // size of one "pixel"
        double ox=cx-HERO_CENTRE[who]*u, oy=cy-art.length*u*.55;
        Object aa=d.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_OFF);
        d.setColor(new Color(10,20,50,70));                 // soft ground shadow
        d.fill(new Ellipse2D.Double(cx-12*u,oy+(art.length-2.8)*u,24*u,3.6*u));
        for(int row=0;row<art.length;row++) for(int col=0;col<art[row].length();col++) {
            if (!restingWeapon && col >= WEAPON_COLUMN[who]) continue;
            Color c=switch(art[row].charAt(col)) {
                case 'O' -> ink;
                case 'E' -> blink && row+1<art.length && art[row+1].charAt(col)=='E' ? skin : ink;
                case 'X' -> outfit;
                case 'H' -> Theme.blend(outfit,Color.WHITE,.45f);
                case 'D' -> Theme.blend(outfit,ink,.35f);
                case 'S' -> new Color(54,58,78);
                case 'T' -> new Color(160,170,200);
                case 's' -> new Color(32,34,48);
                case 'K' -> skin;
                case 'k' -> new Color(214,160,120);
                case 'M' -> new Color(96,58,32);
                case 'G' -> new Color(255,196,40);
                case 'B' -> new Color(96,62,38);
                case 'W' -> new Color(150,100,56);
                case 'N' -> new Color(44,46,68);
                case 'n' -> new Color(80,84,110);
                case 'L' -> new Color(248,248,252);
                case 'P' -> Theme.blend(outfit,Color.WHITE,.6f);
                case 'R' -> new Color(200,90,90);
                default -> null;
            };
            if(c==null) continue;
            d.setColor(c);
            d.fill(new Rectangle2D.Double(Math.floor(ox+col*u),Math.floor(oy+row*u),Math.ceil(u)+.5,Math.ceil(u)+.5));
        }
        d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,aa);
    }
    private void paintPiece(Graphics2D d, Piece p,
            double cx, double cy, double diameter,
            boolean legal, boolean opponentTarget) {
        double r=diameter/2;
        if (legal || opponentTarget) {
            float pulse = (float) ((Math.sin(glowPhase) + 1.0) / 2.0);
            Color glowColor = opponentTarget
                    ? new Color(255, 45, 65)
                    : new Color(70, 255, 180);

            double glowSize = 4 + pulse * 5;

            // Soft outer glow
            d.setColor(new Color(
                    glowColor.getRed(),
                    glowColor.getGreen(),
                    glowColor.getBlue(),
                    (int)(45 + pulse * 65)
            ));
            d.fill(new Ellipse2D.Double(
                cx - r - glowSize,
                cy - r - glowSize,
                diameter + glowSize * 2,
                diameter + glowSize * 2
            ));

            // Bright pulsing ring
            d.setColor(new Color(
                    glowColor.getRed(),
                    glowColor.getGreen(),
                    glowColor.getBlue(),
                    (int)(150 + pulse * 105)
            ));
            d.setStroke(new BasicStroke(
                (float)(2.0 + pulse * 2.0)
            ));

            d.draw(new Ellipse2D.Double(
                cx - r - 3,
                cy - r - 3,
                diameter + 6,
                diameter + 6
            ));
        }
        drawHero(d,p,cx,cy,diameter);
        if (legal && !opponentTarget) {                      // bouncing arrow: "move me!"
            double a=diameter*.22, ay=cy-diameter*1.02-Math.abs(Math.sin(lifeTick*.5))*diameter*.12;
            Path2D arrow=new Path2D.Double();
            arrow.moveTo(cx-a,ay); arrow.lineTo(cx+a,ay); arrow.lineTo(cx,ay+a*1.1); arrow.closePath();
            d.setColor(new Color(255,200,40)); d.fill(arrow);
            d.setColor(new Color(14,18,40)); d.setStroke(new BasicStroke(1.2f)); d.draw(arrow);
        }
    }
    private static void centerText(Graphics2D d,String text,double cx,double baseline) {
        FontMetrics m=d.getFontMetrics();
        d.drawString(text,(float)(cx-m.stringWidth(text)/2.0),(float)baseline);
    }
}
