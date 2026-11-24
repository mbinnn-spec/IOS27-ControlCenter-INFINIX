package com.ios27.controlcenter.hooks;

import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SeekBar;

import com.ios27.controlcenter.HookEntry;
import com.ios27.controlcenter.prefs.ModulePrefs;
import com.ios27.controlcenter.ui.TransparentNoOpDrawable;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Renders iOS 26/27 Liquid Glass vertical capsule brightness & volume sliders
 * with frosted fill clipping and dynamic yellow/blue icon tinting.
 */
public final class SliderHook {

    private static final String CLS_SEEKBAR = "com.transsion.systemui.widget.QcToggleSeekBar";
    private static final String CLS_BRIGHTNESS = "com.transsion.systemui.widget.TrBrightnessSliderView";
    private static final String CLS_VOLUME = "com.transsion.systemui.widget.VolumeSliderView";

    private static final int BRIGHTNESS_ACTIVE_YELLOW = 0xFFFFCC00;
    private static final int VOLUME_ACTIVE_BLUE = 0xFF32ADE6;
    private static final int TAG_LAST_ICON_COLOR = 0x7F0F2702;

    private SliderHook() {
    }

    public static void install(ClassLoader cl) {
        hookBrightnessView(cl);
        hookVolumeView(cl);
        hookSeekBarDraw(cl);
    }

    private static void hookBrightnessView(ClassLoader cl) {
        Class<?> cls = XposedHelpers.findClassIfExists(CLS_BRIGHTNESS, cl);
        if (cls == null) {
            return;
        }

        XposedHelpers.findAndHookMethod(cls, "onFinishInflate", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    if (ModulePrefs.isEnabled() && ModulePrefs.isIosSlidersEnabled()) {
                        clearContainerBackground((View) param.thisObject);
                    }
                } catch (Throwable ignored) {
                }
            }
        });

        XposedHelpers.findAndHookMethod(cls, "updateIcon", boolean.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    if (ModulePrefs.isEnabled() && ModulePrefs.isIosSlidersEnabled()) {
                        View root = (View) param.thisObject;
                        clearContainerBackground(root);
                        syncSiblingIconTint(root);
                    }
                } catch (Throwable ignored) {
                }
            }
        });
    }

    private static void hookVolumeView(ClassLoader cl) {
        Class<?> cls = XposedHelpers.findClassIfExists(CLS_VOLUME, cl);
        if (cls == null) {
            return;
        }

        XC_MethodHook clearHook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    if (ModulePrefs.isEnabled() && ModulePrefs.isIosSlidersEnabled()) {
                        View root = (View) param.thisObject;
                        clearContainerBackground(root);
                        syncSiblingIconTint(root);
                    }
                } catch (Throwable ignored) {
                }
            }
        };

        XposedHelpers.findAndHookMethod(cls, "onFinishInflate", clearHook);
        XposedHelpers.findAndHookMethod(cls, "updateResources", clearHook);
        XposedHelpers.findAndHookMethod(cls, "checkVolumeIcon", int.class, clearHook);
        XposedHelpers.findAndHookMethod(cls, "checkVolumeTintByPercent", int.class, clearHook);
    }

    private static void hookSeekBarDraw(ClassLoader cl) {
        Class<?> cls = XposedHelpers.findClassIfExists(CLS_SEEKBAR, cl);
        if (cls == null) {
            return;
        }

        XposedHelpers.findAndHookMethod(cls, "onDraw", Canvas.class, new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                if (!ModulePrefs.isEnabled() || !ModulePrefs.isIosSlidersEnabled()) {
                    return XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args);
                }
                try {
                    SeekBar seekBar = (SeekBar) param.thisObject;
                    Canvas canvas = (Canvas) param.args[0];
                    drawLiquidGlassSlider(seekBar, canvas);
                } catch (Throwable t) {
                    XposedBridge.log(HookEntry.TAG + ": slider onDraw err: " + t);
                    return XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args);
                }
                return null;
            }
        });
    }

    private static void drawLiquidGlassSlider(SeekBar seekBar, Canvas canvas) {
        int w = seekBar.getWidth();
        int h = seekBar.getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }

        if (!(seekBar.getBackground() instanceof TransparentNoOpDrawable)) {
            TransparentNoOpDrawable.apply(seekBar);
        }

        if (seekBar.getParent() instanceof View) {
            clearContainerBackground((View) seekBar.getParent());
        }

        float density = seekBar.getResources().getDisplayMetrics().density;
        float radius = w * 0.5f;

        int min = seekBar.getMin();
        int max = Math.max(min + 1, seekBar.getMax());
        float fraction = (seekBar.getProgress() - min) / (float) (max - min);
        fraction = Math.max(0f, Math.min(1f, fraction));

        DrawState state = getOrCreateDrawState(seekBar, w, h, density);

        int saveCount = canvas.save();
        canvas.clipPath(state.clipPath);

        // Dark frosted track
        canvas.drawRoundRect(state.bounds, radius, radius, state.trackPaint);
        canvas.drawRoundRect(state.bounds, radius, radius, state.sheenPaint);

        // Bright frosted-white fill from bottom
        if (fraction > 0.005f) {
            float fillTop = h * (1f - fraction);
            state.fillRect.set(0f, fillTop, w, h);
            canvas.drawRect(state.fillRect, state.fillPaint);
        }

        canvas.restoreToCount(saveCount);

        // Directional specular glass rim
        if (ModulePrefs.getRimIntensity() > 0) {
            float rimRadius = Math.max(0f, radius - state.strokeWidth * 0.5f);
            canvas.drawRoundRect(state.strokeBounds, rimRadius, rimRadius, state.rimPaint);
        }

        updateParentIconTint(seekBar, fraction);
    }

    private static void syncSiblingIconTint(View sliderContainer) {
        View slider = ControlCenterHook.findViewByIdName(sliderContainer, "slider");
        if (slider instanceof SeekBar) {
            SeekBar sb = (SeekBar) slider;
            int min = sb.getMin();
            int max = Math.max(min + 1, sb.getMax());
            float fraction = Math.max(0f, Math.min(1f, (sb.getProgress() - min) / (float) (max - min)));
            updateParentIconTint(sb, fraction);
        }
    }

    private static void updateParentIconTint(SeekBar seekBar, float fraction) {
        if (!(seekBar.getParent() instanceof ViewGroup)) {
            return;
        }
        ViewGroup parent = (ViewGroup) seekBar.getParent();
        String parentClass = parent.getClass().getName();
        boolean isVolume = CLS_VOLUME.equals(parentClass);
        boolean isBrightness = CLS_BRIGHTNESS.equals(parentClass);
        if (!isVolume && !isBrightness) {
            return;
        }

        View iconView = ControlCenterHook.findViewByIdName(parent, isVolume ? "volume_icon" : "icon");
        if (!(iconView instanceof ImageView)) {
            return;
        }

        boolean filledOverIcon = fraction >= 0.20f;
        int targetColor = filledOverIcon
                ? (isVolume ? VOLUME_ACTIVE_BLUE : BRIGHTNESS_ACTIVE_YELLOW)
                : 0xF2FFFFFF;

        ImageView iv = (ImageView) iconView;
        Object prev = iv.getTag(TAG_LAST_ICON_COLOR);
        if (prev instanceof Integer && (Integer) prev == targetColor && iv.getAlpha() >= 0.99f) {
            return;
        }
        iv.setTag(TAG_LAST_ICON_COLOR, targetColor);
        iv.setAlpha(1.0f);
        iv.clearColorFilter();
        iv.setImageTintList(ColorStateList.valueOf(targetColor));
    }

    private static void clearContainerBackground(View v) {
        if (v != null && !(v.getBackground() instanceof TransparentNoOpDrawable)) {
            TransparentNoOpDrawable.apply(v);
        }
    }

    private static DrawState getOrCreateDrawState(SeekBar seekBar, int w, int h, float density) {
        Object tag = seekBar.getTag(0x7F0F2703);
        int glassOpacity = ModulePrefs.getGlassOpacity();
        int rimIntensity = ModulePrefs.getRimIntensity();
        if (tag instanceof DrawState) {
            DrawState ds = (DrawState) tag;
            if (ds.w == w && ds.h == h && ds.glassOpacity == glassOpacity && ds.rimIntensity == rimIntensity) {
                return ds;
            }
        }
        DrawState ds = new DrawState(w, h, density, glassOpacity, rimIntensity);
        seekBar.setTag(0x7F0F2703, ds);
        return ds;
    }

    private static final class DrawState {
        final int w;
        final int h;
        final int glassOpacity;
        final int rimIntensity;
        final float strokeWidth;

        final RectF bounds = new RectF();
        final RectF strokeBounds = new RectF();
        final RectF fillRect = new RectF();
        final Path clipPath = new Path();

        final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint sheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        DrawState(int w, int h, float density, int glassOpacity, int rimIntensity) {
            this.w = w;
            this.h = h;
            this.glassOpacity = glassOpacity;
            this.rimIntensity = rimIntensity;
            this.strokeWidth = Math.max(1.8f, 1.0f * density);

            float radius = w * 0.5f;
            bounds.set(0f, 0f, w, h);
            float half = strokeWidth * 0.5f;
            strokeBounds.set(half, half, w - half, h - half);
            clipPath.addRoundRect(bounds, radius, radius, Path.Direction.CW);

            float glassFactor = glassOpacity / 100f;
            float rimFactor = rimIntensity / 100f;

            trackPaint.setStyle(Paint.Style.FILL);
            trackPaint.setColor(Color.argb(Math.round(76 + 108 * glassFactor), 22, 24, 32));

            sheenPaint.setStyle(Paint.Style.FILL);
            sheenPaint.setShader(new LinearGradient(
                    0f, 0f, 0f, h,
                    new int[]{
                            Color.argb(24, 255, 255, 255),
                            Color.argb(4, 255, 255, 255),
                            Color.argb(14, 255, 255, 255)
                    },
                    new float[]{0f, 0.45f, 1f},
                    Shader.TileMode.CLAMP));

            fillPaint.setStyle(Paint.Style.FILL);
            fillPaint.setShader(new LinearGradient(
                    0f, 0f, 0f, h,
                    Color.argb(246, 255, 255, 255),
                    Color.argb(232, 242, 246, 252),
                    Shader.TileMode.CLAMP));

            rimPaint.setStyle(Paint.Style.STROKE);
            rimPaint.setStrokeWidth(strokeWidth);
            rimPaint.setShader(new LinearGradient(
                    0f, 0f, w, h,
                    new int[]{
                            Color.argb(Math.round(175 * rimFactor), 255, 255, 255),
                            Color.argb(Math.round(24 * rimFactor), 255, 255, 255),
                            Color.argb(Math.round(115 * rimFactor), 255, 255, 255)
                    },
                    new float[]{0f, 0.5f, 1f},
                    Shader.TileMode.CLAMP));
        }
    }
}

