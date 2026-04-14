package is.dyino.ui.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.media.audiofx.Visualizer;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;

/**
 * Real-time audio visualizer that hooks into Android's Visualizer API.
 * Shows animated bars driven by actual FFT frequency data.
 *
 * Usage:
 *   visualizer.attachAudioSession(audioSessionId);  // from MediaPlayer.getAudioSessionId()
 *   visualizer.setColors(accentColor, bgColor);
 *   visualizer.setPowerSaving(false);               // disable for static fill
 *   visualizer.release();                           // call in onPause / onDestroy
 */
public class AudioVisualizerView extends View {

    // ── Config ─────────────────────────────────────────────────────
    private static final int BAR_COUNT   = 32;   // number of frequency bars
    private static final int CAPTURE_SIZE= 1024; // FFT capture size
    private static final float CORNER_R  = 4f;   // bar corner radius dp
    private static final float MIN_H     = 0.04f; // min bar height as fraction of view height
    private static final float SMOOTHING = 0.35f; // lerp factor (lower = smoother)

    // ── State ─────────────────────────────────────────────────────
    private Visualizer visualizer;
    private final float[] magnitudes = new float[BAR_COUNT];
    private final float[] targets    = new float[BAR_COUNT];
    private boolean   attached   = false;
    private boolean   powerSaving= false;

    // ── Paint ─────────────────────────────────────────────────────
    private final Paint barPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF barRect    = new RectF();

    private int accentColor = 0xFF6C63FF;
    private int bgColor     = 0xFF0D0D14;
    private float cornerR;

    // ── Render loop ───────────────────────────────────────────────
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable renderTick = new Runnable() {
        @Override public void run() {
            smoothToTargets();
            invalidate();
            if (attached) handler.postDelayed(this, 32); // ~30 fps
        }
    };

    // ── Idle animation (used when no audio data or in power-save) ─
    private float idlePhase = 0f;
    private boolean idle    = true;

    public AudioVisualizerView(Context ctx) { super(ctx); init(); }
    public AudioVisualizerView(Context ctx, AttributeSet a) { super(ctx, a); init(); }
    public AudioVisualizerView(Context ctx, AttributeSet a, int d) { super(ctx, a, d); init(); }

    private void init() {
        bgPaint.setStyle(Paint.Style.FILL);
        barPaint.setStyle(Paint.Style.FILL);
        cornerR = CORNER_R * getResources().getDisplayMetrics().density;
        for (int i = 0; i < BAR_COUNT; i++) magnitudes[i] = MIN_H;
    }

    // ── Public API ────────────────────────────────────────────────

    public void setColors(int accent, int bg) {
        accentColor = accent;
        bgColor     = bg;
        invalidate();
    }

    public void setPowerSaving(boolean ps) {
        powerSaving = ps;
        if (ps) {
            // Fill bars to mid-height statically — no animation
            for (int i = 0; i < BAR_COUNT; i++) magnitudes[i] = 0.3f + 0.1f * ((i % 4) - 1.5f);
        }
        invalidate();
    }

    /**
     * Attach to the audio session of the playing MediaPlayer.
     * Call this after MediaPlayer.prepare() / onPrepared().
     * audioSessionId = mediaPlayer.getAudioSessionId()
     */
    public void attachAudioSession(int audioSessionId) {
        release(); // release any previous
        try {
            visualizer = new Visualizer(audioSessionId);
            visualizer.setCaptureSize(CAPTURE_SIZE);
            visualizer.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
                @Override
                public void onWaveFormDataCapture(Visualizer v, byte[] waveform, int samplingRate) {}

                @Override
                public void onFftDataCapture(Visualizer v, byte[] fft, int samplingRate) {
                    processFft(fft);
                }
            }, Visualizer.getMaxCaptureRate() / 2, false, true);
            visualizer.setEnabled(true);
            attached = true;
            idle     = false;
        } catch (Exception e) {
            // Permissions or hardware not available — fall back to idle
            attached = false;
            idle     = true;
        }
        startRender();
    }

    /** Switch to idle sine-wave animation (no audio session). */
    public void startIdle() {
        release();
        idle     = true;
        attached = true; // keep render loop running
        startRender();
    }

    public void release() {
        handler.removeCallbacks(renderTick);
        attached = false;
        if (visualizer != null) {
            try { visualizer.setEnabled(false); visualizer.release(); } catch (Exception ignored) {}
            visualizer = null;
        }
    }

    // ── FFT processing ────────────────────────────────────────────
    private void processFft(byte[] fft) {
        // fft[0] = DC, fft[1..n-1] = real/imag pairs for each bucket
        int buckets = Math.min(fft.length / 2, BAR_COUNT * 4);
        int step    = Math.max(1, buckets / BAR_COUNT);

        for (int i = 0; i < BAR_COUNT; i++) {
            int start = i * step + 1;
            int end   = Math.min(start + step, fft.length / 2);
            float sum = 0;
            for (int j = start; j < end; j++) {
                float re = fft[j * 2];
                float im = (j * 2 + 1 < fft.length) ? fft[j * 2 + 1] : 0;
                sum += (float) Math.sqrt(re * re + im * im);
            }
            float mag = Math.min(1f, (sum / (step * 128f)));
            targets[i] = Math.max(MIN_H, mag);
        }
    }

    /** Lerp current magnitudes toward targets for smooth animation. */
    private void smoothToTargets() {
        if (powerSaving) return;
        if (idle) {
            // Rippling idle sine waves
            idlePhase += 0.08f;
            for (int i = 0; i < BAR_COUNT; i++) {
                float t = (float) i / BAR_COUNT;
                magnitudes[i] = MIN_H + 0.15f
                    * (float)(0.5 + 0.5 * Math.sin(2 * Math.PI * t * 2 + idlePhase))
                    * (float)(0.7 + 0.3 * Math.sin(2 * Math.PI * t * 5 + idlePhase * 1.3));
            }
        } else {
            for (int i = 0; i < BAR_COUNT; i++) {
                magnitudes[i] = magnitudes[i] + SMOOTHING * (targets[i] - magnitudes[i]);
            }
        }
    }

    // ── Render loop ───────────────────────────────────────────────
    private void startRender() {
        handler.removeCallbacks(renderTick);
        handler.post(renderTick);
    }

    // ── Draw ──────────────────────────────────────────────────────
    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;

        // Background
        bgPaint.setColor(bgColor);
        canvas.drawRect(0, 0, w, h, bgPaint);

        if (powerSaving) {
            // Static colored bars — no gradient, no animation
            barPaint.setShader(null);
            barPaint.setColor(accentColor & 0x99FFFFFF | (accentColor & 0xFF000000));
            drawBars(canvas, w, h);
            return;
        }

        // Gradient: bottom = accent, top = accent dimmed
        int topColor   = blendColors(accentColor, bgColor, 0.3f);
        int botColor   = accentColor;
        barPaint.setShader(new LinearGradient(0, 0, 0, h, topColor, botColor, Shader.TileMode.CLAMP));
        drawBars(canvas, w, h);
    }

    private void drawBars(Canvas canvas, int w, int h) {
        float totalGap = w * 0.3f;
        float barW     = (w - totalGap) / BAR_COUNT;
        float gapW     = totalGap / (BAR_COUNT + 1);

        for (int i = 0; i < BAR_COUNT; i++) {
            float barH = Math.max(cornerR * 2, magnitudes[i] * h);
            float left = gapW + i * (barW + gapW);
            float top  = h - barH;
            barRect.set(left, top, left + barW, h);
            canvas.drawRoundRect(barRect, cornerR, cornerR, barPaint);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────
    private static int blendColors(int c1, int c2, float ratio) {
        int a = (int)(((c1>>24)&0xFF)*(1-ratio) + ((c2>>24)&0xFF)*ratio);
        int r = (int)(((c1>>16)&0xFF)*(1-ratio) + ((c2>>16)&0xFF)*ratio);
        int g = (int)(((c1>> 8)&0xFF)*(1-ratio) + ((c2>> 8)&0xFF)*ratio);
        int b = (int)(((c1    )&0xFF)*(1-ratio) + ((c2    )&0xFF)*ratio);
        return (a<<24)|(r<<16)|(g<<8)|b;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        release();
    }
}
