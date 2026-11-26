package com.ios27.controlcenter.hooks;

import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.OvershootInterpolator;
import android.view.animation.PathInterpolator;

import com.ios27.controlcenter.prefs.ModulePrefs;

/**
 * 120Hz-optimized iOS Control Center animations:
 * - Caches row View references (zero getIdentifier() lookups per frame)
 * - Uses View.LAYER_TYPE_HARDWARE during swipe & spring animations for zero-jank GPU compositing
 * - Requests max display refresh rate (120Hz) on NotificationShade while Control Center is active
 */
public final class AnimationHook {

    private static final String CLS_ANIM_HELPER = "com.transsion.systemui.controlcenter.ControlCenterAnimHelper";
    private static final int KEY_IOS_SHAPE = 0x7F0F2701;
    private static final int SHAPE_HIDDEN = 0;
    private static final int KEY_ROW_CACHE = 0x7F0F2704;
    private static final int KEY_REFRESH_ACTIVE = 0x7F0F2705;

    private static final PathInterpolator IOS_EXPAND_INTERP = new PathInterpolator(0.22f, 1.0f, 0.36f, 1.0f);
    private static final PathInterpolator IOS_COLLAPSE_INTERP = new PathInterpolator(0.32f, 0.0f, 0.67f, 1.0f);
    private static final OvershootInterpolator IOS_SPRING_OPEN = new OvershootInterpolator(0.38f);

    private AnimationHook() {
    }

    public static void install(ClassLoader cl) {
        Class<?> cls = HookHelper.findClass(CLS_ANIM_HELPER, cl);
        if (cls == null) {
            return;
        }

        HookHelper.hookAllConstructorsAfter(cls, (chain, res) -> {
            if (!ModulePrefs.isEnabled() || !ModulePrefs.isIosAnimationsEnabled()) {
                return;
            }
            Object helper = chain.getThisObject();
            HookHelper.setObjectField(helper, "INTEPOLATOR_EXPAND", IOS_EXPAND_INTERP);
            HookHelper.setObjectField(helper, "INTEPOLATOR_COLLAPSE", IOS_COLLAPSE_INTERP);
            HookHelper.setLongField(helper, "EXPAND_DURATION", 290L);
            HookHelper.setLongField(helper, "COLLAPSE_DURATION", 200L);
            HookHelper.setLongField(helper, "EXPAND_INTERVAL", 14L);
            HookHelper.setLongField(helper, "COLLAPSE_INTERVAL", 10L);
        });

        HookHelper.hook(cls, "setAlphaAndScale", chain -> {
            if (!ModulePrefs.isEnabled()) {
                return chain.proceed();
            }
            View v = (View) chain.getArg(0);
            if (v != null) {
                Object tag = v.getTag(KEY_IOS_SHAPE);
                if (tag instanceof Integer && (Integer) tag == SHAPE_HIDDEN) {
                    v.setVisibility(View.INVISIBLE);
                    return chain.proceed(new Object[]{v, 0f, 0f});
                }
                if (ModulePrefs.isIosAnimationsEnabled() && v.getWidth() > 0 && v.getHeight() > 0) {
                    v.setPivotX(v.getWidth() * 0.5f);
                    v.setPivotY(v.getHeight() * 0.4f);
                }
            }
            return chain.proceed();
        }, View.class, float.class, float.class);

        HookHelper.hookAfter(cls, "startPanelAnim", (chain, res) -> {
            if (!ModulePrefs.isEnabled() || !ModulePrefs.isIosAnimationsEnabled()) {
                return;
            }
            float fraction = (float) chain.getArg(0);
            View container = getContainerView(chain.getThisObject());
            if (container != null) {
                setHighRefreshRate(container, fraction > 0.005f);
                applyInteractiveSwipe(container, fraction);
            }
        }, float.class);

        HookHelper.hookAfter(cls, "startPanelAnim", (chain, res) -> {
            if (!ModulePrefs.isEnabled() || !ModulePrefs.isIosAnimationsEnabled()) {
                return;
            }
            boolean expand = (boolean) chain.getArg(0);
            View container = getContainerView(chain.getThisObject());
            if (container != null) {
                setHighRefreshRate(container, expand);
                animateRowSnap(container, expand);
            }
        }, boolean.class, boolean.class);

        HookHelper.hookAfter(cls, "resetControlPanelView", (chain, res) -> {
            View container = getContainerView(chain.getThisObject());
            if (container != null) {
                resetRowTransforms(container);
            }
        }, String.class, boolean.class);

        HookHelper.hookAfter(cls, "resetPanelView", (chain, res) -> {
            View container = getContainerView(chain.getThisObject());
            if (container != null) {
                resetRowTransforms(container);
            }
        }, String.class);
    }

    private static View getContainerView(Object animHelper) {
        Object ccController = HookHelper.getObjectField(animHelper, "controlCenterController");
        if (ccController == null) {
            return null;
        }
        return (View) HookHelper.getObjectField(ccController, "mQsContainerView");
    }

    private static RowCache getCache(View container) {
        Object tag = container.getTag(KEY_ROW_CACHE);
        if (tag instanceof RowCache) {
            return (RowCache) tag;
        }
        RowCache cache = new RowCache(container);
        container.setTag(KEY_ROW_CACHE, cache);
        return cache;
    }

    private static void applyInteractiveSwipe(View container, float rawFraction) {
        float f = clamp(rawFraction, 0f, 1f);
        RowCache c = getCache(container);

        if (c.isSnapping) {
            c.cancelAllAnimations();
            c.isSnapping = false;
        }

        boolean activeLayer = f > 0.005f && f < 0.995f;
        setHardwareLayer(c.header, activeLayer);
        setHardwareLayer(c.row1, activeLayer);
        setHardwareLayer(c.row2, activeLayer);
        setHardwareLayer(c.quickQs, activeLayer);
        setHardwareLayer(c.fullQs, activeLayer);

        if (c.header != null) {
            float p = easeOutCubic(clamp((f - 0.06f) / 0.94f, 0f, 1f));
            c.header.setAlpha(clamp((f - 0.06f) * 1.45f, 0f, 1f));
            c.header.setTranslationY(-12f * c.density * (1f - p));
        }

        applyRowProgress(c.row1, f, 0.0f, 0.91f, 22f * c.density);
        applyRowProgress(c.row2, f, 0.05f, 0.88f, 30f * c.density);
        applyRowProgress(c.quickQs, f, 0.10f, 0.85f, 38f * c.density);
        applyRowProgress(c.fullQs, f, 0.10f, 0.85f, 38f * c.density);
    }

    private static void applyRowProgress(View view, float f, float delayFrac, float minScale, float maxSlidePx) {
        if (view == null) {
            return;
        }
        float local = clamp((f - delayFrac) / (1f - delayFrac), 0f, 1f);
        float p = easeOutCubic(local);
        float scale = minScale + (1f - minScale) * p;
        if (view.getWidth() > 0) {
            view.setPivotX(view.getWidth() * 0.5f);
            view.setPivotY(0f);
        }
        view.setScaleX(scale);
        view.setScaleY(scale);
        view.setTranslationY(-maxSlidePx * (1f - p));
        view.setAlpha(clamp(local * 1.35f, 0f, 1f));
    }

    private static void animateRowSnap(View container, boolean expand) {
        RowCache c = getCache(container);
        c.isSnapping = true;

        if (expand) {
            snapOpen(c.header, 0L);
            snapOpen(c.row1, 0L);
            snapOpen(c.row2, 16L);
            snapOpen(c.quickQs, 32L);
            snapOpen(c.fullQs, 32L);
        } else {
            snapClose(c.header, 0L, 10f * c.density);
            snapClose(c.row1, 0L, 16f * c.density);
            snapClose(c.row2, 8L, 22f * c.density);
            snapClose(c.quickQs, 14L, 28f * c.density);
            snapClose(c.fullQs, 14L, 28f * c.density);
        }
    }

    private static void snapOpen(View view, long delayMs) {
        if (view == null) {
            return;
        }
        setHardwareLayer(view, true);
        if (view.getWidth() > 0) {
            view.setPivotX(view.getWidth() * 0.5f);
            view.setPivotY(0f);
        }
        view.animate()
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .alpha(1f)
                .setStartDelay(delayMs)
                .setDuration(280L)
                .setInterpolator(IOS_SPRING_OPEN)
                .withEndAction(() -> setHardwareLayer(view, false))
                .start();
    }

    private static void snapClose(View view, long delayMs, float slideUpPx) {
        if (view == null) {
            return;
        }
        setHardwareLayer(view, true);
        view.animate()
                .scaleX(0.91f)
                .scaleY(0.91f)
                .translationY(-slideUpPx)
                .alpha(0f)
                .setStartDelay(delayMs)
                .setDuration(190L)
                .setInterpolator(IOS_COLLAPSE_INTERP)
                .withEndAction(() -> setHardwareLayer(view, false))
                .start();
    }

    private static void resetRowTransforms(View container) {
        RowCache c = getCache(container);
        c.cancelAllAnimations();
        c.isSnapping = false;
        resetView(c.header);
        resetView(c.row1);
        resetView(c.row2);
        resetView(c.quickQs);
        resetView(c.fullQs);
    }

    private static void resetView(View v) {
        if (v == null) {
            return;
        }
        setHardwareLayer(v, false);
        v.setScaleX(1f);
        v.setScaleY(1f);
        v.setTranslationY(0f);
        v.setAlpha(1f);
    }

    private static void setHardwareLayer(View v, boolean enabled) {
        if (v == null) {
            return;
        }
        int target = enabled ? View.LAYER_TYPE_HARDWARE : View.LAYER_TYPE_NONE;
        if (v.getLayerType() != target) {
            v.setLayerType(target, null);
        }
    }

    private static void setHighRefreshRate(View container, boolean enable) {
        try {
            View root = container.getRootView();
            if (root == null || !root.isAttachedToWindow()) {
                return;
            }
            Object prev = root.getTag(KEY_REFRESH_ACTIVE);
            if (prev instanceof Boolean && (Boolean) prev == enable) {
                return;
            }
            root.setTag(KEY_REFRESH_ACTIVE, enable);

            ViewGroup.LayoutParams rawLp = root.getLayoutParams();
            if (!(rawLp instanceof WindowManager.LayoutParams)) {
                return;
            }
            WindowManager.LayoutParams wlp = (WindowManager.LayoutParams) rawLp;
            Display display = root.getDisplay();
            if (display == null) {
                return;
            }

            if (enable) {
                Display.Mode bestMode = null;
                float maxHz = 0f;
                for (Display.Mode m : display.getSupportedModes()) {
                    if (m.getRefreshRate() > maxHz) {
                        maxHz = m.getRefreshRate();
                        bestMode = m;
                    }
                }
                if (bestMode != null && maxHz >= 90f) {
                    wlp.preferredDisplayModeId = bestMode.getModeId();
                    wlp.preferredRefreshRate = maxHz;
                }
            } else {
                wlp.preferredDisplayModeId = 0;
                wlp.preferredRefreshRate = 0f;
            }

            WindowManager wm = root.getContext().getSystemService(WindowManager.class);
            if (wm != null) {
                wm.updateViewLayout(root, wlp);
            }
        } catch (Throwable ignored) {
        }
    }

    private static float easeOutCubic(float t) {
        float inv = 1f - t;
        return 1f - inv * inv * inv;
    }

    private static float clamp(float val, float min, float max) {
        return Math.max(min, Math.min(max, val));
    }

    private static final class RowCache {
        final float density;
        final View header;
        final View row1;
        final View row2;
        final View quickQs;
        final View fullQs;
        boolean isSnapping = false;

        RowCache(View container) {
            this.density = container.getResources().getDisplayMetrics().density;
            this.header = ControlCenterHook.findViewByIdName(container, "header");
            this.row1 = ControlCenterHook.findViewByIdName(container, "fixed_row1");
            this.row2 = ControlCenterHook.findViewByIdName(container, "fixed_row2");
            this.quickQs = ControlCenterHook.findViewByIdName(container, "quick_qs_panel");
            this.fullQs = ControlCenterHook.findViewByIdName(container, "quick_settings_panel");
        }

        void cancelAllAnimations() {
            if (header != null) header.animate().cancel();
            if (row1 != null) row1.animate().cancel();
            if (row2 != null) row2.animate().cancel();
            if (quickQs != null) quickQs.animate().cancel();
            if (fullQs != null) fullQs.animate().cancel();
        }
    }
}
