# Keep Xposed entry, hooks, prefs, UI drawables, and self-status check
-keep class com.ios27.controlcenter.HookEntry { *; }
-keep class com.ios27.controlcenter.hooks.** { *; }
-keep class com.ios27.controlcenter.prefs.** { *; }
-keep class com.ios27.controlcenter.ui.** { *; }
-keepclassmembers class com.ios27.controlcenter.MainActivity {
    public static boolean isModuleActive();
}
-dontwarn de.robv.android.xposed.**
