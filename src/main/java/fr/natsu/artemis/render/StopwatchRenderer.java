package fr.natsu.artemis.render;

import fr.natsu.artemis.module.stopwatch.StopwatchState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Draws stopwatches and timers as small boxed HUD labels. A server {@code hud_position} is read as
 * scaled pixels from the left/top edge, or from the right/bottom edge when negative; clocks without
 * one are stacked down the left side.
 */
public final class StopwatchRenderer {

    private static final int MARGIN = 5;
    private static final int PADDING = 3;
    private static final int LINE_SPACING = 3;
    private static final int BACKGROUND = 0x80000000;

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL || StopwatchState.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.gameSettings.showDebugInfo) {
            return;
        }
        FontRenderer font = mc.fontRendererObj;
        ScaledResolution resolution = new ScaledResolution(mc);
        long now = System.currentTimeMillis();

        int[] stackY = {resolution.getScaledHeight() / 3};
        for (StopwatchState.Clock clock : StopwatchState.stopwatches()) {
            draw(font, resolution, clock, now, stackY);
        }
        for (StopwatchState.Clock clock : StopwatchState.timers()) {
            draw(font, resolution, clock, now, stackY);
        }
    }

    private static void draw(FontRenderer font, ScaledResolution resolution, StopwatchState.Clock clock, long now,
                             int[] stackY) {
        if (clock.hideWhenStopped && !clock.running()) {
            return;
        }
        String time = StopwatchState.format(clock.format, clock.shown(now));
        String text = clock.name == null || clock.name.isEmpty() ? time : clock.name + ": " + time;
        int width = font.getStringWidth(text) + PADDING * 2;
        int height = font.FONT_HEIGHT + PADDING * 2 - 1;

        int x;
        int y;
        if (clock.position != null) {
            x = clock.position[0] < 0.0F
                ? resolution.getScaledWidth() + Math.round(clock.position[0]) - width
                : Math.round(clock.position[0]);
            y = clock.position[1] < 0.0F
                ? resolution.getScaledHeight() + Math.round(clock.position[1]) - height
                : Math.round(clock.position[1]);
        } else {
            x = MARGIN;
            y = stackY[0];
            stackY[0] += height + LINE_SPACING;
        }

        Gui.drawRect(x, y, x + width, y + height, BACKGROUND);
        font.drawStringWithShadow(text, x + PADDING, y + PADDING, 0xFF000000 | clock.rgb);
    }
}
