package view;

import java.awt.EventQueue;
import java.awt.SecondaryLoop;
import java.awt.Toolkit;
import javax.swing.Timer;

/** One EDT-owned playback. The nested event pump keeps paint, resize and exit responsive
 * while preserving the controller's ordered special-square and turn continuations. */
final class CapturePlayback {
    static final int DURATION_MS = 1600;
    final CaptureAnimation animation;
    private final Runnable repaint;
    private final Timer timer;
    private final SecondaryLoop loop;
    private final double durationNanos;
    private long start;
    private double progress;
    private boolean cancelled;

    CapturePlayback(CaptureAnimation animation, double speed, Runnable repaint) {
        if (!EventQueue.isDispatchThread()) throw new IllegalStateException("Playback requires the EDT");
        if (!(speed > 0) || !Double.isFinite(speed)) throw new IllegalArgumentException("Invalid speed");
        this.animation = animation;
        this.repaint = repaint;
        durationNanos = DURATION_MS * 1_000_000.0 / speed;
        loop = Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();
        timer = new Timer(16, e -> tick());
    }

    double progress() { return progress; }

    boolean play() {
        start = System.nanoTime();
        timer.start();
        repaint.run();
        try {
            return loop.enter() && !cancelled;
        } finally {
            timer.stop();
        }
    }

    private void tick() {
        progress = Math.min(1, (System.nanoTime() - start) / durationNanos);
        repaint.run();
        if (progress >= 1) {
            timer.stop();
            loop.exit();
        }
    }

    void cancel() {
        cancelled = true;
        timer.stop();
        loop.exit();
    }
}
