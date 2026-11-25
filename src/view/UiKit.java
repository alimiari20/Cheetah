package view;

import static view.Theme.Tone.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.*;

/** Lightweight vector visual system; no CSS/webview or platform-specific dependencies. */
public final class UiKit {
    private UiKit(){}
    public enum Kind { GOLD, BLUE, DARK, GREEN, RED }

    public static class GradientPage extends JPanel {
        public GradientPage(LayoutManager layout){super(layout);setOpaque(false);}
        @Override protected void paintComponent(Graphics g){
            Graphics2D d=(Graphics2D)g.create();d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            d.setPaint(new GradientPaint(0,0,PAGE_TOP.color(),getWidth(),getHeight(),PAGE_BOTTOM.color()));d.fillRect(0,0,getWidth(),getHeight());
            d.dispose();
            super.paintComponent(g);
        }
    }
    public static class GlassCard extends JPanel {
        private final int arc;
        public GlassCard(LayoutManager layout){this(layout,20);}
        public GlassCard(LayoutManager layout,int arc){super(layout);this.arc=arc;setOpaque(false);}
        @Override protected void paintComponent(Graphics g){
            Graphics2D d=(Graphics2D)g.create();d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            d.setColor(SHADOW.color());d.fillRoundRect(1,4,getWidth()-3,getHeight()-6,arc,arc);
            d.setColor(SURFACE.color());d.fillRoundRect(1,1,getWidth()-3,getHeight()-5,arc,arc);
            d.setColor(BORDER.color());d.setStroke(new BasicStroke(1f));d.drawRoundRect(1,1,getWidth()-3,getHeight()-5,arc,arc);
            d.dispose();super.paintComponent(g);
        }
    }
    public static class GlowButton extends JButton {
        private final Kind kind;
        private boolean active;
        public void setActive(boolean selected){
            active=selected;
            updateTextColor();
            repaint();
        }
        private boolean isPrimary(){return active || kind==Kind.GOLD || kind==Kind.GREEN;}
        private void updateTextColor(){Theme.foreground(this,isPrimary()?ON_PRIMARY:TEXT);}
        public GlowButton(String text,Kind kind){
            super();Messages.set(this,text);this.kind=kind;setFont(Theme.font(13,Font.BOLD));setOpaque(false);setBorderPainted(false);
            setContentAreaFilled(false);setFocusPainted(false);setRolloverEnabled(true);updateTextColor();
            setBorder(new EmptyBorder(11,16,11,16));setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(140,42));
        }
        @Override protected void paintComponent(Graphics g){
            Graphics2D d=(Graphics2D)g.create();d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            paintButton(d, this, isPrimary(), kind == Kind.RED, getModel().isRollover() ? 1 : 0);
            d.dispose();
            super.paintComponent(g);
        }
    }
    static void paintButton(Graphics2D g, JButton button, boolean primary, boolean destructive, float hover) {
        int w = button.getWidth(), h = button.getHeight();
        Color base = (primary ? PRIMARY : SECONDARY).color();
        Color highlight = primary ? Theme.blend(base, Color.WHITE, .24f) : Theme.blend(base, BORDER.color(), .55f);
        Color fill = button.isEnabled() ? Theme.blend(base, highlight, hover) : SURFACE.color();
        if (button.getModel().isPressed() && button.getModel().isArmed()) fill = fill.darker();
        Color border = destructive && button.isEnabled() ? DANGER.color()
                : primary && button.isEnabled() ? ACCENT.color() : Theme.blend(BORDER.color(), ACCENT.color(), hover * .5f);
        Shape shape = new RoundRectangle2D.Float(2, 2, w - 5, h - 5, 15, 15);
        g.setColor(fill);
        g.fill(shape);
        g.setColor(border);
        g.draw(shape);
        if (button.hasFocus()) {
            g.setColor((primary ? ON_PRIMARY : ACCENT).color());
            g.drawRoundRect(5, 5, w - 11, h - 11, 12, 12);
        }
    }

    public static JLabel title(String t,int size){JLabel l=Theme.label(t,size,Font.BOLD,TEXT);l.setHorizontalAlignment(SwingConstants.CENTER);return l;}
    public static JLabel sub(String s){JLabel l=Theme.label(s,12,Font.PLAIN,MUTED);l.setHorizontalAlignment(SwingConstants.CENTER);return l;}
    public static JLabel goldLabel(String s){return Theme.label(s,12,Font.BOLD,ACCENT);}
    public static JTextField field(String text){
        JTextField field=new JTextField(text);
        styleField(field);
        return field;
    }
    static void styleField(JTextField field){
        field.setFont(Theme.font(13,Font.PLAIN));Theme.foreground(field,TEXT);
        Theme.textSelection(field);Theme.background(field,BACKGROUND);
        Theme.lineBorder(field,new EmptyBorder(8,10,8,10));
    }
    public static JTextArea area(String text,int rows){
        JTextArea f=new JTextArea(text,rows,34);f.setLineWrap(true);f.setWrapStyleWord(true);
        f.setFont(Theme.font(13,Font.PLAIN));Theme.foreground(f,TEXT);Theme.textSelection(f);
        Theme.background(f,BACKGROUND);f.setBorder(new EmptyBorder(9,10,9,10));return f;
    }
    public static JPanel transparent(){return transparent(new FlowLayout());}
    public static JPanel transparent(LayoutManager layout){JPanel p=new JPanel(layout);p.setOpaque(false);return p;}
    public static JPanel padded(JComponent component,int pad){JPanel p=transparent(new BorderLayout());p.setBorder(new EmptyBorder(pad,pad,pad,pad));p.add(component);return p;}
    public static JLabel chip(String text,Color foreground) {
        JLabel l=Theme.label(text,11,Font.BOLD,foreground);l.setOpaque(true);
        Theme.background(l,SECONDARY);l.setBorder(new EmptyBorder(6,12,6,12));return l;
    }
    /** Consistent dark modal used for confirmations and special effects. */
    public static void notice(JFrame owner,String title,String headline,String message,String closeLabel){
        JDialog dialog=new JDialog(owner,title,true);
        GradientPage root=new GradientPage(new BorderLayout(0,18));
        root.setBorder(new EmptyBorder(22,25,22,25));
        root.add(UiKit.title(headline,22),BorderLayout.NORTH);
        GlassCard body=new GlassCard(new BorderLayout());body.setBorder(new EmptyBorder(18,20,18,20));
        JTextArea text=area("",6);Messages.set(text,message);text.setEditable(false);text.setFocusable(false);
        text.setFont(Theme.font(14,Font.PLAIN));body.add(text,BorderLayout.CENTER);root.add(body,BorderLayout.CENTER);
        JButton ok=Theme.button(closeLabel,true);ok.addActionListener(e->dialog.dispose());
        root.add(ok,BorderLayout.SOUTH);
        GlobalHeader.install(dialog,root);dialog.setSize(525,375);dialog.setMinimumSize(new Dimension(460,310));
        dialog.setLocationRelativeTo(owner);dialog.setVisible(true);
    }
    public static boolean confirm(JFrame owner,String title,String headline,String message,String positive){
        JDialog dialog=new JDialog(owner,title,true);final boolean[] confirmed={false};
        GradientPage root=new GradientPage(new BorderLayout(0,18));
        root.setBorder(new EmptyBorder(24,26,22,26));
        root.add(UiKit.title(headline,21),BorderLayout.NORTH);
        GlassCard pane=new GlassCard(new BorderLayout());pane.setBorder(new EmptyBorder(21,20,21,20));
        JTextArea body=area("",4);Messages.set(body,message);body.setEditable(false);body.setFocusable(false);
        pane.add(body,BorderLayout.CENTER);root.add(pane,BorderLayout.CENTER);
        JPanel buttons=transparent(new GridLayout(1,2,12,0));
        JButton cancel=new GlowButton("CANCEL",Kind.DARK);
        // A matching dark secondary action with explicit red confirmation.
        JButton yes=new GlowButton(positive,Kind.RED);
        cancel.addActionListener(e->dialog.dispose());
        yes.addActionListener(e->{confirmed[0]=true;dialog.dispose();});
        buttons.add(cancel);buttons.add(yes);root.add(buttons,BorderLayout.SOUTH);
        GlobalHeader.install(dialog,root);dialog.setSize(500,345);dialog.setMinimumSize(new Dimension(460,285));
        dialog.setLocationRelativeTo(owner);dialog.setVisible(true);return confirmed[0];
    }

    public static final class Brand extends JComponent {
        public Brand(){setPreferredSize(new Dimension(360,142));}
        @Override protected void paintComponent(Graphics g) {
            Graphics2D d=(Graphics2D)g.create();d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            int w=getWidth(),cx=w/2,y=8;
            int pillw=Math.min(174,w-40);
            d.setPaint(new GradientPaint(cx-pillw/2,0,Theme.blend(PRIMARY.color(),Color.WHITE,.24f),cx+pillw/2,0,PRIMARY.color()));
            d.fillRoundRect(cx-pillw/2,y,pillw,35,22,22);
            d.setFont(Theme.font(17,Font.BOLD));d.setColor(ON_PRIMARY.color());center(d,"C H E E T A H",cx,y+24);
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
            d.setFont(Theme.font(10,Font.BOLD));d.setColor(ACCENT.color());center(d,Messages.get("★  CLASSIC DESKTOP EDITION  ★"),cx,y+112);d.dispose();
        }
        private static void center(Graphics2D g,String text,int x,int baseline){
            g.drawString(text,x-g.getFontMetrics().stringWidth(text)/2,baseline);
        }
    }
}
