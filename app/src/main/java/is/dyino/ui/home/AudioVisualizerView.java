package is.dyino.ui.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.media.audiofx.Visualizer;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;

/**
 * Multi-mode audio visualizer with 7 rendering types.
 *
 * Type strings (set via setVisualizerType):
 *   "center_bars"   – symmetric bars from center (default)
 *   "spectrum"      – classic L→R frequency bars, mirrored
 *   "dots"          – bouncing dots with gravity
 *   "heartbeat"     – EKG with 4–5 spikes across full width
 *   "circular"      – radial ring bars
 *   "waveform"      – smooth FFT-reconstructed waveform (NOT raw PCM)
 *   "river_waves"   – overlapping translucent water waves
 *
 * Preset names are resolved to type strings externally (SettingsFragment reads
 * assets/visualizer/*.json and passes the "type" field here).
 */
public class AudioVisualizerView extends View {

    // ── Type string constants ─────────────────────────────────────
    public static final String T_CENTER_BARS = "center_bars";
    public static final String T_SPECTRUM    = "spectrum";
    public static final String T_DOTS        = "dots";
    public static final String T_HEARTBEAT   = "heartbeat";
    public static final String T_CIRCULAR    = "circular";
    public static final String T_WAVEFORM    = "waveform";
    public static final String T_RIVER_WAVES = "river_waves";

    // ── FFT config ────────────────────────────────────────────────
    private static final int   HALF_BARS    = 22;
    private static final int   CAPTURE_SIZE = 1024;
    private static final float MIN_H        = 0.04f;
    private static final float SMOOTHING    = 0.28f;

    // ── Audio data ────────────────────────────────────────────────
    private Visualizer    visualizer;
    private final float[] magnitudes = new float[HALF_BARS];
    private final float[] targets    = new float[HALF_BARS];

    // ── State ─────────────────────────────────────────────────────
    private boolean attached    = false;
    private boolean powerSaving = false;
    private String  visType     = T_CENTER_BARS;

    private float   idlePhase = 0f;
    private boolean idle      = true;

    // ── Heartbeat ─────────────────────────────────────────────────
    private float hbAmplitude = 0.25f;
    private float hbPhase     = 0f;

    // ── Dots ──────────────────────────────────────────────────────
    private static final int  DOT_COLS = 22;
    private final float[] dotY   = new float[DOT_COLS];
    private final float[] dotVel = new float[DOT_COLS];
    private boolean dotsReady = false;

    // ── River Waves ───────────────────────────────────────────────
    private float riverPhase = 0f;

    // ── Waveform (FFT-based, smooth) ──────────────────────────────
    private final float[] waveSmooth = new float[HALF_BARS]; // smoothed FFT magnitudes for wave

    // ── Paint ─────────────────────────────────────────────────────
    private final Paint barPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tailPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF barRect   = new RectF();
    private final Path  path      = new Path();

    private int   accentColor = 0xFF6C63FF;
    private int   bgColor     = 0xFF0D0D14;
    private float cornerR;

    // ── Render loop ───────────────────────────────────────────────
    private final Handler  handler = new Handler(Looper.getMainLooper());
    private final Runnable tick    = new Runnable() {
        @Override public void run() {
            animate(); invalidate();
            if (attached) handler.postDelayed(this, 33);
        }
    };

    // ── Constructors ──────────────────────────────────────────────
    public AudioVisualizerView(Context ctx)                          { super(ctx); init(); }
    public AudioVisualizerView(Context ctx, AttributeSet a)         { super(ctx, a); init(); }
    public AudioVisualizerView(Context ctx, AttributeSet a, int d)  { super(ctx, a, d); init(); }

    private void init() {
        bgPaint.setStyle(Paint.Style.FILL);
        barPaint.setStyle(Paint.Style.FILL);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        dotPaint.setStyle(Paint.Style.FILL);
        tailPaint.setStyle(Paint.Style.STROKE);
        tailPaint.setStrokeCap(Paint.Cap.ROUND);
        cornerR = 3f * getResources().getDisplayMetrics().density;
        for (int i = 0; i < HALF_BARS; i++) magnitudes[i] = MIN_H;
    }

    // ── Public API ────────────────────────────────────────────────

    public void setColors(int accent, int bg) { accentColor = accent; bgColor = bg; invalidate(); }

    public void setVisualizerType(String type) {
        visType   = (type != null) ? type : T_CENTER_BARS;
        dotsReady = false;
        invalidate();
    }

    public void setPowerSaving(boolean ps) {
        powerSaving = ps;
        if (ps) for (int i = 0; i < HALF_BARS; i++)
            magnitudes[i] = 0.12f * ((float)(HALF_BARS - i) / HALF_BARS);
        invalidate();
    }

    public void attachAudioSession(int sessionId) {
        release();
        try {
            visualizer = new Visualizer(sessionId);
            visualizer.setCaptureSize(CAPTURE_SIZE);
            visualizer.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
                @Override public void onWaveFormDataCapture(Visualizer v, byte[] wf, int sr) {}
                @Override public void onFftDataCapture(Visualizer v, byte[] fft, int sr) { processFft(fft); }
            }, Visualizer.getMaxCaptureRate() / 2, false, true);
            visualizer.setEnabled(true);
            attached = true; idle = false;
        } catch (Exception e) {
            attached = true; idle = true;
        }
        startLoop();
    }

    public void startIdle() { release(); idle = true; attached = true; startLoop(); }

    public void release() {
        handler.removeCallbacks(tick); attached = false;
        if (visualizer != null) {
            try { visualizer.setEnabled(false); visualizer.release(); } catch (Exception ignored) {}
            visualizer = null;
        }
    }

    // ── FFT processing ────────────────────────────────────────────

    private void processFft(byte[] fft) {
        int buckets = Math.max(1, fft.length / 2);
        for (int i = 0; i < HALF_BARS; i++) {
            float ls = (float) Math.pow(buckets, (float) i       / HALF_BARS);
            float le = (float) Math.pow(buckets, (float)(i + 1)  / HALF_BARS);
            int s = Math.max(1, (int) ls), e = Math.min(buckets, (int) le + 1);
            float sum = 0; int cnt = 0;
            for (int j = s; j < e; j++) {
                float re = (j*2     < fft.length) ? fft[j*2]     : 0;
                float im = (j*2 + 1 < fft.length) ? fft[j*2 + 1] : 0;
                sum += (float) Math.sqrt(re*re + im*im); cnt++;
            }
            targets[i] = cnt > 0 ? Math.max(MIN_H, Math.min(1f, sum / (cnt * 128f))) : MIN_H;
        }
    }

    // ── Per-frame animation ───────────────────────────────────────

    private void animate() {
        if (powerSaving) return;
        if (idle) {
            idlePhase  += 0.045f;
            hbPhase    += 0.06f;
            riverPhase += 0.03f;
            for (int i = 0; i < HALF_BARS; i++) {
                float t = (float) i / HALF_BARS;
                float e = (float) Math.pow(1f - t, 0.7f);
                float m = MIN_H + 0.20f * e * (float)(0.5 + 0.5 * Math.sin(2*Math.PI*t*1.5 - idlePhase));
                magnitudes[i]  = m;
                waveSmooth[i]  = m;
            }
            hbAmplitude = 0.25f + 0.18f * (float)(0.5 + 0.5 * Math.sin(hbPhase));
        } else {
            // Smooth FFT → magnitudes (bars)
            for (int i = 0; i < HALF_BARS; i++)
                magnitudes[i] += SMOOTHING * (targets[i] - magnitudes[i]);

            // Extra-smooth for waveform (less jitter)
            for (int i = 0; i < HALF_BARS; i++)
                waveSmooth[i] += 0.12f * (targets[i] - waveSmooth[i]);

            // Heartbeat amplitude from bass
            float bass = 0; for (int i = 0; i < 4; i++) bass += targets[i]; bass /= 4f;
            hbAmplitude += 0.30f * (bass - hbAmplitude);
            hbPhase += 0.10f + hbAmplitude * 0.25f;

            riverPhase += 0.025f + hbAmplitude * 0.04f;
            idlePhase  += 0.03f;
        }
        if (T_DOTS.equals(visType)) tickDots();
    }

    private void tickDots() {
        int w = getWidth(), h = getHeight(); if (w==0||h==0) return;
        if (!dotsReady) { for (int i=0;i<DOT_COLS;i++){dotY[i]=h;dotVel[i]=0;} dotsReady=true; }
        float grav = h * 0.018f;
        for (int i = 0; i < DOT_COLS; i++) {
            int   mi  = i * HALF_BARS / DOT_COLS;
            float tgt = h * (1f - magnitudes[mi]);
            if (dotY[i] >= h * 0.92f && tgt < h * 0.78f)
                dotVel[i] = -(float) Math.sqrt(2 * grav * (h - tgt));
            dotVel[i] += grav;
            dotY[i]    = Math.max(0, Math.min(h, dotY[i] + dotVel[i]));
            if (dotY[i] >= h) dotVel[i] = 0;
        }
    }

    private void startLoop() { handler.removeCallbacks(tick); handler.post(tick); }

    // ── Draw dispatch ─────────────────────────────────────────────

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight(); if (w==0||h==0) return;
        bgPaint.setColor(bgColor);
        canvas.drawRect(0, 0, w, h, bgPaint);
        if (powerSaving) {
            barPaint.setColor((accentColor & 0x00FFFFFF) | 0x88000000);
            barPaint.setShader(null);
            drawCenterBars(canvas, w, h); return;
        }
        switch (visType) {
            case T_SPECTRUM:    drawSpectrum(canvas, w, h);   break;
            case T_DOTS:        drawDots(canvas, w, h);       break;
            case T_HEARTBEAT:   drawHeartbeat(canvas, w, h);  break;
            case T_CIRCULAR:    drawCircular(canvas, w, h);   break;
            case T_WAVEFORM:    drawWaveform(canvas, w, h);   break;
            case T_RIVER_WAVES: drawRiverWaves(canvas, w, h); break;
            default:            drawCenterBars(canvas, w, h); break;
        }
    }

    // ── 0: Center bars ────────────────────────────────────────────

    private void drawCenterBars(Canvas canvas, int w, int h) {
        float barW = w * 0.92f / (HALF_BARS * 2 * 1.35f + 0.35f);
        float gapW = barW * 0.35f, cx = w / 2f;
        barPaint.setShader(new LinearGradient(0,0,0,h, blend(accentColor,bgColor,0.4f), accentColor, Shader.TileMode.CLAMP));
        for (int i = 0; i < HALF_BARS; i++) {
            float bh  = Math.max(cornerR*2, magnitudes[i]*h), top = h-bh;
            float off = gapW/2f + i*(barW+gapW);
            barRect.set(cx+off, top, cx+off+barW, h); canvas.drawRoundRect(barRect,cornerR,cornerR,barPaint);
            barRect.set(cx-off-barW, top, cx-off, h); canvas.drawRoundRect(barRect,cornerR,cornerR,barPaint);
        }
        barPaint.setShader(null);
    }

    // ── 1: Symmetric spectrum ─────────────────────────────────────

    private void drawSpectrum(Canvas canvas, int w, int h) {
        float barW = w / (HALF_BARS * 2 * 1.25f), gapW = barW * 0.25f;
        float totalW = HALF_BARS * 2 * (barW+gapW), sx = (w-totalW)/2f;
        barPaint.setShader(new LinearGradient(0,h*0.15f,0,h, blend(accentColor,bgColor,0.5f), accentColor, Shader.TileMode.CLAMP));
        for (int i = 0; i < HALF_BARS; i++) {
            int   mi  = HALF_BARS-1-i;
            float bh  = Math.max(cornerR*2, magnitudes[mi]*h), x = sx+i*(barW+gapW);
            barRect.set(x, h-bh, x+barW, h); canvas.drawRoundRect(barRect,cornerR,cornerR,barPaint);
            float x2  = sx+(HALF_BARS+i)*(barW+gapW);
            float bh2 = Math.max(cornerR*2, magnitudes[i]*h);
            barRect.set(x2, h-bh2, x2+barW, h); canvas.drawRoundRect(barRect,cornerR,cornerR,barPaint);
        }
        barPaint.setShader(null);
    }

    // ── 2: Bouncing dots ─────────────────────────────────────────

    private void drawDots(Canvas canvas, int w, int h) {
        float cellW = (float) w / DOT_COLS, r = Math.min(cellW*0.32f, h*0.07f);
        tailPaint.setStrokeWidth(r * 0.7f);
        for (int i = 0; i < DOT_COLS; i++) {
            float cx = cellW*i + cellW/2f, cy = dotY[i];
            float bright = 1f - cy/h;
            int col = blend(accentColor, bgColor, 0.6f - bright*0.5f);
            tailPaint.setColor((col & 0x00FFFFFF) | 0x44000000);
            canvas.drawLine(cx, h, cx, cy+r, tailPaint);
            dotPaint.setColor(col);
            canvas.drawCircle(cx, cy, r, dotPaint);
        }
    }

    // ── 3: Heartbeat EKG with 4-5 spikes ─────────────────────────
    // Each beat cycle has a full P-QRS-T complex.
    // 4 complete cycles shown across the width.

    private void drawHeartbeat(Canvas canvas, int w, int h) {
        float cy  = h / 2f;
        float amp = Math.max(h * 0.08f, h * 0.44f * hbAmplitude);
        float strokeW = Math.max(2f, h * 0.05f);
        linePaint.setStrokeWidth(strokeW);
        linePaint.setShader(new LinearGradient(0,0,w,0, blend(accentColor,bgColor,0.4f), accentColor, Shader.TileMode.CLAMP));

        int beats = 4;
        float sw = (float) w / beats;
        path.reset();

        for (int b = 0; b < beats; b++) {
            float o = b * sw;
            // Flat baseline (18%)
            if (b == 0) path.moveTo(o, cy); else path.lineTo(o, cy);
            path.lineTo(o + sw*0.18f, cy);
            // P-wave: small upward bump (8%)
            path.quadTo(o + sw*0.20f, cy - amp*0.18f, o + sw*0.23f, cy - amp*0.12f);
            path.quadTo(o + sw*0.25f, cy - amp*0.06f, o + sw*0.27f, cy);
            // PR segment flat (5%)
            path.lineTo(o + sw*0.32f, cy);
            // Q dip (3%)
            path.lineTo(o + sw*0.35f, cy + amp*0.22f);
            // R spike UP – the main tall spike (7%)
            path.lineTo(o + sw*0.42f, cy - amp);
            // S back down past baseline (5%)
            path.lineTo(o + sw*0.47f, cy + amp*0.15f);
            // Back to baseline (3%)
            path.lineTo(o + sw*0.50f, cy);
            // ST segment flat (8%)
            path.lineTo(o + sw*0.58f, cy);
            // T-wave: gentle dome (14%)
            path.quadTo(o + sw*0.65f, cy - amp*0.32f, o + sw*0.72f, cy);
            // Flat to end of cycle (28%)
            path.lineTo(o + sw, cy);
        }

        canvas.drawPath(path, linePaint);
        linePaint.setShader(null);
    }

    // ── 4: Circular radial ────────────────────────────────────────

    private void drawCircular(Canvas canvas, int w, int h) {
        float cx = w/2f, cy = h/2f;
        float minR = Math.min(w,h)*0.22f, maxR = Math.min(w,h)*0.48f;
        int   nBars  = HALF_BARS * 2;
        float dAngle = 360f / nBars;
        barPaint.setStyle(Paint.Style.STROKE);
        barPaint.setStrokeCap(Paint.Cap.ROUND);
        barPaint.setStrokeWidth(Math.max(2f, dAngle*(float)Math.PI*minR/180f - 1.5f));
        barPaint.setShader(new SweepGradient(cx, cy, accentColor, blend(accentColor,bgColor,0.5f)));
        for (int i = 0; i < nBars; i++) {
            int   mi  = i < HALF_BARS ? i : nBars-1-i; mi = Math.min(mi, HALF_BARS-1);
            float r   = minR + (maxR-minR)*magnitudes[mi];
            float rad = (float) Math.toRadians(dAngle*i - 90f);
            float cos = (float) Math.cos(rad), sin = (float) Math.sin(rad);
            canvas.drawLine(cx+minR*cos, cy+minR*sin, cx+r*cos, cy+r*sin, barPaint);
        }
        barPaint.setStyle(Paint.Style.FILL);
        barPaint.setShader(null);
    }

    // ── 5: Waveform – FFT-reconstructed, no raw PCM noise ─────────
    // Reconstructs a sine-blend from smoothed FFT magnitudes.
    // Dominates on low-freq (bass) so it pulses cleanly with the music.

    private void drawWaveform(Canvas canvas, int w, int h) {
        float cy  = h / 2f;
        float strokeW = Math.max(3f, h * 0.055f);
        linePaint.setStrokeWidth(strokeW);
        linePaint.setShader(new LinearGradient(0,0,w,0, blend(accentColor,bgColor,0.25f), accentColor, Shader.TileMode.CLAMP));
        path.reset();

        // Build waveform by summing frequency components from waveSmooth
        int steps = w;
        for (int x = 0; x <= steps; x++) {
            float t = (float) x / steps;  // 0..1 across width
            float y = 0;
            // Sum first 8 harmonics weighted by magnitude
            for (int k = 0; k < Math.min(8, HALF_BARS); k++) {
                float freq  = (k + 1) * 2f;
                float phase = idlePhase * (1f + k * 0.3f);
                float amp   = waveSmooth[k] * h * 0.36f / (k + 1f);
                y += (float)(Math.sin(t * 2 * Math.PI * freq + phase)) * amp;
            }
            y = cy + y;
            if (x == 0) path.moveTo(0, y); else path.lineTo(x, y);
        }
        canvas.drawPath(path, linePaint);
        linePaint.setShader(null);
    }

    // ── 6: River Waves ────────────────────────────────────────────
    // 4 overlapping translucent wave layers at different speeds/amplitudes.
    // Amplitude driven by overall audio energy.

    private void drawRiverWaves(Canvas canvas, int w, int h) {
        float energy = 0; for (int i=0;i<HALF_BARS;i++) energy += magnitudes[i]; energy /= HALF_BARS;
        float baseAmp = h * (0.12f + energy * 0.28f);
        float cy = h * 0.55f;   // slightly below center for water-surface feel

        // 4 wave layers from back (most transparent) to front (most opaque)
        float[][] layers = {
            {0.55f,  1.7f,  0.22f, 0.18f},   // {speedMul, freqMul, ampMul, alpha}
            {0.80f,  2.3f,  0.35f, 0.28f},
            {1.10f,  3.1f,  0.55f, 0.40f},
            {1.40f,  4.0f,  0.78f, 0.65f},
        };

        for (float[] layer : layers) {
            float speed = layer[0], freq = layer[1], ampMul = layer[2], alpha = layer[3];
            float amp   = baseAmp * ampMul;
            float phase = riverPhase * speed;

            int layerAlpha = (int)(alpha * 255);
            int col = (layerAlpha << 24) | (accentColor & 0x00FFFFFF);

            Paint wavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            wavePaint.setStyle(Paint.Style.FILL);
            wavePaint.setColor(col);

            path.reset();
            int steps = w / 2;   // fewer steps = faster, still smooth
            for (int x = 0; x <= steps; x++) {
                float t = (float) x * 2 / steps;  // 0..2 (two screen widths for wrap feel)
                float xPx = (float) x * w / steps;
                // Primary wave
                float y = cy
                    + (float)(Math.sin(t * Math.PI * freq + phase)             * amp)
                    + (float)(Math.sin(t * Math.PI * freq * 1.6f + phase*0.7f) * amp * 0.4f)
                    + (float)(Math.cos(t * Math.PI * freq * 0.4f - phase*0.3f) * amp * 0.2f);
                if (x == 0) path.moveTo(xPx, y); else path.lineTo(xPx, y);
            }
            // Close fill to bottom
            path.lineTo(w, h); path.lineTo(0, h); path.close();
            canvas.drawPath(path, wavePaint);
        }
    }

    // ── Helper ────────────────────────────────────────────────────

    private static int blend(int c1, int c2, float r) {
        float v=1f-r;
        return (((int)(((c1>>24)&0xFF)*v+((c2>>24)&0xFF)*r))<<24)
             | (((int)(((c1>>16)&0xFF)*v+((c2>>16)&0xFF)*r))<<16)
             | (((int)(((c1>>8) &0xFF)*v+((c2>>8) &0xFF)*r))<< 8)
             | (((int)(( c1     &0xFF)*v+( c2     &0xFF)*r)));
    }

    @Override protected void onDetachedFromWindow() { super.onDetachedFromWindow(); release(); }
}