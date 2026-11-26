package com.ios27.controlcenter.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.Checkable;

import androidx.annotation.Nullable;

/**
 * Neo-Brutalist pill switch (50dp x 28dp + 2dp hard #111111 shadow) matching Figma NB/Toggle Switch.
 */
public class NbToggleView extends View implements Checkable {

    public interface OnCheckedChangeListener {
        void onCheckedChanged(NbToggleView view, boolean isChecked);
    }

    private static final int COLOR_INK = 0xFF111111;
    private static final int COLOR_ON = 0xFF22D15E;
    private static final int COLOR_OFF = 0xFFF1ECE1;
    private static final int COLOR_WHITE = 0xFFFFFFFF;

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();
    private final float mDensity;

    private boolean mChecked = true;
    private float mProgress = 1f;
    private ValueAnimator mAnimator;
    private OnCheckedChangeListener mListener;

    public NbToggleView(Context context) {
        this(context, null);
    }

    public NbToggleView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public NbToggleView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mDensity = context.getResources().getDisplayMetrics().density;
        setClickable(true);
        setFocusable(true);
    }

    public void setOnCheckedChangeListener(@Nullable OnCheckedChangeListener listener) {
        mListener = listener;
    }

    @Override
    public boolean isChecked() {
        return mChecked;
    }

    @Override
    public void setChecked(boolean checked) {
        setCheckedInternal(checked, false, false);
    }

    public void setCheckedAnimated(boolean checked, boolean notify) {
        setCheckedInternal(checked, true, notify);
    }

    private void setCheckedInternal(boolean checked, boolean animate, boolean notify) {
        boolean changed = (mChecked != checked);
        mChecked = checked;
        float target = checked ? 1f : 0f;

        if (mAnimator != null) {
            mAnimator.cancel();
            mAnimator = null;
        }
        if (animate) {
            mAnimator = ValueAnimator.ofFloat(mProgress, target);
            mAnimator.setDuration(140L);
            mAnimator.setInterpolator(new DecelerateInterpolator());
            mAnimator.addUpdateListener(a -> {
                mProgress = (float) a.getAnimatedValue();
                invalidate();
            });
            mAnimator.start();
        } else {
            mProgress = target;
            invalidate();
        }

        if (changed && notify && mListener != null) {
            mListener.onCheckedChanged(this, mChecked);
        }
    }

    @Override
    public void toggle() {
        setCheckedInternal(!mChecked, true, true);
    }

    @Override
    public boolean performClick() {
        toggle();
        return super.performClick();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = Math.round(52f * mDensity);
        int h = Math.round(30f * mDensity);
        setMeasuredDimension(resolveSize(w, widthMeasureSpec), resolveSize(h, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float d = mDensity;
        float shadow = 2f * d;
        float trackW = Math.min(50f * d, getWidth() - shadow);
        float trackH = Math.min(28f * d, getHeight() - shadow);
        float radius = trackH * 0.5f;
        float border = 2f * d;
        float halfBorder = border * 0.5f;

        // Hard offset shadow
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(COLOR_INK);
        mRect.set(shadow, shadow, shadow + trackW, shadow + trackH);
        canvas.drawRoundRect(mRect, radius, radius, mPaint);

        // Track fill (blended for crisp toggle feel)
        mPaint.setColor(blendColors(COLOR_OFF, COLOR_ON, mProgress));
        mRect.set(halfBorder, halfBorder, trackW - halfBorder, trackH - halfBorder);
        canvas.drawRoundRect(mRect, radius, radius, mPaint);

        // Track border
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(border);
        mPaint.setColor(COLOR_INK);
        canvas.drawRoundRect(mRect, radius, radius, mPaint);

        // Thumb circle (18dp diameter, cx 15dp..35dp)
        float startX = 15f * d;
        float endX = trackW - 15f * d;
        float cx = startX + (endX - startX) * mProgress;
        float cy = trackH * 0.5f;
        float r = 9f * d;

        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(COLOR_WHITE);
        canvas.drawCircle(cx, cy, r, mPaint);

        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(border);
        mPaint.setColor(COLOR_INK);
        canvas.drawCircle(cx, cy, r, mPaint);
    }

    private static int blendColors(int from, int to, float f) {
        float t = Math.max(0f, Math.min(1f, f));
        int a = Math.round(((from >>> 24) & 0xFF) * (1f - t) + ((to >>> 24) & 0xFF) * t);
        int r = Math.round(((from >>> 16) & 0xFF) * (1f - t) + ((to >>> 16) & 0xFF) * t);
        int g = Math.round(((from >>> 8) & 0xFF) * (1f - t) + ((to >>> 8) & 0xFF) * t);
        int b = Math.round((from & 0xFF) * (1f - t) + (to & 0xFF) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
