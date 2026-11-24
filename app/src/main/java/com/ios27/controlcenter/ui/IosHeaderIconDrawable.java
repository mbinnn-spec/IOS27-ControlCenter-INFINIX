package com.ios27.controlcenter.ui;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/**
 * Procedural crisp vector drawable for the top iOS Control Center '+' and Power icons.
 */
public class IosHeaderIconDrawable extends Drawable {

    public enum Type {
        PLUS,
        POWER
    }

    private final Type mType;
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mArcRect = new RectF();

    public IosHeaderIconDrawable(Type type, float density) {
        mType = type;
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        mPaint.setStrokeJoin(Paint.Join.ROUND);
        mPaint.setColor(Color.WHITE);
        mPaint.setStrokeWidth(Math.max(3f, 2.0f * density));
    }

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        if (b.isEmpty()) {
            return;
        }
        float cx = b.exactCenterX();
        float cy = b.exactCenterY();
        float size = Math.min(b.width(), b.height());

        if (mType == Type.PLUS) {
            float halfArm = size * 0.23f;
            canvas.drawLine(cx - halfArm, cy, cx + halfArm, cy, mPaint);
            canvas.drawLine(cx, cy - halfArm, cx, cy + halfArm, mPaint);
        } else {
            float radius = size * 0.22f;
            mArcRect.set(cx - radius, cy - radius, cx + radius, cy + radius);
            canvas.drawArc(mArcRect, -55f, 290f, false, mPaint);
            canvas.drawLine(cx, cy - radius * 1.15f, cx, cy - radius * 0.05f, mPaint);
        }
    }

    @Override
    public void setAlpha(int alpha) {
        mPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        mPaint.setColorFilter(colorFilter);
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}

