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
    /** Game screen background: pixel night sky with stars and castle silhouettes (matches the pixel heroes). */
    public static class NightPage extends JPanel {
        public NightPage(LayoutManager layout){super(layout);setOpaque(false);}
        @Override protected void paintComponent(Graphics g){
            Graphics2D d=(Graphics2D)g.create();
            int w=getWidth(),h=getHeight(),P=6;
            Color[] bands={new Color(24,62,150),new Color(20,52,132),new Color(16,43,114),new Color(12,35,97),new Color(9,28,82),new Color(7,23,70)};
            for(int i=0;i<bands.length;i++){d.setColor(bands[i]);d.fillRect(0,h*i/bands.length,w,h/bands.length+1);}
            java.util.Random random=new java.util.Random(7);                 // fixed seed: the sky does not flicker
            for(int i=0;i<80;i++){                                           // small stars only, so they never sit on top of text
                int x=random.nextInt(Math.max(1,w))/P*P, y=random.nextInt(Math.max(1,h*6/10))/P*P;
                d.setColor(new Color(255,255,255,70+random.nextInt(110)));d.fillRect(x,y,2,2);
            }
            d.setColor(new Color(10,30,88));                                 // hills
            for(int x=0;x<w;x+=P){int hh=(int)(170+60*Math.sin(x/140.0)+25*Math.sin(x/53.0))/P*P;d.fillRect(x,h-hh-60,P,hh+60);}
            Color ink=new Color(5,14,44);
            int[][] towers={{0,90,300},{90,60,230},{150,50,370},{200,70,250},{w-270,70,260},{w-200,50,380},{w-150,90,290},{w-60,60,230}};
            for(int[] t:towers){                                             // castle towers at the two edges {x, width, height}
                int x=t[0]/P*P,tw=t[1]/P*P,th=t[2]/P*P;
                d.setColor(ink);d.fillRect(x,h-th,tw,th);
                for(int k=0;k<tw;k+=2*P)d.fillRect(x+k,h-th-P*2,P,P*2);
                d.setColor(new Color(255,205,60));d.fillRect(x+tw/2-P/2,h-th+P*4,P,P*2);  // lit window
            }
            d.setColor(ink);d.fillRect(0,h-40,w,40);
            d.dispose();
            super.paintComponent(g);
        }
    }
    /** Panel in the pixel style of the board: flat dark fill, solid border and a hard shadow. */
    public static class GlassCard extends JPanel {
        public GlassCard(LayoutManager layout){this(layout,20);}
        public GlassCard(LayoutManager layout,int arc){super(layout);setOpaque(false);}
        @Override protected void paintComponent(Graphics g){
            Graphics2D d=(Graphics2D)g.create();
            int w=getWidth(),h=getHeight();
            d.setColor(new Color(4,10,30,170));d.fillRect(4,4,w-4,h-4);              // hard shadow
            d.setColor(new Color(40,62,128));d.fillRect(0,0,w-4,h-4);               // border
            d.setColor(new Color(16,36,96,240));d.fillRect(3,3,w-10,h-10);         // fill
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
            Graphics2D d=(Graphics2D)g.create();
            Color a=switch(active?Kind.GOLD:kind) {
                case GOLD -> new Color(255,200,30);
                case GREEN -> new Color(15,200,150);
                case RED -> new Color(230,40,70);
                case DARK -> new Color(14,30,80);
                default -> new Color(40,102,246);
            };
            if(active)setForeground(Theme.NAVY);else setForeground(kind==Kind.GOLD?Theme.NAVY:Theme.WHITE);
            if(!isEnabled()) a=Theme.blend(a,Theme.DEEP,.62f);
            else if(getModel().isRollover()) a=a.brighter();
            if(getModel().isPressed()) a=a.darker();
            int w=getWidth(),h=getHeight(),press=getModel().isPressed()?2:0;
            Color ink=new Color(10,16,44);
            if(isEnabled() && press==0){d.setColor(new Color(4,10,30,200));d.fillRect(4,4,w-4,h-4);}   // hard pixel shadow
            d.setColor(ink);d.fillRect(press,press,w-4,h-4);                                          // solid outline
            d.setColor(a);d.fillRect(press+3,press+3,w-10,h-10);                                     // flat face
            d.setColor(Theme.blend(a,Color.WHITE,.35f));d.fillRect(press+3,press+3,w-10,3);          // top shine
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