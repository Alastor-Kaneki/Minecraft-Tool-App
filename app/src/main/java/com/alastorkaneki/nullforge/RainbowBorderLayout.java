package com.alastorkaneki.nullforge;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.util.AttributeSet;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;

public final class RainbowBorderLayout extends FrameLayout {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private float phase;
    private float radius;
    private float stroke;
    private ValueAnimator animator;

    public RainbowBorderLayout(Context context) {
        this(context, null);
    }

    public RainbowBorderLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        radius = Ui.dp(context, 18);
        stroke = Ui.dp(context, 2);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(stroke);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        animator = ValueAnimator.ofFloat(0f, 360f);
        animator.setDuration(2800);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(value -> {
            phase = (float) value.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float inset = stroke;
        bounds.set(inset, inset, getWidth() - inset, getHeight() - inset);
        int[] colors = {
                Color.rgb(255, 32, 78),
                Color.rgb(255, 80, 214),
                Color.rgb(136, 76, 255),
                Color.rgb(42, 134, 255),
                Color.rgb(255, 32, 78)
        };
        SweepGradient gradient = new SweepGradient(getWidth() / 2f, getHeight() / 2f, colors, null);
        android.graphics.Matrix matrix = new android.graphics.Matrix();
        matrix.setRotate(phase, getWidth() / 2f, getHeight() / 2f);
        gradient.setLocalMatrix(matrix);
        paint.setShader(gradient);
        canvas.drawRoundRect(bounds, radius, radius, paint);
        paint.setShader(null);
    }
}
