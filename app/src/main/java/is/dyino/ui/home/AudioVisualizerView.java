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

import is.dyino.util.AppPrefs;

/**
 * Multi-mode audio visualizer.
 *
 * Type constants live in AppPrefs so the preference can be saved/read
 * without importing this UI class from non-UI code.
 *
 * Modes:
 *   0 — CENTER_BARS  symmetric bars from center, bass-driven
 *   1 — WAVEFORM     smooth waveform line, driven by raw PCM
 *   2 — SPECTRUM     symmetric left→right frequency spectrum
 *   3 — DOTS         bouncing dots with gravity
 *   4 — HEARTBEAT    EKG pulse driven by bass amplitude
 *   5 — CIRCULAR     radial bar ring
 */
public class AudioVisualizerView extends View {

    private static final int   HALF_BARS    = 22;
    private static final int   DOT_COLS     = 22;
    private static final int   CAPTURE_SIZE = 1024;
    private static final float MIN_H        = 0.04f;
    private static final float SMOOTHING    = 0.28f;

    // ── Audio data ────────────────────────────────────────────────
    private Visualizer     visualizer;
    private final float[]  magnitudes   = new float[HALF_BARS];
    private final float[]  targets      = new float[HALF_BARS];
    private volatile byte[] waveformData = null;

    // ── State ─────────────────────────────────────────────────────
    private boolean attached    = false;
    private boolean powerSaving = false;
    private int     visType     = AppPrefs.VIS_CENTER_BARS;

    private float   idlePhase = 0f;
    private boolean idle      = true;

    // Heartbeat
    private float hbPhase     = 0f;
    private float hbAmplitude = 0.3f;
    private float hbTarget    = 0.3f;

    // Dots
    private final float[] dotY   = new float[DOT_COLS];
    private final float[] dotVel = new float[DOT_COLS];
    private boolean dotsReady    = false;

    // ── Paint objects ─────────────────────────────────────────────
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
            animate();
            invalidate();
            if (attached) handler.postDelayed(this, 33);
        }
    };

    // ── Constructor ───────────────────────────────────────────────
    public AudioVisualizerView(Context ctx)                         { super(ctx); init(); }
    public AudioVisualizerView(Context ctx, AttributeSet a)        { super(ctx, a); init(); }
    public AudioVisualizerView(Context ctx, AttributeSet a, int d) { super(ctx, a, d); init(); }

    private void init() {
        bgPaint.setStyle(Paint.Style.FILL);
        barPaint.setStyle(Paint.Style.FILL);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(4f);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        dotPaint.setStyle(Paint.Style.FILL);
        tailPaint.setStyle(Paint.Style.STROKE);
        tailPaint.setStrokeCap(Paint.Cap.ROUND);
        cornerR = 3f * getResources().getDisplayMetrics().density;
        for (int i = 0; i < HALF_BARS; i++) magnitudes[i] = MIN_H;
    }

    // ── Public API ────────────────────────────────────────────────

    public void setColors(int accent, int bg) {
        accentColor = accent; bgColor = bg; invalidate();
    }

    public void setVisualizerType(int type) {
        visType   = type;
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
                @Override public void onWaveFormDataCapture(Visualizer v, byte[] wf, int sr) {
                    waveformData = wf.clone();
                }
                @Override public void onFftDataCapture(Visualizer v, byte[] fft, int sr) {
                    processFft(fft);
                }
            }, Visualizer.getMaxCaptureRate() / 2, true, true);
            visualizer.setEnabled(true);
            attached = true; idle = false;
        } catch (Exception e) {
            attached = true; idle = true;
        }
        startLoop();
    }

    public void startIdle() {
        release(); idle = true; attached = true; startLoop();
    }

    public void release() {
        handler.removeCallbacks(tick);
        attached = false;
        if (visualizer != null) {
            try { visualizer.setEnabled(false); visualizer.release(); }
            catch (Exception ignored) {}
            visualizer = null;
        }
        waveformData = null;
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
            idlePhase += 0.055f; hbPhase += 0.08f;
            for (int i = 0; i < HALF_BARS; i++) {
                float t = (float) i / HALF_BARS;
                float e = (float) Math.pow(1f - t, 0.7f);
                magnitudes[i] = MIN_H + 0.22f * e
                        * (float)(0.5 + 0.5 * Math.sin(2 * Math.PI * t * 1.5 - idlePhase));
            }
            hbAmplitude = 0.3f + 0.2f * (float)(0.5 + 0.5 * Math.sin(hbPhase));
        } else {
            for (int i = 0; i < HALF_BARS; i++)
                magnitudes[i] += SMOOTHING * (targets[i] - magnitudes[i]);
            float bass = 0;
            for (int i = 0; i < 3; i++) bass += magnitudes[i];
            bass /= 3f;
            hbAmplitude += 0.35f * (bass - hbAmplitude);
            hbPhase += 0.12f + hbAmplitude * 0.3f;
        }
        if (visType == AppPrefs.VIS_DOTS) tickDots();
    }

    private void tickDots() {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        if (!dotsReady) {
            for (int i = 0; i < DOT_COLS; i++) { dotY[i] = h; dotVel[i] = 0; }
            dotsReady = true;
        }
        float grav = h * 0.018f;
        for (int i = 0; i < DOT_COLS; i++) {
            int   mi  = i * HALF_BARS / DOT_COLS;
            float tgt = h * (1f - magnitudes[mi]);
            if (dotY[i] >= h * 0.92f && tgt < h * 0.78f)
                dotVel[i] = -(float) Math.sqrt(2 * grav * (h - tgt));
            dotVel[i] += grav;
            dotY[i]   = Math.max(0, Math.min(h, dotY[i] + dotVel[i]));
            if (dotY[i] >= h) dotVel[i] = 0;
        }
    }

    private void startLoop() {
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    // ── Draw dispatch ─────────────────────────────────────────────

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        bgPaint.setColor(bgColor);
        canvas.drawRect(0, 0, w, h, bgPaint);
        if (powerSaving) { barPaint.setColor((accentColor & 0x00FFFFFF)|0x88000000); drawCenterBars(canvas,w,h); return; }
        switch (visType) {
            case AppPrefs.VIS_WAVEFORM:  drawWaveform(canvas,w,h);  break;
            case AppPrefs.VIS_SPECTRUM:  drawSpectrum(canvas,w,h);  break;
            case AppPrefs.VIS_DOTS:      drawDots(canvas,w,h);      break;
            case AppPrefs.VIS_HEARTBEAT: drawHeartbeat(canvas,w,h); break;
            case AppPrefs.VIS_CIRCULAR:  drawCircular(canvas,w,h);  break;
            default:                     drawCenterBars(canvas,w,h); break;
        }
    }

    // ── 0: Center bars ────────────────────────────────────────────

    private void drawCenterBars(Canvas canvas, int w, int h) {
        float barW = w * 0.92f / (HALF_BARS * 2 * 1.35f + 0.35f);
        float gapW = barW * 0.35f, cx = w / 2f;
        barPaint.setShader(new LinearGradient(0,0,0,h, blend(accentColor,bgColor,0.4f), accentColor, Shader.TileMode.CLAMP));
        for (int i = 0; i < HALF_BARS; i++) {
            float bh = Math.max(cornerR*2, magnitudes[i]*h), top = h-bh, off = gapW/2f + i*(barW+gapW);
            barRect.set(cx+off, top, cx+off+barW, h); canvas.drawRoundRect(barRect,cornerR,cornerR,barPaint);
            barRect.set(cx-off-barW, top, cx-off, h); canvas.drawRoundRect(barRect,cornerR,cornerR,barPaint);
        }
        barPaint.setShader(null);
    }

    // ── 1: Waveform ───────────────────────────────────────────────

    private void drawWaveform(Canvas canvas, int w, int h) {
        linePaint.setStrokeWidth(Math.max(3f, h * 0.05f));
        linePaint.setShader(new LinearGradient(0,0,w,0, blend(accentColor,bgColor,0.3f), accentColor, Shader.TileMode.CLAMP));
        byte[] data = waveformData;
        float cy = h/2f, amp = h*0.42f;
        path.reset();
        if (data != null && data.length > 1) {
            for (int x = 0; x < w; x++) {
                int idx = Math.min((int)((float)x/w*data.length), data.length-1);
                float y = cy - (data[idx]/128f)*amp;
                if (x==0) path.moveTo(0,y); else path.lineTo(x,y);
            }
        } else {
            for (int x = 0; x <= w; x++) {
                float t = (float)x/w;
                float y = cy + (float)(Math.sin(t*2*Math.PI*3+idlePhase)*amp*0.55 + Math.sin(t*2*Math.PI*7+idlePhase*1.3)*amp*0.2);
                if (x==0) path.moveTo(0,y); else path.lineTo(x,y);
            }
        }
        canvas.drawPath(path, linePaint);
        linePaint.setShader(null);
    }

    // ── 2: Symmetric spectrum ─────────────────────────────────────

    private void drawSpectrum(Canvas canvas, int w, int h) {
        float barW = w/(HALF_BARS*2*1.25f), gapW = barW*0.25f;
        float totalW = HALF_BARS*2*(barW+gapW), sx = (w-totalW)/2f;
        barPaint.setShader(new LinearGradient(0,h*0.15f,0,h, blend(accentColor,bgColor,0.5f), accentColor, Shader.TileMode.CLAMP));
        for (int i = 0; i < HALF_BARS; i++) {
            int mi = HALF_BARS-1-i;
            float bh = Math.max(cornerR*2, magnitudes[mi]*h), x = sx+i*(barW+gapW);
            barRect.set(x, h-bh, x+barW, h); canvas.drawRoundRect(barRect,cornerR,cornerR,barPaint);
            float x2 = sx+(HALF_BARS+i)*(barW+gapW);
            float bh2 = Math.max(cornerR*2, magnitudes[i]*h);
            barRect.set(x2, h-bh2, x2+barW, h); canvas.drawRoundRect(barRect,cornerR,cornerR,barPaint);
        }
        barPaint.setShader(null);
    }

    // ── 3: Bouncing dots ─────────────────────────────────────────

    private void drawDots(Canvas canvas, int w, int h) {
        float cellW = (float)w/DOT_COLS, r = Math.min(cellW*0.32f, h*0.07f);
        tailPaint.setStrokeWidth(r*0.7f);
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

    // ── 4: Heartbeat / EKG ───────────────────────────────────────

    private void drawHeartbeat(Canvas canvas, int w, int h) {
        float cy  = h/2f, amp = h*0.40f*Math.max(0.2f, hbAmplitude);
        linePaint.setStrokeWidth(Math.max(2f, h*0.045f));
        linePaint.setShader(new LinearGradient(0,0,w,0, blend(accentColor,bgColor,0.5f), accentColor, Shader.TileMode.CLAMP));
        path.reset();
        int beats = 2;
        float sw = (float)w/beats;
        for (int b = 0; b < beats; b++) {
            float o = b*sw;
            path.moveTo(o,           cy);
            path.lineTo(o+sw*0.20f,  cy);
            path.quadTo(o+sw*0.23f,  cy-amp*0.15f, o+sw*0.26f, cy);
            path.lineTo(o+sw*0.38f,  cy);
            path.lineTo(o+sw*0.43f,  cy+amp*0.25f);
            path.lineTo(o+sw*0.53f,  cy-amp);
            path.lineTo(o+sw*0.60f,  cy+amp*0.10f);
            path.lineTo(o+sw*0.65f,  cy);
            path.quadTo(o+sw*0.73f,  cy-amp*0.28f, o+sw*0.80f, cy);
            path.lineTo(o+sw,        cy);
        }
        canvas.drawPath(path, linePaint);
        linePaint.setShader(null);
    }

    // ── 5: Circular radial ────────────────────────────────────────

    private void drawCircular(Canvas canvas, int w, int h) {
        float cx = w/2f, cy = h/2f;
        float minR = Math.min(w,h)*0.22f, maxR = Math.min(w,h)*0.48f;
        int nBars  = HALF_BARS*2;
        float dAng = 360f/nBars;
        barPaint.setStyle(Paint.Style.STROKE);
        barPaint.setStrokeCap(Paint.Cap.ROUND);
        barPaint.setStrokeWidth(Math.max(2f, dAng*(float)Math.PI*minR/180f - 1.5f));
        barPaint.setShader(new SweepGradient(cx, cy, accentColor, blend(accentColor,bgColor,0.5f)));
        for (int i = 0; i < nBars; i++) {
            int   mi  = i < HALF_BARS ? i : nBars-1-i; mi = Math.min(mi, HALF_BARS-1);
            float r   = minR + (maxR-minR)*magnitudes[mi];
            float rad = (float)Math.toRadians(dAng*i - 90f);
            float cos = (float)Math.cos(rad), sin = (float)Math.sin(rad);
            canvas.drawLine(cx+minR*cos, cy+minR*sin, cx+r*cos, cy+r*sin, barPaint);
        }
        barPaint.setStyle(Paint.Style.FILL);
        barPaint.setShader(null);
    }

    // ── Helper ────────────────────────────────────────────────────

    private static int blend(int c1, int c2, float r) {
        float v = 1f-r;
        return (((int)(((c1>>24)&0xFF)*v+((c2>>24)&0xFF)*r))<<24)
             | (((int)(((c1>>16)&0xFF)*v+((c2>>16)&0xFF)*r))<<16)
             | (((int)(((c1>> 8)&0xFF)*v+((c2>> 8)&0xFF)*r))<< 8)
             | (((int)(( c1     &0xFF)*v+( c2     &0xFF)*r)));
    }

    @Override protected void onDetachedFromWindow() { super.onDetachedFromWindow(); release(); }
}
