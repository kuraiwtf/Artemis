package fr.natsu.artemis.mixins;

import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fr.natsu.artemis.module.chat.TitleDiagnostic;
import net.minecraft.client.gui.FontRenderer;

/**
 * Hooks into Lunar Client's cached string renderer, which only exists once Lunar's mixins are
 * merged. Applied solely under Lunar via {@link ArtemisMixinPlugin}: on a plain Forge client the
 * target method is absent and Mixin 0.7.11 would abort the launch, {@code require = 0}
 * notwithstanding.
 */
@Mixin(FontRenderer.class)
public abstract class MixinFontRendererLunar {

    @Dynamic
    @Inject(method = "lunar$renderStringAtPos(Ljava/lang/String;ZI)V", at = @At("HEAD"), remap = false, require = 0)
    private void artemis$titleDiagString(String text, boolean shadow, int color, CallbackInfo ci) {
        TitleDiagnostic.onString((FontRenderer) (Object) this, text, shadow, color);
    }
}
