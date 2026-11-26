# Keep Modern LibXposed API 102 entry point, hooks, prefs, and UI drawables
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep class com.ios27.controlcenter.HookEntry {
    public <init>();
    *;
}
-keep class com.ios27.controlcenter.hooks.** { *; }
-keep class com.ios27.controlcenter.prefs.** { *; }
-keep class com.ios27.controlcenter.ui.** { *; }
-dontwarn io.github.libxposed.**
