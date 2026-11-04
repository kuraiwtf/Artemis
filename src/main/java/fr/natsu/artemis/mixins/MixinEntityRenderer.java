package fr.natsu.artemis.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fr.natsu.artemis.render.GlowRenderer;
import net.minecraft.client.renderer.EntityRenderer;

@Mixin(EntityRenderer.class)
public abstract class MixinEntityRenderer {

    @Inject(method = "renderWorldPass", at = @At("RETURN"), require = 0)
    private void artemis$displayGlow(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        GlowRenderer.displayFramebuffer();
    }
}
