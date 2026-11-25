package view;

import model.*;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;

/** Exercises the actual artwork, production playback and the developer fixture on the EDT. */
public final class CaptureAnimationTests {
    private static final GameEngine ENGINE = new GameEngine(new BoardRepository());

    public static void main(String[] args) throws Exception {
        model.CaptureRuleTests.run();
        SwingUtilities.invokeAndWait(() -> {
            for (Difficulty difficulty : Difficulty.values()) {
                for (PlayerColor color : PlayerColor.values()) {
                    for (PlayerColor foe : PlayerColor.values()) {
                        if (color==foe) continue;
                        for (CaptureScenario.Direction direction : CaptureScenario.Direction.values()) {
                            for (int hero=0;hero<4;hero++) {
                                CaptureScenario scene = CaptureScenario.create(ENGINE,difficulty,color,hero,foe,(hero+1)%4,direction);
                                verifyEndpoints(scene);
                            }
                        }
                    }
                    for (int hero=0;hero<4;hero++) verifyArtwork(difficulty,color,hero);
                }
            }
            verifyLifecycle();
        });
        System.out.println("Capture tests passed: 1152 direction/color/hero/board scenarios, sprite bounds, resize, replay and cancellation.");
    }

    private static void verifyEndpoints(CaptureScenario scene) {
        Board board=scene.session().board();
        CaptureMove move=scene.capture(ENGINE);
        check(move.victims().equals(List.of(scene.defender())),"wrong defender");
        CaptureAnimation a=new CaptureAnimation(move,board);
        check(a.hides(scene.attacker()) && a.hides(scene.defender()),"duplicate sprites");
        Board.Position end=Board.Position.parse(scene.attacker().squareId(board));
        check(a.attackerAt(1).equals(new CaptureAnimation.Point(end.column()-.5,end.row()-.5)),"wrong landing");
        double[] yard=BoardPanel.castleArea(board,scene.defender().color(),0,0,1);
        double[] slot=BoardPanel.yardSpot(yard,scene.defender().number());
        CaptureAnimation.Point returned=a.defenderAt(0,1);
        check(Math.hypot(returned.x()-slot[0],returned.y()-slot[1])<1e-8,"wrong castle slot");
        check(scene.defender().state()==Piece.State.CASTLE,"model capture");
        ENGINE.completeMove(scene.session());
        check(scene.session().turn()==2 && scene.session().phase()==GameSession.Phase.ROLL,"turn stalled");
    }

    private static CaptureScenario scene(Difficulty d,PlayerColor color,int hero) {
        return CaptureScenario.create(ENGINE,d,color,hero,PlayerColor.values()[(color.ordinal()+1)%4],
                (hero+1)%4,CaptureScenario.Direction.East);
    }

    private static void verifyArtwork(Difficulty d,PlayerColor color,int hero) {
        CaptureScenario scene=scene(d,color,hero);
        CaptureAnimation a=new CaptureAnimation(scene.capture(ENGINE),scene.session().board());
        for(int tile:new int[]{18,46}) {
            int size=tile*d.boardSize()+16;
            BufferedImage image=new BufferedImage(size,size,BufferedImage.TYPE_INT_ARGB);
            int previous=0;
            for(double t:new double[]{0,.29,.44,.53,.72,.96}) {
                Graphics2D g=image.createGraphics();
                g.setComposite(AlphaComposite.Clear);g.fillRect(0,0,size,size);
                g.setComposite(AlphaComposite.SrcOver);
                AffineTransform before=g.getTransform();
                a.paint(g,8,8,tile,t);
                check(before.equals(g.getTransform()) && g.getComposite().equals(AlphaComposite.SrcOver),"graphics state leaked");
                g.dispose();
                int[] pixels=((java.awt.image.DataBufferInt)image.getRaster().getDataBuffer()).getData();
                int hash=Arrays.hashCode(pixels);
                check(hash!=previous,"frozen pose"); previous=hash;
                CaptureAnimation.Point p=a.attackerAt(t);
                int x=(int)(8+tile*p.x()), y=(int)(8+tile*p.y());
                check((image.getRGB(x,y)>>>24)>0,"missing attacker");
                if(t==0) {
                    // At rest, the first sprite occupies a fraction of one tile and has a clear margin.
                    int opaque=0;
                    for(int j=-tile/2;j<tile/2;j++) for(int i=-tile/2;i<tile/2;i++)
                        if((image.getRGB(x+i,y+j)>>>24)>0) opaque++;
                    check(opaque>.10*tile*tile && opaque<.75*tile*tile,"square or missing sprite");
                }
            }
        }
    }

    private static void verifyLifecycle() {
        BoardPanel board=new BoardPanel();
        for(int run=0;run<6;run++) {
            CaptureScenario scene=scene(Difficulty.HARD,PlayerColor.BLUE,run%4);
            board.setSession(scene.session());
            CaptureMove move=scene.capture(ENGINE);
            boolean cancel=run%2!=0;
            int[] frames={0};
            Timer probe=new Timer(22,e->{
                if(!board.isCapturePlaying()) return;
                frames[0]++;
                board.setSize(frames[0]%2==0?560:680,500);
                BufferedImage image=new BufferedImage(board.getWidth(),500,BufferedImage.TYPE_INT_ARGB);
                Graphics2D g=image.createGraphics();board.paint(g);g.dispose();
                if(cancel) board.setSession(null);
            });
            probe.start();
            boolean completed;
            try { completed=board.playCapture(move,run==0 ? 1 : 5); }
            finally {probe.stop();}
            check(completed!=cancel && !board.isCapturePlaying() && frames[0]>0,"playback lifecycle");
            check(scene.defender().state()==Piece.State.CASTLE,"playback changed model");
        }
        int[] ticks={0};
        CaptureScenario scene=scene(Difficulty.EASY,PlayerColor.RED,0);
        CapturePlayback playback=new CapturePlayback(new CaptureAnimation(scene.capture(ENGINE),scene.session().board()),8,()->ticks[0]++);
        check(playback.play(),"playback did not finish");
        int finalTicks=ticks[0];
        java.awt.SecondaryLoop wait=Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();
        Timer settle=new Timer(85,e->wait.exit());settle.setRepeats(false);settle.start();wait.enter();
        check(finalTicks==ticks[0],"timer fired after completion");
    }

    private static void check(boolean ok,String message) { if(!ok) throw new AssertionError(message); }
}
