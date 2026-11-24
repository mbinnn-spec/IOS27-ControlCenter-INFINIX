package com.ios27.controlcenter.hooks;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.ios27.controlcenter.HookEntry;
import com.ios27.controlcenter.prefs.ModulePrefs;
import com.ios27.controlcenter.ui.LiquidGlassDrawable;
import com.ios27.controlcenter.ui.TileAccentPalette;
import com.ios27.controlcenter.ui.TransparentNoOpDrawable;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Transforms XOS 15 QS tiles into iOS 26/27 Liquid Glass circles and capsules,
 * and overrides BOTH TrQuickQSPanel$QQSSideLabelTileLayout (collapsed) and
 * TrQSPanel$QSSideLabelTileLayout (expanded) for responsive Hybrid / Pills / Circles layouts.
 */
public final class TileStyleHook {

    private static final String CLS_TILE_BASE = "com.transsion.systemui.qs.tileimpl.TsQSTileBaseView";
    private static final String CLS_TILE_VIEW = "com.transsion.systemui.qs.tileimpl.TsQSTileView";
    private static final String CLS_BIG_RESIDENT_TILE = "com.transsion.systemui.qs.tileimpl.TsBigResidentQSTileView";
    private static final String CLS_QS_ICON_VIEW = "com.android.systemui.qs.tileimpl.TrQSIconViewImpl";
    private static final String CLS_QS_TILE_LAYOUT = "com.transsion.systemui.controlcenter.qscontrol.TrQSPanel$QSSideLabelTileLayout";
    private static final String CLS_QQS_TILE_LAYOUT = "com.transsion.systemui.controlcenter.qscontrol.TrQuickQSPanel$QQSSideLabelTileLayout";
    private static final String CLS_TR_TILE_LAYOUT = "com.android.systemui.qs.TrTileLayout";

    private static final int KEY_IOS_SHAPE = 0x7F0F2701;
    private static final int SHAPE_HIDDEN = 0;
    private static final int SHAPE_CIRCLE_COMPACT = 1;
    private static final int SHAPE_PILL = 2;
    private static final int SHAPE_CIRCLE_LABELED = 3;

    private static final int COLOR_TEXT_DARK = 0xFF111114;
    private static final int COLOR_SUBTEXT_DARK = 0xFF6E6E73;
    private static final int COLOR_TEXT_LIGHT = 0xFFF5F5F7;
    private static final int COLOR_SUBTEXT_LIGHT = 0xB8FFFFFF;

    private TileStyleHook() {
    }

    public static void install(ClassLoader cl) {
        hookTileStates(cl);
        hookIconTinting(cl);
        hookTileGrids(cl);
    }

    private static void hookTileStates(ClassLoader cl) {
        Class<?> stateCls = XposedHelpers.findClassIfExists("com.android.systemui.plugins.qs.QSTile$State", cl);
        if (stateCls == null) {
            return;
        }

        Class<?> baseCls = XposedHelpers.findClassIfExists(CLS_TILE_BASE, cl);
        if (baseCls != null) {
            XposedHelpers.findAndHookMethod(baseCls, "handleStateChanged", stateCls, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        View self = (View) param.thisObject;
                        if (isCustomizeTile(self)) {
                            return;
                        }
                        FrameLayout iconFrame = (FrameLayout) XposedHelpers.getObjectField(self, "mIconFrame");
                        if (iconFrame != null && iconFrame.getBackground() == null) {
                            TransparentNoOpDrawable.apply(iconFrame);
                        }
                    } catch (Throwable ignored) {
                    }
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        Object self = param.thisObject;
                        if (CLS_TILE_BASE.equals(self.getClass().getName())) {
                            applyTileStyle((View) self);
                        }
                    } catch (Throwable t) {
                        XposedBridge.log(HookEntry.TAG + ": base tile hook err: " + t);
                    }
                }
            });
            XposedHelpers.findAndHookMethod(baseCls, "onConfigurationChanged",
                    Configuration.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        applyTileStyle((View) param.thisObject);
                    } catch (Throwable ignored) {
                    }
                }
            });
        }

        Class<?> tileCls = XposedHelpers.findClassIfExists(CLS_TILE_VIEW, cl);
        if (tileCls != null) {
            XposedHelpers.findAndHookMethod(tileCls, "handleStateChanged", stateCls, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        Object self = param.thisObject;
                        if (CLS_TILE_VIEW.equals(self.getClass().getName())) {
                            applyTileStyle((View) self);
                        }
                    } catch (Throwable t) {
                        XposedBridge.log(HookEntry.TAG + ": tile view hook err: " + t);
                    }
                }
            });
        }

        Class<?> bigCls = XposedHelpers.findClassIfExists(CLS_BIG_RESIDENT_TILE, cl);
        if (bigCls != null) {
            XposedHelpers.findAndHookMethod(bigCls, "handleStateChanged", stateCls, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        applyTileStyle((View) param.thisObject);
                    } catch (Throwable t) {
                        XposedBridge.log(HookEntry.TAG + ": big resident hook err: " + t);
                    }
                }
            });
        }
    }

    private static void hookIconTinting(ClassLoader cl) {
        Class<?> iconCls = XposedHelpers.findClassIfExists(CLS_QS_ICON_VIEW, cl);
        Class<?> stateCls = XposedHelpers.findClassIfExists("com.android.systemui.plugins.qs.QSTile$State", cl);
        if (iconCls == null || stateCls == null) {
            return;
        }
        XposedHelpers.findAndHookMethod(
                iconCls,
                "updateIcon",
                ImageView.class,
                ImageView.class,
                stateCls,
                boolean.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            if (!ModulePrefs.isEnabled()) {
                                return;
                            }
                            View iconView = (View) param.thisObject;
                            View tile = findParentTile(iconView);
                            if (tile != null && !isCustomizeTile(tile)) {
                                applyIconTintOnly(tile);
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                }
        );
    }

    private static void hookTileGrids(ClassLoader cl) {
        Class<?> qsLayoutCls = XposedHelpers.findClassIfExists(CLS_QS_TILE_LAYOUT, cl);
        Class<?> qqsLayoutCls = XposedHelpers.findClassIfExists(CLS_QQS_TILE_LAYOUT, cl);
        Class<?> trTileLayoutCls = XposedHelpers.findClassIfExists(CLS_TR_TILE_LAYOUT, cl);
        if (trTileLayoutCls == null) {
            return;
        }

        XC_MethodHook measureHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    ModulePrefs.reloadIfChanged();
                    if (!ModulePrefs.isEnabled()) {
                        return;
                    }
                    ViewGroup grid = (ViewGroup) param.thisObject;
                    boolean isQuickPanel = CLS_QQS_TILE_LAYOUT.equals(grid.getClass().getName());
                    int width = View.MeasureSpec.getSize((int) param.args[0]);
                    if (width <= 0) {
                        return;
                    }

                    int mode = ModulePrefs.getLayoutMode();
                    GridMetrics m = GridMetrics.compute(grid.getContext(), width, mode, isQuickPanel);
                    int maxTiles = maxTilesFor(mode, isQuickPanel);

                    int visibleIndex = 0;
                    int maxBottom = 0;
                    for (int i = 0; i < grid.getChildCount(); i++) {
                        View child = grid.getChildAt(i);
                        if (child.getVisibility() == View.GONE) {
                            continue;
                        }
                        if (visibleIndex >= maxTiles) {
                            child.setTag(KEY_IOS_SHAPE, SHAPE_HIDDEN);
                            child.setVisibility(View.INVISIBLE);
                            child.setAlpha(0f);
                            child.setScaleX(0f);
                            child.setScaleY(0f);
                            child.setClickable(false);
                            child.measure(
                                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.EXACTLY),
                                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.EXACTLY));
                            visibleIndex++;
                            continue;
                        }

                        child.setVisibility(View.VISIBLE);
                        child.setAlpha(1f);
                        child.setScaleX(1f);
                        child.setScaleY(1f);
                        child.setClickable(true);
                        CellRect rect = m.cellFor(visibleIndex, mode, isQuickPanel);
                        int shape = shapeForCell(visibleIndex, mode, isQuickPanel);
                        child.setTag(KEY_IOS_SHAPE, shape);
                        if (CLS_TILE_VIEW.equals(child.getClass().getName())) {
                            applyTileStyle(child);
                        }

                        child.measure(
                                View.MeasureSpec.makeMeasureSpec(rect.width, View.MeasureSpec.EXACTLY),
                                View.MeasureSpec.makeMeasureSpec(rect.height, View.MeasureSpec.EXACTLY));

                        if (rect.top + rect.height > maxBottom) {
                            maxBottom = rect.top + rect.height;
                        }
                        visibleIndex++;
                    }

                    int totalHeight = maxBottom + m.bottomPad;
                    XposedHelpers.callMethod(grid, "setMeasuredDimension", width, totalHeight);
                    param.setResult(null);
                } catch (Throwable t) {
                    XposedBridge.log(HookEntry.TAG + ": grid onMeasure err: " + t);
                }
            }
        };

        if (qsLayoutCls != null) {
            XposedHelpers.findAndHookMethod(qsLayoutCls, "onMeasure", int.class, int.class, measureHook);
        }
        if (qqsLayoutCls != null) {
            XposedHelpers.findAndHookMethod(qqsLayoutCls, "onMeasure", int.class, int.class, measureHook);
        }

        XposedHelpers.findAndHookMethod(
                trTileLayoutCls,
                "onLayout",
                boolean.class,
                int.class,
                int.class,
                int.class,
                int.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        try {
                            if (!ModulePrefs.isEnabled()) {
                                return;
                            }
                            String clsName = param.thisObject.getClass().getName();
                            boolean isQs = CLS_QS_TILE_LAYOUT.equals(clsName);
                            boolean isQqs = CLS_QQS_TILE_LAYOUT.equals(clsName);
                            if (!isQs && !isQqs) {
                                return;
                            }

                            ViewGroup grid = (ViewGroup) param.thisObject;
                            int l = (int) param.args[1];
                            int r = (int) param.args[3];
                            int width = r - l;
                            if (width <= 0) {
                                width = grid.getMeasuredWidth();
                            }
                            if (width <= 0) {
                                return;
                            }

                            int mode = ModulePrefs.getLayoutMode();
                            GridMetrics m = GridMetrics.compute(grid.getContext(), width, mode, isQqs);
                            int maxTiles = maxTilesFor(mode, isQqs);

                            int visibleIndex = 0;
                            for (int i = 0; i < grid.getChildCount(); i++) {
                                View child = grid.getChildAt(i);
                                if (child.getVisibility() == View.GONE) {
                                    continue;
                                }
                                if (visibleIndex >= maxTiles) {
                                    child.layout(0, 0, 0, 0);
                                    visibleIndex++;
                                    continue;
                                }
                                CellRect rect = m.cellFor(visibleIndex, mode, isQqs);
                                child.layout(rect.left, rect.top, rect.left + rect.width, rect.top + rect.height);
                                visibleIndex++;
                            }
                            param.setResult(null);
                        } catch (Throwable t) {
                            XposedBridge.log(HookEntry.TAG + ": grid onLayout err: " + t);
                        }
                    }
                }
        );
    }

    private static int maxTilesFor(int mode, boolean isQuickPanel) {
        if (!isQuickPanel) {
            return Integer.MAX_VALUE;
        }
        if (mode == ModulePrefs.LAYOUT_HYBRID) {
            return 7;
        }
        if (mode == ModulePrefs.LAYOUT_PILLS) {
            return 6;
        }
        return 12;
    }

    private static int shapeForCell(int visibleIndex, int mode, boolean isQuickPanel) {
        if (mode == ModulePrefs.LAYOUT_CIRCLES) {
            return isQuickPanel ? SHAPE_CIRCLE_COMPACT : SHAPE_CIRCLE_LABELED;
        }
        if (mode == ModulePrefs.LAYOUT_HYBRID && visibleIndex < 2) {
            return SHAPE_CIRCLE_COMPACT;
        }
        return SHAPE_PILL;
    }

    public static void applyTileStyle(View tile) {
        if (!ModulePrefs.isEnabled() || tile == null || isCustomizeTile(tile)) {
            return;
        }
        ModulePrefs.reloadIfChanged();

        String clsName = tile.getClass().getName();
        int state = XposedHelpers.getIntField(tile, "mState");
        String spec = (String) XposedHelpers.getObjectField(tile, "mTileSpec");
        boolean isUnavailable = (state == 0);

        if (CLS_TILE_BASE.equals(clsName)) {
            styleFixedConnectivityCircle(tile, spec, state == 2, isUnavailable);
        } else if (CLS_BIG_RESIDENT_TILE.equals(clsName)) {
            boolean tileState = true;
            try {
                tileState = XposedHelpers.getBooleanField(tile, "mTileState");
            } catch (Throwable ignored) {
            }
            boolean isActive = (state == 2) && tileState;
            styleBigResidentPill(tile, spec, isActive, isUnavailable);
        } else if (CLS_TILE_VIEW.equals(clsName)) {
            Object tag = tile.getTag(KEY_IOS_SHAPE);
            if (tag instanceof Integer && (Integer) tag == SHAPE_HIDDEN) {
                tile.setVisibility(View.INVISIBLE);
                tile.setAlpha(0f);
                tile.setScaleX(0f);
                tile.setScaleY(0f);
                return;
            }
            int mode = ModulePrefs.getLayoutMode();
            int shape;
            if (tag instanceof Integer) {
                shape = (Integer) tag;
            } else if (mode == ModulePrefs.LAYOUT_CIRCLES) {
                shape = SHAPE_CIRCLE_COMPACT;
            } else {
                shape = SHAPE_PILL;
            }

            boolean isActive = (state == 2);
            if (shape == SHAPE_PILL) {
                styleExpandedPill((LinearLayout) tile, spec, isActive, isUnavailable);
            } else if (shape == SHAPE_CIRCLE_COMPACT) {
                styleExpandedCompactCircle((LinearLayout) tile, spec, isActive, isUnavailable);
            } else {
                styleExpandedLabeledCircle((LinearLayout) tile, spec, isActive, isUnavailable);
            }
        }
    }

    private static void styleFixedConnectivityCircle(View tile, String spec, boolean isActive, boolean isUnavailable) {
        FrameLayout iconFrame = (FrameLayout) XposedHelpers.getObjectField(tile, "mIconFrame");
        if (iconFrame == null) {
            return;
        }
        float density = tile.getResources().getDisplayMetrics().density;
        int accent = TileAccentPalette.forSpec(spec, null);
        LiquidGlassDrawable.Style style = isActive
                ? LiquidGlassDrawable.Style.ACTIVE_COLOR_CIRCLE
                : LiquidGlassDrawable.Style.INACTIVE_CIRCLE_GLASS;

        centerIconInsideFrame(iconFrame);
        applyGlassBackground(iconFrame, style, accent, true, 0f, density, false);
        tintTileIcons(tile, isUnavailable ? 0x73FFFFFF : Color.WHITE);
    }

    private static void styleBigResidentPill(View tile, String spec, boolean isActive, boolean isUnavailable) {
        float density = tile.getResources().getDisplayMetrics().density;
        TextView label = (TextView) XposedHelpers.getObjectField(tile, "mLabel");
        TextView secondLabel = (TextView) XposedHelpers.getObjectField(tile, "mSecondLabel");
        FrameLayout iconFrame = (FrameLayout) XposedHelpers.getObjectField(tile, "mIconFrame");

        CharSequence labelCs = label != null ? label.getText() : null;
        int accent = TileAccentPalette.forSpec(spec, labelCs);
        int ambient = TileAccentPalette.ambientGlowForSpec(spec, labelCs);

        if (tile instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) tile;
            for (int i = 0; i < vg.getChildCount(); i++) {
                TransparentNoOpDrawable.apply(vg.getChildAt(i));
            }
        }
        if (iconFrame != null) {
            TransparentNoOpDrawable.apply(iconFrame);
            centerIconInsideFrame(iconFrame);
        }

        LiquidGlassDrawable.Style style = isActive
                ? LiquidGlassDrawable.Style.ACTIVE_WHITE_PILL
                : LiquidGlassDrawable.Style.DARK_GLASS;

        applyGlassBackground(
                tile,
                style,
                isActive ? accent : ambient,
                true,
                0f,
                density,
                !isActive && ModulePrefs.isAmbientGlowEnabled()
        );

        int iconColor;
        if (isUnavailable) {
            iconColor = 0x73FFFFFF;
        } else if (isActive) {
            iconColor = accent;
        } else {
            iconColor = (ambient == TileAccentPalette.IOS_GREEN) ? 0xFF34C759 : Color.WHITE;
        }
        tintTileIcons(tile, iconColor);

        configurePillLabels(label, secondLabel, isActive, isUnavailable, false);
    }

    private static void styleExpandedPill(LinearLayout tile, String spec, boolean isActive, boolean isUnavailable) {
        float density = tile.getResources().getDisplayMetrics().density;
        FrameLayout iconFrame = (FrameLayout) XposedHelpers.getObjectField(tile, "mIconFrame");
        View labelContainer = (View) XposedHelpers.getObjectField(tile, "mLabelContainer");
        TextView label = (TextView) XposedHelpers.getObjectField(tile, "mLabel");
        TextView secondLabel = (TextView) XposedHelpers.getObjectField(tile, "mSecondLabel");

        CharSequence labelCs = label != null ? label.getText() : null;
        int accent = TileAccentPalette.forSpec(spec, labelCs);
        int ambient = TileAccentPalette.ambientGlowForSpec(spec, labelCs);

        tile.setOrientation(LinearLayout.HORIZONTAL);
        tile.setGravity(Gravity.CENTER_VERTICAL);
        tile.setPadding(dp(14, density), 0, dp(14, density), 0);

        if (iconFrame != null) {
            TransparentNoOpDrawable.apply(iconFrame);
            iconFrame.setPadding(0, 0, 0, 0);
            int iconBox = dp(34, density);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) iconFrame.getLayoutParams();
            if (lp == null || lp.width != iconBox || lp.height != iconBox || lp.gravity != Gravity.CENTER_VERTICAL) {
                LinearLayout.LayoutParams newLp = new LinearLayout.LayoutParams(iconBox, iconBox);
                newLp.gravity = Gravity.CENTER_VERTICAL;
                iconFrame.setLayoutParams(newLp);
            }
            centerIconInsideFrame(iconFrame);
        }

        if (labelContainer != null) {
            labelContainer.setVisibility(View.VISIBLE);
            labelContainer.setPadding(0, 0, 0, 0);
            labelContainer.setMinimumHeight(0);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) labelContainer.getLayoutParams();
            if (lp == null || lp.width != 0 || lp.weight != 1f || lp.getMarginStart() != dp(10, density)) {
                LinearLayout.LayoutParams newLp = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                newLp.setMarginStart(dp(10, density));
                newLp.gravity = Gravity.CENTER_VERTICAL;
                labelContainer.setLayoutParams(newLp);
            }
        }

        if (label != null && secondLabel != null) {
            layoutLabelsForPill(label, secondLabel, density);
        }

        LiquidGlassDrawable.Style style = isActive
                ? LiquidGlassDrawable.Style.ACTIVE_WHITE_PILL
                : LiquidGlassDrawable.Style.DARK_GLASS;

        applyGlassBackground(
                tile,
                style,
                isActive ? accent : ambient,
                true,
                0f,
                density,
                !isActive && ModulePrefs.isAmbientGlowEnabled()
        );

        int iconColor = isUnavailable ? 0x73FFFFFF : (isActive ? accent : Color.WHITE);
        tintTileIcons(tile, iconColor);
        configurePillLabels(label, secondLabel, isActive, isUnavailable, true);
    }

    private static void styleExpandedCompactCircle(LinearLayout tile, String spec, boolean isActive, boolean isUnavailable) {
        float density = tile.getResources().getDisplayMetrics().density;
        FrameLayout iconFrame = (FrameLayout) XposedHelpers.getObjectField(tile, "mIconFrame");
        View labelContainer = (View) XposedHelpers.getObjectField(tile, "mLabelContainer");

        TextView label = null;
        try {
            label = (TextView) XposedHelpers.getObjectField(tile, "mLabel");
        } catch (Throwable ignored) {
        }
        CharSequence labelCs = label != null ? label.getText() : null;
        int accent = TileAccentPalette.forSpec(spec, labelCs);
        int ambient = TileAccentPalette.ambientGlowForSpec(spec, labelCs);

        TransparentNoOpDrawable.apply(tile);
        tile.setPadding(0, 0, 0, 0);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);

        if (labelContainer != null) {
            labelContainer.setVisibility(View.GONE);
        }

        if (iconFrame != null) {
            iconFrame.setPadding(0, 0, 0, 0);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) iconFrame.getLayoutParams();
            if (lp == null || lp.width != ViewGroup.LayoutParams.MATCH_PARENT
                    || lp.height != ViewGroup.LayoutParams.MATCH_PARENT) {
                LinearLayout.LayoutParams newLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
                iconFrame.setLayoutParams(newLp);
            }
            centerIconInsideFrame(iconFrame);
            LiquidGlassDrawable.Style style = isActive
                    ? LiquidGlassDrawable.Style.ACTIVE_WHITE_PILL
                    : LiquidGlassDrawable.Style.DARK_GLASS;
            applyGlassBackground(
                    iconFrame,
                    style,
                    isActive ? accent : ambient,
                    true,
                    0f,
                    density,
                    !isActive && ModulePrefs.isAmbientGlowEnabled()
            );
        }

        int iconColor = isUnavailable ? 0x73FFFFFF : (isActive ? accent : Color.WHITE);
        tintTileIcons(tile, iconColor);
    }

    private static void styleExpandedLabeledCircle(LinearLayout tile, String spec, boolean isActive, boolean isUnavailable) {
        float density = tile.getResources().getDisplayMetrics().density;
        FrameLayout iconFrame = (FrameLayout) XposedHelpers.getObjectField(tile, "mIconFrame");
        View labelContainer = (View) XposedHelpers.getObjectField(tile, "mLabelContainer");
        TextView label = (TextView) XposedHelpers.getObjectField(tile, "mLabel");
        TextView secondLabel = (TextView) XposedHelpers.getObjectField(tile, "mSecondLabel");

        CharSequence labelCs = label != null ? label.getText() : null;
        int accent = TileAccentPalette.forSpec(spec, labelCs);
        int ambient = TileAccentPalette.ambientGlowForSpec(spec, labelCs);

        TransparentNoOpDrawable.apply(tile);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER_HORIZONTAL);
        tile.setPadding(0, 0, 0, 0);

        if (iconFrame != null) {
            iconFrame.setPadding(0, 0, 0, 0);
            int circleSize = dp(60, density);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) iconFrame.getLayoutParams();
            if (lp == null || lp.width != circleSize || lp.height != circleSize) {
                LinearLayout.LayoutParams newLp = new LinearLayout.LayoutParams(circleSize, circleSize);
                newLp.gravity = Gravity.CENTER_HORIZONTAL;
                iconFrame.setLayoutParams(newLp);
            }
            centerIconInsideFrame(iconFrame);
            LiquidGlassDrawable.Style style = isActive
                    ? LiquidGlassDrawable.Style.ACTIVE_WHITE_PILL
                    : LiquidGlassDrawable.Style.DARK_GLASS;
            applyGlassBackground(
                    iconFrame,
                    style,
                    isActive ? accent : ambient,
                    true,
                    0f,
                    density,
                    !isActive && ModulePrefs.isAmbientGlowEnabled()
            );
        }

        if (labelContainer != null) {
            labelContainer.setVisibility(View.VISIBLE);
            labelContainer.setPadding(0, 0, 0, 0);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) labelContainer.getLayoutParams();
            if (lp == null || lp.width != ViewGroup.LayoutParams.MATCH_PARENT) {
                LinearLayout.LayoutParams newLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                newLp.topMargin = dp(4, density);
                labelContainer.setLayoutParams(newLp);
            }
        }
        if (label != null) {
            ViewGroup.LayoutParams rawLp = label.getLayoutParams();
            if (rawLp instanceof RelativeLayout.LayoutParams) {
                RelativeLayout.LayoutParams rlp = (RelativeLayout.LayoutParams) rawLp;
                rlp.width = ViewGroup.LayoutParams.MATCH_PARENT;
                rlp.topMargin = 0;
                rlp.setMarginStart(0);
                rlp.setMarginEnd(0);
                label.setLayoutParams(rlp);
            }
            label.setPadding(0, 0, 0, 0);
            label.setGravity(Gravity.CENTER_HORIZONTAL);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            label.setTextColor(isUnavailable ? 0x73FFFFFF : COLOR_TEXT_LIGHT);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
            label.setShadowLayer(0f, 0f, 0f, 0);
        }
        if (secondLabel != null) {
            secondLabel.setVisibility(View.GONE);
        }

        int iconColor = isUnavailable ? 0x73FFFFFF : (isActive ? accent : Color.WHITE);
        tintTileIcons(tile, iconColor);
    }

    private static void centerIconInsideFrame(FrameLayout iconFrame) {
        for (int i = 0; i < iconFrame.getChildCount(); i++) {
            View child = iconFrame.getChildAt(i);
            ViewGroup.LayoutParams lp = child.getLayoutParams();
            if (lp instanceof FrameLayout.LayoutParams) {
                FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) lp;
                if (flp.gravity != Gravity.CENTER
                        || flp.leftMargin != 0 || flp.topMargin != 0
                        || flp.rightMargin != 0 || flp.bottomMargin != 0) {
                    flp.gravity = Gravity.CENTER;
                    flp.setMargins(0, 0, 0, 0);
                    child.setLayoutParams(flp);
                }
            }
        }
    }

    private static void layoutLabelsForPill(TextView label, TextView secondLabel, float density) {
        ViewGroup.LayoutParams rawLabelLp = label.getLayoutParams();
        if (rawLabelLp instanceof RelativeLayout.LayoutParams) {
            RelativeLayout.LayoutParams rlp = (RelativeLayout.LayoutParams) rawLabelLp;
            rlp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            rlp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            rlp.topMargin = 0;
            rlp.bottomMargin = 0;
            rlp.setMarginStart(0);
            rlp.setMarginEnd(0);
            rlp.removeRule(RelativeLayout.CENTER_HORIZONTAL);
            rlp.addRule(RelativeLayout.ALIGN_PARENT_START);
            rlp.addRule(RelativeLayout.ALIGN_PARENT_TOP);
            label.setLayoutParams(rlp);
        }
        label.setPadding(0, 0, 0, 0);
        label.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        label.setSingleLine(false);
        label.setMaxLines(2);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        label.setShadowLayer(0f, 0f, 0f, 0);
        label.setIncludeFontPadding(false);

        ViewGroup.LayoutParams rawSecLp = secondLabel.getLayoutParams();
        if (rawSecLp instanceof RelativeLayout.LayoutParams) {
            RelativeLayout.LayoutParams slp = (RelativeLayout.LayoutParams) rawSecLp;
            slp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            slp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            slp.topMargin = dp(2, density);
            slp.bottomMargin = 0;
            slp.setMarginStart(0);
            slp.setMarginEnd(0);
            slp.removeRule(RelativeLayout.CENTER_HORIZONTAL);
            slp.addRule(RelativeLayout.ALIGN_PARENT_START);
            if (label.getId() != View.NO_ID) {
                slp.addRule(RelativeLayout.BELOW, label.getId());
            }
            secondLabel.setLayoutParams(slp);
        }
        secondLabel.setPadding(0, 0, 0, 0);
        secondLabel.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        secondLabel.setSingleLine(true);
        secondLabel.setEllipsize(TextUtils.TruncateAt.END);
        secondLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        secondLabel.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        secondLabel.setShadowLayer(0f, 0f, 0f, 0);
        secondLabel.setIncludeFontPadding(false);
    }

    private static void configurePillLabels(
            TextView label,
            TextView secondLabel,
            boolean isActive,
            boolean isUnavailable,
            boolean fillFallbackSubtitle
    ) {
        if (label != null) {
            label.setTextColor(isUnavailable
                    ? 0x73FFFFFF
                    : (isActive ? COLOR_TEXT_DARK : COLOR_TEXT_LIGHT));
            label.setShadowLayer(0f, 0f, 0f, 0);
            label.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        }
        if (secondLabel != null) {
            CharSequence existing = secondLabel.getText();
            if (fillFallbackSubtitle && TextUtils.isEmpty(existing) && ModulePrefs.isShowStatusSubtitle()) {
                secondLabel.setText(isActive ? "On" : "Off");
            }
            boolean hasText = !TextUtils.isEmpty(secondLabel.getText());
            secondLabel.setVisibility(hasText ? View.VISIBLE : View.GONE);
            secondLabel.setTextColor(isUnavailable
                    ? 0x55FFFFFF
                    : (isActive ? COLOR_SUBTEXT_DARK : COLOR_SUBTEXT_LIGHT));
            secondLabel.setShadowLayer(0f, 0f, 0f, 0);
            secondLabel.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        }
    }

    private static void applyIconTintOnly(View tile) {
        String clsName = tile.getClass().getName();
        int state = XposedHelpers.getIntField(tile, "mState");
        String spec = (String) XposedHelpers.getObjectField(tile, "mTileSpec");
        boolean isUnavailable = (state == 0);

        if (CLS_TILE_BASE.equals(clsName)) {
            tintTileIcons(tile, isUnavailable ? 0x73FFFFFF : Color.WHITE);
            return;
        }

        CharSequence labelCs = null;
        try {
            TextView label = (TextView) XposedHelpers.getObjectField(tile, "mLabel");
            if (label != null) {
                labelCs = label.getText();
            }
        } catch (Throwable ignored) {
        }

        boolean isActive = (state == 2);
        if (CLS_BIG_RESIDENT_TILE.equals(clsName)) {
            try {
                isActive = isActive && XposedHelpers.getBooleanField(tile, "mTileState");
            } catch (Throwable ignored) {
            }
        }

        int accent = TileAccentPalette.forSpec(spec, labelCs);
        int ambient = TileAccentPalette.ambientGlowForSpec(spec, labelCs);
        int iconColor;
        if (isUnavailable) {
            iconColor = 0x73FFFFFF;
        } else if (isActive) {
            iconColor = accent;
        } else if (CLS_BIG_RESIDENT_TILE.equals(clsName) && ambient == TileAccentPalette.IOS_GREEN) {
            iconColor = 0xFF34C759;
        } else {
            iconColor = Color.WHITE;
        }
        tintTileIcons(tile, iconColor);
    }

    private static void tintTileIcons(View tile, int color) {
        try {
            Object qsIcon = XposedHelpers.getObjectField(tile, "mIcon");
            if (qsIcon instanceof ViewGroup) {
                ViewGroup vg = (ViewGroup) qsIcon;
                for (int i = 0; i < vg.getChildCount(); i++) {
                    View child = vg.getChildAt(i);
                    if (child instanceof ImageView) {
                        ImageView iv = (ImageView) child;
                        iv.setAlpha(1f);
                        iv.setImageTintList(ColorStateList.valueOf(color));
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void applyGlassBackground(
            View view,
            LiquidGlassDrawable.Style style,
            int accentColor,
            boolean fullCapsule,
            float cornerRadiusPx,
            float density,
            boolean ambientGlow
    ) {
        Drawable current = view.getBackground();
        LiquidGlassDrawable glass;
        if (current instanceof LiquidGlassDrawable) {
            glass = (LiquidGlassDrawable) current;
            glass.setFullCapsule(fullCapsule);
            glass.setCornerRadiusPx(cornerRadiusPx);
        } else {
            glass = new LiquidGlassDrawable(style, cornerRadiusPx, fullCapsule, density);
            view.setBackground(glass);
        }
        glass.configure(
                style,
                accentColor,
                ModulePrefs.getGlassOpacity(),
                ModulePrefs.getRimIntensity(),
                ambientGlow
        );
    }

    private static View findParentTile(View v) {
        View cur = v;
        for (int depth = 0; depth < 6 && cur != null; depth++) {
            String name = cur.getClass().getName();
            if (CLS_TILE_BASE.equals(name) || CLS_TILE_VIEW.equals(name) || CLS_BIG_RESIDENT_TILE.equals(name)) {
                return cur;
            }
            if (cur.getParent() instanceof View) {
                cur = (View) cur.getParent();
            } else {
                break;
            }
        }
        return null;
    }

    private static boolean isCustomizeTile(View v) {
        return v.getClass().getName().contains("Customize");
    }

    private static int dp(int dp, float density) {
        return Math.round(dp * density);
    }

    private static final class CellRect {
        final int left;
        final int top;
        final int width;
        final int height;

        CellRect(int left, int top, int width, int height) {
            this.left = left;
            this.top = top;
            this.width = width;
            this.height = height;
        }
    }

    private static final class GridMetrics {
        final int marginH;
        final int topPad;
        final int bottomPad;
        final int gap;
        final int pillW;
        final int circleSize;
        final int rowH;

        private GridMetrics(int marginH, int topPad, int bottomPad, int gap, int pillW, int circleSize, int rowH) {
            this.marginH = marginH;
            this.topPad = topPad;
            this.bottomPad = bottomPad;
            this.gap = gap;
            this.pillW = pillW;
            this.circleSize = circleSize;
            this.rowH = rowH;
        }

        static GridMetrics compute(Context ctx, int width, int mode, boolean isQuickPanel) {
            float d = ctx.getResources().getDisplayMetrics().density;
            int marginH = Math.round(width * 0.023f);
            int gap = marginH * 2;
            int usableW = Math.max(120, width - 2 * marginH);
            int circleSize = (usableW - 3 * gap) / 4;
            int pillW = circleSize * 2 + gap;
            int rowH = (mode == ModulePrefs.LAYOUT_CIRCLES && !isQuickPanel)
                    ? circleSize + dp(24, d)
                    : circleSize;
            int topPad = isQuickPanel ? dp(6, d) : dp(10, d);
            int bottomPad = isQuickPanel ? dp(8, d) : dp(18, d);
            return new GridMetrics(marginH, topPad, bottomPad, gap, pillW, circleSize, rowH);
        }

        CellRect cellFor(int visibleIndex, int mode, boolean isQuickPanel) {
            if (mode == ModulePrefs.LAYOUT_CIRCLES) {
                int col = visibleIndex % 4;
                int row = visibleIndex / 4;
                int left = marginH + col * (circleSize + gap);
                int top = topPad + row * (rowH + gap);
                return new CellRect(left, top, circleSize, rowH);
            }
            if (mode == ModulePrefs.LAYOUT_HYBRID) {
                if (visibleIndex == 0) {
                    return new CellRect(marginH, topPad, circleSize, rowH);
                }
                if (visibleIndex == 1) {
                    return new CellRect(marginH + circleSize + gap, topPad, circleSize, rowH);
                }
                if (visibleIndex == 2) {
                    return new CellRect(marginH + pillW + gap, topPad, pillW, rowH);
                }
                int shifted = visibleIndex - 3;
                int col = shifted % 2;
                int row = 1 + (shifted / 2);
                int left = marginH + col * (pillW + gap);
                int top = topPad + row * (rowH + gap);
                return new CellRect(left, top, pillW, rowH);
            }
            int col = visibleIndex % 2;
            int row = visibleIndex / 2;
            int left = marginH + col * (pillW + gap);
            int top = topPad + row * (rowH + gap);
            return new CellRect(left, top, pillW, rowH);
        }
    }
}
