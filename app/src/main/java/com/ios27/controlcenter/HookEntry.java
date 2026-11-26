package com.ios27.controlcenter;

import androidx.annotation.NonNull;

import com.ios27.controlcenter.hooks.AnimationHook;
import com.ios27.controlcenter.hooks.ControlCenterHook;
import com.ios27.controlcenter.hooks.HookHelper;
import com.ios27.controlcenter.hooks.SliderHook;
import com.ios27.controlcenter.hooks.TileStyleHook;
import com.ios27.controlcenter.prefs.ModulePrefs;

import io.github.libxposed.api.XposedModule;

/**
 * Modern LibXposed API 102 entry point for iOS 27 Control Center (XOS 15).
 */
public class HookEntry extends XposedModule {

    public static final String TAG = "IOS27CC";

    public HookEntry() {
        super();
    }

    @Override
    public void onPackageReady(@NonNull PackageReadyParam param) {
        if (!"com.android.systemui".equals(param.getPackageName()) || !param.isFirstPackage()) {
            return;
        }

        HookHelper.init(this);
        ModulePrefs.init(this);

        ClassLoader cl = param.getClassLoader();

        try {
            ControlCenterHook.install(cl);
        } catch (Throwable t) {
            HookHelper.log("ControlCenterHook failed", t);
        }

        try {
            TileStyleHook.install(cl);
        } catch (Throwable t) {
            HookHelper.log("TileStyleHook failed", t);
        }

        try {
            SliderHook.install(cl);
        } catch (Throwable t) {
            HookHelper.log("SliderHook failed", t);
        }

        try {
            AnimationHook.install(cl);
        } catch (Throwable t) {
            HookHelper.log("AnimationHook failed", t);
        }
    }
}
