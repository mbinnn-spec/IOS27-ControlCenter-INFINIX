package com.ios27.controlcenter.prefs;

import android.os.SystemClock;

import de.robv.android.xposed.XSharedPreferences;

/**
 * Central preference constants and safe XSharedPreferences reader for SystemUI hooks.
 */
public final class ModulePrefs {

    public static final String PACKAGE_NAME = "com.ios27.controlcenter";
    public static final String PREFS_NAME = "ios27_cc_prefs";

    public static final int LAYOUT_HYBRID = 0;
    public static final int LAYOUT_PILLS = 1;
    public static final int LAYOUT_CIRCLES = 2;

    public static final String KEY_ENABLED = "enabled";
    public static final String KEY_LAYOUT_MODE = "layout_mode";
    public static final String KEY_GLASS_OPACITY = "glass_opacity";
    public static final String KEY_RIM_INTENSITY = "rim_intensity";
    public static final String KEY_AMBIENT_GLOW = "ambient_glow";
    public static final String KEY_BG_DIM_ALPHA = "bg_dim_alpha";
    public static final String KEY_IOS_HEADER = "ios_header";
    public static final String KEY_IOS_SLIDERS = "ios_sliders";
    public static final String KEY_IOS_MEDIA = "ios_media";
    public static final String KEY_SHOW_STATUS_SUBTITLE = "show_status_subtitle";
    public static final String KEY_HIDE_IOT_CARD = "hide_iot_card";
    public static final String KEY_IOS_ANIMATIONS = "ios_animations";

    private static volatile XSharedPreferences sPrefs;
    private static volatile long sLastCheckMs = 0L;

    private ModulePrefs() {
    }

    public static void init() {
        try {
            XSharedPreferences prefs = new XSharedPreferences(PACKAGE_NAME, PREFS_NAME);
            prefs.makeWorldReadable();
            prefs.reload();
            sPrefs = prefs;
        } catch (Throwable ignored) {
            sPrefs = null;
        }
    }

    public static void reloadIfChanged() {
        long now = SystemClock.uptimeMillis();
        if (now - sLastCheckMs < 500L) {
            return;
        }
        sLastCheckMs = now;
        try {
            XSharedPreferences prefs = sPrefs;
            if (prefs == null) {
                init();
                return;
            }
            if (prefs.hasFileChanged()) {
                prefs.reload();
            }
        } catch (Throwable ignored) {
        }
    }

    public static boolean isEnabled() {
        return getBoolean(KEY_ENABLED, true);
    }

    public static int getLayoutMode() {
        return getInt(KEY_LAYOUT_MODE, LAYOUT_HYBRID);
    }

    public static int getGlassOpacity() {
        return clamp(getInt(KEY_GLASS_OPACITY, 46), 10, 95);
    }

    public static int getRimIntensity() {
        return clamp(getInt(KEY_RIM_INTENSITY, 85), 0, 100);
    }

    public static boolean isAmbientGlowEnabled() {
        return getBoolean(KEY_AMBIENT_GLOW, true);
    }

    public static int getBgDimAlpha() {
        return clamp(getInt(KEY_BG_DIM_ALPHA, 18), 0, 80);
    }

    public static boolean isIosHeaderEnabled() {
        return getBoolean(KEY_IOS_HEADER, true);
    }

    public static boolean isIosSlidersEnabled() {
        return getBoolean(KEY_IOS_SLIDERS, true);
    }

    public static boolean isIosMediaEnabled() {
        return getBoolean(KEY_IOS_MEDIA, true);
    }

    public static boolean isShowStatusSubtitle() {
        return getBoolean(KEY_SHOW_STATUS_SUBTITLE, true);
    }

    public static boolean isHideIotCard() {
        return getBoolean(KEY_HIDE_IOT_CARD, true);
    }

    public static boolean isIosAnimationsEnabled() {
        return getBoolean(KEY_IOS_ANIMATIONS, true);
    }

    private static boolean getBoolean(String key, boolean def) {
        try {
            XSharedPreferences prefs = sPrefs;
            return prefs != null ? prefs.getBoolean(key, def) : def;
        } catch (Throwable ignored) {
            return def;
        }
    }

    private static int getInt(String key, int def) {
        try {
            XSharedPreferences prefs = sPrefs;
            return prefs != null ? prefs.getInt(key, def) : def;
        } catch (Throwable ignored) {
            return def;
        }
    }

    private static int clamp(int val, int min, int max) {
        return Math.max(min, Math.min(max, val));
    }
}

