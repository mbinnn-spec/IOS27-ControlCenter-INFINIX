package com.ios27.controlcenter.ui;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.view.View;

/**
 * Empty transparent Drawable with no-op tint/alpha/colorFilter setters.
 * Prevents SystemUI views (e.g., TsQSTileBaseView.mIconFrame.getBackground().setTint)
 * from throwing NullPointerException when a background would otherwise be cleared.
 */
public class TransparentNoOpDrawable extends Drawable {

    public static void apply(View view) {
        if (view != null && !(view.getBackground() instanceof TransparentNoOpDrawable)) {
            view.setBackground(new TransparentNoOpDrawable());
        }
    }

    @Override
    public void draw(Canvas canvas) {
        // Intentionally empty.
    }

    @Override
    public void setAlpha(int alpha) {
        // No-op.
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        // No-op.
    }

    @Override
    public void setTint(int tintColor) {
        // No-op.
    }

    @Override
    public void setTintList(ColorStateList tint) {
        // No-op.
    }

    @Override
    public void setTintMode(PorterDuff.Mode tintMode) {
        // No-op.
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }
}

