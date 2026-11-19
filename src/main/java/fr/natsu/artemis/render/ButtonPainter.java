package fr.natsu.artemis.render;

import java.util.List;

import org.lwjgl.opengl.GL11;

import fr.natsu.artemis.module.button.ApolloButton;
import fr.natsu.artemis.module.button.ButtonContent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;

/**
 * Draws Apollo buttons (rounded square or circle, background, 1 px border, centered content) and
 * their tooltips.
 */
public final class ButtonPainter {

    private static final int CIRCLE_SEGMENTS = 32;

    private ButtonPainter() {
    }

    /** Draws {@code button} inside a box whose top-left corner is ({@code boxX}, {@code boxY}). */
    public static void paint(ApolloButton button, float boxX, float boxY, boolean hovered) {
        float x = boxX + button.x;
        float y = boxY + button.y;
        int background = hovered ? button.hoveredBackground : button.background;
        int border = hovered ? button.hoveredBorder : button.border;
        if (button.shape == ApolloButton.SHAPE_CIRCLE) {
            float diameter = Math.min(button.width, button.height);
            circle(x + button.width / 2.0F, y + button.height / 2.0F, diameter / 2.0F, background, border);
        } else {
            roundedRect(Math.round(x), Math.round(y), Math.round(button.width), Math.round(button.height),
                background, border);
        }
        ButtonContent content = button.content;
        if (content != null) {
            float maxIcon = Math.max(8.0F, (Math.min(button.width, button.height) - 4.0F) / content.scale);
            content.draw(Minecraft.getMinecraft().fontRendererObj, x + button.width / 2.0F,
                y + button.height / 2.0F, content.scale, maxIcon, 1.0F);
        }
    }

    /** A 1 px-corner rounded rectangle with a 1 px border; either color may be fully transparent. */
    public static void roundedRect(int x, int y, int w, int h, int background, int border) {
        if (w < 3 || h < 3) {
            Gui.drawRect(x, y, x + w, y + h, background);
            return;
        }
        if ((background >>> 24) != 0) {
            Gui.drawRect(x + 1, y + 1, x + w - 1, y + h - 1, background);
        }
        if ((border >>> 24) != 0) {
            Gui.drawRect(x + 1, y, x + w - 1, y + 1, border);
            Gui.drawRect(x + 1, y + h - 1, x + w - 1, y + h, border);
            Gui.drawRect(x, y + 1, x + 1, y + h - 1, border);
            Gui.drawRect(x + w - 1, y + 1, x + w, y + h - 1, border);
        }
    }

    public static void circle(float cx, float cy, float radius, int background, int border) {
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer buffer = tessellator.getWorldRenderer();

        if ((background >>> 24) != 0) {
            buffer.begin(GL11.GL_TRIANGLE_FAN, DefaultVertexFormats.POSITION_COLOR);
            color(buffer.pos(cx, cy, 0.0), background);
            for (int i = CIRCLE_SEGMENTS; i >= 0; i--) {
                double angle = Math.PI * 2.0 * i / CIRCLE_SEGMENTS;
                color(buffer.pos(cx + Math.cos(angle) * radius, cy + Math.sin(angle) * radius, 0.0), background);
            }
            tessellator.draw();
        }
        if ((border >>> 24) != 0) {
            GL11.glLineWidth(Math.max(1.0F, new ScaledResolution(Minecraft.getMinecraft()).getScaleFactor()));
            buffer.begin(GL11.GL_LINE_LOOP, DefaultVertexFormats.POSITION_COLOR);
            for (int i = 0; i < CIRCLE_SEGMENTS; i++) {
                double angle = Math.PI * 2.0 * i / CIRCLE_SEGMENTS;
                color(buffer.pos(cx + Math.cos(angle) * (radius - 0.5), cy + Math.sin(angle) * (radius - 0.5), 0.0),
                    border);
            }
            tessellator.draw();
            GL11.glLineWidth(1.0F);
        }
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    private static void color(WorldRenderer vertex, int argb) {
        vertex.color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF).endVertex();
    }

    /** A vanilla item-style tooltip at the mouse, kept on screen. */
    public static void tooltip(List<String> lines, int mouseX, int mouseY) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution resolution = new ScaledResolution(mc);
        FontRenderer font = mc.fontRendererObj;
        int width = 0;
        for (String line : lines) {
            width = Math.max(width, font.getStringWidth(line));
        }
        int height = lines.size() == 1 ? 8 : 10 + (lines.size() - 1) * 10;
        int x = mouseX + 12;
        int y = mouseY - 12;
        if (x + width + 4 > resolution.getScaledWidth()) {
            x = Math.max(4, mouseX - 16 - width);
        }
        y = Math.max(4, Math.min(y, resolution.getScaledHeight() - height - 4));

        GlStateManager.pushMatrix();
        GlStateManager.translate(0.0F, 0.0F, 400.0F);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        int background = 0xF0100010;
        int borderTop = 0x505000FF;
        int borderBottom = 0x5028007F;
        gradient(x - 3, y - 4, x + width + 3, y - 3, background, background);
        gradient(x - 3, y + height + 3, x + width + 3, y + height + 4, background, background);
        gradient(x - 3, y - 3, x + width + 3, y + height + 3, background, background);
        gradient(x - 4, y - 3, x - 3, y + height + 3, background, background);
        gradient(x + width + 3, y - 3, x + width + 4, y + height + 3, background, background);
        gradient(x - 3, y - 3 + 1, x - 3 + 1, y + height + 3 - 1, borderTop, borderBottom);
        gradient(x + width + 2, y - 3 + 1, x + width + 3, y + height + 3 - 1, borderTop, borderBottom);
        gradient(x - 3, y - 3, x + width + 3, y - 3 + 1, borderTop, borderTop);
        gradient(x - 3, y + height + 2, x + width + 3, y + height + 3, borderBottom, borderBottom);
        for (int i = 0; i < lines.size(); i++) {
            font.drawStringWithShadow(lines.get(i), x, y, 0xFFFFFFFF);
            y += i == 0 ? 12 : 10;
        }
        GlStateManager.enableDepth();
        GlStateManager.popMatrix();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void gradient(int left, int top, int right, int bottom, int startColor, int endColor) {
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer buffer = tessellator.getWorldRenderer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        color(buffer.pos(right, top, 0.0), startColor);
        color(buffer.pos(left, top, 0.0), startColor);
        color(buffer.pos(left, bottom, 0.0), endColor);
        color(buffer.pos(right, bottom, 0.0), endColor);
        tessellator.draw();
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.disableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
    }
}
