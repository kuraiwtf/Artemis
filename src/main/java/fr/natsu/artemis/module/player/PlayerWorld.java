package fr.natsu.artemis.module.player;

import com.google.protobuf.Any;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;

import fr.natsu.artemis.Artemis;
import fr.natsu.artemis.net.ApolloNetwork;

/**
 * The server-side name of the world the player is in. The client has no idea what the server calls
 * its worlds, so Apollo sends {@code player.v1.UpdatePlayerWorldMessage} ({@code world} #1) on join
 * and on every world change; world-scoped modules (height limit) key on it.
 */
public final class PlayerWorld {

    private static final String UPDATE = "lunarclient.apollo.player.v1.UpdatePlayerWorldMessage";

    private static volatile String current;

    private PlayerWorld() {
    }

    public static void register(ApolloNetwork network) {
        network.register(UPDATE, PlayerWorld::onUpdate);
        network.onDisconnect(() -> current = null);
    }

    /** The current world name, or {@code null} if the server never said. */
    public static String current() {
        return current;
    }

    private static void onUpdate(Any message) throws Exception {
        CodedInputStream in = message.getValue().newCodedInput();
        int tag;
        while ((tag = in.readTag()) != 0) {
            if (WireFormat.getTagFieldNumber(tag) == 1) {
                current = in.readString();
                Artemis.LOGGER.info("[Player] world is now {}", current);
            } else {
                in.skipField(tag);
            }
        }
    }
}
