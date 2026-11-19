package fr.natsu.artemis.render;

import java.util.ArrayList;
import java.util.List;

import fr.natsu.artemis.module.button.ApolloIcon;
import fr.natsu.artemis.module.pingmarker.PingMarkerModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Ping markers, drawn in screen space over the pinged spot (visible through walls): a colored disc
 * with the ping's icon (or its type's), the distance under it, pulsing when the ping asks for focus
 * and fading out at the end of its duration.
 */
public final class PingMarkerRenderer {

    private static final float RADIUS = 7.0F;
    private static final float ICON_SIZE = 10.0F;
    private static final long FADE_MILLIS = 500L;

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event) {
        long now = System.currentTimeMillis();
        PingMarkerModule.expire(now);
        if (PingMarkerModule.pings().isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        Entity viewer = mc.getRenderViewEntity();
        if (viewer == null) {
            return;
        }
        RenderManager rm = mc.getRenderManager();
        ScaledResolution resolution = new ScaledResolution(mc);

        List<Object[]> visible = new ArrayList<>();
        for (PingMarkerModule.Ping ping : PingMarkerModule.pings()) {
            double[] screen = MarkerRenderer.project(ping.x - rm.viewerPosX, ping.y - rm.viewerPosY,
                ping.z - rm.viewerPosZ, resolution);
            if (screen != null) {
                visible.add(new Object[] {screen, ping});
            }
        }
        if (visible.isEmpty()) {
            return;
        }

        MarkerRenderer.setupOverlay(resolution);
        FontRenderer font = mc.fontRendererObj;
        for (Object[] entry : visible) {
            double[] screen = (double[]) entry[0];
            PingMarkerModule.Ping ping = (PingMarkerModule.Ping) entry[1];
            float x = (float) screen[0];
            float y = (float) screen[1];
            long remaining = ping.createdAt + ping.duration - now;
            float alpha = remaining < FADE_MILLIS ? Math.max(0.0F, remaining / (float) FADE_MILLIS) : 1.0F;
            float radius = RADIUS;
            if (ping.focus) {
                radius += 2.0F * (float) Math.abs(Math.sin((now - ping.createdAt) / 250.0));
            }
            int fill = (Math.round(0xC0 * alpha) << 24) | (ping.color & 0xFFFFFF);
            int ring = (Math.round(0xFF * alpha) << 24) | 0xFFFFFF;
            ButtonPainter.circle(x, y, radius, fill, ring);

            ApolloIcon icon = ping.icon != null ? ping.icon : PingMarkerModule.typeIcon(ping.type);
            if (icon != null) {
                float w = icon.height > 0 ? ICON_SIZE * icon.width / icon.height : ICON_SIZE;
                icon.draw(x - w / 2.0F, y - ICON_SIZE / 2.0F, w, ICON_SIZE, alpha);
            }

            int distance = (int) Math.round(viewer.getDistance(ping.x, ping.y, ping.z));
            String text = distance + "m";
            int textAlpha = Math.max(5, Math.round(255 * alpha));
            font.drawStringWithShadow(text, x - font.getStringWidth(text) / 2.0F, y + radius + 2.0F,
                (textAlpha << 24) | 0xFFFFFF);
        }
        GlStateManager.disableBlend();
        MarkerRenderer.cleanupOverlay();
    }
}
