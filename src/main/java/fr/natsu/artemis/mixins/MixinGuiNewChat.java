package fr.natsu.artemis.mixins;

import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fr.natsu.artemis.gui.ChatOverlay;
import fr.natsu.artemis.module.chat.ChatTabs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.IChatComponent;

/**
 * Apollo chat tabs: lines that don't belong to the selected tab are still stored (so switching tabs
 * can bring them back with {@code refreshChat()}), but never drawn. Also keeps chat component
 * clicks aligned when the chat log is lifted above the tab bar and button box.
 */
@Mixin(GuiNewChat.class)
public abstract class MixinGuiNewChat {

    private static final int MAX_STORED_LINES = 100;

    @Shadow
    @Final
    private List<ChatLine> chatLines;

    @Inject(method = "setChatLine", at = @At("HEAD"), cancellable = true, require = 0)
    private void artemis$tabFilter(IChatComponent component, int chatLineId, int updateCounter, boolean displayOnly,
            CallbackInfo ci) {
        if (ChatTabs.accepts(component, chatLineId)) {
            return;
        }
        if (!displayOnly) {
            this.chatLines.add(0, new ChatLine(updateCounter, component, chatLineId));
            while (this.chatLines.size() > MAX_STORED_LINES) {
                this.chatLines.remove(this.chatLines.size() - 1);
            }
        }
        ci.cancel();
    }

    @ModifyVariable(method = "getChatComponent", at = @At("HEAD"), argsOnly = true, ordinal = 1, require = 0)
    private int artemis$liftedMouseY(int mouseY) {
        int shift = ChatOverlay.chatShift();
        if (shift == 0) {
            return mouseY;
        }
        return mouseY - shift * new ScaledResolution(Minecraft.getMinecraft()).getScaleFactor();
    }
}
