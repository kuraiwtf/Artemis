package fr.natsu.artemis.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import fr.natsu.artemis.module.entity.EntityModule;
import fr.natsu.artemis.module.glow.GlowState;
import fr.natsu.artemis.module.serverrule.ServerRules;
import fr.natsu.artemis.render.GlowRenderer;
import fr.natsu.artemis.render.TeamColors;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EnumPlayerModelParts;
import net.minecraft.util.EnumChatFormatting;

@Mixin(RendererLivingEntity.class)
public abstract class MixinRendererLivingEntity<T extends EntityLivingBase> {

    @Inject(method = "renderLayers", at = @At("HEAD"), cancellable = true, require = 0)
    private void artemis$skipLayersInOutline(T entity, float limbSwing, float limbSwingAmount,
            float partialTicks, float ageInTicks, float netHeadYaw, float headPitch, float scale,
            CallbackInfo ci) {
        if (GlowRenderer.isRenderingOutline) {
            ci.cancel();
        }
    }

    @Inject(method = "setScoreTeamColor", at = @At("HEAD"), cancellable = true)
    private void artemis$glowColor(EntityLivingBase entity, CallbackInfoReturnable<Boolean> cir) {
        if (!(entity instanceof EntityPlayer)) {
            return;
        }
        int rgb;
        if (GlowRenderer.isRenderingOutline && GlowState.isGlowing(entity.getUniqueID())) {
            rgb = GlowState.color(entity.getUniqueID());
        } else {
            String format = entity.getTeam() instanceof ScorePlayerTeam
                ? FontRenderer.getFormatFromString(((ScorePlayerTeam) entity.getTeam()).getColorPrefix()) : "";
            if (format.length() < 2 || Character.toLowerCase(format.charAt(1)) != 'x') {
                return;
            }
            rgb = TeamColors.rgbFromFormat(format);
        }

        float red = (rgb >> 16 & 0xFF) / 255.0F;
        float green = (rgb >> 8 & 0xFF) / 255.0F;
        float blue = (rgb & 0xFF) / 255.0F;

        GlStateManager.disableLighting();
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.color(red, green, blue, 1.0F);
        GlStateManager.disableTexture2D();
        GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        GlStateManager.disableTexture2D();
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        cir.setReturnValue(true);
    }

    /**
     * Apollo flipped entities: the same transform vanilla applies to entities named
     * {@code Dinnerbone}/{@code Grumm}, skipped when vanilla already flipped this one.
     */
    @Inject(method = "rotateCorpse", at = @At("TAIL"), require = 0)
    private void artemis$flipEntity(T entity, float pitch, float yaw, float partialTicks, CallbackInfo ci) {
        if (!EntityModule.isFlipped(entity) || isVanillaFlipped(entity)) {
            return;
        }
        GlStateManager.translate(0.0F, entity.height + 0.1F, 0.0F);
        GlStateManager.rotate(180.0F, 0.0F, 0.0F, 1.0F);
    }

    private static boolean isVanillaFlipped(EntityLivingBase entity) {
        String name = EnumChatFormatting.getTextWithoutFormattingCodes(entity.getName());
        return name != null && (name.equals("Dinnerbone") || name.equals("Grumm"))
            && (!(entity instanceof EntityPlayer) || ((EntityPlayer) entity).isWearing(EnumPlayerModelParts.CAPE));
    }

    /**
     * Server rule nametag range on clients without Forge's {@code NAME_TAG_RANGE} patch, where vanilla
     * still hardcodes 64 blocks (32 sneaking). Under real Forge the constant is gone and this does
     * nothing; {@link ServerRules#applyNametagRange()} handles it instead.
     */
    @ModifyConstant(method = "renderName(Lnet/minecraft/entity/EntityLivingBase;DDD)V", constant = @Constant(floatValue = 64.0F), require = 0)
    private float artemis$nametagRange(float vanilla) {
        float override = ServerRules.nametagRangeOverride();
        return override > 0.0F ? override : vanilla;
    }

    @ModifyConstant(method = "renderName(Lnet/minecraft/entity/EntityLivingBase;DDD)V", constant = @Constant(floatValue = 32.0F), require = 0)
    private float artemis$nametagRangeSneaking(float vanilla) {
        float override = ServerRules.nametagRangeOverride();
        return override > 0.0F ? override / 2.0F : vanilla;
    }
}
