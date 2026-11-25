package view;

import model.Board;
import model.CaptureMove;
import model.Piece;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.List;

/** A capture in board coordinates. Only the final projection uses the current panel size. */
final class CaptureAnimation {
    record Point(double x, double y) {
        Point toward(Point b, double f) { return new Point(x+(b.x-x)*f, y+(b.y-y)*f); }
        Point plus(double dx, double dy) { return new Point(x+dx, y+dy); }
    }
    private record Defender(Piece piece, Point home, double homeSize) {}
    private final CaptureMove move;
    private final List<Point> route;
    private final List<Defender> defenders;
    private final Point target;
    private final double dx, dy, angle, reach;
    private final int boardSize;

    CaptureAnimation(CaptureMove move, Board board) {
        this.move = move;
        route = move.route().stream().map(p -> new Point(p.column()-.5, p.row()-.5)).toList();
        target = route.get(route.size()-1);
        Point from = route.size()>1 ? route.get(route.size()-2) : target.plus(-1,0);
        double distance = Math.hypot(target.x-from.x,target.y-from.y);
        dx = (target.x-from.x)/distance;
        dy = (target.y-from.y)/distance;
        angle = Math.atan2(dy,dx);
        reach = Math.min(distance * .88, move.attacker().number()==1 || move.attacker().number()==2 ? 1.18 : .94);
        boardSize = board.size();
        defenders = move.victims().stream().map(piece -> {
            double[] yard = BoardPanel.castleArea(board,piece.color(),0,0,1);
            double[] slot = BoardPanel.yardSpot(yard,piece.number());
            return new Defender(piece,new Point(slot[0],slot[1]),Math.min(1.7,.29*Math.min(yard[2],yard[3])));
        }).toList();
    }

    boolean hides(Piece piece) { return move.attacker()==piece || move.victims().contains(piece); }

    static double phase(double time,double start,double end) {
        double u = Math.max(0,Math.min(1,(time-start)/(end-start)));
        return u*u*(3-2*u);
    }

    Point attackerAt(double t) {
        Point stance = target.plus(-dx*reach,-dy*reach);
        if (t<.22 && route.size()>1) {
            double progress = phase(t,0,.22)*(route.size()-1);
            int i = Math.min(route.size()-2,(int)progress);
            Point next = i==route.size()-2 ? stance : route.get(i+1);
            return route.get(i).toward(next,progress-i);
        }
        double windup = phase(t,.22,.35)*(1-phase(t,.35,.48));
        double strike = phase(t,.36,.52)*(1-phase(t,.55,.72));
        int hero = move.attacker().number();
        double lunge = switch(hero) { case 0 -> .36; case 3 -> .50; default -> .05; };
        return stance.plus(dx*(strike*lunge-.08*windup),dy*(strike*lunge-.08*windup))
                .toward(target,phase(t,.83,1));
    }

    Point defenderAt(int index,double t) {
        Defender defender = defenders.get(index);
        double side = (index-(defenders.size()-1)*.5)*.26;
        double push = move.attacker().number()==3 ? .40 : .25;
        double knock = push*phase(t,.52,.64);
        Point start = inside(target.plus(dx*knock-dy*side,dy*knock+dx*side));
        double flight = phase(t,.64,.92);
        Point ground = start.toward(defender.home,flight);
        return inside(ground.plus(0,-.85*Math.sin(Math.PI*flight)));
    }

    private Point inside(Point p) {
        return new Point(Math.max(.42,Math.min(boardSize-.42,p.x)),Math.max(.42,Math.min(boardSize-.42,p.y)));
    }

    void paint(Graphics2D graphics,double originX,double originY,double tile,double t) {
        Graphics2D g = (Graphics2D)graphics.create();
        try {
            g.translate(originX,originY);
            g.scale(tile,tile);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            Point attacker = attackerAt(t);
            Piece piece = move.attacker();
            double prepare = phase(t,.21,.36)*(1-phase(t,.36,.52));
            double strike = phase(t,.36,.52)*(1-phase(t,.55,.71));
            double lean = switch(piece.number()) {
                case 0 -> -.18*prepare+.25*strike;
                case 1 -> -.13*prepare;
                case 2 -> -.18*prepare+.13*strike;
                default -> -.12*prepare+.32*strike;
            };
            boolean armed = t>=.21 && t<.83;
            for (int i=0;i<defenders.size();i++) {
                Defender defender = defenders.get(i);
                Point position = defenderAt(i,t);
                double flight = phase(t,.64,.92);
                double size = .69+(defender.homeSize-.69)*phase(flight,.70,1);
                double reaction = Math.sin(Math.PI*phase(t,.52,.69));
                boolean magic = piece.number()==1;
                double dissolve = magic ? phase(t,.52,.66)*(1-phase(t,.78,.92)) : 0;
                sprite(g,defender.piece,position,size,false,false,-.25*dx*reaction,1-dissolve);
                if (dissolve>0) CharacterAttackRenderer.motes(g,position.x,position.y,t*2,
                        Theme.player(defender.piece.color()),dissolve);
            }
            sprite(g,piece,attacker,.69,armed,dx<0 && armed,lean*(dx<0?-1:1),1);
            if (armed) CharacterAttackRenderer.paint(g,piece,attacker,target,angle,t);
        } finally {
            g.dispose();
        }
    }

    // Rasterize the original pixel artwork once at a fixed pixel pitch. Board transforms
    // then scale one transparent image, never individual rounded Java2D rectangles.
    private static final BufferedImage[][][] ART = new BufferedImage[4][4][2];
    private static void sprite(Graphics2D g,Piece piece,Point p,double size,boolean armed,
                               boolean mirror,double lean,double opacity) {
        if (opacity<=0) return;
        BufferedImage[] variants = ART[piece.color().ordinal()][piece.number()];
        int variant = armed ? 1 : 0;
        if (variants[variant]==null) {
            BufferedImage image = new BufferedImage(160,160,BufferedImage.TYPE_INT_ARGB);
            Graphics2D pixels = image.createGraphics();
            BoardPanel.paintHero(pixels,piece.number(),Theme.player(piece.color()),80,80,88,false,!armed);
            pixels.dispose();
            variants[variant] = image;
        }
        Graphics2D sprite = (Graphics2D)g.create();
        sprite.setComposite(AlphaComposite.SrcOver.derive((float)opacity));
        sprite.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        sprite.translate(p.x,p.y);
        sprite.rotate(lean);
        sprite.scale((mirror?-size:size)/88,size/88);
        sprite.drawImage(variants[variant],-80,-80,null);
        sprite.dispose();
    }
}
