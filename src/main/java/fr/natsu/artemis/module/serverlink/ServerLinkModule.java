package fr.natsu.artemis.module.serverlink;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;

import fr.natsu.artemis.Artemis;
import fr.natsu.artemis.gui.GuiServerLinks;
import fr.natsu.artemis.module.button.ButtonContent;
import fr.natsu.artemis.module.configurable.ConfigurableModule;
import fr.natsu.artemis.net.ApolloNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Server links ({@code lunarclient.apollo.serverlink.v1}): a "Server Links" button in the pause menu
 * opening a list of the server's links. Like Lunar on 1.7-1.12, the button goes on a new row, or
 * replaces Achievements / Statistics per the {@code legacy-button-placement} option.
 *
 * <ul>
 *   <li>{@code AddServerLinkMessage}: {@code server_links} (#1, each id #1, display name JSON #2, url #3)</li>
 *   <li>{@code RemoveServerLinkMessage}: {@code server_link_ids} (#1, repeated)</li>
 *   <li>{@code OverrideServerLinkResourceMessage}: {@code icon} (#1, ResourceLocationIcon: location #1),
 *       the image shown at the top of the links screen</li>
 * </ul>
 */
public final class ServerLinkModule {

    private static final String PREFIX = "lunarclient.apollo.serverlink.v1.";
    private static final String MODULE_ID = "server_link";

    private static final int BUTTON_ID = 0xA57E;
    private static final int ACHIEVEMENTS_ID = 5;
    private static final int STATISTICS_ID = 6;

    public static final class Link {
        public final String id;
        public final String name;
        public final String url;

        Link(String id, String name, String url) {
            this.id = id;
            this.name = name;
            this.url = url;
        }
    }

    private static final Map<String, Link> LINKS = new LinkedHashMap<>();
    private static volatile List<Link> snapshot = Collections.emptyList();
    private static volatile ResourceLocation icon;

    public static void register(ApolloNetwork network) {
        network.register(PREFIX + "AddServerLinkMessage", ServerLinkModule::onAdd);
        network.register(PREFIX + "RemoveServerLinkMessage", ServerLinkModule::onRemove);
        network.register(PREFIX + "ResetServerLinksMessage", m -> resetLinks());
        network.register(PREFIX + "OverrideServerLinkResourceMessage", ServerLinkModule::onIcon);
        network.register(PREFIX + "ResetServerLinkResourceMessage", m -> icon = null);
        network.onDisconnect(() -> {
            resetLinks();
            icon = null;
        });
    }

    public static List<Link> links() {
        return snapshot;
    }

    public static ResourceLocation icon() {
        return icon;
    }

    @SubscribeEvent
    public void onPauseMenu(GuiScreenEvent.InitGuiEvent.Post event) {
        if (!(event.gui instanceof GuiIngameMenu) || snapshot.isEmpty()) {
            return;
        }
        Object placement = ConfigurableModule.get(MODULE_ID, "legacy-button-placement");
        String label = "Server Links";
        int replace = "REPLACE_ACHIEVEMENTS".equals(placement) ? ACHIEVEMENTS_ID
            : "REPLACE_STATISTICS".equals(placement) ? STATISTICS_ID : -1;
        if (replace >= 0) {
            for (GuiButton button : event.buttonList) {
                if (button.id == replace) {
                    button.id = BUTTON_ID;
                    button.displayString = label;
                    button.enabled = true;
                    return;
                }
            }
        }
        int width = event.gui.width;
        int height = event.gui.height;
        event.buttonList.add(new GuiButton(BUTTON_ID, width / 2 - 100, height / 4 + 144 - 16, label));
    }

    @SubscribeEvent
    public void onPauseButton(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (event.gui instanceof GuiIngameMenu && event.button.id == BUTTON_ID) {
            event.setCanceled(true);
            Minecraft.getMinecraft().displayGuiScreen(new GuiServerLinks(event.gui));
        }
    }

    private static void onAdd(Any message) throws Exception {
        CodedInputStream in = message.getValue().newCodedInput();
        int tag;
        while ((tag = in.readTag()) != 0) {
            if (WireFormat.getTagFieldNumber(tag) == 1) {
                Link link = parseLink(in.readBytes());
                if (link != null) {
                    synchronized (LINKS) {
                        LINKS.put(link.id, link);
                        publish();
                    }
                }
            } else {
                in.skipField(tag);
            }
        }
    }

    private static Link parseLink(ByteString data) throws Exception {
        CodedInputStream in = data.newCodedInput();
        String id = null;
        String name = null;
        String url = null;
        int tag;
        while ((tag = in.readTag()) != 0) {
            switch (WireFormat.getTagFieldNumber(tag)) {
                case 1:
                    id = in.readString();
                    break;
                case 2:
                    name = ButtonContent.encode(in.readString());
                    break;
                case 3:
                    url = in.readString();
                    break;
                default:
                    in.skipField(tag);
            }
        }
        if (id == null || url == null || url.isEmpty()) {
            return null;
        }
        return new Link(id, name == null || name.isEmpty() ? url : name, url);
    }

    private static void onRemove(Any message) throws Exception {
        CodedInputStream in = message.getValue().newCodedInput();
        int tag;
        while ((tag = in.readTag()) != 0) {
            if (WireFormat.getTagFieldNumber(tag) == 1) {
                String id = in.readString();
                synchronized (LINKS) {
                    LINKS.remove(id);
                    publish();
                }
            } else {
                in.skipField(tag);
            }
        }
    }

    private static void onIcon(Any message) throws Exception {
        CodedInputStream in = message.getValue().newCodedInput();
        int tag;
        while ((tag = in.readTag()) != 0) {
            if (WireFormat.getTagFieldNumber(tag) == 1) {
                CodedInputStream iconIn = in.readBytes().newCodedInput();
                int iconTag;
                while ((iconTag = iconIn.readTag()) != 0) {
                    if (WireFormat.getTagFieldNumber(iconTag) == 1) {
                        String location = iconIn.readString();
                        icon = location.isEmpty() ? null : new ResourceLocation(location);
                    } else {
                        iconIn.skipField(iconTag);
                    }
                }
            } else {
                in.skipField(tag);
            }
        }
        Artemis.LOGGER.info("[ServerLink] icon {}", icon);
    }

    private static void resetLinks() {
        synchronized (LINKS) {
            LINKS.clear();
            publish();
        }
    }

    private static void publish() {
        snapshot = Collections.unmodifiableList(new ArrayList<>(LINKS.values()));
    }
}
