package fr.natsu.artemis.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import fr.natsu.artemis.module.entity.EntityModule;
import net.minecraft.client.renderer.entity.layers.LayerSheepWool;
import net.minecraft.entity.passive.EntitySheep;

/**
 * Apollo rainbow sheep: makes the vanilla {@code "jeb_"} name check pass for the sheep the server
 * picked, so the wool cycles colors without renaming the entity.
 */
@Mixin(LayerSheepWool.class)
public class MixinLayerSheepWool {

    @Redirect(method = "doRenderLayer(Lnet/minecraft/entity/passive/EntitySheep;FFFFFFF)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/passive/EntitySheep;hasCustomName()Z"),
        require = 0)
    private boolean artemis$rainbowHasName(EntitySheep sheep) {
        return EntityModule.isRainbow(sheep) || sheep.hasCustomName();
    }

    @Redirect(method = "doRenderLayer(Lnet/minecraft/entity/passive/EntitySheep;FFFFFFF)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/passive/EntitySheep;getCustomNameTag()Ljava/lang/String;"),
        require = 0)
    private String artemis$rainbowName(EntitySheep sheep) {
        return EntityModule.isRainbow(sheep) ? "jeb_" : sheep.getCustomNameTag();
    }
}
