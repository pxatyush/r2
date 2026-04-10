package is.dyino.ui.sounds;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * WaveView — animated wave fill that also acts as a vertical drag volume control.
 *
 * Usage (from SoundsFragment):
 *   wave.setVolumeDragListener(vol -> audioService.setSoundVolume(fn, vol));
 *   wave.setVolume(currentVol);
 *   wave.startWave();
 *
 * Long-press → enters volume-drag mode.
 * Drag UP   → increase volume (wave rises).
 * Drag DOWN → decrease volume (wave drops).
 * Release   → exits drag mode after 2 s idle.
 */
public class WaveView extends View {

    // ── Paint / drawing ───────────────────────────────────────────
    private final Paint wavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path  path      = new Path();

    private float volumeFraction = 0.5f;
    private float waveOffset     = 0f;
    private float cornerRadius   = 0f;
    private int   waveColor      = 0x556C63FF;
    private int   bgColor        = 0xFF1A1A26;

    // ── Animation ─────────────────────────────────────────────────
    private ValueAnimator animator;
    private boolean       wantWave = false;

    // ── Volume-drag state ─────────────────────────────────────────
    public interface VolumeDragListener {
        void onVolumeChanged(float vol);  // called in real-time while dragging
    }
    private VolumeDragListener dragListener;
    private boolean isDragging = false;
    private float   dragStartY = 0f;
    private float   dragStartVol = 0f;

    // auto-dismiss timeout
    private final android.os.Handler dismissHandler = new android.os.Handler(
            android.os.Looper.getMainLooper());
    private final Runnable dismissRunnable = () -> endDrag(true);

    public WaveView(Context ctx)                           { super(ctx); init(); }
    public WaveView(Context ctx, AttributeSet a)          { super(ctx, a); init(); }
    public WaveView(Context ctx, AttributeSet a, int def) { super(ctx, a, def); init(); }

    private void init() {
        wavePaint.setStyle(Paint.Style.FILL);
        bgPaint.setStyle(Paint.Style.FILL);
        cornerRadius = 14 * getResources().getDisplayMetrics().density;
    }

    // ── Public API ────────────────────────────────────────────────
    public void setColors(int bg, int wave) {
        bgColor = bg; waveColor = wave;
        bgPaint.setColor(bgColor); wavePaint.setColor(waveColor);
        invalidate();
    }

    public void setVolume(float v) {
        volumeFraction = Math.max(0f, Math.min(1f, v));
        invalidate();
    }

    public float getVolume() { return volumeFraction; }

    public void setVolumeDragListener(VolumeDragListener l) { this.dragListener = l; }

    // ── Wave animation ────────────────────────────────────────────
    public void startWave() {
        wantWave = true;
        if (animator != null && animator.isRunning()) return;
        doStartAnimator();
    }

    private void doStartAnimator() {
        if (animator != null) { animator.cancel(); animator = null; }
        animator = ValueAnimator.ofFloat(0f, (float)(2 * Math.PI));
        animator.setDuration(2200);
        animator.setInterpolator(new LinearInterpolator());
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.RESTART);
        animator.addUpdateListener(a -> {
            waveOffset = (float) a.getAnimatedValue();
            postInvalidate();
        });
        animator.start();
    }

    public void stopWave() {
        wantWave = false;
        if (animator != null) { animator.cancel(); animator = null; }
        waveOffset = 0f;
        invalidate();
    }

    public boolean isWaving() { return wantWave && animator != null && animator.isRunning(); }

    // ── Vertical drag volume control ──────────────────────────────

    /**
     * Called by SoundsFragment when a long-press is detected.
     * Puts the view into drag mode so touch events control volume.
     */
    public void beginVolumeDrag(float initialTouchY) {
        isDragging    = true;
        dragStartY    = initialTouchY;
        dragStartVol  = volumeFraction;
        scheduleDismiss();
        invalidate();
    }

    /**
     * Call from the parent view's onTouchListener while dragging.
     * Returns the new volume so the caller can forward to AudioService.
     */
    public float handleDragMove(float currentY) {
        if (!isDragging) return volumeFraction;
        float h       = getHeight();
        if (h == 0) return volumeFraction;
        float delta   = (dragStartY - currentY) / h; // drag UP → positive
        float newVol  = Math.max(0f, Math.min(1f, dragStartVol + delta));
        volumeFraction = newVol;
        invalidate();
        scheduleDismiss();
        if (dragListener != null) dragListener.onVolumeChanged(newVol);
        return newVol;
    }

    public void endDrag(boolean animate) {
        isDragging = false;
        dismissHandler.removeCallbacks(dismissRunnable);
        invalidate();
    }

    public boolean isDragging() { return isDragging; }

    private void scheduleDismiss() {
        dismissHandler.removeCallbacks(dismissRunnable);
        dismissHandler.postDelayed(dismissRunnable, 2500);
    }

    // ── Layout ────────────────────────────────────────────────────
    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (wantWave && (animator == null || !animator.isRunning())) doStartAnimator();
    }

    // ── Drawing ───────────────────────────────────────────────────
    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;

        bgPaint.setColor(bgColor);
        canvas.drawRoundRect(0, 0, w, h, cornerRadius, cornerRadius, bgPaint);

        // When dragging, draw a brighter volume indicator at top
        if (isDragging) {
            Paint indicatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            indicatorPaint.setColor(0xCCFFFFFF);
            indicatorPaint.setTextSize(11 * getResources().getDisplayMetrics().density);
            indicatorPaint.setTextAlign(Paint.Align.CENTER);
            // volume % label
            String label = Math.round(volumeFraction * 100) + "%";
            canvas.drawText(label, w / 2f, h * 0.22f, indicatorPaint);
        }

        float fillTop = h * (1f - volumeFraction);
        wavePaint.setColor(isDragging ? blend(waveColor, 0x80FFFFFF, 0.4f) : waveColor);
        path.reset();
        int steps = 60;
        for (int i = 0; i <= steps; i++) {
            float x    = w * i / (float) steps;
            float wave = (float)(
                Math.sin(2 * Math.PI * i / steps + waveOffset) * h * 0.04 +
                Math.sin(4 * Math.PI * i / steps + waveOffset * 1.3) * h * 0.02);
            float y = fillTop + wave;
            if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }
        path.lineTo(w, h); path.lineTo(0, h); path.close();

        canvas.save();
        android.graphics.RectF rect = new android.graphics.RectF(0, 0, w, h);
        android.graphics.Path clip = new android.graphics.Path();
        clip.addRoundRect(rect, cornerRadius, cornerRadius, android.graphics.Path.Direction.CW);
        canvas.clipPath(clip);
        canvas.drawPath(path, wavePaint);
        canvas.restore();
    }

    /** Simple colour blend: ratio 0 = c1, 1 = c2 */
    private static int blend(int c1, int c2, float ratio) {
        int a = (int)(((c1 >> 24) & 0xFF) * (1 - ratio) + ((c2 >> 24) & 0xFF) * ratio);
        int r = (int)(((c1 >> 16) & 0xFF) * (1 - ratio) + ((c2 >> 16) & 0xFF) * ratio);
        int g = (int)(((c1 >>  8) & 0xFF) * (1 - ratio) + ((c2 >>  8) & 0xFF) * ratio);
        int b = (int)(((c1      ) & 0xFF) * (1 - ratio) + ((c2      ) & 0xFF) * ratio);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopWave();
        dismissHandler.removeCallbacksAndMessages(null);
    }
}
