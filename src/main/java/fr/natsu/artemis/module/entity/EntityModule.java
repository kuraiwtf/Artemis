package fr.natsu.artemis.module.entity;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.google.protobuf.Any;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;

import fr.natsu.artemis.Artemis;
import fr.natsu.artemis.net.ApolloNetwork;
import fr.natsu.artemis.net.ApolloProtos;
import net.minecraft.entity.Entity;

/**
 * Entity module ({@code lunarclient.apollo.entity.v1}): per-entity rainbow wool on sheep (the
 * {@code jeb_} effect) and upside-down rendering (the {@code Dinnerbone} effect), without renaming
 * the entity. Every message carries {@code entity_ids} (#1, repeated EntityId).
 */
public final class EntityModule {

    private static final String PREFIX = "lunarclient.apollo.entity.v1.";

    private static final Set<Integer> RAINBOW_IDS = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Set<UUID> RAINBOW_UUIDS = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Set<Integer> FLIPPED_IDS = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Set<UUID> FLIPPED_UUIDS = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private EntityModule() {
    }

    public static void register(ApolloNetwork network) {
        network.register(PREFIX + "OverrideRainbowSheepMessage", m -> apply(m, RAINBOW_IDS, RAINBOW_UUIDS, true));
        network.register(PREFIX + "ResetRainbowSheepMessage", m -> apply(m, RAINBOW_IDS, RAINBOW_UUIDS, false));
        network.register(PREFIX + "FlipEntityMessage", m -> apply(m, FLIPPED_IDS, FLIPPED_UUIDS, true));
        // The typo is Apollo's.
        network.register(PREFIX + "ResetFlipedEntityMessage", m -> apply(m, FLIPPED_IDS, FLIPPED_UUIDS, false));
        network.onDisconnect(() -> {
            RAINBOW_IDS.clear();
            RAINBOW_UUIDS.clear();
            FLIPPED_IDS.clear();
            FLIPPED_UUIDS.clear();
        });
    }

    public static boolean isRainbow(Entity entity) {
        return matches(entity, RAINBOW_IDS, RAINBOW_UUIDS);
    }

    public static boolean isFlipped(Entity entity) {
        return matches(entity, FLIPPED_IDS, FLIPPED_UUIDS);
    }

    private static boolean matches(Entity entity, Set<Integer> ids, Set<UUID> uuids) {
        if (ids.isEmpty() && uuids.isEmpty()) {
            return false;
        }
        return ids.contains(entity.getEntityId()) || uuids.contains(entity.getUniqueID());
    }

    private static void apply(Any message, Set<Integer> ids, Set<UUID> uuids, boolean add) throws Exception {
        CodedInputStream in = message.getValue().newCodedInput();
        int count = 0;
        int tag;
        while ((tag = in.readTag()) != 0) {
            if (WireFormat.getTagFieldNumber(tag) != 1) {
                in.skipField(tag);
                continue;
            }
            ApolloProtos.EntityRef ref = ApolloProtos.parseEntityId(in.readBytes());
            if (ref.id >= 0) {
                if (add) {
                    ids.add(ref.id);
                } else {
                    ids.remove(ref.id);
                }
            }
            if (ref.uuid != null) {
                if (add) {
                    uuids.add(ref.uuid);
                } else {
                    uuids.remove(ref.uuid);
                }
            }
            count++;
        }
        Artemis.LOGGER.info("[Entity] {} {} entit{}", add ? "applied to" : "reset on", count, count == 1 ? "y" : "ies");
    }
}
