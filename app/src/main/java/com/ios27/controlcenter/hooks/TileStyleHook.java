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

import com.ios27.controlcenter.prefs.ModulePrefs;
import com.ios27.controlcenter.ui.LiquidGlassDrawable;
import com.ios27.controlcenter.ui.TileAccentPalette;
import com.ios27.controlcenter.ui.TransparentNoOpDrawable;

import io.github.libxposed.api.XposedInterface;

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
    private static final String CLS_CUSTOM_GRID_LAYOUT = "com.transsion.systemui.controlcenter.widget.CustomGridLayout";
    private static final String CLS_VARIABLE_FIXED_LAYOUT = "com.transsion.systemui.controlcenter.widget.VariableFixedTilesLayout";

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
        hookVariableFixedRow(cl);
    }

    private static void hookTileStates(ClassLoader cl) {
        Class<?> stateCls = HookHelper.findClass("com.android.systemui.plugins.qs.QSTile$State", cl);
        if (stateCls == null) {
            return;
        }

        Class<?> baseCls = HookHelper.findClass(CLS_TILE_BASE, cl);
        if (baseCls != null) {
            HookHelper.hook(baseCls, "handleStateChanged", chain -> {
                try {
                    View self = (View) chain.getThisObject();
                    if (self != null && !isCustomizeTile(self)) {
                        FrameLayout iconFrame = (FrameLayout) HookHelper.getObjectField(self, "mIconFrame");
                        if (iconFrame != null && iconFrame.getBackground() == null) {
                            TransparentNoOpDrawable.apply(iconFrame);
                        }
                    }
                } catch (Throwable ignored) {
                }

                Object res = chain.proceed();

                try {
                    Object self = chain.getThisObject();
                    if (self != null && CLS_TILE_BASE.equals(self.getClass().getName())) {
                        applyTileStyle((View) self);
                    }
                } catch (Throwable t) {
                    HookHelper.log("base tile hook err", t);
                }
                return res;
            }, stateCls);

            HookHelper.hookAfter(baseCls, "onConfigurationChanged", (chain, res) -> {
                applyTileStyle((View) chain.getThisObject());
            }, Configuration.class);
        }

        Class<?> tileCls = HookHelper.findClass(CLS_TILE_VIEW, cl);
        if (tileCls != null) {
            HookHelper.hookAfter(tileCls, "handleStateChanged", (chain, res) -> {
                Object self = chain.getThisObject();
                if (self != null && CLS_TILE_VIEW.equals(self.getClass().getName())) {
                    applyTileStyle((View) self);
                }
            }, stateCls);

            HookHelper.hookAfter(tileCls, "onConfigurationChanged", (chain, res) -> {
                Object self = chain.getThisObject();
                if (self != null && CLS_TILE_VIEW.equals(self.getClass().getName())) {
                    applyTileStyle((View) self);
                }
            }, Configuration.class);
        }

        Class<?> bigCls = HookHelper.findClass(CLS_BIG_RESIDENT_TILE, cl);
        if (bigCls != null) {
            HookHelper.hookAfter(bigCls, "handleStateChanged", (chain, res) -> {
                applyTileStyle((View) chain.getThisObject());
            }, stateCls);

            HookHelper.hookAfter(bigCls, "onConfigurationChanged", (chain, res) -> {
                View self = (View) chain.getThisObject();
                applyTileStyle(self);
                if (self != null && self.getParent() instanceof View) {
                    ((View) self.getParent()).requestLayout();
                }
            }, Configuration.class);
        }
    }

    private static void hookIconTinting(ClassLoader cl) {
        Class<?> iconCls = HookHelper.findClass(CLS_QS_ICON_VIEW, cl);
        Class<?> stateCls = HookHelper.findClass("com.android.systemui.plugins.qs.QSTile$State", cl);
        if (iconCls == null || stateCls == null) {
            return;
        }
        HookHelper.hookAfter(iconCls, "updateIcon", (chain, res) -> {
            if (!ModulePrefs.isEnabled()) {
                return;
            }
            View iconView = (View) chain.getThisObject();
            View tile = findParentTile(iconView);
            if (tile != null && !isCustomizeTile(tile)) {
                applyIconTintOnly(tile);
            }
        }, ImageView.class, ImageView.class, stateCls, boolean.class);
    }

    private static void hookTileGrids(ClassLoader cl) {
        Class<?> qsLayoutCls = HookHelper.findClass(CLS_QS_TILE_LAYOUT, cl);
        Class<?> qqsLayoutCls = HookHelper.findClass(CLS_QQS_TILE_LAYOUT, cl);
        Class<?> trTileLayoutCls = HookHelper.findClass(CLS_TR_TILE_LAYOUT, cl);
        if (trTileLayoutCls == null) {
            return;
        }

        XposedInterface.Hooker measureHooker = chain -> {
            if (!ModulePrefs.isEnabled()) {
                return chain.proceed();
            }
            try {
                ViewGroup grid = (ViewGroup) chain.getThisObject();
                boolean isQuickPanel = CLS_QQS_TILE_LAYOUT.equals(grid.getClass().getName());
                int width = View.MeasureSpec.getSize((int) chain.getArg(0));
                if (width <= 0) {
                    return chain.proceed();
                }

                int mode = ModulePrefs.getLayoutMode();
                GridMetrics m = GridMetrics.compute(grid.getContext(), width, mode, isQuickPanel);
                int maxTiles = m.maxTilesFor(mode, isQuickPanel);

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
                HookHelper.callSetMeasuredDimension(grid, width, totalHeight);
                return null;
            } catch (Throwable t) {
                HookHelper.log("grid onMeasure err", t);
                return chain.proceed();
            }
        };

        if (qsLayoutCls != null) {
            HookHelper.hook(qsLayoutCls, "onMeasure", measureHooker, int.class, int.class);
            HookHelper.hookAfter(qsLayoutCls, "onConfigurationChanged", (chain, res) -> {
                if (ModulePrefs.isEnabled()) {
                    ((View) chain.getThisObject()).requestLayout();
                }
            }, Configuration.class);
        }
        if (qqsLayoutCls != null) {
            HookHelper.hook(qqsLayoutCls, "onMeasure", measureHooker, int.class, int.class);
            HookHelper.hookAfter(qqsLayoutCls, "onConfigurationChanged", (chain, res) -> {
                if (ModulePrefs.isEnabled()) {
                    ((View) chain.getThisObject()).requestLayout();
                }
            }, Configuration.class);
        }

        HookHelper.hook(trTileLayoutCls, "onLayout", chain -> {
            if (!ModulePrefs.isEnabled()) {
                return chain.proceed();
            }
            Object self = chain.getThisObject();
            String clsName = self.getClass().getName();
            boolean isQs = CLS_QS_TILE_LAYOUT.equals(clsName);
            boolean isQqs = CLS_QQS_TILE_LAYOUT.equals(clsName);
            if (!isQs && !isQqs) {
                return chain.proceed();
            }
            try {
                ViewGroup grid = (ViewGroup) self;
                int width = (int) chain.getArg(3) - (int) chain.getArg(1);
                return layoutTileGrid(grid, width, isQqs) ? null : chain.proceed();
            } catch (Throwable t) {
                HookHelper.log("grid onLayout err", t);
                return chain.proceed();
            }
        }, boolean.class, int.class, int.class, int.class, int.class);

        // setSquishinessFraction() invokes trLayoutTileRecords() directly without calling onLayout().
        HookHelper.hook(trTileLayoutCls, "trLayoutTileRecords", chain -> {
            if (!ModulePrefs.isEnabled()) {
                return chain.proceed();
            }
            Object self = chain.getThisObject();
            String clsName = self.getClass().getName();
            boolean isQs = CLS_QS_TILE_LAYOUT.equals(clsName);
            boolean isQqs = CLS_QQS_TILE_LAYOUT.equals(clsName);
            if (!isQs && !isQqs) {
                return chain.proceed();
            }
            try {
                ViewGroup grid = (ViewGroup) self;
                int width = grid.getWidth() > 0 ? grid.getWidth() : grid.getMeasuredWidth();
                return layoutTileGrid(grid, width, isQqs) ? null : chain.proceed();
            } catch (Throwable t) {
                HookHelper.log("grid trLayoutTileRecords err", t);
                return chain.proceed();
            }
        }, int.class);
    }

    private static boolean layoutTileGrid(ViewGroup grid, int rawWidth, boolean isQqs) {
        int width = rawWidth > 0 ? rawWidth : grid.getMeasuredWidth();
        if (width <= 0) {
            return false;
        }
        int mode = ModulePrefs.getLayoutMode();
        GridMetrics m = GridMetrics.compute(grid.getContext(), width, mode, isQqs);
        int maxTiles = m.maxTilesFor(mode, isQqs);

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
        return true;
    }

    private static void hookVariableFixedRow(ClassLoader cl) {
        Class<?> customGridCls = HookHelper.findClass(CLS_CUSTOM_GRID_LAYOUT, cl);
        Class<?> varFixedCls = HookHelper.findClass(CLS_VARIABLE_FIXED_LAYOUT, cl);
        if (customGridCls == null) {
            return;
        }

        HookHelper.hook(customGridCls, "onMeasure", chain -> {
            if (!ModulePrefs.isEnabled()) {
                return chain.proceed();
            }
            ViewGroup grid = (ViewGroup) chain.getThisObject();
            if (!CLS_VARIABLE_FIXED_LAYOUT.equals(grid.getClass().getName())) {
                return chain.proceed();
            }
            boolean isLandscape = grid.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
            if (!isLandscape) {
                return chain.proceed();
            }
            try {
                float density = grid.getResources().getDisplayMetrics().density;
                int specWidth = View.MeasureSpec.getSize((int) chain.getArg(0));
                int width = resolveVariableRowWidth(grid, specWidth, density);

                java.util.ArrayList<View> tiles = new java.util.ArrayList<>();
                for (int i = 0; i < grid.getChildCount(); i++) {
                    View child = grid.getChildAt(i);
                    if (child.getVisibility() == View.GONE) {
                        continue;
                    }
                    if (CLS_BIG_RESIDENT_TILE.equals(child.getClass().getName())) {
                        tiles.add(child);
                    } else {
                        child.measure(
                                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.EXACTLY),
                                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.EXACTLY));
                    }
                }

                if (tiles.isEmpty()) {
                    HookHelper.callSetMeasuredDimension(grid, width, 0);
                    return null;
                }

                int count = tiles.size();
                int gap = dp(14, density);
                int pillW = Math.max(dp(120, density), (width - (count - 1) * gap) / count);
                int pillH = dp(64, density);

                for (View tile : tiles) {
                    applyTileStyle(tile);
                    tile.measure(
                            View.MeasureSpec.makeMeasureSpec(pillW, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(pillH, View.MeasureSpec.EXACTLY));
                }

                HookHelper.callSetMeasuredDimension(grid, width, pillH);
                return null;
            } catch (Throwable t) {
                HookHelper.log("var fixed onMeasure err", t);
                return chain.proceed();
            }
        }, int.class, int.class);

        HookHelper.hook(customGridCls, "onLayout", chain -> {
            if (!ModulePrefs.isEnabled()) {
                return chain.proceed();
            }
            ViewGroup grid = (ViewGroup) chain.getThisObject();
            if (!CLS_VARIABLE_FIXED_LAYOUT.equals(grid.getClass().getName())) {
                return chain.proceed();
            }
            boolean isLandscape = grid.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
            if (!isLandscape) {
                return chain.proceed();
            }
            try {
                float density = grid.getResources().getDisplayMetrics().density;
                int width = (int) chain.getArg(3) - (int) chain.getArg(1);
                if (width <= 0) {
                    width = grid.getMeasuredWidth();
                }
                int height = (int) chain.getArg(4) - (int) chain.getArg(2);
                if (height <= 0) {
                    height = grid.getMeasuredHeight();
                }

                java.util.ArrayList<View> tiles = new java.util.ArrayList<>();
                for (int i = 0; i < grid.getChildCount(); i++) {
                    View child = grid.getChildAt(i);
                    if (child.getVisibility() == View.GONE) {
                        continue;
                    }
                    if (CLS_BIG_RESIDENT_TILE.equals(child.getClass().getName())) {
                        tiles.add(child);
                    } else {
                        child.layout(0, 0, 0, 0);
                    }
                }

                if (tiles.isEmpty()) {
                    return null;
                }

                int count = tiles.size();
                int gap = dp(14, density);
                int pillW = (width - (count - 1) * gap) / count;
                int totalUsed = count * pillW + (count - 1) * gap;
                int startX = Math.max(0, (width - totalUsed) / 2);
                boolean isRtl = grid.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;

                for (int i = 0; i < count; i++) {
                    View tile = tiles.get(i);
                    int left = isRtl
                            ? width - startX - (i + 1) * pillW - i * gap
                            : startX + i * (pillW + gap);
                    tile.layout(left, 0, left + pillW, height);
                }
                return null;
            } catch (Throwable t) {
                HookHelper.log("var fixed onLayout err", t);
                return chain.proceed();
            }
        }, boolean.class, int.class, int.class, int.class, int.class);

        if (varFixedCls != null) {
            HookHelper.hookAfter(varFixedCls, "onConfigurationChanged", (chain, res) -> {
                if (!ModulePrefs.isEnabled()) {
                    return;
                }
                ViewGroup grid = (ViewGroup) chain.getThisObject();
                boolean isLandscape = grid.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
                float density = grid.getResources().getDisplayMetrics().density;
                ViewGroup.LayoutParams lp = grid.getLayoutParams();
                if (lp != null && isLandscape) {
                    lp.width = resolveVariableRowWidth(grid, 0, density);
                    lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    grid.setLayoutParams(lp);
                } else if (lp != null && grid.getParent() instanceof View && ((View) grid.getParent()).getParent() instanceof View) {
                    View fixedArea = (View) ((View) grid.getParent()).getParent();
                    View fixedTiles = ControlCenterHook.findViewByIdName(fixedArea, "fixed_tiles_view");
                    ViewGroup.LayoutParams refLp = fixedTiles != null ? fixedTiles.getLayoutParams() : null;
                    if (refLp != null && refLp.width > 0 && refLp.height > 0) {
                        lp.width = refLp.width;
                        lp.height = refLp.height;
                        grid.setLayoutParams(lp);
                    }
                }
                grid.requestLayout();
            }, Configuration.class);
        }
    }

    private static int resolveVariableRowWidth(ViewGroup grid, int specWidth, float density) {
        View parent = grid.getParent() instanceof View ? (View) grid.getParent() : null;
        View fixedArea = parent != null && parent.getParent() instanceof View ? (View) parent.getParent() : null;
        if (fixedArea != null) {
            View row1 = ControlCenterHook.findViewByIdName(fixedArea, "fixed_row1");
            if (row1 != null && row1.getMeasuredWidth() > 0) {
                return row1.getMeasuredWidth();
            }
            if (fixedArea.getMeasuredWidth() > 0) {
                return Math.max(dp(240, density), fixedArea.getMeasuredWidth() - dp(14, density));
            }
        }
        return specWidth > dp(240, density) ? specWidth : dp(442, density);
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

        String clsName = tile.getClass().getName();
        int state = HookHelper.getIntField(tile, "mState");
        String spec = (String) HookHelper.getObjectField(tile, "mTileSpec");
        boolean isUnavailable = (state == 0);

        if (CLS_TILE_BASE.equals(clsName)) {
            styleFixedConnectivityCircle(tile, spec, state == 2, isUnavailable);
        } else if (CLS_BIG_RESIDENT_TILE.equals(clsName)) {
            boolean tileState = HookHelper.getBooleanField(tile, "mTileState", true);
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
            } else {
                shape = inferShapeFromParent(tile, mode);
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
        FrameLayout iconFrame = (FrameLayout) HookHelper.getObjectField(tile, "mIconFrame");
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
        boolean isLandscape = tile.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        TextView label = (TextView) HookHelper.getObjectField(tile, "mLabel");
        TextView secondLabel = (TextView) HookHelper.getObjectField(tile, "mSecondLabel");
        FrameLayout iconFrame = (FrameLayout) HookHelper.getObjectField(tile, "mIconFrame");
        TextView tvTitle = (TextView) HookHelper.getObjectField(tile, "mTvTitle");

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
        if (isLandscape) {
            styleBigResidentLandscapeContent(tile, label, secondLabel, iconFrame, tvTitle, density);
        } else if (tvTitle != null) {
            tvTitle.setTextColor(isUnavailable
                    ? 0x73FFFFFF
                    : (isActive ? COLOR_TEXT_DARK : COLOR_TEXT_LIGHT));
            tvTitle.setShadowLayer(0f, 0f, 0f, 0);
        }
    }

    private static void styleBigResidentLandscapeContent(
            View tile,
            TextView label,
            TextView secondLabel,
            FrameLayout iconFrame,
            TextView tvTitle,
            float density
    ) {
        if (tvTitle != null) {
            tvTitle.setVisibility(View.GONE);
        }
        if (!(tile instanceof LinearLayout)) {
            return;
        }
        LinearLayout root = (LinearLayout) tile;
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setPadding(0, 0, 0, 0);

        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            if (child == tvTitle) {
                continue;
            }
            if (child instanceof LinearLayout) {
                LinearLayout row = (LinearLayout) child;
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(16, density), 0, dp(16, density), 0);
                ViewGroup.LayoutParams rowLp = row.getLayoutParams();
                if (rowLp == null || rowLp.width != ViewGroup.LayoutParams.MATCH_PARENT
                        || rowLp.height != ViewGroup.LayoutParams.MATCH_PARENT) {
                    LinearLayout.LayoutParams newRowLp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
                    newRowLp.gravity = Gravity.CENTER_VERTICAL;
                    row.setLayoutParams(newRowLp);
                }
            }
        }

        if (iconFrame != null) {
            iconFrame.setPadding(0, 0, 0, 0);
            int iconBox = dp(34, density);
            ViewGroup.LayoutParams rawIconLp = iconFrame.getLayoutParams();
            if (rawIconLp instanceof LinearLayout.LayoutParams) {
                LinearLayout.LayoutParams ilp = (LinearLayout.LayoutParams) rawIconLp;
                if (ilp.width != iconBox || ilp.height != iconBox || ilp.gravity != Gravity.CENTER_VERTICAL
                        || ilp.topMargin != 0 || ilp.bottomMargin != 0 || ilp.getMarginStart() != 0) {
                    LinearLayout.LayoutParams newLp = new LinearLayout.LayoutParams(iconBox, iconBox);
                    newLp.gravity = Gravity.CENTER_VERTICAL;
                    iconFrame.setLayoutParams(newLp);
                }
            }
        }

        View labelContainer = (View) HookHelper.getObjectField(tile, "mLabelContainer");
        if (labelContainer != null) {
            labelContainer.setVisibility(View.VISIBLE);
            labelContainer.setPadding(0, 0, 0, 0);
            labelContainer.setMinimumHeight(0);
            ViewGroup.LayoutParams rawLcLp = labelContainer.getLayoutParams();
            if (rawLcLp instanceof LinearLayout.LayoutParams) {
                LinearLayout.LayoutParams lclp = (LinearLayout.LayoutParams) rawLcLp;
                if (lclp.width != 0 || lclp.weight != 1f || lclp.gravity != Gravity.CENTER_VERTICAL
                        || lclp.getMarginStart() != dp(12, density)) {
                    LinearLayout.LayoutParams newLp = new LinearLayout.LayoutParams(
                            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                    newLp.setMarginStart(dp(12, density));
                    newLp.gravity = Gravity.CENTER_VERTICAL;
                    labelContainer.setLayoutParams(newLp);
                }
            }
        }
        if (label != null && secondLabel != null) {
            layoutLabelsForPill(label, secondLabel, density);
        }
    }

    private static int inferShapeFromParent(View tile, int mode) {
        if (tile.getParent() instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) tile.getParent();
            String pCls = parent.getClass().getName();
            if (CLS_QS_TILE_LAYOUT.equals(pCls) || CLS_QQS_TILE_LAYOUT.equals(pCls)) {
                boolean isQqs = CLS_QQS_TILE_LAYOUT.equals(pCls);
                int idx = 0;
                for (int i = 0; i < parent.getChildCount(); i++) {
                    View c = parent.getChildAt(i);
                    if (c == tile) {
                        return shapeForCell(idx, mode, isQqs);
                    }
                    if (c.getVisibility() != View.GONE) {
                        idx++;
                    }
                }
            }
        }
        return mode == ModulePrefs.LAYOUT_CIRCLES ? SHAPE_CIRCLE_COMPACT : SHAPE_PILL;
    }

    private static void styleExpandedPill(LinearLayout tile, String spec, boolean isActive, boolean isUnavailable) {
        float density = tile.getResources().getDisplayMetrics().density;
        FrameLayout iconFrame = (FrameLayout) HookHelper.getObjectField(tile, "mIconFrame");
        View labelContainer = (View) HookHelper.getObjectField(tile, "mLabelContainer");
        TextView label = (TextView) HookHelper.getObjectField(tile, "mLabel");
        TextView secondLabel = (TextView) HookHelper.getObjectField(tile, "mSecondLabel");

        CharSequence labelCs = label != null ? label.getText() : null;
        int accent = TileAccentPalette.forSpec(spec, labelCs);
        int ambient = TileAccentPalette.ambientGlowForSpec(spec, labelCs);

        tile.setMinimumWidth(0);
        tile.setMinimumHeight(0);
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
        FrameLayout iconFrame = (FrameLayout) HookHelper.getObjectField(tile, "mIconFrame");
        View labelContainer = (View) HookHelper.getObjectField(tile, "mLabelContainer");
        TextView label = (TextView) HookHelper.getObjectField(tile, "mLabel");

        CharSequence labelCs = label != null ? label.getText() : null;
        int accent = TileAccentPalette.forSpec(spec, labelCs);
        int ambient = TileAccentPalette.ambientGlowForSpec(spec, labelCs);

        TransparentNoOpDrawable.apply(tile);
        tile.setMinimumWidth(0);
        tile.setMinimumHeight(0);
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
        FrameLayout iconFrame = (FrameLayout) HookHelper.getObjectField(tile, "mIconFrame");
        View labelContainer = (View) HookHelper.getObjectField(tile, "mLabelContainer");
        TextView label = (TextView) HookHelper.getObjectField(tile, "mLabel");
        TextView secondLabel = (TextView) HookHelper.getObjectField(tile, "mSecondLabel");

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
        int state = HookHelper.getIntField(tile, "mState");
        String spec = (String) HookHelper.getObjectField(tile, "mTileSpec");
        boolean isUnavailable = (state == 0);

        if (CLS_TILE_BASE.equals(clsName)) {
            tintTileIcons(tile, isUnavailable ? 0x73FFFFFF : Color.WHITE);
            return;
        }

        TextView label = (TextView) HookHelper.getObjectField(tile, "mLabel");
        CharSequence labelCs = label != null ? label.getText() : null;

        boolean isActive = (state == 2);
        if (CLS_BIG_RESIDENT_TILE.equals(clsName)) {
            isActive = isActive && HookHelper.getBooleanField(tile, "mTileState", true);
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
            Object qsIcon = HookHelper.getObjectField(tile, "mIcon");
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
        final boolean isLandscape;
        final int pillCols;
        final int circleCols;
        final int marginH;
        final int topPad;
        final int bottomPad;
        final int gap;
        final int pillW;
        final int circleSize;
        final int rowH;

        private GridMetrics(
                boolean isLandscape,
                int pillCols,
                int circleCols,
                int marginH,
                int topPad,
                int bottomPad,
                int gap,
                int pillW,
                int circleSize,
                int rowH
        ) {
            this.isLandscape = isLandscape;
            this.pillCols = pillCols;
            this.circleCols = circleCols;
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
            boolean isLandscape = ctx.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;

            if (!isLandscape) {
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
                return new GridMetrics(false, 2, 4, marginH, topPad, bottomPad, gap, pillW, circleSize, rowH);
            }

            int marginH = dp(7, d);
            int gap = dp(14, d);
            int usableW = Math.max(dp(240, d), width - 2 * marginH);
            int targetPillUnit = dp(142, d) + gap;
            int pillCols = Math.max(3, Math.min(4, Math.round((usableW + gap) / (float) targetPillUnit)));
            int circleCols = pillCols * 2;
            int pillW = (usableW - (pillCols - 1) * gap) / pillCols;
            int circleSize = (pillW - gap) / 2;
            int baseH = Math.min(dp(58, d), Math.max(dp(48, d), circleSize));
            int rowH = (mode == ModulePrefs.LAYOUT_CIRCLES && !isQuickPanel)
                    ? baseH + dp(22, d)
                    : baseH;
            int topPad = dp(8, d);
            int bottomPad = dp(12, d);
            return new GridMetrics(true, pillCols, circleCols, marginH, topPad, bottomPad, gap, pillW, circleSize, rowH);
        }

        int maxTilesFor(int mode, boolean isQuickPanel) {
            if (!isQuickPanel) {
                return Integer.MAX_VALUE;
            }
            if (!isLandscape) {
                if (mode == ModulePrefs.LAYOUT_HYBRID) {
                    return 7;
                }
                if (mode == ModulePrefs.LAYOUT_PILLS) {
                    return 6;
                }
                return 12;
            }
            if (mode == ModulePrefs.LAYOUT_HYBRID) {
                return 1 + pillCols * 2;
            }
            if (mode == ModulePrefs.LAYOUT_PILLS) {
                return pillCols * 2;
            }
            return circleCols * 2;
        }

        CellRect cellFor(int visibleIndex, int mode, boolean isQuickPanel) {
            if (mode == ModulePrefs.LAYOUT_CIRCLES) {
                int col = visibleIndex % circleCols;
                int row = visibleIndex / circleCols;
                int cellW = circleSize;
                int left = marginH + col * (circleSize + gap);
                int top = topPad + row * (rowH + gap);
                if (isQuickPanel && circleSize > rowH) {
                    left += (circleSize - rowH) / 2;
                    cellW = rowH;
                }
                return new CellRect(left, top, cellW, rowH);
            }
            if (mode == ModulePrefs.LAYOUT_HYBRID) {
                if (visibleIndex == 0) {
                    int w = Math.min(circleSize, rowH);
                    int left = marginH + (circleSize - w) / 2;
                    return new CellRect(left, topPad, w, rowH);
                }
                if (visibleIndex == 1) {
                    int w = Math.min(circleSize, rowH);
                    int left = marginH + circleSize + gap + (circleSize - w) / 2;
                    return new CellRect(left, topPad, w, rowH);
                }
                int row0Pills = pillCols - 1;
                if (visibleIndex < 2 + row0Pills) {
                    int pCol = 1 + (visibleIndex - 2);
                    int left = marginH + pCol * (pillW + gap);
                    return new CellRect(left, topPad, pillW, rowH);
                }
                int shifted = visibleIndex - (2 + row0Pills);
                int col = shifted % pillCols;
                int row = 1 + (shifted / pillCols);
                int left = marginH + col * (pillW + gap);
                int top = topPad + row * (rowH + gap);
                return new CellRect(left, top, pillW, rowH);
            }
            int col = visibleIndex % pillCols;
            int row = visibleIndex / pillCols;
            int left = marginH + col * (pillW + gap);
            int top = topPad + row * (rowH + gap);
            return new CellRect(left, top, pillW, rowH);
        }
    }
}
