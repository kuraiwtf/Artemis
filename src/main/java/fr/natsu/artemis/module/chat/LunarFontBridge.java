package fr.natsu.artemis.module.chat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import net.minecraft.client.gui.FontRenderer;

public final class LunarFontBridge {

    private static Method getHolder;
    private static Method setHolder;
    private static Field shadowFlag;
    private static boolean resolved;

    private LunarFontBridge() {
    }

    private static void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        for (Method method : FontRenderer.class.getMethods()) {
            if (method.getName().equals("lunar$getColorHolder") && method.getParameterTypes().length == 0) {
                getHolder = method;
            } else if (method.getName().equals("lunar$setColorHolder") && method.getParameterTypes().length == 1) {
                setHolder = method;
            }
        }
        for (Field field : FontRenderer.class.getDeclaredFields()) {
            if (field.getName().equals("lunar$shadow") && field.getType() == boolean.class) {
                field.setAccessible(true);
                shadowFlag = field;
            }
        }
    }

    public static Object current(FontRenderer font) {
        resolve();
        if (getHolder == null || setHolder == null) {
            return null;
        }
        try {
            return getHolder.invoke(font);
        } catch (Throwable t) {
            return null;
        }
    }

    public static int colorOf(Object holder, float x, float y, int fallback) {
        if (holder == null) {
            return fallback;
        }
        try {
            Method apply = holder.getClass().getMethod("apply", Object.class);
            apply.setAccessible(true);
            Object value = apply.invoke(holder, Float.valueOf(x + y));
            return value instanceof Integer ? (Integer) value : fallback;
        } catch (Throwable t) {
            return fallback;
        }
    }

    public static boolean isShadowPass(FontRenderer font) {
        resolve();
        if (shadowFlag == null) {
            return false;
        }
        try {
            return shadowFlag.getBoolean(font);
        } catch (Throwable t) {
            return false;
        }
    }

    public static void suspend(FontRenderer font) {
        try {
            setHolder.invoke(font, new Object[] {null});
        } catch (Throwable ignored) {
        }
    }

    public static void restore(FontRenderer font, Object holder) {
        if (holder == null) {
            return;
        }
        try {
            setHolder.invoke(font, holder);
        } catch (Throwable ignored) {
        }
    }
}
