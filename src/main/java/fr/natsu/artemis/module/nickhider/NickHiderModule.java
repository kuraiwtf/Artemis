package fr.natsu.artemis.module.nickhider;

import com.google.protobuf.Any;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;

import fr.natsu.artemis.Artemis;
import fr.natsu.artemis.net.ApolloNetwork;
import net.minecraft.client.Minecraft;

/**
 * Nick Hider module ({@code lunarclient.apollo.nickhider.v1}): the server gives the player a
 * nickname, and every piece of text the client draws (chat, tab, nametags, scoreboard) shows it in
 * place of the player's own username. Applied in {@code MixinFontRenderer}.
 *
 * <p>{@code OverrideNickHiderMessage}: {@code nick} (#1); {@code ResetNickHiderMessage}: empty.</p>
 */
public final class NickHiderModule {

    private static final String OVERRIDE = "lunarclient.apollo.nickhider.v1.OverrideNickHiderMessage";
    private static final String RESET = "lunarclient.apollo.nickhider.v1.ResetNickHiderMessage";

    private static volatile String nick;
    private static volatile String username;

    private NickHiderModule() {
    }

    public static void register(ApolloNetwork network) {
        network.register(OVERRIDE, NickHiderModule::onOverride);
        network.register(RESET, message -> set(null));
        network.onDisconnect(() -> set(null));
    }

    /** {@code text} with the player's username swapped for the nick, or {@code text} itself. */
    public static String apply(String text) {
        String currentNick = nick;
        String currentName = username;
        if (currentNick == null || currentName == null || text == null || text.indexOf(currentName) < 0) {
            return text;
        }
        return text.replace(currentName, currentNick);
    }

    private static void set(String value) {
        if (value == null || value.isEmpty()) {
            nick = null;
            Artemis.LOGGER.info("[NickHider] reset");
            return;
        }
        String name = Minecraft.getMinecraft().getSession().getUsername();
        username = name == null || name.isEmpty() ? null : name;
        nick = value;
        Artemis.LOGGER.info("[NickHider] nick set");
    }

    private static void onOverride(Any message) throws Exception {
        CodedInputStream in = message.getValue().newCodedInput();
        int tag;
        while ((tag = in.readTag()) != 0) {
            if (WireFormat.getTagFieldNumber(tag) == 1) {
                set(in.readString());
            } else {
                in.skipField(tag);
            }
        }
    }
}
