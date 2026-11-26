package com.ios27.controlcenter.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;

import androidx.annotation.Nullable;

/**
 * Neo-Brutalist slider control:
 * 12dp pill track (#F1ECE1 bg + colored fill) with 2dp #111111 border
 * and 20dp rounded-square knob with 2dp hard offset shadow.
 */
public class NbSeekBar extends View {

    public interface OnValueChangeListener {
        void onValueChanged(NbSeekBar view, int value, boolean fromUser);
    }

    private static final int COLOR_INK = 0xFF111111;
    private static final int COLOR_TRACK_BG = 0xFFF1ECE1;
    private static final int COLOR_WHITE = 0xFFFFFFFF;

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();
    private final float mDensity;

    private int mMin = 0;
    private int mMax = 100;
    private int mValue = 50;
    private int mFillColor = 0xFF22D15E;
    private OnValueChangeListener mListener;

    public NbSeekBar(Context context) {
        this(context, null);
    }

    public NbSeekBar(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public NbSeekBar(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mDensity = context.getResources().getDisplayMetrics().density;
        setClickable(true);
        setFocusable(true);
    }

    public void setRange(int min, int max) {
        mMin = min;
        mMax = Math.max(min + 1, max);
        setValue(mValue, false);
    }

    public void setFillColor(int color) {
        mFillColor = color;
        invalidate();
    }

    public void setValue(int value, boolean fromUser) {
        int clamped = Math.max(mMin, Math.min(mMax, value));
        if (clamped != mValue) {
            mValue = clamped;
            invalidate();
            if (mListener != null) {
                mListener.onValueChanged(this, mValue, fromUser);
            }
        } else {
            invalidate();
        }
    }

    public int getValue() {
        return mValue;
    }

    public void setOnValueChangeListener(@Nullable OnValueChangeListener listener) {
        mListener = listener;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int h = Math.round(30f * mDensity);
        int w = Math.round(240f * mDensity);
        setMeasuredDimension(resolveSize(w, widthMeasureSpec), resolveSize(h, heightMeasureSpec));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                ViewParent parent = getParent();
                if (parent != null) {
                    parent.requestDisallowInterceptTouchEvent(true);
                }
                updateFromTouch(event.getX());
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                updateFromTouch(event.getX());
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                updateFromTouch(event.getX());
                ViewParent parent = getParent();
                if (parent != null) {
                    parent.requestDisallowInterceptTouchEvent(false);
                }
                return true;
            }
            default:
                return super.onTouchEvent(event);
        }
    }

    private void updateFromTouch(float x) {
        float d = mDensity;
        float knobHalf = 10f * d;
        float shadow = 2f * d;
        float minCx = knobHalf;
        float maxCx = Math.max(minCx + 1f, getWidth() - knobHalf - shadow);
        float frac = Math.max(0f, Math.min(1f, (x - minCx) / (maxCx - minCx)));
        int newVal = mMin + Math.round(frac * (mMax - mMin));
        setValue(newVal, true);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float d = mDensity;
        float shadow = 2f * d;
        float border = 2f * d;
        float halfBorder = border * 0.5f;

        float knobSize = 20f * d;
        float knobHalf = knobSize * 0.5f;
        float knobRadius = 6f * d;

        float trackH = 12f * d;
        float trackRadius = trackH * 0.5f;
        float cy = getHeight() * 0.5f;
        float trackTop = cy - trackH * 0.5f;
        float trackBottom = cy + trackH * 0.5f;
        float trackRight = Math.max(knobSize, getWidth() - shadow);

        // Background track pill
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(COLOR_TRACK_BG);
        mRect.set(halfBorder, trackTop + halfBorder, trackRight - halfBorder, trackBottom - halfBorder);
        canvas.drawRoundRect(mRect, trackRadius, trackRadius, mPaint);

        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(border);
        mPaint.setColor(COLOR_INK);
        canvas.drawRoundRect(mRect, trackRadius, trackRadius, mPaint);

        // Active fill fraction
        float frac = (mValue - mMin) / (float) Math.max(1, mMax - mMin);
        float minCx = knobHalf;
        float maxCx = Math.max(minCx + 1f, trackRight - knobHalf);
        float knobCx = minCx + frac * (maxCx - minCx);

        float fillRight = Math.min(trackRight - halfBorder, knobCx + knobHalf * 0.45f);
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(mFillColor);
        mRect.set(halfBorder, trackTop + halfBorder, Math.max(halfBorder + trackH, fillRight), trackBottom - halfBorder);
        canvas.drawRoundRect(mRect, trackRadius, trackRadius, mPaint);

        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(border);
        mPaint.setColor(COLOR_INK);
        canvas.drawRoundRect(mRect, trackRadius, trackRadius, mPaint);

        // Knob hard drop shadow
        float knobLeft = knobCx - knobHalf;
        float knobTop = cy - knobHalf;
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(COLOR_INK);
        mRect.set(knobLeft + shadow, knobTop + shadow, knobLeft + knobSize + shadow, knobTop + knobSize + shadow);
        canvas.drawRoundRect(mRect, knobRadius, knobRadius, mPaint);

        // Knob body + border
        mPaint.setColor(COLOR_WHITE);
        mRect.set(knobLeft + halfBorder, knobTop + halfBorder, knobLeft + knobSize - halfBorder, knobTop + knobSize - halfBorder);
        canvas.drawRoundRect(mRect, knobRadius, knobRadius, mPaint);

        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(border);
        mPaint.setColor(COLOR_INK);
        canvas.drawRoundRect(mRect, knobRadius, knobRadius, mPaint);
    }
}
