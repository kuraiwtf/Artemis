package fr.natsu.artemis.module.heightlimit;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.google.protobuf.Any;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;

import fr.natsu.artemis.Artemis;
import fr.natsu.artemis.module.nametag.AdventureText;
import fr.natsu.artemis.module.nametag.AdventureText.Run;
import fr.natsu.artemis.module.player.PlayerWorld;
import fr.natsu.artemis.net.ApolloNetwork;

/**
 * Height Limit module ({@code lunarclient.apollo.heightlimit.v1}): a per-world Y level where the
 * server denies block placement, shown as a HUD line and a plane over the denied layer. Purely
 * visual: the server enforces it.
 *
 * <ul>
 *   <li>{@code OverrideHeightLimitMessage}: {@code world} (#1), {@code limit} (#2, int32),
 *       {@code display_name_adventure_json_lines} (#3)</li>
 *   <li>{@code RemoveHeightLimitMessage}: {@code world} (#1); {@code ResetHeightLimitsMessage}: empty</li>
 * </ul>
 */
public final class HeightLimitModule {

    private static final String PREFIX = "lunarclient.apollo.heightlimit.v1.";

    public static final class Limit {
        public final int limit;
        public final List<Run> displayName;

        Limit(int limit, List<Run> displayName) {
            this.limit = limit;
            this.displayName = displayName;
        }
    }

    private static final Map<String, Limit> LIMITS = new ConcurrentHashMap<>();

    private HeightLimitModule() {
    }

    public static void register(ApolloNetwork network) {
        network.register(PREFIX + "OverrideHeightLimitMessage", HeightLimitModule::onOverride);
        network.register(PREFIX + "RemoveHeightLimitMessage", HeightLimitModule::onRemove);
        network.register(PREFIX + "ResetHeightLimitsMessage", m -> LIMITS.clear());
        network.onDisconnect(LIMITS::clear);
    }

    /**
     * The limit for the player's current world. When the server never named the world, a lone limit is
     * assumed to apply.
     */
    public static Limit current() {
        if (LIMITS.isEmpty()) {
            return null;
        }
        String world = PlayerWorld.current();
        if (world != null) {
            return LIMITS.get(world);
        }
        return LIMITS.size() == 1 ? LIMITS.values().iterator().next() : null;
    }

    private static void onOverride(Any message) throws Exception {
        CodedInputStream in = message.getValue().newCodedInput();
        String world = null;
        int limit = 0;
        String json = null;
        int tag;
        while ((tag = in.readTag()) != 0) {
            switch (WireFormat.getTagFieldNumber(tag)) {
                case 1:
                    world = in.readString();
                    break;
                case 2:
                    limit = in.readInt32();
                    break;
                case 3:
                    json = in.readString();
                    break;
                default:
                    in.skipField(tag);
            }
        }
        if (world == null) {
            return;
        }
        List<Run> name = json == null || json.isEmpty() ? Collections.<Run>emptyList() : AdventureText.parse(json);
        LIMITS.put(world, new Limit(limit, name));
        Artemis.LOGGER.info("[HeightLimit] {} -> y={}", world, limit);
    }

    private static void onRemove(Any message) throws Exception {
        CodedInputStream in = message.getValue().newCodedInput();
        int tag;
        while ((tag = in.readTag()) != 0) {
            if (WireFormat.getTagFieldNumber(tag) == 1) {
                LIMITS.remove(in.readString());
            } else {
                in.skipField(tag);
            }
        }
    }
}
