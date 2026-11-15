package fr.natsu.artemis.mixins;

import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import fr.natsu.artemis.module.glow.GlowState;
import fr.natsu.artemis.render.GlowRenderer;
import fr.natsu.artemis.render.TeamColors;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.scoreboard.ScorePlayerTeam;

/**
 * Hooks into methods that only exist once Lunar Client's own mixins are merged. Applied solely
 * under Lunar via {@link ArtemisMixinPlugin}: on a plain Forge client the target method is absent
 * and Mixin 0.7.11 would abort the launch, {@code require = 0} notwithstanding.
 */
@Mixin(RendererLivingEntity.class)
public abstract class MixinRendererLivingEntityLunar {

    @Dynamic
    @Inject(method = "lunar$setScoreTeamColor", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void artemis$lunarOutlineColor(EntityLivingBase entity, CallbackInfoReturnable<Integer> cir) {
        int rgb = 0xFFFFFF;
        if (GlowRenderer.isRenderingOutline && GlowState.isGlowing(entity.getUniqueID())) {
            rgb = GlowState.color(entity.getUniqueID()) & 0xFFFFFF;
        } else if (entity instanceof EntityPlayer && entity.getTeam() instanceof ScorePlayerTeam) {
            rgb = TeamColors.rgbFromFormat(
                FontRenderer.getFormatFromString(((ScorePlayerTeam) entity.getTeam()).getColorPrefix()));
        }
        GlStateManager.resetColor();
        GL11.glDisable(GL11.GL_LIGHTING);
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GL11.glColor4f((rgb >> 16 & 0xFF) / 255.0F, (rgb >> 8 & 0xFF) / 255.0F, (rgb & 0xFF) / 255.0F, 1.0F);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        cir.setReturnValue(rgb);
    }
}
