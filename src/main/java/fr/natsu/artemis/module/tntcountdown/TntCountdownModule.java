package fr.natsu.artemis.module.tntcountdown;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.google.protobuf.Any;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;

import fr.natsu.artemis.module.configurable.ConfigurableModule;
import fr.natsu.artemis.net.ApolloNetwork;
import fr.natsu.artemis.net.ApolloProtos;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityTNTPrimed;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * TNT Countdown module ({@code lunarclient.apollo.tntcountdown.v1}). The server owns the real fuse;
 * the client only needs a matching fuse on its own copy of the entity so the countdown it shows is
 * right. Vanilla clients always assume 80 ticks.
 *
 * <ul>
 *   <li>{@code tnt-ticks} option: fuse for every new primed TNT (default 80)</li>
 *   <li>{@code SetTntCountdownMessage}: {@code entity_id} (#1, EntityId), {@code duration_ticks}
 *       (#2, int32) for one TNT entity</li>
 * </ul>
 */
public final class TntCountdownModule {

    public static final String MODULE_ID = "tnt_countdown";

    private static final String SET = "lunarclient.apollo.tntcountdown.v1.SetTntCountdownMessage";
    private static final int VANILLA_FUSE = 80;
    /** A custom fuse that arrived before its entity is kept this long. */
    private static final long PENDING_MILLIS = 5000L;

    private static final Map<Integer, long[]> PENDING = new ConcurrentHashMap<>();

    public static void register(ApolloNetwork network) {
        network.register(SET, TntCountdownModule::onSet);
        network.onDisconnect(PENDING::clear);
    }

    public static boolean isActive() {
        return ConfigurableModule.isEnabled(MODULE_ID);
    }

    private static void onSet(Any message) throws Exception {
        CodedInputStream in = message.getValue().newCodedInput();
        ApolloProtos.EntityRef ref = null;
        int ticks = -1;
        int tag;
        while ((tag = in.readTag()) != 0) {
            switch (WireFormat.getTagFieldNumber(tag)) {
                case 1:
                    ref = ApolloProtos.parseEntityId(in.readBytes());
                    break;
                case 2:
                    ticks = in.readInt32();
                    break;
                default:
                    in.skipField(tag);
            }
        }
        if (ref == null || ticks < 0) {
            return;
        }
        ApolloProtos.EntityRef target = ref;
        int fuse = ticks;
        Minecraft mc = Minecraft.getMinecraft();
        mc.addScheduledTask(() -> {
            if (mc.theWorld != null) {
                Entity entity = target.id >= 0 ? mc.theWorld.getEntityByID(target.id) : null;
                if (entity == null && target.uuid != null) {
                    for (Entity candidate : mc.theWorld.loadedEntityList) {
                        if (target.matches(candidate)) {
                            entity = candidate;
                            break;
                        }
                    }
                }
                if (entity instanceof EntityTNTPrimed) {
                    ((EntityTNTPrimed) entity).fuse = fuse;
                    return;
                }
            }
            if (target.id >= 0) {
                PENDING.put(target.id, new long[] {fuse, System.currentTimeMillis()});
            }
        });
    }

    @SubscribeEvent
    public void onJoin(EntityJoinWorldEvent event) {
        if (!(event.entity instanceof EntityTNTPrimed) || !event.world.isRemote) {
            return;
        }
        EntityTNTPrimed tnt = (EntityTNTPrimed) event.entity;
        long now = System.currentTimeMillis();
        long[] pending = PENDING.remove(tnt.getEntityId());
        if (pending != null && now - pending[1] <= PENDING_MILLIS) {
            tnt.fuse = (int) pending[0];
        } else {
            int ticks = ConfigurableModule.getInt(MODULE_ID, "tnt-ticks", VANILLA_FUSE);
            if (ticks > 0) {
                tnt.fuse = ticks;
            }
        }
        if (!PENDING.isEmpty()) {
            for (Iterator<long[]> it = PENDING.values().iterator(); it.hasNext(); ) {
                if (now - it.next()[1] > PENDING_MILLIS) {
                    it.remove();
                }
            }
        }
    }
}
