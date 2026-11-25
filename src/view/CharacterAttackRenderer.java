package view;

import model.Piece;
import java.awt.*;
import java.awt.geom.*;
import static view.CaptureAnimation.phase;

/** Procedural weapons and impact marks. Coordinates are measured in tiles from the hand. */
final class CharacterAttackRenderer {
    private static final Color STEEL = new Color(194,211,230);
    private static final Color EDGE = new Color(249,252,255);
    private static final Color WOOD = new Color(144,84,44);
    private static final Color GOLD = new Color(255,196,77);

    static void paint(Graphics2D board, Piece piece, CaptureAnimation.Point actor,
                      CaptureAnimation.Point target,double direction,double t) {
        double gripX = actor.x()+(Math.cos(direction)<-.001 ? -.20 : .20);
        double gripY = actor.y()+.02;
        double angle = Math.atan2(target.y()-gripY,target.x()-gripX);
        double range = Math.hypot(target.x()-gripX,target.y()-gripY);
        Graphics2D g = (Graphics2D)board.create();
        try {
            g.translate(gripX,gripY);
            g.rotate(angle);
            // Keep the top of the blade/bow above the hand on either horizontal facing.
            if (Math.cos(angle)<0) g.scale(1,-1);
            double draw = phase(t,.21,.36);
            double release = phase(t,.36,.52);
            double recoil = phase(t,.55,.80);
            Color tint = Theme.player(piece.color());
            switch (piece.number()) {
                case 0 -> sword(g,range,draw,release,recoil);
                case 1 -> magic(g,range,draw,release,recoil,t,tint);
                case 2 -> bow(g,range,draw,release,t);
                case 3 -> spear(g,range,draw,release,recoil);
                default -> throw new IllegalArgumentException("Unknown hero");
            }
            double impact = phase(t,.52,.68);
            if (impact>0 && impact<1) {
                Color spark = piece.number()==1 ? Theme.blend(tint,EDGE,.5f) : GOLD;
                if (piece.number()==1) {
                    double radius = .10+.28*impact;
                    g.setColor(alpha(spark,1-impact)); g.setStroke(new BasicStroke(.035f));
                    g.draw(new Ellipse2D.Double(range-radius,-radius,2*radius,2*radius));
                }
                motes(g,range,0,impact,spark,Math.sin(Math.PI*impact));
            }
        } finally { g.dispose(); }
    }

    private static void sword(Graphics2D g,double range,double draw,double swing,double recoil) {
        if (swing>.1 && recoil<.9) {
            double radius = Math.max(.37,range);
            g.setColor(alpha(STEEL,.80*(1-recoil)));
            g.setStroke(new BasicStroke(.068f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
            g.draw(new Arc2D.Double(-radius,-radius,2*radius,2*radius,95-130*swing,62,Arc2D.OPEN));
        }
        g = (Graphics2D)g.create();
        g.rotate(-.65-.95*draw+1.62*swing-.52*recoil);
        double tip = .40+(Math.max(.40,range)-.40)*swing*(1-recoil);
        line(g,WOOD,.06,-.10,0,.06,0);
        line(g,GOLD,.055,.03,-.09,.03,.09);
        polygon(g,STEEL,.06,-.055,tip-.08,-.045,tip,0,tip-.08,.045,.06,.055);
        line(g,EDGE,.022,.08,-.025,tip-.05,-.016);
        g.dispose();
    }

    private static void magic(Graphics2D g,double range,double draw,double release,double recoil,double t,Color color) {
        double lift = draw*(1-recoil);
        double topX = .12+.10*lift, topY = -.20-.18*lift;
        line(g,WOOD,.065,-.13,.22,topX,topY);
        orb(g,topX,topY,.067, color);
        if (t<.52) {
            double x = topX+(range-topX)*release, y = topY*(1-release)-.10*Math.sin(Math.PI*release);
            if (release==0) motes(g,topX,topY,1-draw,color,Math.sin(Math.PI*draw));
            orb(g,x,y,.035+.115*draw,color);
        }
    }

    private static void orb(Graphics2D g,double x,double y,double r,Color tint) {
        for (int layer=2;layer>=0;layer--) {
            double radius = r*(1+.38*layer);
            g.setColor(layer==0?EDGE:alpha(tint,layer==1?.74:.24));
            g.fill(new Ellipse2D.Double(x-radius,y-radius,2*radius,2*radius));
        }
    }

    private static void bow(Graphics2D g,double range,double draw,double release,double t) {
        g = (Graphics2D)g.create();
        g.rotate(.55*(1-draw));
        g.setColor(WOOD); g.setStroke(new BasicStroke(.052f));
        g.draw(new QuadCurve2D.Double(-.04,-.31,.25,0,-.04,.31));
        double string = -.04-.23*draw*(1-phase(t,.36,.39));
        line(g,EDGE,.018,-.04,-.31,string,0);
        line(g,EDGE,.018,string,0,-.04,.31);
        if (t<.56) {
            double tip = (.35+string)*(1-release)+range*release;
            if (release>0) line(g,alpha(GOLD,.40),.05,tip-.50,0,tip-.27,0);
            line(g,WOOD,.032,tip-.36,0,tip,0);
            polygon(g,STEEL,tip,0,tip-.10,-.055,tip-.085,.055);
            line(g,EDGE,.035,tip-.32,0,tip-.38,-.055);
        }
        g.dispose();
    }

    private static void spear(Graphics2D g,double range,double draw,double thrust,double recoil) {
        double extension = thrust*(1-recoil);
        double tip = .41-.15*draw+(range-.26)*extension;
        g = (Graphics2D)g.create();
        g.rotate(-.67*(1-draw)-.20*recoil);
        line(g,WOOD,.059,tip-.86,0,tip,0);
        polygon(g,STEEL,tip,0,tip-.18,-.07,tip-.14,0,tip-.18,.07);
        if (extension>0) for(int i=0;i<2;i++) {
            double y = .13+i*.09;
            line(g,alpha(STEEL,.62*extension),.025,tip-.96+i*.12,y,tip-.49,y);
        }
        g.dispose();
    }

    static void motes(Graphics2D g,double x,double y,double travel,Color color,double opacity) {
        for(int i=0;i<8;i++) {
            double a = i*Math.PI/4+.24, r = (.13+.34*travel)*(i%2==0?1:.75);
            double s = i%2==0?.066:.045;
            g.setColor(alpha(i%3==0?EDGE:color,opacity));
            g.fill(new Rectangle2D.Double(x+r*Math.cos(a)-s/2,y+r*Math.sin(a)-s/2,s,s));
        }
    }

    private static Color alpha(Color c,double opacity) {
        return new Color(c.getRed(),c.getGreen(),c.getBlue(),(int)(255*Math.max(0,Math.min(1,opacity))));
    }
    private static void line(Graphics2D g, Color color,double width,double x,double y,double ex,double ey) {
        g.setColor(color); g.setStroke(new BasicStroke((float)width,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(x,y,ex,ey));
    }
    private static void polygon(Graphics2D g,Color color,double... points) {
        Path2D shape = new Path2D.Double();
        shape.moveTo(points[0],points[1]);
        for(int i=2;i<points.length;i+=2) shape.lineTo(points[i],points[i+1]);
        shape.closePath(); g.setColor(color); g.fill(shape);
    }
}
