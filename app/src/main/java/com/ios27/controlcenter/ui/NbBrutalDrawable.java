package com.ios27.controlcenter.ui;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Crisp Neo-Brutalist surface drawable with a solid #111111 offset drop shadow
 * and heavy ink border. Reserves shadow space inside view padding so parents never clip it.
 */
public final class NbBrutalDrawable extends Drawable {

    private static final int COLOR_INK = 0xFF111111;

    private final Paint mShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();

    private final float mDensity;
    private final float mCornerRadiusPx;
    private final boolean mFullPill;
    private final boolean mInteractive;
    private float mBorderWidthPx;
    private int mShadowOffsetPx;
    private final int mReservedShadowPx;
    private final int mPadL;
    private final int mPadT;
    private final int mPadR;
    private final int mPadB;
    private boolean mPressed;

    private NbBrutalDrawable(
            float density,
            int fillColor,
            float cornerDp,
            boolean fullPill,
            float borderDp,
            float shadowDp,
            float reserveShadowDp,
            boolean interactive,
            int padL,
            int padT,
            int padR,
            int padB
    ) {
        mDensity = density;
        mCornerRadiusPx = cornerDp * density;
        mFullPill = fullPill;
        mBorderWidthPx = borderDp * density;
        mShadowOffsetPx = Math.round(shadowDp * density);
        mReservedShadowPx = Math.round(Math.max(shadowDp, reserveShadowDp) * density);
        mInteractive = interactive;
        mPadL = padL;
        mPadT = padT;
        mPadR = padR;
        mPadB = padB;

        mShadowPaint.setStyle(Paint.Style.FILL);
        mShadowPaint.setColor(COLOR_INK);

        mFillPaint.setStyle(Paint.Style.FILL);
        mFillPaint.setColor(fillColor);

        mStrokePaint.setStyle(Paint.Style.STROKE);
        mStrokePaint.setColor(COLOR_INK);
        mStrokePaint.setStrokeWidth(mBorderWidthPx);
    }

    public static NbBrutalDrawable apply(
            View view,
            int fillColor,
            float cornerDp,
            float borderDp,
            float shadowDp,
            boolean interactive
    ) {
        return applyInternal(view, fillColor, cornerDp, false, borderDp, shadowDp, shadowDp, interactive);
    }

    public static NbBrutalDrawable apply(
            View view,
            int fillColor,
            float cornerDp,
            float borderDp,
            float shadowDp,
            float reserveShadowDp,
            boolean interactive
    ) {
        return applyInternal(view, fillColor, cornerDp, false, borderDp, shadowDp, reserveShadowDp, interactive);
    }

    public static NbBrutalDrawable applyPill(
            View view,
            int fillColor,
            float borderDp,
            float shadowDp,
            boolean interactive
    ) {
        return applyInternal(view, fillColor, 0f, true, borderDp, shadowDp, shadowDp, interactive);
    }

    private static NbBrutalDrawable applyInternal(
            View view,
            int fillColor,
            float cornerDp,
            boolean fullPill,
            float borderDp,
            float shadowDp,
            float reserveShadowDp,
            boolean interactive
    ) {
        int pl = view.getPaddingLeft();
        int pt = view.getPaddingTop();
        int pr = view.getPaddingRight();
        int pb = view.getPaddingBottom();
        float d = view.getResources().getDisplayMetrics().density;
        NbBrutalDrawable drawable = new NbBrutalDrawable(
                d, fillColor, cornerDp, fullPill, borderDp, shadowDp, reserveShadowDp, interactive, pl, pt, pr, pb);
        view.setBackground(drawable);
        return drawable;
    }

    public void setFillColor(int color) {
        if (mFillPaint.getColor() != color) {
            mFillPaint.setColor(color);
            invalidateSelf();
        }
    }

    public void setShadowDp(float shadowDp) {
        int px = Math.round(shadowDp * mDensity);
        if (mShadowOffsetPx != px) {
            mShadowOffsetPx = px;
            invalidateSelf();
        }
    }

    @Override
    public boolean isStateful() {
        return mInteractive;
    }

    @Override
    protected boolean onStateChange(@NonNull int[] stateSet) {
        if (!mInteractive) {
            return false;
        }
        boolean pressed = false;
        for (int s : stateSet) {
            if (s == android.R.attr.state_pressed) {
                pressed = true;
                break;
            }
        }
        if (pressed != mPressed) {
            mPressed = pressed;
            invalidateSelf();
            return true;
        }
        return false;
    }

    @Override
    public boolean getPadding(@NonNull Rect padding) {
        padding.set(mPadL, mPadT, mPadR + mReservedShadowPx, mPadB + mReservedShadowPx);
        return true;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect b = getBounds();
        if (b.isEmpty()) {
            return;
        }

        float bodyW = Math.max(0f, b.width() - mReservedShadowPx);
        float bodyH = Math.max(0f, b.height() - mReservedShadowPx);
        float radius = mFullPill ? bodyH * 0.5f : mCornerRadiusPx;

        if (mShadowOffsetPx > 0) {
            mRect.set(
                    b.left + mShadowOffsetPx,
                    b.top + mShadowOffsetPx,
                    b.left + mShadowOffsetPx + bodyW,
                    b.top + mShadowOffsetPx + bodyH
            );
            canvas.drawRoundRect(mRect, radius, radius, mShadowPaint);
        }

        float shift = (mPressed && mShadowOffsetPx > 0) ? mShadowOffsetPx * 0.5f : 0f;
        float halfStroke = mBorderWidthPx * 0.5f;

        mRect.set(
                b.left + shift + halfStroke,
                b.top + shift + halfStroke,
                b.left + shift + bodyW - halfStroke,
                b.top + shift + bodyH - halfStroke
        );
        canvas.drawRoundRect(mRect, radius, radius, mFillPaint);
        if (mBorderWidthPx > 0f) {
            canvas.drawRoundRect(mRect, radius, radius, mStrokePaint);
        }
    }

    @Override
    public void setAlpha(int alpha) {
        mFillPaint.setAlpha(alpha);
        mStrokePaint.setAlpha(alpha);
        mShadowPaint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        mFillPaint.setColorFilter(colorFilter);
        mStrokePaint.setColorFilter(colorFilter);
        mShadowPaint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
