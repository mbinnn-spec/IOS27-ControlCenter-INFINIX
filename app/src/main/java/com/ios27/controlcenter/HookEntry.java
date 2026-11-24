package com.ios27.controlcenter;

import com.ios27.controlcenter.hooks.AnimationHook;
import com.ios27.controlcenter.hooks.ControlCenterHook;
import com.ios27.controlcenter.hooks.SliderHook;
import com.ios27.controlcenter.hooks.TileStyleHook;
import com.ios27.controlcenter.prefs.ModulePrefs;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * LSPosed entry point for iOS 27 Control Center (XOS 15).
 */
public class HookEntry implements IXposedHookLoadPackage {

    public static final String TAG = "IOS27CC";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (ModulePrefs.PACKAGE_NAME.equals(lpparam.packageName)) {
            hookSelfStatus(lpparam.classLoader);
            return;
        }

        if (!"com.android.systemui".equals(lpparam.packageName)) {
            return;
        }

        ModulePrefs.init();

        try {
            ControlCenterHook.install(lpparam.classLoader);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": ControlCenterHook failed: " + t);
        }

        try {
            TileStyleHook.install(lpparam.classLoader);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": TileStyleHook failed: " + t);
        }

        try {
            SliderHook.install(lpparam.classLoader);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": SliderHook failed: " + t);
        }

        try {
            AnimationHook.install(lpparam.classLoader);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": AnimationHook failed: " + t);
        }
    }

    private void hookSelfStatus(ClassLoader classLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                    "com.ios27.controlcenter.MainActivity",
                    classLoader,
                    "isModuleActive",
                    XC_MethodReplacement.returnConstant(Boolean.TRUE)
            );
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": self status hook failed: " + t);
        }
    }
}

