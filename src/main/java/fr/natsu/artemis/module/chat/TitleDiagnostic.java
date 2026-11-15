package fr.natsu.artemis.module.chat;

import java.lang.reflect.Field;
import java.nio.FloatBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import fr.natsu.artemis.Artemis;
import fr.natsu.artemis.LunarHost;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiIngame;

public final class TitleDiagnostic {

    private static final String[] TITLE_FIELDS = {"displayedTitle", "field_175201_x"};

    private static Field titleField;
    private static boolean resolved;
    private static boolean active;
    private static int chars;
    private static String lastText;
    private static long lastLog;

    private TitleDiagnostic() {
    }

    public static void onString(FontRenderer font, String text, boolean shadow, int color) {
        active = false;
        if (!LunarHost.isLunar() || text == null || text.isEmpty()) {
            return;
        }
        String title = displayedTitle();
        if (title == null || !title.equals(text)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (text.equals(lastText) && now - lastLog < 3000L) {
            return;
        }
        active = true;
        chars = 0;
        lastText = text;
        lastLog = now;
        FloatBuffer current = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_CURRENT_COLOR, current);
        Object holder = LunarFontBridge.current(font);
        Artemis.LOGGER.info("[TitleDiag] title '{}' shadow={} color={} holder={} lunarShadow={} glColor={},{},{},{}",
            text, shadow, Integer.toHexString(color),
            holder == null ? "null" : holder.getClass().getName(), LunarFontBridge.isShadowPass(font),
            current.get(0), current.get(1), current.get(2), current.get(3));
    }

    public static void onChar(FontRenderer font) {
        if (!active || chars >= 3) {
            return;
        }
        chars++;
        Artemis.LOGGER.info("[TitleDiag]   char {} textColor={} fields r={} g={} b={} a={}",
            chars, Integer.toHexString(((Number) read(font, "textColor", -1)).intValue()),
            read(font, "red", -1F), read(font, "blue", -1F), read(font, "green", -1F), read(font, "alpha", -1F));
    }

    private static Object read(FontRenderer font, String name, Object fallback) {
        try {
            Field field = FontRenderer.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(font);
        } catch (Throwable t) {
            return fallback;
        }
    }

    private static String displayedTitle() {
        GuiIngame gui = Minecraft.getMinecraft().ingameGUI;
        if (gui == null) {
            return null;
        }
        if (!resolved) {
            resolved = true;
            for (String name : TITLE_FIELDS) {
                try {
                    Field field = GuiIngame.class.getDeclaredField(name);
                    field.setAccessible(true);
                    titleField = field;
                    break;
                } catch (Throwable ignored) {
                }
            }
            if (titleField == null) {
                Artemis.LOGGER.warn("[TitleDiag] displayedTitle field not found");
            }
        }
        if (titleField == null) {
            return null;
        }
        try {
            Object value = titleField.get(gui);
            return value instanceof String ? (String) value : null;
        } catch (Throwable t) {
            return null;
        }
    }
}
