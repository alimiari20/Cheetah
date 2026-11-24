package view;

import model.PlayerColor;
import java.awt.*;
import java.awt.geom.*;

/** Pure visual effect; coordinates are relative to the board cell. */
final class CaptureAnimation {
    static void paint(Graphics2D graphics, int hero, PlayerColor color,
                      double x, double y, double s, double t) {
        Graphics2D g=(Graphics2D)graphics.create();
        g.translate(x,y); g.scale(s,s);
        Color tint=Theme.player(color);
        double strike=Math.max(0,Math.min(1,(t-.20)/.38));
        double fade=1-Math.max(0,(t-.65)/.35);
        g.setComposite(AlphaComposite.SrcOver.derive((float)Math.max(0,fade)));
        double ax=-.78 + ((hero==0||hero==3)? .52*Math.sin(Math.PI*strike):0);
        BoardPanel.paintHero(g,hero,tint,ax,-.12,.98,false);
        g.setStroke(new BasicStroke(.065f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        g.setColor(tint);
        if(t>.18 && t<.83) {
            switch(hero) {
                case 0 -> {
                    g.setStroke(new BasicStroke(.15f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
                    g.draw(new Arc2D.Double(-.65,-.65,1.2,1.15,35-160*strike,100,Arc2D.OPEN));
                    g.setColor(Color.WHITE);g.setStroke(new BasicStroke(.045f));
                    g.draw(new Arc2D.Double(-.65,-.65,1.2,1.15,35-160*strike,100,Arc2D.OPEN));
                }
                case 1 -> {
                    double px=-.65+.9*strike, py=-.25-.2*Math.sin(Math.PI*strike);
                    for(int i=3;i>=1;i--){g.setColor(new Color(tint.getRed(),tint.getGreen(),tint.getBlue(),45));double r=.09*i;g.fill(new Ellipse2D.Double(px-r,py-r,2*r,2*r));}
                    g.setColor(Color.WHITE);g.fill(new Ellipse2D.Double(px-.10,py-.10,.20,.20));
                    g.setColor(tint);g.draw(new Ellipse2D.Double(px-.18,py-.18,.36,.36));
                }
                case 2 -> {
                    double px=-.55+.95*strike;
                    g.setColor(new Color(244,219,150));g.draw(new Line2D.Double(px-.48,-.20,px,-.20));
                    g.setColor(Color.WHITE);g.draw(new Line2D.Double(px,-.20,px-.13,-.30));g.draw(new Line2D.Double(px,-.20,px-.13,-.10));
                }
                case 3 -> {
                    double px=-.25+.63*strike;
                    g.setColor(new Color(174,112,65));g.draw(new Line2D.Double(px-.95,-.12,px,-.12));
                    Path2D tip=new Path2D.Double();tip.moveTo(px+.25,-.12);tip.lineTo(px-.08,-.25);tip.lineTo(px-.08,.01);tip.closePath();g.setColor(Color.WHITE);g.fill(tip);
                    g.setColor(tint);g.draw(new Line2D.Double(px-.95,.13,px-.30,.13));
                }
            }
        }
        if(t>.50){double q=(t-.50)/.50;for(int i=0;i<9;i++){double a=i*Math.PI*2/9;double r=.15+.7*q;g.setColor(i%2==0?Color.WHITE:tint);g.fill(new Ellipse2D.Double(.25+Math.cos(a)*r,-.12+Math.sin(a)*r,.085*(1-q),.085*(1-q)));}}
        g.dispose();
    }
}
