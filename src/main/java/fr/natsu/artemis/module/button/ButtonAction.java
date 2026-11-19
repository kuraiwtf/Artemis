package fr.natsu.artemis.module.button;

import java.net.URI;
import java.util.function.Consumer;

import com.google.protobuf.ByteString;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;

import fr.natsu.artemis.Artemis;
import fr.natsu.artemis.gui.GuiPointList;
import fr.natsu.artemis.module.chat.ChatTabs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiConfirmOpenLink;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiYesNo;
import net.minecraftforge.client.ClientCommandHandler;

/**
 * What a button (or a chat popup option) does when clicked: run a command, open a URL, run a client
 * action, or select a chat tab. Commands and URLs ask the player first, like Lunar does; URLs also
 * follow the vanilla chat link settings.
 */
public final class ButtonAction {

    public enum Kind { RUN_COMMAND, OPEN_URL, CLIENT_ACTION, SELECT_CHAT_TAB }

    /** {@code ButtonClientAction} values. */
    public static final int CLIENT_OPEN_MINIMAP_VIEW = 1;
    public static final int CLIENT_OPEN_WAYPOINTS_MENU = 2;

    public final Kind kind;
    public final String value;
    public final int clientAction;
    public final boolean openChat;

    private ButtonAction(Kind kind, String value, int clientAction, boolean openChat) {
        this.kind = kind;
        this.value = value;
        this.clientAction = clientAction;
        this.openChat = openChat;
    }

    public static ButtonAction runCommand(String command) {
        return command == null || command.isEmpty() ? null : new ButtonAction(Kind.RUN_COMMAND, command, 0, false);
    }

    public static ButtonAction openUrl(String url) {
        return url == null || url.isEmpty() ? null : new ButtonAction(Kind.OPEN_URL, url, 0, false);
    }

    public static ButtonAction clientAction(int action) {
        return action <= 0 ? null : new ButtonAction(Kind.CLIENT_ACTION, null, action, false);
    }

    /** {@code SelectChatTabAction}: {@code tab_id} (#1), {@code open_chat} (#2). */
    public static ButtonAction parseSelectChatTab(ByteString data) throws Exception {
        CodedInputStream in = data.newCodedInput();
        String tab = null;
        boolean open = false;
        int tag;
        while ((tag = in.readTag()) != 0) {
            switch (WireFormat.getTagFieldNumber(tag)) {
                case 1:
                    tab = in.readString();
                    break;
                case 2:
                    open = in.readBool();
                    break;
                default:
                    in.skipField(tag);
            }
        }
        return tab == null || tab.isEmpty() ? null : new ButtonAction(Kind.SELECT_CHAT_TAB, tab, 0, open);
    }

    /**
     * Runs the action from {@code screen} (where the player returns after a prompt). {@code done} gets
     * {@code true} once the action really happened, {@code false} when the player cancelled a prompt or
     * the action could not run.
     */
    public void perform(GuiScreen screen, Consumer<Boolean> done) {
        Minecraft mc = Minecraft.getMinecraft();
        switch (this.kind) {
            case RUN_COMMAND:
                String command = this.value.startsWith("/") ? this.value : "/" + this.value;
                mc.displayGuiScreen(new GuiYesNo(fr.natsu.artemis.gui.YesNo.of(accepted -> {
                    mc.displayGuiScreen(screen);
                    if (accepted && mc.thePlayer != null) {
                        if (ClientCommandHandler.instance.executeCommand(mc.thePlayer, command) == 0) {
                            mc.thePlayer.sendChatMessage(command);
                        }
                    }
                    done.accept(accepted);
                }), "Run this command?", command, 0));
                break;
            case OPEN_URL:
                openUrl(mc, screen, this.value, done);
                break;
            case CLIENT_ACTION:
                if (this.clientAction == CLIENT_OPEN_WAYPOINTS_MENU) {
                    mc.displayGuiScreen(new GuiPointList(screen));
                    done.accept(true);
                } else {
                    Artemis.LOGGER.info("[Button] client action {} has no Forge equivalent", this.clientAction);
                    done.accept(false);
                }
                break;
            case SELECT_CHAT_TAB:
                ChatTabs.select(this.value, this.openChat);
                done.accept(true);
                break;
            default:
                done.accept(false);
        }
    }

    public static void openUrl(Minecraft mc, GuiScreen screen, String url, Consumer<Boolean> done) {
        URI uri;
        try {
            uri = new URI(url);
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                done.accept(false);
                return;
            }
        } catch (Exception e) {
            done.accept(false);
            return;
        }
        if (!mc.gameSettings.chatLinks) {
            done.accept(false);
            return;
        }
        if (!mc.gameSettings.chatLinksPrompt) {
            done.accept(browse(uri));
            return;
        }
        URI target = uri;
        mc.displayGuiScreen(new GuiConfirmOpenLink(fr.natsu.artemis.gui.YesNo.of(accepted -> {
            mc.displayGuiScreen(screen);
            done.accept(accepted && browse(target));
        }), url, 0, false));
    }

    private static boolean browse(URI uri) {
        try {
            Class<?> desktop = Class.forName("java.awt.Desktop");
            Object instance = desktop.getMethod("getDesktop").invoke(null);
            desktop.getMethod("browse", URI.class).invoke(instance, uri);
            return true;
        } catch (Throwable t) {
            Artemis.LOGGER.warn("[Button] could not open {}: {}", uri, t.toString());
            return false;
        }
    }
}
