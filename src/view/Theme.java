package view;

import javax.swing.*;
import javax.swing.border.*;

import model.PlayerColor;

import java.awt.*;

/** Prototype-inspired shared palette and cross-platform Swing components. */
public final class Theme {
    private Theme() {}
    public static final Color NAVY = new Color(11, 25, 66);
    public static final Color DEEP = new Color(7, 19, 56);
    public static final Color BACKGROUND = new Color(20, 54, 135);
    public static final Color SURFACE = new Color(21, 49, 116);
    public static final Color SURFACE_2 = new Color(15, 37, 94);
    public static final Color MUTED = new Color(173, 202, 250);
    public static final Color ACCENT = new Color(255, 185, 22);
    public static final Color GOLD = new Color(255, 198, 30);
    public static final Color TRACK = new Color(251, 252, 255);
    public static final Color LINE = new Color(61, 109, 204);
    public static final Color SUCCESS = new Color(15, 221, 160);
    public static final Color WHITE = new Color(244, 249, 255);
    public static Color player(PlayerColor color) {
        return switch (color) {
            case RED -> new Color(230, 24, 50);
            case GREEN -> new Color(6, 158, 103);
            case YELLOW -> new Color(255, 179, 13);
            case BLUE -> new Color(27, 111, 236);
        };
    }
    public static Color light(Color c) { return blend(c,Color.WHITE,.82f); }
    public static Color blend(Color a, Color b,float k) {
        k=Math.max(0,Math.min(1,k));
        return new Color((int)(a.getRed()*(1-k)+b.getRed()*k),(int)(a.getGreen()*(1-k)+b.getGreen()*k),
                (int)(a.getBlue()*(1-k)+b.getBlue()*k));
    }
    public static Font font(float size,int style){return new Font("SansSerif",style,Math.round(size));}
    public static JLabel label(String text,int size,int weight,Color color){
        JLabel l=new JLabel(text);l.setFont(font(size,weight));l.setForeground(color);return l;
    }
    public static JButton button(String text,boolean primary){return new UiKit.GlowButton(text,primary?UiKit.Kind.GOLD:UiKit.Kind.BLUE);}
    public static JPanel surface(LayoutManager layout,int pad) {
        JPanel p=new UiKit.GlassCard(layout);
        p.setBorder(new EmptyBorder(pad,pad,pad,pad));
        return p;
    }
}
