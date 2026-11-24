package view;

import model.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.swing.*;

/** Tests the effect renderer in all sixteen character/colour combinations. */
public final class CaptureAnimationTests {
    public static void main(String[] args) throws Exception {
        int count=0;
        for(PlayerColor color:PlayerColor.values()) for(int hero=0;hero<4;hero++) {
            long previous=-1;
            for(double t:new double[]{0,.25,.50,.75,.95}) {
                BufferedImage image=new BufferedImage(220,180,BufferedImage.TYPE_INT_ARGB);
                Graphics2D g=image.createGraphics();
                CaptureAnimation.paint(g,hero,color,110,90,60,t);g.dispose();
                long hash=0; int visible=0;
                for(int y=0;y<180;y++)for(int x=0;x<220;x++) {
                    int pixel=image.getRGB(x,y);hash=hash*31+pixel;
                    if((pixel>>>24)!=0)visible++;
                }
                if(visible==0 || hash==previous)throw new AssertionError("Empty or unchanged frame: "+color+"/"+hero+"/"+t);
                previous=hash;
            }
            count++;
        }
        SwingUtilities.invokeAndWait(()->{
            GameSession session=new GameEngine(new BoardRepository()).start(Difficulty.EASY,List.of("A","B"));
            BoardPanel panel=new BoardPanel();panel.setSize(700,700);panel.setSession(session);
            Piece attacker=session.players().get(0).pieces().get(0),victim=session.players().get(1).pieces().get(0);
            // Use an in-memory panel to exercise the timer, painting, and cleanup without changing the model.
            int[] frames={0};
            Timer probe=new Timer(45,e->{
                if(!panel.isCapturePlaying())return;
                BufferedImage surface=new BufferedImage(700,700,BufferedImage.TYPE_INT_ARGB);
                Graphics2D g=surface.createGraphics();panel.paint(g);g.dispose();frames[0]++;
            });
            probe.start();panel.playCapture(attacker,List.of(victim));probe.stop();
            if(panel.isCapturePlaying()||frames[0]<2)throw new AssertionError("Animation lifecycle failed");
            if(attacker.state()!=Piece.State.CASTLE||victim.state()!=Piece.State.CASTLE)throw new AssertionError("View changed model");
        });
        System.out.println("Capture animation: "+count+" character/color combinations, 80 frames and timer lifecycle PASSED");
        System.exit(0);
    }
}
