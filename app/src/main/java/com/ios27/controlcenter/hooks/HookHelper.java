package com.ios27.controlcenter.hooks;

import android.util.Log;

import com.ios27.controlcenter.HookEntry;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

import io.github.libxposed.api.XposedInterface;

/**
 * Lightweight reflection & hooking bridge over Modern LibXposed API 102 (XposedInterface).
 */
public final class HookHelper {

    public interface AfterCallback {
        void after(XposedInterface.Chain chain, Object result) throws Throwable;
    }

    private static volatile XposedInterface sXposed;
    private static final ConcurrentHashMap<String, Field> FIELD_CACHE = new ConcurrentHashMap<>();

    private HookHelper() {
    }

    public static void init(XposedInterface xposed) {
        sXposed = xposed;
    }

    public static void log(String msg) {
        XposedInterface xp = sXposed;
        if (xp != null) {
            xp.log(Log.INFO, HookEntry.TAG, msg);
        }
    }

    public static void log(String msg, Throwable t) {
        XposedInterface xp = sXposed;
        if (xp != null) {
            xp.log(Log.WARN, HookEntry.TAG, msg, t);
        }
    }

    public static Class<?> findClass(String className, ClassLoader cl) {
        try {
            return Class.forName(className, false, cl);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static Method findMethod(Class<?> cls, String name, Class<?>... paramTypes) {
        Class<?> cur = cls;
        while (cur != null) {
            try {
                Method m = cur.getDeclaredMethod(name, paramTypes);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
                cur = cur.getSuperclass();
            }
        }
        return null;
    }

    public static void hook(Class<?> cls, String methodName, XposedInterface.Hooker hooker, Class<?>... paramTypes) {
        XposedInterface xp = sXposed;
        if (xp == null || cls == null) {
            return;
        }
        Method m = findMethod(cls, methodName, paramTypes);
        if (m == null) {
            return;
        }
        xp.hook(m)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(hooker);
    }

    public static void hookAfter(Class<?> cls, String methodName, AfterCallback cb, Class<?>... paramTypes) {
        hook(cls, methodName, chain -> {
            Object result = chain.proceed();
            try {
                cb.after(chain, result);
            } catch (Throwable t) {
                log(cls.getSimpleName() + "." + methodName + " after err", t);
            }
            return result;
        }, paramTypes);
    }

    public static void hookAllConstructorsAfter(Class<?> cls, AfterCallback cb) {
        XposedInterface xp = sXposed;
        if (xp == null || cls == null) {
            return;
        }
        for (Constructor<?> ctor : cls.getDeclaredConstructors()) {
            ctor.setAccessible(true);
            xp.hook(ctor)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        try {
                            cb.after(chain, result);
                        } catch (Throwable t) {
                            log(cls.getSimpleName() + ".<init> after err", t);
                        }
                        return result;
                    });
        }
    }

    private static Field findField(Class<?> cls, String fieldName) {
        String key = cls.getName() + "#" + fieldName;
        Field cached = FIELD_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        Class<?> cur = cls;
        while (cur != null) {
            try {
                Field f = cur.getDeclaredField(fieldName);
                f.setAccessible(true);
                FIELD_CACHE.put(key, f);
                return f;
            } catch (NoSuchFieldException ignored) {
                cur = cur.getSuperclass();
            }
        }
        return null;
    }

    public static Object getObjectField(Object target, String fieldName) {
        if (target == null) {
            return null;
        }
        Field f = findField(target.getClass(), fieldName);
        if (f == null) {
            return null;
        }
        try {
            return f.get(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static int getIntField(Object target, String fieldName) {
        if (target == null) {
            return 0;
        }
        Field f = findField(target.getClass(), fieldName);
        if (f == null) {
            return 0;
        }
        try {
            return f.getInt(target);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    public static float getFloatField(Object target, String fieldName) {
        if (target == null) {
            return 0f;
        }
        Field f = findField(target.getClass(), fieldName);
        if (f == null) {
            return 0f;
        }
        try {
            return f.getFloat(target);
        } catch (Throwable ignored) {
            return 0f;
        }
    }

    public static boolean getBooleanField(Object target, String fieldName, boolean fallback) {
        if (target == null) {
            return fallback;
        }
        Field f = findField(target.getClass(), fieldName);
        if (f == null) {
            return fallback;
        }
        try {
            return f.getBoolean(target);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    public static void setObjectField(Object target, String fieldName, Object value) {
        if (target == null) {
            return;
        }
        Field f = findField(target.getClass(), fieldName);
        if (f != null) {
            try {
                f.set(target, value);
            } catch (Throwable ignored) {
            }
        }
    }

    public static void setLongField(Object target, String fieldName, long value) {
        if (target == null) {
            return;
        }
        Field f = findField(target.getClass(), fieldName);
        if (f != null) {
            try {
                f.setLong(target, value);
            } catch (Throwable ignored) {
            }
        }
    }

    public static void callSetMeasuredDimension(Object view, int w, int h) {
        if (view == null) {
            return;
        }
        Method m = findMethod(view.getClass(), "setMeasuredDimension", int.class, int.class);
        if (m != null) {
            try {
                m.invoke(view, w, h);
            } catch (Throwable ignored) {
            }
        }
    }
}

