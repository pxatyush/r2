package is.dyino.ui.sounds;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * Animated sine-wave fill view.
 *
 * Volume drag: long-press → beginVolumeDrag(rawY), then pass ACTION_MOVE rawY
 * coordinates to handleDragMove().  The view calls requestDisallowInterceptTouchEvent
 * on its parent so the scroll container does not steal the touch.
 *
 * Sensitivity: each pixel of vertical movement = SENSITIVITY fraction of total
 * volume range.  With SENSITIVITY = 0.004 a 250-px drag ≈ 100% change, smooth
 * but not jittery.
 */
public class WaveView extends View {

    // ── Sensitivity: fraction of vol per pixel dragged ────────────
    private static final float SENSITIVITY = 0.004f;

    // ── Paint / drawing ───────────────────────────────────────────
    private final Paint wavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path  path      = new Path();
    private final RectF clipRect  = new RectF();

    private float volumeFraction = 0.5f;
    private float waveOffset     = 0f;
    private float cornerRadius;
    private int   waveColor      = 0x556C63FF;
    private int   bgColor        = 0xFF1A1A26;

    // ── Animation ─────────────────────────────────────────────────
    private ValueAnimator animator;
    private boolean       wantWave = false;

    // ── Drag state ────────────────────────────────────────────────
    public interface VolumeDragListener { void onVolumeChanged(float vol); }
    private VolumeDragListener dragListener;
    private boolean isDragging  = false;
    private float   dragStartY  = 0f;
    private float   dragStartVol = 0f;

    public WaveView(Context ctx)                           { super(ctx); init(); }
    public WaveView(Context ctx, AttributeSet a)          { super(ctx, a); init(); }
    public WaveView(Context ctx, AttributeSet a, int def) { super(ctx, a, def); init(); }

    private void init() {
        wavePaint.setStyle(Paint.Style.FILL);
        bgPaint.setStyle(Paint.Style.FILL);
        cornerRadius = 14f * getResources().getDisplayMetrics().density;
        bgPaint.setColor(bgColor);
        wavePaint.setColor(waveColor);
    }

    // ── Public API ────────────────────────────────────────────────
    public void setColors(int bg, int wave) {
        bgColor   = bg;   bgPaint.setColor(bg);
        waveColor = wave; wavePaint.setColor(wave);
        invalidate();
    }

    public void setVolume(float v) {
        volumeFraction = clamp(v);
        invalidate();
    }

    public float getVolume() { return volumeFraction; }

    public void setVolumeDragListener(VolumeDragListener l) { dragListener = l; }

    public void startWave() {
        wantWave = true;
        if (animator != null && animator.isRunning()) return;
        doStartAnimator();
    }

    public void stopWave() {
        wantWave = false;
        if (animator != null) { animator.cancel(); animator = null; }
        waveOffset = 0f;
        invalidate();
    }

    public boolean isWaving() { return wantWave && animator != null && animator.isRunning(); }

    // ── Drag volume ───────────────────────────────────────────────

    /** Call from the view's onLongClickListener with raw Y position. */
    public void beginVolumeDrag(float rawY) {
        isDragging   = true;
        dragStartY   = rawY;
        dragStartVol = volumeFraction;
        // Tell parent scroll not to steal our touch events
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
    }

    /**
     * Call on ACTION_MOVE with the current raw Y.
     * Returns the new volume so caller can forward to AudioService.
     */
    public float handleDragMove(float rawY) {
        if (!isDragging) return volumeFraction;
        float delta  = (dragStartY - rawY) * SENSITIVITY; // UP → positive
        float newVol = clamp(dragStartVol + delta);
        volumeFraction = newVol;
        wavePaint.setColor(waveColor);
        invalidate();
        if (dragListener != null) dragListener.onVolumeChanged(newVol);
        return newVol;
    }

    public void endDrag() {
        isDragging = false;
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
        invalidate();
    }

    public boolean isDragging() { return isDragging; }

    // ── Layout ────────────────────────────────────────────────────
    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        clipRect.set(0, 0, r - l, b - t);
        if (wantWave && (animator == null || !animator.isRunning())) doStartAnimator();
    }

    // ── Animator ─────────────────────────────────────────────────
    private void doStartAnimator() {
        if (animator != null) animator.cancel();
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

    // ── Draw ─────────────────────────────────────────────────────
    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;

        // Background rounded rect
        canvas.drawRoundRect(0, 0, w, h, cornerRadius, cornerRadius, bgPaint);

        // Wave fill
        float fillTop = h * (1f - volumeFraction);
        wavePaint.setColor(waveColor);
        path.reset();

        // Use enough steps for smooth curve without excess computation
        final int steps = 48;
        final float ampH = h * 0.04f;
        final float ampL = h * 0.015f;

        for (int i = 0; i <= steps; i++) {
            float t = (float) i / steps;
            float x = w * t;
            // Two overlapping sines for organic look; phase advances over time
            float y = fillTop
                + (float)(Math.sin(t * 2 * Math.PI * 2 + waveOffset) * ampH)
                + (float)(Math.sin(t * 2 * Math.PI * 3 + waveOffset * 1.5) * ampL);
            if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }
        path.lineTo(w, h);
        path.lineTo(0, h);
        path.close();

        // Clip to rounded corners
        canvas.save();
        android.graphics.Path clip = new android.graphics.Path();
        clip.addRoundRect(clipRect, cornerRadius, cornerRadius, android.graphics.Path.Direction.CW);
        canvas.clipPath(clip);
        canvas.drawPath(path, wavePaint);
        canvas.restore();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (animator != null) { animator.cancel(); animator = null; }
        wantWave = false;
    }

    private static float clamp(float v) { return Math.max(0f, Math.min(1f, v)); }
}