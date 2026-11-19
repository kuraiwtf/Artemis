package fr.natsu.artemis;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import fr.natsu.artemis.command.ArtemisCommand;
import fr.natsu.artemis.gui.ArtemisGuiController;
import fr.natsu.artemis.module.beam.BeamModule;
import fr.natsu.artemis.module.border.BorderModule;
import fr.natsu.artemis.gui.ChatOverlay;
import fr.natsu.artemis.gui.InventoryButtonOverlay;
import fr.natsu.artemis.module.chat.ChatUiModule;
import fr.natsu.artemis.module.chat.LiveChatModule;
import fr.natsu.artemis.module.inventory.InventoryButtonsModule;
import fr.natsu.artemis.module.pingmarker.PingMarkerModule;
import fr.natsu.artemis.module.serverlink.ServerLinkModule;
import fr.natsu.artemis.render.PingMarkerRenderer;
import fr.natsu.artemis.module.configurable.ConfigurableModule;
import fr.natsu.artemis.module.cooldown.CooldownModule;
import fr.natsu.artemis.module.entity.EntityModule;
import fr.natsu.artemis.module.heightlimit.HeightLimitModule;
import fr.natsu.artemis.module.nickhider.NickHiderModule;
import fr.natsu.artemis.module.player.PlayerWorld;
import fr.natsu.artemis.module.serverrule.ServerRules;
import fr.natsu.artemis.module.stopwatch.StopwatchModule;
import fr.natsu.artemis.module.title.TitleModule;
import fr.natsu.artemis.module.tntcountdown.TntCountdownModule;
import fr.natsu.artemis.module.glow.GlowModule;
import fr.natsu.artemis.module.hologram.HologramModule;
import fr.natsu.artemis.module.nametag.NametagModule;
import fr.natsu.artemis.module.coloredfire.ColoredFireModule;
import fr.natsu.artemis.module.events.ArtemisEvents;
import fr.natsu.artemis.module.icon.IconRegistry;
import fr.natsu.artemis.module.limb.LimbModule;
import fr.natsu.artemis.module.marker.MarkerModule;
import fr.natsu.artemis.module.model.ModelRegistry;
import fr.natsu.artemis.module.notification.NotificationModule;
import fr.natsu.artemis.module.team.TeamModule;
import fr.natsu.artemis.module.vignette.VignetteModule;
import fr.natsu.artemis.module.waypoint.WaypointModule;
import fr.natsu.artemis.net.ApolloNetwork;
import fr.natsu.artemis.render.BeamRenderer;
import fr.natsu.artemis.render.BorderRenderer;
import fr.natsu.artemis.render.BossBarRenderer;
import fr.natsu.artemis.render.CooldownRenderer;
import fr.natsu.artemis.render.CustomModelRenderer;
import fr.natsu.artemis.render.HeightLimitRenderer;
import fr.natsu.artemis.render.StopwatchRenderer;
import fr.natsu.artemis.render.TitleRenderer;
import fr.natsu.artemis.render.TntCountdownRenderer;
import fr.natsu.artemis.render.GlowOverlayListener;
import fr.natsu.artemis.render.HologramRenderer;
import fr.natsu.artemis.render.LightningRenderer;
import fr.natsu.artemis.render.MarkerRenderer;
import fr.natsu.artemis.render.NametagRenderer;
import fr.natsu.artemis.render.NotificationRenderer;
import fr.natsu.artemis.render.TeamRenderer;
import fr.natsu.artemis.render.VignetteRenderer;
import fr.natsu.artemis.render.WaypointRenderer;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

@Mod(
    modid = Artemis.MOD_ID,
    name = Artemis.MOD_NAME,
    version = Artemis.VERSION,
    clientSideOnly = true,
    acceptedMinecraftVersions = "[1.8.9]"
)
public final class Artemis {

    public static final String MOD_ID = "artemis";
    public static final String MOD_NAME = "Artemis";
    public static final String VERSION = "0.2.0";

    public static final Logger LOGGER = LogManager.getLogger(MOD_NAME);

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        ArtemisConfig.init(event.getModConfigurationDirectory());
        LOGGER.info("[Artemis] preInit - Forge -> Apollo (Lunar Client) bridge for 1.8.9");
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        ApolloNetwork network = ApolloNetwork.getInstance();
        safe("MinecraftForge.EVENT_BUS.register(network)", () -> MinecraftForge.EVENT_BUS.register(network));
        boolean lunar = LunarHost.isLunar();

        safe("GlowModule.register(network)", () -> GlowModule.register(network));
        safe("MinecraftForge.EVENT_BUS.register(new GlowOverlayListener())", () -> MinecraftForge.EVENT_BUS.register(new GlowOverlayListener()));
        safe("VignetteModule.register(network)", () -> VignetteModule.register(network));
        safe("MinecraftForge.EVENT_BUS.register(new VignetteRenderer())", () -> MinecraftForge.EVENT_BUS.register(new VignetteRenderer()));
        safe("ModelRegistry.init()", () -> ModelRegistry.init());
        safe("IconRegistry.init()", () -> IconRegistry.init());
        safe("MinecraftForge.EVENT_BUS.register(new CustomModelRenderer())", () -> MinecraftForge.EVENT_BUS.register(new CustomModelRenderer()));
        safe("MinecraftForge.EVENT_BUS.register(new BossBarRenderer())", () -> MinecraftForge.EVENT_BUS.register(new BossBarRenderer()));
        safe("MinecraftForge.EVENT_BUS.register(new LightningRenderer())", () -> MinecraftForge.EVENT_BUS.register(new LightningRenderer()));
        if (lunar) {
            LOGGER.info("[Artemis] init - Lunar profile: Glow/Vignette/Chat/Lightning/Model/Icon/BossBar modules active");
            return;
        }

        safe("NametagModule.register(network)", () -> NametagModule.register(network));
        safe("MinecraftForge.EVENT_BUS.register(new NametagRenderer())", () -> MinecraftForge.EVENT_BUS.register(new NametagRenderer()));
        safe("CooldownModule.register(network)", () -> CooldownModule.register(network));
        safe("MinecraftForge.EVENT_BUS.register(new CooldownRenderer())", () -> MinecraftForge.EVENT_BUS.register(new CooldownRenderer()));
        safe("TeamModule.register(network)", () -> TeamModule.register(network));
        safe("MinecraftForge.EVENT_BUS.register(new TeamRenderer())", () -> MinecraftForge.EVENT_BUS.register(new TeamRenderer()));
        safe("WaypointModule.register(network)", () -> WaypointModule.register(network));
        safe("MinecraftForge.EVENT_BUS.register(new WaypointRenderer())", () -> MinecraftForge.EVENT_BUS.register(new WaypointRenderer()));
        safe("MarkerModule.register(network)", () -> MarkerModule.register(network));
        safe("MinecraftForge.EVENT_BUS.register(new MarkerRenderer())", () -> MinecraftForge.EVENT_BUS.register(new MarkerRenderer()));
        safe("BeamModule.register(network)", () -> BeamModule.register(network));
        safe("MinecraftForge.EVENT_BUS.register(new BeamRenderer())", () -> MinecraftForge.EVENT_BUS.register(new BeamRenderer()));
        safe("NotificationModule.register(network)", () -> NotificationModule.register(network));
        safe("MinecraftForge.EVENT_BUS.register(new NotificationRenderer())", () -> MinecraftForge.EVENT_BUS.register(new NotificationRenderer()));
        safe("BorderModule.register(network)", () -> BorderModule.register(network));
        safe("MinecraftForge.EVENT_BUS.register(new BorderRenderer())", () -> MinecraftForge.EVENT_BUS.register(new BorderRenderer()));
        safe("HologramModule.register(network)", () -> HologramModule.register(network));
        safe("MinecraftForge.EVENT_BUS.register(new HologramRenderer())", () -> MinecraftForge.EVENT_BUS.register(new HologramRenderer()));
        safe("ColoredFireModule.register(network)", () -> ColoredFireModule.register(network));
        safe("LimbModule.register(network)", () -> LimbModule.register(network));
        safe("ConfigurableModule.register(network)", () -> ConfigurableModule.register(network));
        safe("PlayerWorld.register(network)", () -> PlayerWorld.register(network));
        safe("MinecraftForge.EVENT_BUS.register(new ServerRules())", () -> MinecraftForge.EVENT_BUS.register(new ServerRules()));
        safe("LiveChatModule.register(network)", () -> LiveChatModule.register(network));
        safe("ChatUiModule.register(network)", () -> ChatUiModule.register(network));
        safe("MinecraftForge.EVENT_BUS.register(new ChatOverlay())", () -> MinecraftForge.EVENT_BUS.register(new ChatOverlay()));
        safe("fr.natsu.artemis.module.button.ApolloIcon.registerReloadListener()", () -> fr.natsu.artemis.module.button.ApolloIcon.registerReloadListener());
        safe("InventoryButtonsModule.register(network)", () -> InventoryButtonsModule.register(network));
        safe("MinecraftForge.EVENT_BUS.register(new InventoryButtonOverlay())", () -> MinecraftForge.EVENT_BUS.register(new InventoryButtonOverlay()));
        safe("ServerLinkModule.register(network)", () -> ServerLinkModule.register(network));
        safe("MinecraftForge.EVENT_BUS.register(new ServerLinkModule())", () -> MinecraftForge.EVENT_BUS.register(new ServerLinkModule()));
        safe("PingMarkerModule.register(network)", () -> PingMarkerModule.register(network));
        safe("MinecraftForge.EVENT_BUS.register(new PingMarkerModule())", () -> MinecraftForge.EVENT_BUS.register(new PingMarkerModule()));
        safe("MinecraftForge.EVENT_BUS.register(new PingMarkerRenderer())", () -> MinecraftForge.EVENT_BUS.register(new PingMarkerRenderer()));
        safe("TitleModule.register(network)", () -> TitleModule.register(network));
        safe("MinecraftForge.EVENT_BUS.register(new TitleRenderer())", () -> MinecraftForge.EVENT_BUS.register(new TitleRenderer()));
        safe("StopwatchModule.register(network)", () -> StopwatchModule.register(network));
        safe("MinecraftForge.EVENT_BUS.register(new StopwatchModule())", () -> MinecraftForge.EVENT_BUS.register(new StopwatchModule()));
        safe("MinecraftForge.EVENT_BUS.register(new StopwatchRenderer())", () -> MinecraftForge.EVENT_BUS.register(new StopwatchRenderer()));
        safe("TntCountdownModule.register(network)", () -> TntCountdownModule.register(network));
        safe("MinecraftForge.EVENT_BUS.register(new TntCountdownModule())", () -> MinecraftForge.EVENT_BUS.register(new TntCountdownModule()));
        safe("MinecraftForge.EVENT_BUS.register(new TntCountdownRenderer())", () -> MinecraftForge.EVENT_BUS.register(new TntCountdownRenderer()));
        safe("EntityModule.register(network)", () -> EntityModule.register(network));
        safe("HeightLimitModule.register(network)", () -> HeightLimitModule.register(network));
        safe("MinecraftForge.EVENT_BUS.register(new HeightLimitRenderer())", () -> MinecraftForge.EVENT_BUS.register(new HeightLimitRenderer()));
        safe("NickHiderModule.register(network)", () -> NickHiderModule.register(network));
        safe("MinecraftForge.EVENT_BUS.register(new ArtemisEvents())", () -> MinecraftForge.EVENT_BUS.register(new ArtemisEvents()));
        safe("ClientCommandHandler.instance.registerCommand(new ArtemisCommand())", () -> ClientCommandHandler.instance.registerCommand(new ArtemisCommand()));
        safe("MinecraftForge.EVENT_BUS.register(new ArtemisGuiController())", () -> MinecraftForge.EVENT_BUS.register(new ArtemisGuiController()));
        LOGGER.info("[Artemis] init - Apollo network + Glow/Nametag/Cooldown/Vignette/TeamView/Waypoint/Marker/Beam/Notification/Border/Hologram/Model/Chat/Title/Stopwatch/TntCountdown/Entity/HeightLimit/NickHider/ServerRule/ChatUI/InventoryButtons/ServerLink/PingMarker modules active");
    }

    /**
     * Runs one registration step; a failure is logged and skips only that step, instead of aborting
     * the whole init (which leaves every later module silently unregistered).
     */
    private static void safe(String step, Runnable action) {
        try {
            action.run();
        } catch (Throwable t) {
            LOGGER.error("[Artemis] init step failed: {}", step, t);
        }
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        MixinAudit.report();
        LOGGER.info("[Artemis] postInit - ready");
    }
}
