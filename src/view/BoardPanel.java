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
    private GameSession session;
    private Consumer<String> onSelect = id -> {};
    private final List<Hit> hitTargets = new ArrayList<>();
    private record Hit(String id, double x, double y, double radius) {
        boolean contains(double px, double py) { return Math.hypot(px - x, py - y) <= radius; }
    }
    public BoardPanel() {
        setOpaque(false);
        setBackground(Theme.SURFACE);
        setPreferredSize(new Dimension(710,710));
        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                for (int i=hitTargets.size()-1;i>=0;i--) if(hitTargets.get(i).contains(e.getX(),e.getY())) {
                    onSelect.accept(hitTargets.get(i).id());
                    return;
                }
            }
            @Override public void mouseMoved(MouseEvent e) {
                boolean legal=false;
                for (Hit h : hitTargets) if (h.contains(e.getX(),e.getY()) &&
                        session != null && session.legalPieceIds().contains(h.id())) { legal=true; break; }
                setCursor(Cursor.getPredefinedCursor(legal ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }
    public void setOnSelect(Consumer<String> callback) { onSelect=Objects.requireNonNull(callback); }
    public void setSession(GameSession value) { session=value;repaint(); }

    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D d=(Graphics2D)g.create();
        d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        hitTargets.clear();
        if(session==null) { d.dispose(); return; }
        Board board=session.board();
        int n=board.size();
        double square=Math.min((getWidth()-40.0)/n,(getHeight()-40.0)/n);
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
            d.setColor(cellFill(cell));
            d.fill(new Rectangle2D.Double(xx,yy,square,square));
            d.setColor(Theme.LINE);
            d.setStroke(new BasicStroke(.65f));
            d.draw(new Rectangle2D.Double(xx,yy,square,square));
        }
        drawCastles(d,board,x,y,square);
        drawGoal(d,board,x,y,square);
        for(int row=1;row<=n;row++)for(int col=1;col<=n;col++) {
            Board.Cell cell=board.cell(row,col);
            if("-".equals(cell.symbol())) continue;
            drawSymbol(d,cell,x+(col-.5)*square,y+(row-.5)*square,square);
        }
        drawPieces(d,board,x,y,square);
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
        return Theme.TRACK;
    }
    private static PlayerColor parseColor(String value) {
        try { return PlayerColor.valueOf(value.toUpperCase(Locale.ROOT)); }
        catch(IllegalArgumentException ex) { return null; }
    }

    private void drawCastles(Graphics2D d,Board board,double ox,double oy,double s) {
        for(PlayerColor color:PlayerColor.values()) {
            int minR=Integer.MAX_VALUE,minC=Integer.MAX_VALUE,maxR=0,maxC=0;
            for(int i=0;i<4;i++) {
                Board.Position spot=Board.Position.parse(board.castleSlot(color,i));
                minR=Math.min(minR,spot.row()); minC=Math.min(minC,spot.column());
                maxR=Math.max(maxR,spot.row()); maxC=Math.max(maxC,spot.column());
            }
            double bx=ox+(minC-1.45)*s,by=oy+(minR-1.45)*s;
            double width=(maxC-minC+1.9)*s,height=(maxR-minR+1.9)*s;
            d.setColor(Theme.blend(Theme.player(color),Color.WHITE,.86f));
            d.fill(new RoundRectangle2D.Double(bx,by,width,height,s*.65,s*.65));
            d.setColor(Theme.blend(Theme.player(color),Color.WHITE,.15f));
            d.setStroke(new BasicStroke(Math.max(2.7f,(float)(s*.115))));
            d.draw(new RoundRectangle2D.Double(bx,by,width,height,s*.65,s*.65));
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
        PlayerColor top=board.difficulty()==Difficulty.EASY?PlayerColor.YELLOW:PlayerColor.GREEN;
        PlayerColor right=board.difficulty()==Difficulty.EASY?PlayerColor.BLUE:PlayerColor.YELLOW;
        PlayerColor bottom=board.difficulty()==Difficulty.EASY?PlayerColor.RED:PlayerColor.BLUE;
        PlayerColor left=board.difficulty()==Difficulty.EASY?PlayerColor.GREEN:PlayerColor.RED;
        Polygon[] quadrants={north,east,south,west};
        PlayerColor[] colors={top,right,bottom,left};
        for(int i=0;i<4;i++) {d.setColor(Theme.player(colors[i]));d.fill(quadrants[i]);}
        d.setStroke(new BasicStroke(1.5f));
        d.setColor(new Color(96,109,134));
        for(Polygon triangle:quadrants) d.draw(triangle);
        d.setColor(Theme.WHITE);
        d.setFont(Theme.font(Math.max(10f,(float)(s*.37)),Font.BOLD));
        centerText(d,"HOME",cx,cy+(s*.12));
    }

    private void drawSymbol(Graphics2D d,Board.Cell cell,double cx,double cy,double s) {
        double radius=s*.29;
        switch(cell.symbol()) {
            case "star" -> {
                d.setColor(new Color(244,174,45));
                Polygon star=new Polygon();
                for(int i=0;i<10;i++) {
                    double angle=-Math.PI/2 + Math.PI*i/5;
                    double rr=i%2==0?radius:radius*.47;
                    star.addPoint((int)(cx+Math.cos(angle)*rr),(int)(cy+Math.sin(angle)*rr));
                }
                d.fill(star);
            }
            case "question" -> {
                Color c= switch (cell.specialColor()) {
                    case "yellow" -> new Color(218,151,16);
                    case "green" -> new Color(20,143,109);
                    default -> new Color(221,72,99);
                };
                d.setColor(c);d.fill(new Ellipse2D.Double(cx-radius,cy-radius,radius*2,radius*2));
                d.setColor(Color.WHITE);d.setFont(Theme.font(Math.max(12f,(float)(s*.54)),Font.BOLD));
                centerText(d,"?",cx,cy+s*.17);
            }
            case "surprise" -> {
                d.setColor(new Color(147,89,211));
                d.fill(new RoundRectangle2D.Double(cx-radius,cy-radius*.65,radius*2,radius*1.65,4,4));
                d.setColor(Color.WHITE);d.setStroke(new BasicStroke(Math.max(1.3f,(float)(s*.06))));
                d.draw(new Line2D.Double(cx,cy-radius*.65,cx,cy+radius));
                d.draw(new Line2D.Double(cx-radius,cy,cx+radius,cy));
            }
            case "start" -> {
                d.setColor(new Color(77,88,104));
                d.setFont(Theme.font(Math.max(12f,(float)(s*.4)),Font.BOLD));
                centerText(d,"#",cx,cy+s*.12);
            }
            case "arrow" -> {
                Color arrowColor= parseColor(cell.specialColor())==null?Theme.MUTED:
                          Theme.player(parseColor(cell.specialColor()));
                d.setColor(arrowColor);
                d.setStroke(new BasicStroke(Math.max(2f,(float)(s*.1)),BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
                double dx=0,dy=0;
                switch(cell.arrow()) {
                    case "right" -> dx=1;
                    case "left" -> dx=-1;
                    case "down" -> dy=1;
                    case "up" -> dy=-1;
                    default -> {}
                }
                double len=s*.25;
                d.draw(new Line2D.Double(cx-dx*len,cy-dy*len,cx+dx*len,cy+dy*len));
                double hx=cx+dx*len,hy=cy+dy*len;
                double px=-dy,py=dx;
                Path2D triangle=new Path2D.Double();
                triangle.moveTo(hx+dx*len*.38,hy+dy*len*.38);
                triangle.lineTo(hx-dx*len*.35+px*len*.5,hy-dy*len*.35+py*len*.5);
                triangle.lineTo(hx-dx*len*.35-px*len*.5,hy-dy*len*.35-py*len*.5);
                triangle.closePath(); d.fill(triangle);
            }
            default -> {}
        }
    }

    private void drawPieces(Graphics2D d,Board board,double x,double y,double s) {
        Map<String,List<Piece>> grouped=new LinkedHashMap<>();
        for(Player player:session.players())for(Piece piece:player.pieces()) {
            if(piece.state()!=Piece.State.FINISHED)
                grouped.computeIfAbsent(piece.squareId(board),id->new ArrayList<>()).add(piece);
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
                paintPiece(d,piece,cx,cy,diameter,session.legalPieceIds().contains(piece.id()));
                hitTargets.add(new Hit(piece.id(),cx,cy,Math.max(diameter*.53,9)));
            }
        }
        // Finished pieces are represented by small tokens arranged around the center.
        int mid=board.size()/2;
        double centerX=x+(mid+.5)*s,centerY=y+(mid+.5)*s;
        int colorNumber=0;
        for(Player player:session.players()) {
            int count=0;
            for(Piece piece:player.pieces()) if(piece.state()==Piece.State.FINISHED) {
                double angle=Math.PI*2*(colorNumber+.25)/4;
                double cx=centerX+Math.cos(angle)*s*(.55+count*.14);
                double cy=centerY+Math.sin(angle)*s*(.55+count*.14);
                paintPiece(d,piece,cx,cy,s*.25,false);
                count++;
            }
            colorNumber++;
        }
    }
    private void paintPiece(Graphics2D d,Piece p,double cx,double cy,double diameter,boolean legal) {
        double r=diameter/2;
        if(legal) {
            d.setColor(new Color(28,207,155,104));
            d.fill(new Ellipse2D.Double(cx-r-5,cy-r-5,diameter+10,diameter+10));
        }
        d.setColor(new Color(28,44,68,65));
        d.fill(new Ellipse2D.Double(cx-r+1.8,cy-r+3.5,diameter,diameter));
        d.setColor(Theme.player(p.color()));
        d.fill(new Ellipse2D.Double(cx-r,cy-r,diameter,diameter));
        d.setStroke(new BasicStroke((float)Math.max(1.5,diameter*.09)));
        d.setColor(Color.WHITE);
        d.draw(new Ellipse2D.Double(cx-r+1,cy-r+1,diameter-2,diameter-2));
        if(diameter>16) {
            d.setColor(Color.WHITE);
            d.setFont(Theme.font(Math.max(10,(float)(diameter*.42)),Font.BOLD));
            centerText(d,Integer.toString(p.number()+1),cx,cy+diameter*.15);
        }
    }
    private static void centerText(Graphics2D d,String text,double cx,double baseline) {
        FontMetrics m=d.getFontMetrics();
        d.drawString(text,(float)(cx-m.stringWidth(text)/2.0),(float)baseline);
    }
}
