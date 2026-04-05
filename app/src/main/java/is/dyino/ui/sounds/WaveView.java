package is.dyino.ui.sounds;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

public class WaveView extends View {

    private final Paint wavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path  path      = new Path();

    private float volumeFraction = 0.5f;
    private float waveOffset     = 0f;
    private float cornerRadius   = 0f;
    private int   waveColor      = 0x556C63FF;
    private int   bgColor        = 0xFF1A1A26;

    private ValueAnimator animator;
    // Track whether wave should be running — so onLayout can restart it
    private boolean wantWave = false;

    public WaveView(Context ctx)                           { super(ctx); init(); }
    public WaveView(Context ctx, AttributeSet a)          { super(ctx, a); init(); }
    public WaveView(Context ctx, AttributeSet a, int def) { super(ctx, a, def); init(); }

    private void init() {
        wavePaint.setStyle(Paint.Style.FILL);
        bgPaint.setStyle(Paint.Style.FILL);
        cornerRadius = 14 * getResources().getDisplayMetrics().density;
    }

    public void setColors(int bg, int wave) {
        bgColor   = bg;
        waveColor = wave;
        bgPaint.setColor(bgColor);
        wavePaint.setColor(waveColor);
        invalidate();
    }

    public void setVolume(float v) {
        volumeFraction = Math.max(0f, Math.min(1f, v));
        invalidate();
    }

    /**
     * Start wave. If view has no size yet (called before layout), the animator
     * is still started — it will draw correctly once onLayout fires.
     */
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
            // Force draw even if view just became measurable
            postInvalidate();
        });
        animator.start();
    }

    public void stopWave() {
        wantWave = false;
        if (animator != null) { animator.cancel(); animator = null; }
        waveOffset = 0f;
    }

    public boolean isWaving() { return wantWave && animator != null && animator.isRunning(); }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        // If wave was requested before layout completed, restart now that we have dimensions
        if (wantWave && (animator == null || !animator.isRunning())) {
            doStartAnimator();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        bgPaint.setColor(bgColor);
        canvas.drawRoundRect(0, 0, w, h, cornerRadius, cornerRadius, bgPaint);

        // fillTop: 0 = full fill (vol=1), h = no fill (vol=0)
        float fillTop = h * (1f - volumeFraction);

        wavePaint.setColor(waveColor);
        path.reset();

        int steps = 60;
        for (int i = 0; i <= steps; i++) {
            float x    = w * i / (float) steps;
            float wave = (float)(
                Math.sin(2 * Math.PI * i / steps + waveOffset) * h * 0.04 +
                Math.sin(4 * Math.PI * i / steps + waveOffset * 1.3) * h * 0.02
            );
            float y = fillTop + wave;
            if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }
        path.lineTo(w, h);
        path.lineTo(0, h);
        path.close();

        canvas.save();
        android.graphics.RectF rect = new android.graphics.RectF(0, 0, w, h);
        android.graphics.Path clip = new android.graphics.Path();
        clip.addRoundRect(rect, cornerRadius, cornerRadius, android.graphics.Path.Direction.CW);
        canvas.clipPath(clip);
        canvas.drawPath(path, wavePaint);
        canvas.restore();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopWave();
    }
}