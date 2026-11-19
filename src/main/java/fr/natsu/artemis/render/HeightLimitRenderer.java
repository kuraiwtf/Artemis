package fr.natsu.artemis.render;

import java.util.List;

import org.lwjgl.opengl.GL11;

import fr.natsu.artemis.module.heightlimit.HeightLimitModule;
import fr.natsu.artemis.module.nametag.AdventureText.Run;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Height limit visuals: once the player gets close, a red block grid on the first denied layer
 * ({@code y = limit}) and a HUD line with the world's display name and the distance left.
 */
public final class HeightLimitRenderer {

    /** Visuals show up when the player's feet are within this many blocks of the limit. */
    private static final int SHOW_DISTANCE = 10;
    private static final int GRID_RADIUS = 6;

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        Entity viewer = mc.getRenderViewEntity();
        HeightLimitModule.Limit limit = HeightLimitModule.current();
        if (limit == null || viewer == null || mc.theWorld == null) {
            return;
        }
        double feet = viewer.lastTickPosY + (viewer.posY - viewer.lastTickPosY) * event.partialTicks;
        double distance = limit.limit - feet;
        if (distance > SHOW_DISTANCE || distance < -2) {
            return;
        }
        float alpha = (float) Math.max(0.15, Math.min(0.6, 0.6 - distance / SHOW_DISTANCE * 0.45));

        RenderManager rm = mc.getRenderManager();
        double vx = viewer.lastTickPosX + (viewer.posX - viewer.lastTickPosX) * event.partialTicks;
        double vz = viewer.lastTickPosZ + (viewer.posZ - viewer.lastTickPosZ) * event.partialTicks;
        int cx = MathHelper.floor_double(vx);
        int cz = MathHelper.floor_double(vz);
        double y = limit.limit + 0.002 - rm.viewerPosY;

        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.depthMask(false);

        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer buffer = tessellator.getWorldRenderer();
        double x0 = cx - GRID_RADIUS - rm.viewerPosX;
        double x1 = cx + GRID_RADIUS + 1 - rm.viewerPosX;
        double z0 = cz - GRID_RADIUS - rm.viewerPosZ;
        double z1 = cz + GRID_RADIUS + 1 - rm.viewerPosZ;

        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        buffer.pos(x0, y, z0).color(1.0F, 0.2F, 0.2F, alpha * 0.35F).endVertex();
        buffer.pos(x1, y, z0).color(1.0F, 0.2F, 0.2F, alpha * 0.35F).endVertex();
        buffer.pos(x1, y, z1).color(1.0F, 0.2F, 0.2F, alpha * 0.35F).endVertex();
        buffer.pos(x0, y, z1).color(1.0F, 0.2F, 0.2F, alpha * 0.35F).endVertex();
        tessellator.draw();

        GL11.glLineWidth(1.5F);
        buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        for (int i = -GRID_RADIUS; i <= GRID_RADIUS + 1; i++) {
            double gx = cx + i - rm.viewerPosX;
            double gz = cz + i - rm.viewerPosZ;
            buffer.pos(gx, y, z0).color(1.0F, 0.25F, 0.25F, alpha).endVertex();
            buffer.pos(gx, y, z1).color(1.0F, 0.25F, 0.25F, alpha).endVertex();
            buffer.pos(x0, y, gz).color(1.0F, 0.25F, 0.25F, alpha).endVertex();
            buffer.pos(x1, y, gz).color(1.0F, 0.25F, 0.25F, alpha).endVertex();
        }
        tessellator.draw();

        GlStateManager.depthMask(true);
        GlStateManager.disableBlend();
        GlStateManager.enableCull();
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        HeightLimitModule.Limit limit = HeightLimitModule.current();
        if (limit == null || mc.thePlayer == null || mc.gameSettings.showDebugInfo) {
            return;
        }
        int blocksLeft = limit.limit - MathHelper.floor_double(mc.thePlayer.posY);
        if (blocksLeft > SHOW_DISTANCE) {
            return;
        }

        FontRenderer font = mc.fontRendererObj;
        ScaledResolution resolution = new ScaledResolution(mc);
        String suffix = "Height limit: " + limit.limit + " (" + Math.max(0, blocksLeft) + " left)";
        int nameWidth = width(font, limit.displayName);
        int gap = nameWidth > 0 ? font.getStringWidth(" ") : 0;
        int total = nameWidth + gap + font.getStringWidth(suffix);
        int x = (resolution.getScaledWidth() - total) / 2;
        int y = resolution.getScaledHeight() - 84;

        for (Run run : limit.displayName) {
            font.drawStringWithShadow(run.display, x, y, 0xFF000000 | run.rgb);
            x += font.getStringWidth(run.display);
        }
        x += gap;
        font.drawStringWithShadow(suffix, x, y, blocksLeft <= 1 ? 0xFFFF5555 : 0xFFFFFFFF);
    }

    private static int width(FontRenderer font, List<Run> runs) {
        int width = 0;
        for (Run run : runs) {
            width += font.getStringWidth(run.display);
        }
        return width;
    }
}
