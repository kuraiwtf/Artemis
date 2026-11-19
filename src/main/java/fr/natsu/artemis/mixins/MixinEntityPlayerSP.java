package fr.natsu.artemis.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import fr.natsu.artemis.module.serverrule.ServerRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiScreen;

/**
 * Server rule {@code anti-portal-traps}: vanilla closes any open screen while the player stands in a
 * nether portal, so a player trapped in one cannot even type. With the rule on, chat stays open.
 */
@Mixin(EntityPlayerSP.class)
public class MixinEntityPlayerSP {

    @Redirect(method = "onLivingUpdate",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;displayGuiScreen(Lnet/minecraft/client/gui/GuiScreen;)V"),
        require = 0)
    private void artemis$keepChatInPortal(Minecraft mc, GuiScreen screen) {
        if (screen == null && mc.currentScreen instanceof GuiChat && ServerRules.antiPortalTraps()) {
            return;
        }
        mc.displayGuiScreen(screen);
    }
}
