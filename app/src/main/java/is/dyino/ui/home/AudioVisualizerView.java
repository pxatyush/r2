package is.dyino.ui.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.media.audiofx.Visualizer;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;

/**
 * Center-out audio visualizer.
 *
 * HALF_BARS unique frequency magnitudes are computed from the FFT
 * and drawn symmetrically: bar 0 is the innermost pair (closest to
 * center, driven by bass), bar HALF_BARS-1 is the outermost pair
 * (driven by treble). This guarantees the full width is always used
 * and the visualization is perfectly symmetric.
 *
 * Requires RECORD_AUDIO permission.  If the permission is missing the
 * view falls back to an idle sine-wave animation automatically.
 */
public class AudioVisualizerView extends View {

    // ── Config ─────────────────────────────────────────────────────
    /** Unique bars; each is mirrored → 2 × HALF_BARS bars on screen. */
    private static final int   HALF_BARS    = 22;
    private static final int   CAPTURE_SIZE = 1024;
    private static final float CORNER_R_DP  = 3f;
    /** Minimum bar height as a fraction of view height. */
    private static final float MIN_H        = 0.04f;
    /** Lerp smoothing factor (lower = smoother, higher = snappier). */
    private static final float SMOOTHING    = 0.28f;
    /** Fraction of total width used by bars + gaps. */
    private static final float FILL         = 0.92f;
    /** Gap-to-bar width ratio. */
    private static final float GAP_RATIO    = 0.35f;

    // ── State ─────────────────────────────────────────────────────
    private Visualizer visualizer;
    private final float[] magnitudes = new float[HALF_BARS];
    private final float[] targets    = new float[HALF_BARS];

    private boolean attached    = false;
    private boolean powerSaving = false;

    // ── Paint ─────────────────────────────────────────────────────
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF barRect  = new RectF();

    private int   accentColor = 0xFF6C63FF;
    private int   bgColor     = 0xFF0D0D14;
    private float cornerR;

    // ── Render loop ───────────────────────────────────────────────
    private final Handler  handler    = new Handler(Looper.getMainLooper());
    private final Runnable renderTick = new Runnable() {
        @Override public void run() {
            smoothToTargets();
            invalidate();
            if (attached) handler.postDelayed(this, 33); // ~30 fps
        }
    };

    // ── Idle animation ────────────────────────────────────────────
    private float   idlePhase = 0f;
    private boolean idle      = true;

    // ── Constructors ──────────────────────────────────────────────
    public AudioVisualizerView(Context ctx)                           { super(ctx); init(); }
    public AudioVisualizerView(Context ctx, AttributeSet a)          { super(ctx, a); init(); }
    public AudioVisualizerView(Context ctx, AttributeSet a, int d)   { super(ctx, a, d); init(); }

    private void init() {
        bgPaint.setStyle(Paint.Style.FILL);
        barPaint.setStyle(Paint.Style.FILL);
        cornerR = CORNER_R_DP * getResources().getDisplayMetrics().density;
        for (int i = 0; i < HALF_BARS; i++) magnitudes[i] = MIN_H;
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
            // Static mid-height bars — no animation
            for (int i = 0; i < HALF_BARS; i++)
                magnitudes[i] = 0.15f * ((float)(HALF_BARS - i) / HALF_BARS);
        }
        invalidate();
    }

    /**
     * Attaches to the audio session of the currently playing MediaPlayer.
     * Falls back to idle animation if RECORD_AUDIO is not granted or
     * the hardware is unavailable.
     *
     * @param audioSessionId  from MediaPlayer.getAudioSessionId()
     */
    public void attachAudioSession(int audioSessionId) {
        release();
        try {
            visualizer = new Visualizer(audioSessionId);
            visualizer.setCaptureSize(CAPTURE_SIZE);
            visualizer.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
                @Override
                public void onWaveFormDataCapture(Visualizer v, byte[] wf, int sr) {}
                @Override
                public void onFftDataCapture(Visualizer v, byte[] fft, int sr) {
                    processFft(fft);
                }
            }, Visualizer.getMaxCaptureRate() / 2, false, true);
            visualizer.setEnabled(true);
            attached = true;
            idle     = false;
        } catch (Exception e) {
            // SecurityException (no RECORD_AUDIO) or IllegalStateException
            attached = true;  // keep render loop alive for idle
            idle     = true;
        }
        startRender();
    }

    /** Runs the idle sine-wave animation (used when nothing is playing). */
    public void startIdle() {
        release();
        idle     = true;
        attached = true;
        startRender();
    }

    public void release() {
        handler.removeCallbacks(renderTick);
        attached = false;
        if (visualizer != null) {
            try { visualizer.setEnabled(false); visualizer.release(); }
            catch (Exception ignored) {}
            visualizer = null;
        }
    }

    // ── FFT Processing ────────────────────────────────────────────

    /**
     * Maps FFT buckets to HALF_BARS using a logarithmic scale so that
     * bass frequencies (low index = center) have more resolution than
     * treble (high index = edge).  Bar 0 is the innermost pair.
     */
    private void processFft(byte[] fft) {
        int buckets = Math.max(1, fft.length / 2);

        for (int i = 0; i < HALF_BARS; i++) {
            // Logarithmic mapping: 0 → 1 Hz range, HALF_BARS-1 → Nyquist
            float logStart = (float) Math.pow(buckets, (float) i       / HALF_BARS);
            float logEnd   = (float) Math.pow(buckets, (float)(i + 1)  / HALF_BARS);
            int start = Math.max(1, (int) logStart);
            int end   = Math.min(buckets, (int) logEnd + 1);

            float sum = 0;
            int   cnt = 0;
            for (int j = start; j < end; j++) {
                float re = (j * 2    < fft.length) ? fft[j * 2]     : 0;
                float im = (j * 2 + 1 < fft.length) ? fft[j * 2 + 1] : 0;
                sum += (float) Math.sqrt(re * re + im * im);
                cnt++;
            }
            float mag = cnt > 0 ? sum / (cnt * 128f) : 0;
            targets[i] = Math.max(MIN_H, Math.min(1f, mag));
        }
    }

    private void smoothToTargets() {
        if (powerSaving) return;

        if (idle) {
            // Symmetric ripple that propagates outward from center
            idlePhase += 0.055f;
            for (int i = 0; i < HALF_BARS; i++) {
                float t = (float) i / HALF_BARS;
                // Inner bars taller, outer bars shorter in idle
                float envelope = (float) Math.pow(1f - t, 0.7f);
                float wave = (float)(0.5 + 0.5 * Math.sin(
                        2 * Math.PI * t * 1.5f - idlePhase));
                magnitudes[i] = MIN_H + 0.22f * envelope * wave;
            }
        } else {
            for (int i = 0; i < HALF_BARS; i++) {
                magnitudes[i] += SMOOTHING * (targets[i] - magnitudes[i]);
            }
        }
    }

    private void startRender() {
        handler.removeCallbacks(renderTick);
        handler.post(renderTick);
    }

    // ── Drawing ───────────────────────────────────────────────────

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;

        // Background
        bgPaint.setColor(bgColor);
        canvas.drawRect(0, 0, w, h, bgPaint);

        if (powerSaving) {
            barPaint.setShader(null);
            barPaint.setColor((accentColor & 0x00FFFFFF) | 0x88000000);
            drawCenterOutBars(canvas, w, h);
            return;
        }

        // Gradient: top dimmed, bottom fully saturated
        int topColor = blendColors(accentColor, bgColor, 0.45f);
        barPaint.setShader(new LinearGradient(
                0, 0, 0, h, topColor, accentColor, Shader.TileMode.CLAMP));
        drawCenterOutBars(canvas, w, h);
    }

    /**
     * Draws HALF_BARS pairs symmetrically from the center.
     * Bar i=0 is the innermost (bass-driven), i=HALF_BARS-1 is outermost.
     */
    private void drawCenterOutBars(Canvas canvas, int w, int h) {
        float usableW  = w * FILL;
        float barCount = HALF_BARS * 2;               // total bars on screen
        float barW     = usableW / (barCount + barCount * GAP_RATIO + GAP_RATIO);
        float gapW     = barW * GAP_RATIO;

        // Total width of one bar+gap unit = barW + gapW
        // Center gap (between the two innermost bars) = gapW
        float centerX = w / 2f;

        for (int i = 0; i < HALF_BARS; i++) {
            float barH = Math.max(cornerR * 2, magnitudes[i] * h);
            float top  = h - barH;

            // Distance from center: innermost bar (i=0) is closest
            float offset = gapW / 2f + i * (barW + gapW);

            // Right bar
            float rx = centerX + offset;
            barRect.set(rx, top, rx + barW, h);
            canvas.drawRoundRect(barRect, cornerR, cornerR, barPaint);

            // Left bar (mirror)
            float lx = centerX - offset - barW;
            barRect.set(lx, top, lx + barW, h);
            canvas.drawRoundRect(barRect, cornerR, cornerR, barPaint);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────

    private static int blendColors(int c1, int c2, float ratio) {
        float inv = 1f - ratio;
        int a = (int)(((c1>>24)&0xFF)*inv + ((c2>>24)&0xFF)*ratio);
        int r = (int)(((c1>>16)&0xFF)*inv + ((c2>>16)&0xFF)*ratio);
        int g = (int)(((c1>> 8)&0xFF)*inv + ((c2>> 8)&0xFF)*ratio);
        int b = (int)(( c1     &0xFF)*inv + ( c2     &0xFF)*ratio);
        return (a<<24)|(r<<16)|(g<<8)|b;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        release();
    }
}
