package fr.natsu.artemis.render;

import fr.natsu.artemis.module.tntcountdown.TntCountdownModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityTNTPrimed;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Seconds left before each primed TNT goes off, floating above it, fading from green to red. The fuse
 * comes from the client entity, which {@link TntCountdownModule} keeps in sync with the server.
 */
public final class TntCountdownRenderer {

    private static final double MAX_DIST_SQ = 64.0 * 64.0;
    private static final float TEXT_SCALE = 0.025F;
    private static final float FULL_FUSE = 80.0F;

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.getRenderViewEntity() == null || !TntCountdownModule.isActive()) {
            return;
        }
        RenderManager renderManager = mc.getRenderManager();
        FontRenderer font = mc.fontRendererObj;
        float partialTicks = event.partialTicks;

        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (!(entity instanceof EntityTNTPrimed)) {
                continue;
            }
            EntityTNTPrimed tnt = (EntityTNTPrimed) entity;
            double x = tnt.lastTickPosX + (tnt.posX - tnt.lastTickPosX) * partialTicks - renderManager.viewerPosX;
            double y = tnt.lastTickPosY + (tnt.posY - tnt.lastTickPosY) * partialTicks - renderManager.viewerPosY;
            double z = tnt.lastTickPosZ + (tnt.posZ - tnt.lastTickPosZ) * partialTicks - renderManager.viewerPosZ;
            if (x * x + y * y + z * z > MAX_DIST_SQ) {
                continue;
            }
            float ticksLeft = Math.max(0.0F, tnt.fuse - partialTicks);
            draw(renderManager, font, x, y + tnt.height + 0.5, z, ticksLeft);
        }
    }

    private static void draw(RenderManager renderManager, FontRenderer font, double x, double y, double z,
                             float ticksLeft) {
        String text = String.format("%.2f", ticksLeft / 20.0F);
        float fraction = Math.min(1.0F, ticksLeft / FULL_FUSE);
        int red = (int) (255 * (1.0F - fraction));
        int green = (int) (255 * fraction);
        int color = 0xFF000000 | (red << 16) | (green << 8);

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        GlStateManager.rotate(-renderManager.playerViewY, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(renderManager.playerViewX, 1.0F, 0.0F, 0.0F);
        GlStateManager.scale(-TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE);
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        font.drawStringWithShadow(text, -font.getStringWidth(text) / 2.0F, 0.0F, color);
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
    }
}
