package com.ios27.controlcenter.ui;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * Procedural iOS 26/27 Liquid Glass drawable with cached shaders, directional specular rim,
 * subtle inner top sheen, and optional per-tile ambient color refraction.
 *
 * Tint/ColorFilter setters are intentionally overridden as no-ops so SystemUI tile state tinting
 * cannot flatten the glass appearance.
 */
public class LiquidGlassDrawable extends Drawable {

    public enum Style {
        DARK_GLASS,
        ACTIVE_WHITE_PILL,
        ACTIVE_COLOR_CIRCLE,
        INACTIVE_CIRCLE_GLASS,
        HEADER_CIRCLE_GLASS
    }

    private Style mStyle;
    private float mCornerRadiusPx;
    private boolean mFullCapsule;
    private int mAccentColor = 0xFF007AFF;
    private int mGlassOpacity = 46;
    private int mRimIntensity = 85;
    private boolean mAmbientGlow = true;
    private float mStrokeWidthPx;
    private int mAlpha = 255;

    private final Paint mBasePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mSheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mRimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mInnerTopRimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF mOuterRect = new RectF();
    private final RectF mStrokeRect = new RectF();
    private final RectF mInnerTopRect = new RectF();

    private boolean mShadersDirty = true;

    public LiquidGlassDrawable(Style style, float cornerRadiusPx, boolean fullCapsule, float density) {
        mStyle = style;
        mCornerRadiusPx = cornerRadiusPx;
        mFullCapsule = fullCapsule;
        mStrokeWidthPx = Math.max(1.8f, 1.0f * density);

        mBasePaint.setStyle(Paint.Style.FILL);
        mSheenPaint.setStyle(Paint.Style.FILL);
        mGlowPaint.setStyle(Paint.Style.FILL);

        mRimPaint.setStyle(Paint.Style.STROKE);
        mRimPaint.setStrokeWidth(mStrokeWidthPx);

        mInnerTopRimPaint.setStyle(Paint.Style.STROKE);
        mInnerTopRimPaint.setStrokeWidth(Math.max(1f, 0.7f * density));
    }

    public void configure(Style style, int accentColor, int glassOpacity, int rimIntensity, boolean ambientGlow) {
        if (mStyle != style
                || mAccentColor != accentColor
                || mGlassOpacity != glassOpacity
                || mRimIntensity != rimIntensity
                || mAmbientGlow != ambientGlow) {
            mStyle = style;
            mAccentColor = accentColor;
            mGlassOpacity = glassOpacity;
            mRimIntensity = rimIntensity;
            mAmbientGlow = ambientGlow;
            mShadersDirty = true;
            invalidateSelf();
        }
    }

    public void setStyle(Style style) {
        if (mStyle != style) {
            mStyle = style;
            mShadersDirty = true;
            invalidateSelf();
        }
    }

    public void setAccentColor(int accentColor) {
        if (mAccentColor != accentColor) {
            mAccentColor = accentColor;
            mShadersDirty = true;
            invalidateSelf();
        }
    }

    public void setFullCapsule(boolean fullCapsule) {
        if (mFullCapsule != fullCapsule) {
            mFullCapsule = fullCapsule;
            mShadersDirty = true;
            invalidateSelf();
        }
    }

    public void setCornerRadiusPx(float cornerRadiusPx) {
        if (mCornerRadiusPx != cornerRadiusPx) {
            mCornerRadiusPx = cornerRadiusPx;
            mShadersDirty = true;
            invalidateSelf();
        }
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        mShadersDirty = true;
    }

    private void ensureShaders(Rect bounds) {
        if (!mShadersDirty || bounds.width() <= 0 || bounds.height() <= 0) {
            return;
        }
        mShadersDirty = false;

        float w = bounds.width();
        float h = bounds.height();
        float left = bounds.left;
        float top = bounds.top;

        mOuterRect.set(bounds);
        float halfStroke = mStrokeWidthPx * 0.5f;
        mStrokeRect.set(
                bounds.left + halfStroke,
                bounds.top + halfStroke,
                bounds.right - halfStroke,
                bounds.bottom - halfStroke
        );
        mInnerTopRect.set(
                bounds.left + mStrokeWidthPx * 1.1f,
                bounds.top + mStrokeWidthPx * 1.1f,
                bounds.right - mStrokeWidthPx * 1.1f,
                bounds.bottom - mStrokeWidthPx * 1.1f
        );

        float rimFactor = mRimIntensity / 100f;
        float glassFactor = mGlassOpacity / 100f;

        mBasePaint.setShader(null);
        mSheenPaint.setShader(null);
        mGlowPaint.setShader(null);
        mRimPaint.setShader(null);
        mInnerTopRimPaint.setShader(null);

        switch (mStyle) {
            case ACTIVE_WHITE_PILL: {
                int topColor = Color.argb(246, 255, 255, 255);
                int bottomColor = Color.argb(232, 242, 246, 252);
                mBasePaint.setShader(new LinearGradient(
                        left, top, left, top + h,
                        topColor, bottomColor, Shader.TileMode.CLAMP));

                int rimStart = Color.argb(Math.round(195 * Math.max(0.45f, rimFactor)), 255, 255, 255);
                int rimMid = Color.argb(Math.round(75 * Math.max(0.45f, rimFactor)), 255, 255, 255);
                int rimEnd = Color.argb(Math.round(130 * Math.max(0.45f, rimFactor)), 255, 255, 255);
                mRimPaint.setShader(new LinearGradient(
                        left, top, left + w, top + h,
                        new int[]{rimStart, rimMid, rimEnd},
                        new float[]{0f, 0.5f, 1f},
                        Shader.TileMode.CLAMP));
                break;
            }
            case ACTIVE_COLOR_CIRCLE: {
                mBasePaint.setColor(mAccentColor);
                mSheenPaint.setShader(new LinearGradient(
                        left, top, left, top + h * 0.72f,
                        Color.argb(66, 255, 255, 255),
                        Color.argb(0, 255, 255, 255),
                        Shader.TileMode.CLAMP));
                mRimPaint.setShader(new LinearGradient(
                        left, top, left + w, top + h,
                        Color.argb(Math.round(135 * rimFactor), 255, 255, 255),
                        Color.argb(Math.round(45 * rimFactor), 255, 255, 255),
                        Shader.TileMode.CLAMP));
                break;
            }
            case INACTIVE_CIRCLE_GLASS: {
                int baseAlpha = Math.round(38 + 36 * glassFactor);
                mBasePaint.setColor(Color.argb(baseAlpha, 255, 255, 255));
                mSheenPaint.setShader(new LinearGradient(
                        left, top, left, top + h,
                        Color.argb(26, 255, 255, 255),
                        Color.argb(6, 255, 255, 255),
                        Shader.TileMode.CLAMP));
                mRimPaint.setShader(new LinearGradient(
                        left, top, left + w, top + h,
                        new int[]{
                                Color.argb(Math.round(120 * rimFactor), 255, 255, 255),
                                Color.argb(Math.round(25 * rimFactor), 255, 255, 255),
                                Color.argb(Math.round(85 * rimFactor), 255, 255, 255)
                        },
                        new float[]{0f, 0.5f, 1f},
                        Shader.TileMode.CLAMP));
                break;
            }
            case HEADER_CIRCLE_GLASS: {
                int baseAlpha = Math.round(70 + 80 * glassFactor);
                mBasePaint.setColor(Color.argb(baseAlpha, 42, 46, 56));
                mSheenPaint.setShader(new LinearGradient(
                        left, top, left, top + h,
                        Color.argb(42, 255, 255, 255),
                        Color.argb(8, 255, 255, 255),
                        Shader.TileMode.CLAMP));
                mRimPaint.setShader(new LinearGradient(
                        left, top, left + w, top + h,
                        new int[]{
                                Color.argb(Math.round(160 * rimFactor), 255, 255, 255),
                                Color.argb(Math.round(40 * rimFactor), 255, 255, 255),
                                Color.argb(Math.round(115 * rimFactor), 255, 255, 255)
                        },
                        new float[]{0f, 0.5f, 1f},
                        Shader.TileMode.CLAMP));
                break;
            }
            case DARK_GLASS:
            default: {
                int baseAlpha = Math.round(76 + 108 * glassFactor);
                mBasePaint.setColor(Color.argb(baseAlpha, 22, 24, 32));

                mSheenPaint.setShader(new LinearGradient(
                        left, top, left, top + h,
                        new int[]{
                                Color.argb(24, 255, 255, 255),
                                Color.argb(4, 255, 255, 255),
                                Color.argb(14, 255, 255, 255)
                        },
                        new float[]{0f, 0.45f, 1f},
                        Shader.TileMode.CLAMP));

                if (mAmbientGlow && Color.alpha(mAccentColor) > 0) {
                    float cx = left + w * 0.24f;
                    float cy = top + h * 0.5f;
                    float radius = Math.max(w, h) * 0.72f;
                    int r = Color.red(mAccentColor);
                    int g = Color.green(mAccentColor);
                    int b = Color.blue(mAccentColor);
                    mGlowPaint.setShader(new RadialGradient(
                            cx, cy, radius,
                            new int[]{
                                    Color.argb(62, r, g, b),
                                    Color.argb(22, r, g, b),
                                    Color.argb(0, r, g, b)
                            },
                            new float[]{0f, 0.55f, 1f},
                            Shader.TileMode.CLAMP));
                }

                mRimPaint.setShader(new LinearGradient(
                        left, top, left + w, top + h,
                        new int[]{
                                Color.argb(Math.round(175 * rimFactor), 255, 255, 255),
                                Color.argb(Math.round(24 * rimFactor), 255, 255, 255),
                                Color.argb(Math.round(115 * rimFactor), 255, 255, 255)
                        },
                        new float[]{0f, 0.48f, 1f},
                        Shader.TileMode.CLAMP));

                mInnerTopRimPaint.setShader(new LinearGradient(
                        left, top, left, top + h * 0.32f,
                        Color.argb(Math.round(52 * rimFactor), 255, 255, 255),
                        Color.argb(0, 255, 255, 255),
                        Shader.TileMode.CLAMP));
                break;
            }
        }
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        if (bounds.isEmpty()) {
            return;
        }
        ensureShaders(bounds);

        float radius = mFullCapsule
                ? Math.min(bounds.width(), bounds.height()) * 0.5f
                : mCornerRadiusPx;

        canvas.drawRoundRect(mOuterRect, radius, radius, mBasePaint);

        if (mGlowPaint.getShader() != null) {
            canvas.drawRoundRect(mOuterRect, radius, radius, mGlowPaint);
        }

        if (mSheenPaint.getShader() != null) {
            canvas.drawRoundRect(mOuterRect, radius, radius, mSheenPaint);
        }

        if (mInnerTopRimPaint.getShader() != null && mRimIntensity > 0) {
            float innerRadius = Math.max(0f, radius - mStrokeWidthPx);
            canvas.drawRoundRect(mInnerTopRect, innerRadius, innerRadius, mInnerTopRimPaint);
        }

        if (mRimIntensity > 0) {
            float strokeRadius = Math.max(0f, radius - mStrokeWidthPx * 0.5f);
            canvas.drawRoundRect(mStrokeRect, strokeRadius, strokeRadius, mRimPaint);
        }
    }

    @Override
    public void setAlpha(int alpha) {
        if (mAlpha != alpha) {
            mAlpha = alpha;
            mBasePaint.setAlpha(alpha);
            mSheenPaint.setAlpha(alpha);
            mGlowPaint.setAlpha(alpha);
            mRimPaint.setAlpha(alpha);
            mInnerTopRimPaint.setAlpha(alpha);
            invalidateSelf();
        }
    }

    @Override
    public int getAlpha() {
        return mAlpha;
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        // Intentionally ignored so SystemUI cannot override Liquid Glass shading.
    }

    @Override
    public void setTint(int tintColor) {
        // Intentionally ignored so TsQSTileBaseView.handleStateChanged cannot tint over glass.
    }

    @Override
    public void setTintList(ColorStateList tint) {
        // Intentionally ignored.
    }

    @Override
    public void setTintMode(PorterDuff.Mode tintMode) {
        // Intentionally ignored.
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}

