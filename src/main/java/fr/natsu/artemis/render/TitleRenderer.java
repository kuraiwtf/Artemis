package fr.natsu.artemis.render;

import java.util.List;

import fr.natsu.artemis.module.nametag.AdventureText.Run;
import fr.natsu.artemis.module.title.TitleState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Draws Apollo titles the way vanilla lays out {@code /title}: the title at 4x around the screen
 * center, the subtitle at 2x just below, each multiplied by the server-sent (possibly interpolating)
 * scale.
 */
public final class TitleRenderer {

    private static final float TITLE_BASE_SCALE = 4.0F;
    private static final float SUBTITLE_BASE_SCALE = 2.0F;
    /** Alpha under ~4/255 makes FontRenderer fall back to opaque, so skip those frames. */
    private static final int MIN_ALPHA = 5;

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) {
            return;
        }
        long now = System.currentTimeMillis();
        TitleState.Title title = TitleState.title(now);
        TitleState.Title subtitle = TitleState.subtitle(now);
        if (title == null && subtitle == null) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer font = mc.fontRendererObj;
        ScaledResolution resolution = new ScaledResolution(mc);
        float centerX = resolution.getScaledWidth() / 2.0F;
        float centerY = resolution.getScaledHeight() / 2.0F;

        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        if (title != null) {
            draw(font, title, now, centerX, centerY, TITLE_BASE_SCALE, -10.0F);
        }
        if (subtitle != null) {
            draw(font, subtitle, now, centerX, centerY, SUBTITLE_BASE_SCALE, 5.0F);
        }
        GlStateManager.disableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void draw(FontRenderer font, TitleState.Title title, long now, float centerX, float centerY,
                             float baseScale, float offsetY) {
        int alpha = (int) (255.0F * title.alpha(now));
        if (alpha < MIN_ALPHA) {
            return;
        }
        float scale = baseScale * title.currentScale(now);
        int width = width(font, title.runs);

        GlStateManager.pushMatrix();
        GlStateManager.translate(centerX, centerY, 0.0F);
        GlStateManager.scale(scale, scale, scale);
        float x = -width / 2.0F;
        for (Run run : title.runs) {
            font.drawString(run.display, x, offsetY, (alpha << 24) | (run.rgb & 0xFFFFFF), true);
            x += font.getStringWidth(run.display);
        }
        GlStateManager.popMatrix();
    }

    private static int width(FontRenderer font, List<Run> runs) {
        int width = 0;
        for (Run run : runs) {
            width += font.getStringWidth(run.display);
        }
        return width;
    }
}
