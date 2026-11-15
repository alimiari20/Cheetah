package view;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.*;

/** Lightweight vector visual system; no CSS/webview or platform-specific dependencies. */
public final class UiKit {
    private UiKit(){}
    public enum Kind { GOLD, BLUE, DARK, GREEN, RED }
    public static final Color TOP=new Color(21, 57, 145), BOTTOM=new Color(7,23,70);

    public static class GradientPage extends JPanel {
        public GradientPage(LayoutManager layout){super(layout);setOpaque(false);}
        @Override protected void paintComponent(Graphics g){
            Graphics2D d=(Graphics2D)g.create();d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            d.setPaint(new GradientPaint(0,0,TOP,getWidth(),getHeight(),BOTTOM));d.fillRect(0,0,getWidth(),getHeight());
            d.setColor(new Color(50,116,255,23));d.fillOval((int)(getWidth()*.19),-190,700,520);
            d.setColor(new Color(0,192,240,23));d.setStroke(new BasicStroke(3f));
            d.drawOval(getWidth()-130,getHeight()-100,280,280);d.dispose();
            super.paintComponent(g);
        }
    }
    public static class GlassCard extends JPanel {
        private final int arc;
        public GlassCard(LayoutManager layout){this(layout,20);}
        public GlassCard(LayoutManager layout,int arc){super(layout);this.arc=arc;setOpaque(false);}
        @Override protected void paintComponent(Graphics g){
            Graphics2D d=(Graphics2D)g.create();d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            d.setColor(new Color(4,15,53,82));d.fillRoundRect(1,4,getWidth()-3,getHeight()-6,arc,arc);
            d.setColor(new Color(24,57,132,216));d.fillRoundRect(1,1,getWidth()-3,getHeight()-5,arc,arc);
            d.setColor(new Color(82,135,229,125));d.setStroke(new BasicStroke(1f));d.drawRoundRect(1,1,getWidth()-3,getHeight()-5,arc,arc);
            d.dispose();super.paintComponent(g);
        }
    }
    public static class GlowButton extends JButton {
        private final Kind kind;
        private boolean active;
        public void setActive(boolean selected){active=selected;repaint();}
        public GlowButton(String text,Kind kind){
            super(text);this.kind=kind;setFont(Theme.font(13,Font.BOLD));setOpaque(false);setBorderPainted(false);
            setContentAreaFilled(false);setFocusPainted(false);setForeground(kind==Kind.GOLD?Theme.NAVY:Theme.WHITE);
            setBorder(new EmptyBorder(11,16,11,16));setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(140,42));
        }
        @Override protected void paintComponent(Graphics g){
            Graphics2D d=(Graphics2D)g.create();d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            Color a,b,stroke;
            switch(active?Kind.GOLD:kind) {
                case GOLD -> {a=new Color(255,218,33);b=new Color(255,160,0);stroke=new Color(255,223,87);}
                case GREEN -> {a=new Color(15,229,168);b=new Color(7,170,128);stroke=new Color(78,246,202);}
                case RED -> {a=new Color(244,40,78);b=new Color(193,17,54);stroke=new Color(251,91,123);}
                case DARK -> {a=new Color(10,25,70);b=new Color(7,17,44);stroke=new Color(57,94,159);}
                default -> {a=new Color(40,102,246);b=new Color(24,66,176);stroke=new Color(77,147,255);}
            }
            if(active)setForeground(Theme.NAVY);else setForeground(kind==Kind.GOLD?Theme.NAVY:Theme.WHITE);
            if(!isEnabled()){a=Theme.blend(a,Theme.DEEP,.62f);b=Theme.blend(b,Theme.DEEP,.62f);}
            else if(getModel().isRollover()){a=a.brighter();b=b.brighter();}
            if(getModel().isPressed()){a=a.darker();b=b.darker();}
            int w=getWidth(),h=getHeight();
            d.setPaint(new GradientPaint(0,1,a,w,h,b));d.fillRoundRect(1,1,w-3,h-4,15,15);
            d.setColor(stroke);d.setStroke(new BasicStroke(1.1f));d.drawRoundRect(1,1,w-3,h-4,15,15);
            d.dispose();super.paintComponent(g);
        }
    }
    public static JLabel title(String t,int size){JLabel l=Theme.label(t,size,Font.BOLD,Theme.WHITE);l.setHorizontalAlignment(SwingConstants.CENTER);return l;}
    public static JLabel sub(String s){JLabel l=Theme.label(s,12,Font.PLAIN,Theme.MUTED);l.setHorizontalAlignment(SwingConstants.CENTER);return l;}
    public static JLabel goldLabel(String s){return Theme.label(s,12,Font.BOLD,Theme.GOLD);}
    public static JTextField field(String text){
        JTextField f=new JTextField(text);f.setFont(Theme.font(13,Font.PLAIN));f.setForeground(Theme.WHITE);
        f.setCaretColor(Theme.GOLD);f.setBackground(new Color(16,44,110));
        f.setBorder(javax.swing.BorderFactory.createCompoundBorder(
             javax.swing.BorderFactory.createLineBorder(new Color(64,117,220)),new EmptyBorder(8,10,8,10)));
        return f;
    }
    public static JTextArea area(String text,int rows){
        JTextArea f=new JTextArea(text,rows,34);f.setLineWrap(true);f.setWrapStyleWord(true);
        f.setFont(Theme.font(13,Font.PLAIN));f.setForeground(Theme.WHITE);f.setCaretColor(Theme.GOLD);
        f.setBackground(new Color(14,38,96));f.setBorder(new EmptyBorder(9,10,9,10));return f;
    }
    public static JPanel transparent(){return transparent(new FlowLayout());}
    public static JPanel transparent(LayoutManager layout){JPanel p=new JPanel(layout);p.setOpaque(false);return p;}
    public static JPanel padded(JComponent component,int pad){JPanel p=transparent(new BorderLayout());p.setBorder(new EmptyBorder(pad,pad,pad,pad));p.add(component);return p;}
    public static JLabel chip(String text,Color foreground) {
        JLabel l=Theme.label(text,11,Font.BOLD,foreground);l.setOpaque(true);
        l.setBackground(new Color(13,35,91));l.setBorder(new EmptyBorder(6,12,6,12));return l;
    }
    /** Consistent dark modal used for confirmations and special effects. */
    public static void notice(JFrame owner,String title,String headline,String message,String closeLabel){
        JDialog dialog=new JDialog(owner,title,true);
        GradientPage root=new GradientPage(new BorderLayout(0,18));
        root.setBorder(new EmptyBorder(22,25,22,25));
        root.add(UiKit.title(headline,22),BorderLayout.NORTH);
        GlassCard body=new GlassCard(new BorderLayout());body.setBorder(new EmptyBorder(18,20,18,20));
        JTextArea text=area(message,6);text.setEditable(false);text.setFocusable(false);
        text.setFont(Theme.font(14,Font.PLAIN));body.add(text,BorderLayout.CENTER);root.add(body,BorderLayout.CENTER);
        JButton ok=Theme.button(closeLabel,true);ok.addActionListener(e->dialog.dispose());
        root.add(ok,BorderLayout.SOUTH);
        dialog.setContentPane(root);dialog.setSize(525,335);dialog.setMinimumSize(new Dimension(460,310));
        dialog.setLocationRelativeTo(owner);dialog.setVisible(true);
    }
    public static boolean confirm(JFrame owner,String title,String headline,String message,String positive){
        JDialog dialog=new JDialog(owner,title,true);final boolean[] confirmed={false};
        GradientPage root=new GradientPage(new BorderLayout(0,18));
        root.setBorder(new EmptyBorder(24,26,22,26));
        root.add(UiKit.title(headline,21),BorderLayout.NORTH);
        GlassCard pane=new GlassCard(new BorderLayout());pane.setBorder(new EmptyBorder(21,20,21,20));
        JTextArea body=area(message,4);body.setEditable(false);body.setFocusable(false);
        pane.add(body,BorderLayout.CENTER);root.add(pane,BorderLayout.CENTER);
        JPanel buttons=transparent(new GridLayout(1,2,12,0));
        JButton cancel=new GlowButton("CANCEL",Kind.DARK);
        // A matching dark secondary action with explicit red confirmation.
        JButton yes=new GlowButton(positive,Kind.RED);
        cancel.addActionListener(e->dialog.dispose());
        yes.addActionListener(e->{confirmed[0]=true;dialog.dispose();});
        buttons.add(cancel);buttons.add(yes);root.add(buttons,BorderLayout.SOUTH);
        dialog.setContentPane(root);dialog.setSize(500,305);dialog.setMinimumSize(new Dimension(460,285));
        dialog.setLocationRelativeTo(owner);dialog.setVisible(true);return confirmed[0];
    }

    public static final class Brand extends JComponent {
        public Brand(){setPreferredSize(new Dimension(360,142));}
        @Override protected void paintComponent(Graphics g) {
            Graphics2D d=(Graphics2D)g.create();d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            int w=getWidth(),cx=w/2,y=8;
            int pillw=Math.min(174,w-40);
            d.setPaint(new GradientPaint(cx-pillw/2,0,new Color(255,225,65),cx+pillw/2,0,new Color(255,156,9)));
            d.fillRoundRect(cx-pillw/2,y,pillw,35,22,22);
            d.setFont(Theme.font(17,Font.BOLD));d.setColor(Theme.NAVY);center(d,"C H E E T A H",cx,y+24);
            String word="LUDO";Color[] colors={new Color(38,116,246),Theme.player(model.PlayerColor.RED),
                Theme.player(model.PlayerColor.GREEN),Theme.player(model.PlayerColor.YELLOW)};
            int radius=Math.min(44,(w-60)/5);int space=radius+8;int sx=cx-2*space+space/2;
            for(int i=0;i<4;i++) {
                int x=sx+i*space;
                d.setColor(new Color(0,0,0,60));d.fillOval(x-radius/2+2,y+44,radius+4,radius+4);
                d.setColor(Color.WHITE);d.fillOval(x-radius/2,y+41,radius,radius);
                d.setColor(colors[i]);d.setStroke(new BasicStroke(3f));d.drawOval(x-radius/2+2,y+43,radius-4,radius-4);
                d.setFont(Theme.font(27,Font.BOLD));center(d,word.substring(i,i+1),x,y+72);
            }
            d.setFont(Theme.font(10,Font.BOLD));d.setColor(Theme.GOLD);center(d,"★  CLASSIC DESKTOP EDITION  ★",cx,y+112);d.dispose();
        }
        private static void center(Graphics2D g,String text,int x,int baseline){
            g.drawString(text,x-g.getFontMetrics().stringWidth(text)/2,baseline);
        }
    }
}
