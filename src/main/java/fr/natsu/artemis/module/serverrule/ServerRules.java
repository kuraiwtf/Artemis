package fr.natsu.artemis.module.serverrule;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import fr.natsu.artemis.Artemis;
import fr.natsu.artemis.module.configurable.ConfigurableModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiYesNo;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * The Apollo server rules ({@code server_rule} module options) that have a 1.8.9 Forge equivalent:
 *
 * <ul>
 *   <li>{@code competitive-game} + {@code competitive-commands}: confirmation before leaving through
 *       the pause menu or one of the listed commands</li>
 *   <li>{@code anti-portal-traps}: chat stays open while standing in a nether portal
 *       ({@code MixinEntityPlayerSP})</li>
 *   <li>{@code override-nametag-render-distance} + {@code nametag-render-distance}: the nametag
 *       range (Forge's {@code NAME_TAG_RANGE}, or vanilla's constant)</li>
 * </ul>
 *
 * <p>The rest (shaders, chunk/shader reloading, Twitch broadcasting, brightness, max chat length,
 * crystal optimizer) target Lunar features that a Forge 1.8.9 client does not have.</p>
 */
public final class ServerRules {

    public static final String MODULE_ID = "server_rule";

    private static final int DISCONNECT_BUTTON_ID = 1;
    private static final List<String> DEFAULT_COMPETITIVE_COMMANDS = Arrays.asList("/server", "/servers", "/hub");

    private static float vanillaNametagRange = Float.NaN;
    private static java.lang.reflect.Field forgeRange;
    private static boolean forgeRangeMissing;
    private static boolean bypassNextCommand;

    public static boolean antiPortalTraps() {
        return ConfigurableModule.isExplicitlyEnabled(MODULE_ID)
            && ConfigurableModule.getBoolean(MODULE_ID, "anti-portal-traps", true);
    }

    private static boolean competitive() {
        return ConfigurableModule.isExplicitlyEnabled(MODULE_ID)
            && ConfigurableModule.getBoolean(MODULE_ID, "competitive-game", false);
    }

    /** The server's nametag render distance, or {@code -1} when it doesn't override it. */
    public static float nametagRangeOverride() {
        boolean override = ConfigurableModule.isExplicitlyEnabled(MODULE_ID)
            && ConfigurableModule.getBoolean(MODULE_ID, "override-nametag-render-distance", false);
        if (!override) {
            return -1.0F;
        }
        return Math.max(1, Math.min(96, ConfigurableModule.getInt(MODULE_ID, "nametag-render-distance", 64)));
    }

    /**
     * Called after every settings update, and on disconnect (when the settings are gone). Real Forge
     * turns vanilla's hardcoded 64-block range into the static {@code NAME_TAG_RANGE}, which is set
     * here through reflection; without Forge (e.g. a Fabric client with a Forge compatibility layer)
     * the field does not exist and {@code MixinRendererLivingEntity} swaps the constant instead.
     */
    public static void applyNametagRange() {
        java.lang.reflect.Field field = forgeRangeField();
        if (field == null) {
            return;
        }
        try {
            if (Float.isNaN(vanillaNametagRange)) {
                vanillaNametagRange = field.getFloat(null);
            }
            float override = nametagRangeOverride();
            float range = override > 0.0F ? override : vanillaNametagRange;
            if (field.getFloat(null) != range) {
                field.setFloat(null, range);
                Artemis.LOGGER.info("[ServerRule] nametag render distance = {}", range);
            }
        } catch (Throwable t) {
            forgeRangeMissing = true;
        }
    }

    private static java.lang.reflect.Field forgeRangeField() {
        if (forgeRangeMissing) {
            return null;
        }
        if (forgeRange == null) {
            try {
                forgeRange = RendererLivingEntity.class.getField("NAME_TAG_RANGE");
            } catch (Throwable t) {
                forgeRangeMissing = true;
                Artemis.LOGGER.info("[ServerRule] no Forge NAME_TAG_RANGE, using the vanilla constant hook");
                return null;
            }
        }
        return forgeRange;
    }

    /**
     * Whether {@code message} must be confirmed first; if so the confirmation screen is queued and the
     * message is sent once the player accepts.
     */
    public static boolean interceptCommand(String message) {
        if (bypassNextCommand) {
            bypassNextCommand = false;
            return false;
        }
        if (message == null || !competitive() || !isCompetitiveCommand(message)) {
            return false;
        }
        Minecraft mc = Minecraft.getMinecraft();
        mc.addScheduledTask(() -> mc.displayGuiScreen(new GuiYesNo(fr.natsu.artemis.gui.YesNo.of(accepted -> {
            mc.displayGuiScreen(null);
            if (accepted && mc.thePlayer != null) {
                bypassNextCommand = true;
                mc.thePlayer.sendChatMessage(message);
                bypassNextCommand = false;
            }
        }), "You are in a competitive game.", "Run " + message.split(" ")[0] + " and leave anyway?", 0)));
        return true;
    }

    private static boolean isCompetitiveCommand(String message) {
        List<Object> configured = ConfigurableModule.getList(MODULE_ID, "competitive-commands");
        String lower = message.trim().toLowerCase(Locale.ROOT);
        for (Object command : configured != null ? configured : DEFAULT_COMPETITIVE_COMMANDS) {
            if (!(command instanceof String) || ((String) command).isEmpty()) {
                continue;
            }
            String c = ((String) command).toLowerCase(Locale.ROOT);
            if (!c.startsWith("/")) {
                c = "/" + c;
            }
            if (lower.equals(c) || lower.startsWith(c + " ")) {
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    public void onButton(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (!(event.gui instanceof GuiIngameMenu) || event.button.id != DISCONNECT_BUTTON_ID || !competitive()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.isIntegratedServerRunning()) {
            return;
        }
        event.setCanceled(true);
        GuiScreen menu = event.gui;
        GuiButton button = event.button;
        mc.displayGuiScreen(new GuiYesNo(fr.natsu.artemis.gui.YesNo.of(accepted -> {
            if (!accepted) {
                mc.displayGuiScreen(menu);
                return;
            }
            button.enabled = false;
            if (mc.theWorld != null) {
                mc.theWorld.sendQuittingDisconnectingPacket();
            }
            mc.loadWorld(null);
            mc.displayGuiScreen(new GuiMultiplayer(new GuiMainMenu()));
        }), "You are in a competitive game.", "Disconnect anyway?", 0));
    }
}
