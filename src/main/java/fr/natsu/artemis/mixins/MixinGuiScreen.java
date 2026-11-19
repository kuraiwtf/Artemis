package fr.natsu.artemis.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fr.natsu.artemis.module.chat.ChatTabs;
import fr.natsu.artemis.module.inventory.InventoryInteraction;
import fr.natsu.artemis.module.serverrule.ServerRules;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;

/**
 * Inventory (interaction) module: hides the tooltip of items tagged {@code lunar.hideItemTooltip}.
 * Chat tabs: messages typed in a tab get its input prefix.
 * Server rules: competitive commands typed in chat wait for a confirmation.
 */
@Mixin(GuiScreen.class)
public class MixinGuiScreen {

    @Inject(method = "renderToolTip", at = @At("HEAD"), cancellable = true, require = 0)
    private void artemis$hideTooltip(ItemStack stack, int x, int y, CallbackInfo ci) {
        if (InventoryInteraction.hideTooltip(stack)) {
            ci.cancel();
        }
    }

    // Declared before the competitive check so that it sees the prefixed message (same-point injectors
    // run in declaration order).
    @ModifyVariable(method = "sendChatMessage(Ljava/lang/String;Z)V", at = @At("HEAD"), argsOnly = true,
        ordinal = 0, require = 0)
    private String artemis$tabPrefix(String message) {
        return (Object) this instanceof GuiChat ? ChatTabs.applyPrefix(message) : message;
    }

    @Inject(method = "sendChatMessage(Ljava/lang/String;Z)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void artemis$competitiveCommand(String message, boolean addToChat, CallbackInfo ci) {
        if (ServerRules.interceptCommand(message)) {
            if (addToChat) {
                net.minecraft.client.Minecraft.getMinecraft().ingameGUI.getChatGUI().addToSentMessages(message);
            }
            ci.cancel();
        }
    }
}
