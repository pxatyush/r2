package is.dyino.util;

import android.content.Context;
import android.os.CountDownTimer;

/**
 * Sleep timer that counts down and stops all audio when it reaches zero.
 * Usage: SleepTimerManager.get().start(millis, audioService);
 *        SleepTimerManager.get().cancel();
 *        SleepTimerManager.get().isRunning();
 *        SleepTimerManager.get().getRemainingMs();
 */
public class SleepTimerManager {

    public interface Listener {
        void onTick(long remainingMs);
        void onFinish();
    }

    private static SleepTimerManager instance;
    public static SleepTimerManager get() {
        if (instance == null) instance = new SleepTimerManager();
        return instance;
    }

    private CountDownTimer timer;
    private long           remainingMs = 0;
    private boolean        running     = false;
    private Listener       listener;

    public void setListener(Listener l) { this.listener = l; }

    public void start(long durationMs, is.dyino.service.AudioService audioService) {
        cancel();
        remainingMs = durationMs;
        running     = true;
        timer = new CountDownTimer(durationMs, 1000) {
            @Override public void onTick(long msLeft) {
                remainingMs = msLeft;
                if (listener != null) listener.onTick(msLeft);
            }
            @Override public void onFinish() {
                remainingMs = 0;
                running     = false;
                if (audioService != null) {
                    audioService.stopRadio();
                    audioService.stopAllSounds();
                }
                if (listener != null) listener.onFinish();
            }
        }.start();
    }

    public void cancel() {
        if (timer != null) { timer.cancel(); timer = null; }
        running     = false;
        remainingMs = 0;
        if (listener != null) listener.onTick(0);
    }

    public boolean isRunning()    { return running; }
    public long    getRemainingMs(){ return remainingMs; }

    /** "29m 45s" or "1h 02m" style label */
    public static String formatRemaining(long ms) {
        long totalSec = ms / 1000;
        long hours    = totalSec / 3600;
        long minutes  = (totalSec % 3600) / 60;
        long seconds  = totalSec % 60;
        if (hours > 0)
            return hours + "h " + String.format("%02d", minutes) + "m";
        return minutes + "m " + String.format("%02d", seconds) + "s";
    }
}
