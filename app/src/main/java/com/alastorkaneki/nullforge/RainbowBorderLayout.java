package com.alastorkaneki.nullforge;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;

public final class RainbowBorderLayout extends FrameLayout {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private final Matrix matrix = new Matrix();
    private final int[] colors = {
            Color.rgb(255, 32, 78),
            Color.rgb(255, 80, 214),
            Color.rgb(136, 76, 255),
            Color.rgb(42, 134, 255),
            Color.rgb(255, 32, 78)
    };
    private float phase;
    private final float radius;
    private final float stroke;
    private SweepGradient gradient;
    private ValueAnimator animator;

    public RainbowBorderLayout(Context context) {
        this(context, null);
    }

    public RainbowBorderLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
        radius = Ui.dp(context, 18);
        stroke = Ui.dp(context, 2);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(stroke);
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (width > 0 && height > 0) {
            gradient = new SweepGradient(width / 2f, height / 2f, colors, null);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        post(this::startAnimator);
    }

    @Override
    protected void onDetachedFromWindow() {
        stopAnimator();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == View.VISIBLE && isAttachedToWindow()) {
            startAnimator();
        } else {
            stopAnimator();
        }
    }

    private void startAnimator() {
        if (!isAttachedToWindow() || animator != null) {
            return;
        }
        animator = ValueAnimator.ofFloat(0f, 360f);
        animator.setDuration(3200L);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(value -> {
            phase = (float) value.getAnimatedValue();
            postInvalidateOnAnimation();
        });
        animator.start();
    }

    private void stopAnimator() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (gradient == null || getWidth() <= 0 || getHeight() <= 0) {
            return;
        }
        float inset = stroke;
        bounds.set(inset, inset, getWidth() - inset, getHeight() - inset);
        matrix.setRotate(phase, getWidth() / 2f, getHeight() / 2f);
        gradient.setLocalMatrix(matrix);
        paint.setShader(gradient);
        canvas.drawRoundRect(bounds, radius, radius, paint);
        paint.setShader(null);
    }
}
