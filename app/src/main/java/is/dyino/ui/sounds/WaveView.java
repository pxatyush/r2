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
 * Draws a fluid wave fill that rises to `volumeFraction` height.
 * Color is set externally. Animates continuously while visible.
 */
public class WaveView extends View {

    private final Paint wavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path  path      = new Path();

    private float volumeFraction = 0.5f;  // 0..1
    private float waveOffset     = 0f;    // animated
    private float cornerRadius   = 0f;
    private int   waveColor      = 0x556C63FF; // 33% opacity accent
    private int   bgColor        = 0xFF1A1A26;

    private ValueAnimator animator;

    public WaveView(Context ctx) { super(ctx); init(); }
    public WaveView(Context ctx, AttributeSet a) { super(ctx, a); init(); }
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

    public void startWave() {
        stopWave();
        animator = ValueAnimator.ofFloat(0f, (float)(2 * Math.PI));
        animator.setDuration(2200);
        animator.setInterpolator(new LinearInterpolator());
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.RESTART);
        animator.addUpdateListener(a -> {
            waveOffset = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    public void stopWave() {
        if (animator != null) { animator.cancel(); animator = null; }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        // Background rounded rect
        bgPaint.setColor(bgColor);
        canvas.drawRoundRect(0, 0, w, h, cornerRadius, cornerRadius, bgPaint);

        // Wave fill — only bottom half fills (volumeFraction controls height within bottom 50%)
        float maxFillY = h;          // fill can go from bottom up to 50% of height
        float minFillY = h * 0.5f;  // wave top never exceeds 50% height line
        float fillTop  = maxFillY - (maxFillY - minFillY) * volumeFraction;

        wavePaint.setColor(waveColor);
        path.reset();
        path.moveTo(0, fillTop);

        // Two sine waves offset for fluid look
        int steps = 60;
        for (int i = 0; i <= steps; i++) {
            float x    = w * i / (float) steps;
            float wave = (float)(
                Math.sin(2 * Math.PI * i / steps + waveOffset) * h * 0.035 +
                Math.sin(4 * Math.PI * i / steps + waveOffset * 1.3) * h * 0.018
            );
            float y = fillTop + wave;
            if (i == 0) path.moveTo(x, y);
            else        path.lineTo(x, y);
        }
        path.lineTo(w, h);
        path.lineTo(0, h);
        path.close();

        // Clip to rounded rect bounds
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
