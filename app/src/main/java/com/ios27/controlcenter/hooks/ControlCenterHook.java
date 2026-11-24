package com.ios27.controlcenter.hooks;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.IBinder;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import com.ios27.controlcenter.HookEntry;
import com.ios27.controlcenter.prefs.ModulePrefs;
import com.ios27.controlcenter.ui.IosHeaderIconDrawable;
import com.ios27.controlcenter.ui.LiquidGlassDrawable;
import com.ios27.controlcenter.ui.TransparentNoOpDrawable;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Hooks XOS 15 Control Center container, top header (+ / Power buttons + status strip),
 * 2x2 connectivity card background, 2x2 media player, and bottom IoT device card.
 */
public final class ControlCenterHook {

    private static final String CLS_QS_CONTAINER = "com.transsion.systemui.controlcenter.qscontrol.TrQSContainerImpl";
    private static final String CLS_HEADER = "com.transsion.systemui.controlcenter.qscontrol.TrControlCenterHeader";
    private static final String CLS_MEDIA_VIEW = "com.android.systemui.controls.media.QuickCenterMediaView";
    private static final String CLS_IOT_CONTROLLER = "com.transsion.systemui.controlcenter.iot.IotCardsViewController";

    private static final String TAG_PLUS_BTN = "ios27_plus_btn";
    private static final String TAG_POWER_BTN = "ios27_power_btn";
    private static final String TAG_HEADER_SPACER = "ios27_header_spacer";

    private ControlCenterHook() {
    }

    public static void install(ClassLoader cl) {
        hookContainer(cl);
        hookHeader(cl);
        hookMediaView(cl);
        hookIotCards(cl);
    }

    private static void hookContainer(ClassLoader cl) {
        Class<?> cls = XposedHelpers.findClassIfExists(CLS_QS_CONTAINER, cl);
        if (cls == null) {
            return;
        }

        XposedHelpers.findAndHookMethod(cls, "onFinishInflate", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    ModulePrefs.reloadIfChanged();
                    if (!ModulePrefs.isEnabled()) {
                        return;
                    }
                    styleContainer((ViewGroup) param.thisObject, 1f);
                } catch (Throwable t) {
                    XposedBridge.log(HookEntry.TAG + ": container onFinishInflate err: " + t);
                }
            }
        });

        XposedHelpers.findAndHookMethod(cls, "updateResources", Configuration.class, String.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    ModulePrefs.reloadIfChanged();
                    if (!ModulePrefs.isEnabled()) {
                        return;
                    }
                    ViewGroup container = (ViewGroup) param.thisObject;
                    float exp = XposedHelpers.getFloatField(container, "mQsExpansion");
                    styleContainer(container, exp);
                } catch (Throwable t) {
                    XposedBridge.log(HookEntry.TAG + ": container updateResources err: " + t);
                }
            }
        });

        XposedHelpers.findAndHookMethod(cls, "setExpansion", float.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    if (!ModulePrefs.isEnabled()) {
                        return;
                    }
                    ViewGroup container = (ViewGroup) param.thisObject;
                    float fraction = (float) param.args[0];
                    updateBackdropDim(container, fraction);
                } catch (Throwable ignored) {
                }
            }
        });
    }

    private static void styleContainer(ViewGroup container, float expansion) {
        Context ctx = container.getContext();
        float density = ctx.getResources().getDisplayMetrics().density;

        updateBackdropDim(container, expansion);

        View fixedTiles = findViewByIdName(container, "fixed_tiles_view");
        if (fixedTiles != null) {
            // Keep XOS native padding/centering inside CustomGridLayout so the 4 circles stay dead-center.
            applyDarkGlass(fixedTiles, 30f * density, 0xFF3E689E);
        }

        View mediaView = findViewByIdName(container, "media_container");
        if (mediaView != null) {
            styleMediaViewInternal(mediaView);
        }

        View iotContainer = findViewByIdName(container, "iot_devices_container");
        if (iotContainer != null) {
            styleIotContainer(iotContainer);
        }
    }

    private static void hookIotCards(ClassLoader cl) {
        Class<?> cls = XposedHelpers.findClassIfExists(CLS_IOT_CONTROLLER, cl);
        if (cls == null) {
            return;
        }

        XposedHelpers.findAndHookMethod(cls, "attachRoot", ViewGroup.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    if (!ModulePrefs.isEnabled()) {
                        return;
                    }
                    ViewGroup root = (ViewGroup) param.args[0];
                    if (root != null) {
                        styleIotContainer(root);
                    }
                } catch (Throwable ignored) {
                }
            }
        });

        XposedHelpers.findAndHookMethod(cls, "setIotVisibility", int.class, boolean.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    if (!ModulePrefs.isEnabled()) {
                        return;
                    }
                    View root = findViewByFieldOrNull(param.thisObject);
                    if (root != null) {
                        styleIotContainer(root);
                    }
                } catch (Throwable ignored) {
                }
            }
        });
    }

    private static View findViewByFieldOrNull(Object obj) {
        for (java.lang.reflect.Field f : obj.getClass().getDeclaredFields()) {
            if (View.class.isAssignableFrom(f.getType())) {
                try {
                    f.setAccessible(true);
                    Object v = f.get(obj);
                    if (v instanceof View) {
                        return (View) v;
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    private static void styleIotContainer(View iotContainer) {
        if (ModulePrefs.isHideIotCard()) {
            iotContainer.setVisibility(View.GONE);
            return;
        }
        iotContainer.setVisibility(View.VISIBLE);
        float density = iotContainer.getResources().getDisplayMetrics().density;
        clearNestedBackgrounds(iotContainer);
        applyDarkGlass(iotContainer, 28f * density, 0xFF687898);
    }

    private static void clearNestedBackgrounds(View root) {
        if (!(root instanceof ViewGroup)) {
            return;
        }
        ViewGroup vg = (ViewGroup) root;
        for (int i = 0; i < vg.getChildCount(); i++) {
            View child = vg.getChildAt(i);
            TransparentNoOpDrawable.apply(child);
            if (child instanceof ViewGroup) {
                clearNestedBackgrounds(child);
            }
        }
    }

    private static void updateBackdropDim(ViewGroup container, float expansion) {
        int dimPct = ModulePrefs.getBgDimAlpha();
        if (dimPct <= 0) {
            if (container.getBackground() instanceof ColorDrawable) {
                TransparentNoOpDrawable.apply(container);
            }
            return;
        }
        float clampedExp = Math.max(0f, Math.min(1f, expansion));
        int alpha = Math.round((dimPct / 100f) * 255f * clampedExp);
        int color = Color.argb(alpha, 6, 8, 14);
        Drawable bg = container.getBackground();
        if (bg instanceof ColorDrawable) {
            ((ColorDrawable) bg).setColor(color);
        } else {
            container.setBackground(new ColorDrawable(color));
        }
    }

    private static void hookHeader(ClassLoader cl) {
        Class<?> cls = XposedHelpers.findClassIfExists(CLS_HEADER, cl);
        if (cls == null) {
            return;
        }

        XC_MethodHook headerHook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    ModulePrefs.reloadIfChanged();
                    if (!ModulePrefs.isEnabled() || !ModulePrefs.isIosHeaderEnabled()) {
                        return;
                    }
                    styleHeader((RelativeLayout) param.thisObject);
                } catch (Throwable t) {
                    XposedBridge.log(HookEntry.TAG + ": header hook err: " + t);
                }
            }
        };

        XposedHelpers.findAndHookMethod(cls, "onFinishInflate", headerHook);
        XposedHelpers.findAndHookMethod(cls, "updateUI", headerHook);
        XposedHelpers.findAndHookMethod(cls, "updateResources", headerHook);
    }

    private static void styleHeader(RelativeLayout header) {
        Context ctx = header.getContext();
        float density = ctx.getResources().getDisplayMetrics().density;

        View statusContainer = findViewByIdName(header, "header_status_bar_container_height");
        View statusIcons = findViewByIdName(header, "qs_status_icons");
        ViewGroup qcHeader = (ViewGroup) findViewByIdName(header, "tr_qc_header");

        if (qcHeader != null) {
            RelativeLayout.LayoutParams qcLp = (RelativeLayout.LayoutParams) qcHeader.getLayoutParams();
            if (qcLp != null) {
                clearRelativeVerticalRules(qcLp);
                qcLp.width = ViewGroup.LayoutParams.MATCH_PARENT;
                qcLp.height = dp(40, density);
                qcLp.topMargin = 0;
                qcLp.bottomMargin = 0;
                qcLp.addRule(RelativeLayout.ALIGN_PARENT_TOP);
                qcHeader.setLayoutParams(qcLp);
            }
            if (qcHeader instanceof LinearLayout) {
                ((LinearLayout) qcHeader).setGravity(Gravity.CENTER_VERTICAL);
            }
            qcHeader.setPadding(dp(7, density), 0, dp(7, density), 0);

            View userView = findViewByIdName(qcHeader, "quick_center_user");
            View settingsView = findViewByIdName(qcHeader, "quick_center_settings");
            View moreView = findViewByIdName(qcHeader, "quick_center_more");

            if (userView != null) {
                userView.setVisibility(View.GONE);
            }
            if (settingsView != null) {
                settingsView.setVisibility(View.GONE);
            }
            if (moreView != null) {
                moreView.setVisibility(View.GONE);
            }

            for (int i = 0; i < qcHeader.getChildCount(); i++) {
                View child = qcHeader.getChildAt(i);
                if (child instanceof FrameLayout && child.getId() == View.NO_ID) {
                    child.setVisibility(View.GONE);
                }
            }

            ensureHeaderActionButtons(qcHeader, settingsView, moreView, density);
        }

        if (statusContainer != null) {
            RelativeLayout.LayoutParams stLp = (RelativeLayout.LayoutParams) statusContainer.getLayoutParams();
            if (stLp != null) {
                clearRelativeVerticalRules(stLp);
                stLp.width = ViewGroup.LayoutParams.MATCH_PARENT;
                stLp.height = dp(26, density);
                stLp.topMargin = dp(48, density);
                stLp.bottomMargin = 0;
                stLp.addRule(RelativeLayout.ALIGN_PARENT_TOP);
                statusContainer.setLayoutParams(stLp);
            }
        }

        if (statusIcons != null) {
            ViewGroup.MarginLayoutParams iconLp = (ViewGroup.MarginLayoutParams) statusIcons.getLayoutParams();
            if (iconLp != null) {
                iconLp.height = dp(24, density);
                iconLp.topMargin = dp(1, density);
                iconLp.bottomMargin = dp(1, density);
                iconLp.leftMargin = dp(7, density);
                iconLp.rightMargin = dp(7, density);
                statusIcons.setLayoutParams(iconLp);
            }
        }
    }

    private static void clearRelativeVerticalRules(RelativeLayout.LayoutParams lp) {
        lp.removeRule(RelativeLayout.ALIGN_PARENT_TOP);
        lp.removeRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        lp.removeRule(RelativeLayout.BELOW);
        lp.removeRule(RelativeLayout.ABOVE);
        lp.removeRule(RelativeLayout.CENTER_VERTICAL);
        lp.removeRule(RelativeLayout.CENTER_IN_PARENT);
    }

    private static void ensureHeaderActionButtons(
            ViewGroup qcHeader,
            View settingsView,
            View moreView,
            float density
    ) {
        Context ctx = qcHeader.getContext();
        int btnSize = dp(40, density);

        ImageView plusBtn = qcHeader.findViewWithTag(TAG_PLUS_BTN);
        if (plusBtn == null) {
            plusBtn = new ImageView(ctx);
            plusBtn.setTag(TAG_PLUS_BTN);
            plusBtn.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            plusBtn.setImageDrawable(new IosHeaderIconDrawable(IosHeaderIconDrawable.Type.PLUS, density));
            plusBtn.setContentDescription("Edit Control Center");
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(btnSize, btnSize);
            plusBtn.setLayoutParams(lp);
            qcHeader.addView(plusBtn, 0);
        }
        applyHeaderCircleGlass(plusBtn, density);
        plusBtn.setOnClickListener(v -> {
            if (moreView != null) {
                moreView.performClick();
            } else if (settingsView != null) {
                settingsView.performClick();
            }
        });
        plusBtn.setOnLongClickListener(v -> {
            if (settingsView != null) {
                settingsView.performClick();
                return true;
            }
            return false;
        });

        View spacer = qcHeader.findViewWithTag(TAG_HEADER_SPACER);
        if (spacer == null) {
            spacer = new Space(ctx);
            spacer.setTag(TAG_HEADER_SPACER);
            LinearLayout.LayoutParams spLp = new LinearLayout.LayoutParams(0, 1, 1f);
            spacer.setLayoutParams(spLp);
            qcHeader.addView(spacer);
        }

        ImageView powerBtn = qcHeader.findViewWithTag(TAG_POWER_BTN);
        if (powerBtn == null) {
            powerBtn = new ImageView(ctx);
            powerBtn.setTag(TAG_POWER_BTN);
            powerBtn.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            powerBtn.setImageDrawable(new IosHeaderIconDrawable(IosHeaderIconDrawable.Type.POWER, density));
            powerBtn.setContentDescription("Power Menu");
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(btnSize, btnSize);
            powerBtn.setLayoutParams(lp);
            qcHeader.addView(powerBtn);
        }
        applyHeaderCircleGlass(powerBtn, density);
        powerBtn.setOnClickListener(v -> triggerPowerMenu(ctx, settingsView));
    }

    private static void applyHeaderCircleGlass(View view, float density) {
        Drawable existing = view.getBackground();
        LiquidGlassDrawable glass;
        if (existing instanceof LiquidGlassDrawable) {
            glass = (LiquidGlassDrawable) existing;
        } else {
            glass = new LiquidGlassDrawable(LiquidGlassDrawable.Style.HEADER_CIRCLE_GLASS, 0f, true, density);
            view.setBackground(glass);
        }
        glass.configure(
                LiquidGlassDrawable.Style.HEADER_CIRCLE_GLASS,
                0xFFFFFFFF,
                ModulePrefs.getGlassOpacity(),
                ModulePrefs.getRimIntensity(),
                false
        );
    }

    private static void triggerPowerMenu(Context ctx, View fallbackSettingsView) {
        try {
            Class<?> wmgClass = Class.forName("android.view.WindowManagerGlobal");
            Object wms = wmgClass.getMethod("getWindowManagerService").invoke(null);
            if (wms != null) {
                Method m = wms.getClass().getMethod("showGlobalActions");
                m.invoke(wms);
                return;
            }
        } catch (Throwable ignored) {
        }

        try {
            Class<?> smClass = Class.forName("android.os.ServiceManager");
            IBinder binder = (IBinder) smClass.getMethod("getService", String.class).invoke(null, "statusbar");
            if (binder != null) {
                Class<?> stub = Class.forName("com.android.internal.statusbar.IStatusBarService$Stub");
                Object svc = stub.getMethod("asInterface", IBinder.class).invoke(null, binder);
                if (svc != null) {
                    svc.getClass().getMethod("showGlobalActions").invoke(svc);
                    return;
                }
            }
        } catch (Throwable ignored) {
        }

        if (fallbackSettingsView != null) {
            fallbackSettingsView.performClick();
        } else {
            Toast.makeText(ctx, "Power menu unavailable", Toast.LENGTH_SHORT).show();
        }
    }

    private static void hookMediaView(ClassLoader cl) {
        Class<?> cls = XposedHelpers.findClassIfExists(CLS_MEDIA_VIEW, cl);
        if (cls == null) {
            return;
        }

        XC_MethodHook mediaHook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    ModulePrefs.reloadIfChanged();
                    if (!ModulePrefs.isEnabled() || !ModulePrefs.isIosMediaEnabled()) {
                        return;
                    }
                    styleMediaViewInternal((View) param.thisObject);
                } catch (Throwable t) {
                    XposedBridge.log(HookEntry.TAG + ": media hook err: " + t);
                }
            }
        };

        XposedHelpers.findAndHookMethod(cls, "onFinishInflate", mediaHook);
        XposedHelpers.findAndHookMethod(cls, "onConfigurationChanged", Configuration.class, mediaHook);
    }

    public static void styleMediaViewInternal(View mediaView) {
        if (!ModulePrefs.isIosMediaEnabled()) {
            return;
        }
        float density = mediaView.getResources().getDisplayMetrics().density;
        applyDarkGlass(mediaView, 30f * density, 0xFF687898);

        View allContainer = findViewByIdName(mediaView, "media_all_container");
        if (allContainer != null) {
            TransparentNoOpDrawable.apply(allContainer);
        }

        View cover = findViewByIdName(mediaView, "media_cover");
        if (cover != null) {
            float coverRadius = 14f * density;
            cover.setClipToOutline(true);
            cover.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), coverRadius);
                }
            });
            if (cover instanceof ImageView && ((ImageView) cover).getDrawable() == null) {
                LiquidGlassDrawable placeholder = new LiquidGlassDrawable(
                        LiquidGlassDrawable.Style.INACTIVE_CIRCLE_GLASS, coverRadius, false, density);
                placeholder.configure(LiquidGlassDrawable.Style.INACTIVE_CIRCLE_GLASS, 0xFFFFFFFF,
                        30, Math.min(55, ModulePrefs.getRimIntensity()), false);
                cover.setBackground(placeholder);
            }
        }

        View phoneIcon = findViewByIdName(mediaView, "media_phone_icon");
        if (phoneIcon != null) {
            LiquidGlassDrawable badge = new LiquidGlassDrawable(
                    LiquidGlassDrawable.Style.INACTIVE_CIRCLE_GLASS, 0f, true, density);
            badge.configure(LiquidGlassDrawable.Style.INACTIVE_CIRCLE_GLASS, 0xFFFFFFFF,
                    ModulePrefs.getGlassOpacity(), ModulePrefs.getRimIntensity(), false);
            phoneIcon.setBackground(badge);
            int pad = dp(6, density);
            phoneIcon.setPadding(pad, pad, pad, pad);
            if (phoneIcon instanceof ImageView) {
                ((ImageView) phoneIcon).setImageTintList(ColorStateList.valueOf(Color.WHITE));
            }
        }

        View noPlay = findViewByIdName(mediaView, "no_play_text");
        if (noPlay instanceof TextView) {
            TextView tv = (TextView) noPlay;
            tv.setGravity(Gravity.START | Gravity.TOP);
            tv.setPadding(dp(12, density), dp(68, density), dp(12, density), 0);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
            tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            tv.setTextColor(0xF2FFFFFF);
        }
        View title = findViewByIdName(mediaView, "media_song_title");
        if (title instanceof TextView) {
            ((TextView) title).setTextColor(0xFFF5F5F7);
        }
        View author = findViewByIdName(mediaView, "media_song_author");
        if (author instanceof TextView) {
            ((TextView) author).setTextColor(0xB3FFFFFF);
        }

        tintImageView(findViewByIdName(mediaView, "prev_icon"), 0xE6FFFFFF);
        tintImageView(findViewByIdName(mediaView, "next_icon"), 0xE6FFFFFF);

        View startIcon = findViewByIdName(mediaView, "start_icon");
        if (startIcon instanceof TextView) {
            ((TextView) startIcon).setCompoundDrawableTintList(ColorStateList.valueOf(Color.WHITE));
        }
    }

    private static void applyDarkGlass(View view, float radiusPx, int accentColor) {
        float density = view.getResources().getDisplayMetrics().density;
        Drawable bg = view.getBackground();
        LiquidGlassDrawable glass;
        if (bg instanceof LiquidGlassDrawable) {
            glass = (LiquidGlassDrawable) bg;
            glass.setCornerRadiusPx(radiusPx);
        } else {
            glass = new LiquidGlassDrawable(LiquidGlassDrawable.Style.DARK_GLASS, radiusPx, false, density);
            view.setBackground(glass);
        }
        glass.configure(
                LiquidGlassDrawable.Style.DARK_GLASS,
                accentColor,
                ModulePrefs.getGlassOpacity(),
                ModulePrefs.getRimIntensity(),
                ModulePrefs.isAmbientGlowEnabled()
        );
    }

    private static void tintImageView(View v, int color) {
        if (v instanceof ImageView) {
            ((ImageView) v).setImageTintList(ColorStateList.valueOf(color));
        }
    }

    static View findViewByIdName(View root, String idName) {
        if (root == null) {
            return null;
        }
        int id = root.getResources().getIdentifier(idName, "id", "com.android.systemui");
        if (id != 0) {
            View found = root.findViewById(id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static int dp(int dp, float density) {
        return Math.round(dp * density);
    }
}
