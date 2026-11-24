package com.ios27.controlcenter.ui;

import java.util.Locale;

/**
 * Maps XOS / AOSP QS tile specs and labels to authentic iOS Control Center accent colors.
 */
public final class TileAccentPalette {

    public static final int IOS_BLUE = 0xFF007AFF;
    public static final int IOS_ORANGE = 0xFFFF9500;
    public static final int IOS_GREEN = 0xFF34C759;
    public static final int IOS_CORAL_RED = 0xFFFF5E57;
    public static final int IOS_RED = 0xFFFF3B30;
    public static final int IOS_INDIGO = 0xFF5E5CE6;
    public static final int IOS_PURPLE = 0xFF7D6BFF;
    public static final int IOS_YELLOW = 0xFFFFCC00;
    public static final int IOS_SKY_BLUE = 0xFF5AC8FA;

    private TileAccentPalette() {
    }

    /**
     * Color used for active icon tint (or active circle fill) on tiles.
     */
    public static int forSpec(String spec, CharSequence label) {
        String key = buildKey(spec, label);
        if (key.contains("airplane") || key.contains("pesawat")) {
            return IOS_ORANGE;
        }
        if (key.contains("rotation") || key.contains("orient") || key.contains("rotasi") || key.contains("potret")) {
            return IOS_CORAL_RED;
        }
        if (key.contains("battery") || key.contains("saver") || key.contains("power") || key.contains("hemat")
                || key.contains("dataconnection") || key.contains("cell") || key.contains("mobiledata")
                || key.contains("hotspot") || key.contains("traffic")) {
            return IOS_GREEN;
        }
        if (key.contains("screenrecord") || key.contains("rekam")) {
            return IOS_RED;
        }
        if (key.contains("flashlight") || key.contains("senter") || key.contains("torch")) {
            return IOS_BLUE;
        }
        if (key.contains("night") || key.contains("dark") || key.contains("gelap") || key.contains("inversion")) {
            return IOS_INDIGO;
        }
        if (key.contains("dnd") || key.contains("zen") || key.contains("jangan ganggu") || key.contains("focus")) {
            return IOS_PURPLE;
        }
        if (key.contains("wifi") || key.contains("bt") || key.contains("bluetooth")
                || key.contains("cast") || key.contains("mirror") || key.contains("nearby")
                || key.contains("airdrop") || key.contains("share")) {
            return IOS_BLUE;
        }
        return IOS_BLUE;
    }

    /**
     * Subtle ambient color refraction tint used inside inactive dark glass tiles.
     */
    public static int ambientGlowForSpec(String spec, CharSequence label) {
        String key = buildKey(spec, label);
        if (key.contains("battery") || key.contains("saver") || key.contains("power") || key.contains("hemat")) {
            return IOS_GREEN;
        }
        if (key.contains("flashlight") || key.contains("senter") || key.contains("torch")) {
            return 0xFF4A90E2;
        }
        if (key.contains("cast") || key.contains("mirror") || key.contains("screen") || key.contains("proyeksi")) {
            return IOS_PURPLE;
        }
        if (key.contains("rotation") || key.contains("orient") || key.contains("rotasi")) {
            return IOS_CORAL_RED;
        }
        if (key.contains("night") || key.contains("dark") || key.contains("gelap")) {
            return 0xFF7B8596;
        }
        if (key.contains("wifi") || key.contains("bt") || key.contains("bluetooth")) {
            return IOS_SKY_BLUE;
        }
        return 0xFF8AA4C8;
    }

    private static String buildKey(String spec, CharSequence label) {
        String s = spec != null ? spec.toLowerCase(Locale.ROOT) : "";
        String l = label != null ? label.toString().toLowerCase(Locale.ROOT) : "";
        return s + " " + l;
    }
}

