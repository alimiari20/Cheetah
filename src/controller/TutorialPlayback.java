package controller;

import model.TutorialScript;
import java.util.List;

/** Playback clock and chapter navigation, driven by a single UI timer. */
public final class TutorialPlayback {
    private final TutorialScript script;
    private final List<TutorialScript.Shot> shots;
    private int position;
    private boolean playing;
    private long started;
    private int origin;

    public record Frame(TutorialScript.Shot shot, int elapsed) {}

    public TutorialPlayback(TutorialScript script) {
        this.script = script;
        shots = script.shots();
    }

    public int position() { return position; }
    public boolean playing() { return playing; }

    public Frame frame() {
        int elapsed = position;
        for (var shot : shots) {
            if (elapsed < shot.duration()) return new Frame(shot, elapsed);
            elapsed -= shot.duration();
        }
        var last = shots.get(shots.size() - 1);
        return new Frame(last, last.duration());
    }

    public void play(long now) {
        if (playing) return;
        if (position == script.duration()) position = 0;
        origin = position;
        started = now;
        playing = true;
    }

    public void tick(long now) {
        if (!playing) return;
        position = (int)Math.min(script.duration(), origin + (now - started) / 1_000_000);
        if (position == script.duration()) playing = false;
    }

    public void pause(long now) {
        tick(now);
        playing = false;
    }

    public void seek(int milliseconds, long now) {
        position = Math.max(0, Math.min(milliseconds, script.duration()));
        origin = position;
        started = now;
        if (position == script.duration()) playing = false;
    }

    public void chapter(int index, long now) {
        seek(script.chapterStart(Math.max(0, Math.min(index, TutorialScript.CHAPTERS.size() - 1))), now);
    }
}
