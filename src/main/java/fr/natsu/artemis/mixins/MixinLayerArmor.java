package fr.natsu.artemis.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fr.natsu.artemis.module.glint.GlintState;
import fr.natsu.artemis.module.glint.GlintTexture;
import fr.natsu.artemis.module.limb.LimbState;
import fr.natsu.artemis.module.model.ModelRegistry;
import fr.natsu.artemis.render.JemItemRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.client.renderer.entity.layers.LayerArmorBase;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;

@Mixin(LayerArmorBase.class)
public abstract class MixinLayerArmor {

    private static final String RENDER_ARMOR_PIECE =
        "renderLayer(Lnet/minecraft/entity/EntityLivingBase;FFFFFFFI)V";

    @Shadow
    @Final
    private RendererLivingEntity<?> renderer;

    @Shadow
    protected abstract void renderGlint(EntityLivingBase entity, ModelBase model, float limbSwing,
                                        float limbSwingAmount, float partialTicks, float ageInTicks,
                                        float netHeadYaw, float headPitch, float scale);

    /** Color of the armor piece being rendered, set by the {@code hasEffect} redirect. */
    private int artemis$glintArgb = GlintState.NONE;

    /** Apollo glint on worn armor: a {@code lunar.glint} piece glints even when not enchanted. */
    @Redirect(method = RENDER_ARMOR_PIECE,
        at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;hasEffect()Z"), require = 0)
    private boolean artemis$glintHasEffect(ItemStack stack) {
        this.artemis$glintArgb = GlintState.glintArgb(stack);
        return this.artemis$glintArgb != GlintState.NONE || stack.hasEffect();
    }

    @Redirect(method = RENDER_ARMOR_PIECE,
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/layers/LayerArmorBase;renderGlint(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/client/model/ModelBase;FFFFFFF)V"),
        require = 0)
    private void artemis$coloredGlint(LayerArmorBase<?> self, EntityLivingBase entity, ModelBase model,
                                      float limbSwing, float limbSwingAmount, float partialTicks,
                                      float ageInTicks, float netHeadYaw, float headPitch, float scale) {
        int argb = this.artemis$glintArgb;
        this.artemis$glintArgb = GlintState.NONE;
        if (argb == GlintState.NONE) {
            this.renderGlint(entity, model, limbSwing, limbSwingAmount, partialTicks, ageInTicks,
                netHeadYaw, headPitch, scale);
            return;
        }

        // Same passes as the vanilla renderGlint, with the alpha glint texture tinted by the color.
        float time = (float) entity.ticksExisted + partialTicks;
        float a = (argb >>> 24) / 255.0F;
        float r = (argb >> 16 & 0xFF) / 255.0F;
        float g = (argb >> 8 & 0xFF) / 255.0F;
        float b = (argb & 0xFF) / 255.0F;

        this.renderer.bindTexture(GlintTexture.get());
        GlStateManager.enableBlend();
        GlStateManager.depthFunc(GL11.GL_EQUAL);
        GlStateManager.depthMask(false);
        for (int i = 0; i < 2; ++i) {
            GlStateManager.disableLighting();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            GlStateManager.color(r, g, b, a);
            GlStateManager.matrixMode(GL11.GL_TEXTURE);
            GlStateManager.loadIdentity();
            GlStateManager.scale(0.33333334F, 0.33333334F, 0.33333334F);
            GlStateManager.rotate(30.0F - (float) i * 60.0F, 0.0F, 0.0F, 1.0F);
            GlStateManager.translate(0.0F, time * (0.001F + (float) i * 0.003F) * 20.0F, 0.0F);
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            model.render(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale);
        }
        GlStateManager.matrixMode(GL11.GL_TEXTURE);
        GlStateManager.loadIdentity();
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.enableLighting();
        GlStateManager.depthMask(true);
        GlStateManager.depthFunc(GL11.GL_LEQUAL);
        GlStateManager.disableBlend();
    }

    @Inject(method = "renderLayer", at = @At("HEAD"), cancellable = true, require = 0)
    private void artemis$hideArmor(EntityLivingBase entity, float limbSwing, float limbSwingAmount,
                                   float partialTicks, float ageInTicks, float netHeadYaw, float headPitch,
                                   float scale, int armorSlot, CallbackInfo ci) {
        if (!(entity instanceof EntityPlayer)) {
            return;
        }
        int piece = 5 - armorSlot;
        if ((LimbState.armorMaskOrZero(entity.getUniqueID()) & LimbState.bit(piece)) != 0) {
            ci.cancel();
        }
    }

    @Inject(method = "renderLayer", at = @At("HEAD"), cancellable = true, require = 0)
    private void artemis$customArmorModel(EntityLivingBase entity, float limbSwing, float limbSwingAmount,
                                          float partialTicks, float ageInTicks, float netHeadYaw,
                                          float headPitch, float scale, int armorSlot, CallbackInfo ci) {
        if (!ModelRegistry.hasItems()) {
            return;
        }
        ItemStack stack = entity.getEquipmentInSlot(armorSlot);
        if (stack != null && JemItemRenderer.renderArmor(entity, stack, armorSlot)) {
            ci.cancel();
        }
    }
}
