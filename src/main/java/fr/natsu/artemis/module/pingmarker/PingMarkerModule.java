package fr.natsu.artemis.module.pingmarker;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.lwjgl.input.Keyboard;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.WireFormat;

import fr.natsu.artemis.Artemis;
import fr.natsu.artemis.module.button.ApolloIcon;
import fr.natsu.artemis.module.configurable.ConfigurableModule;
import fr.natsu.artemis.module.player.PlayerWorld;
import fr.natsu.artemis.net.ApolloNetwork;
import fr.natsu.artemis.net.ApolloProtos;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;

/**
 * Ping markers ({@code lunarclient.apollo.pingmarker.v1}): the player presses the ping key, the
 * client asks the server to ping the looked-at spot ({@code RequestPlayerPingMessage}: id #1,
 * type #2, source_location #3, target_location #4), and the server decides who sees the resulting
 * marker ({@code DisplayPlayerPingMessage}: id #1, type #2, location #3, color #4, icon #5, sound #6,
 * duration #7, focus #8). Ping types ({@code SetPingMarkerType*}) give each type an icon.
 *
 * <p>Requests are only sent once the server has shown it runs the module (enabled in its settings,
 * or ping types sent), so the key does nothing elsewhere.</p>
 */
public final class PingMarkerModule {

    private static final String PREFIX = "lunarclient.apollo.pingmarker.v1.";
    private static final String MODULE_ID = "ping_marker";
    private static final double REACH = 256.0;
    private static final long REQUEST_COOLDOWN_MILLIS = 500L;
    private static final long DEFAULT_DURATION_MILLIS = 8000L;

    public static final class Ping {
        public final UUID id;
        public final String type;
        public final double x;
        public final double y;
        public final double z;
        public final int color;
        public final ApolloIcon icon;
        public final boolean focus;
        public final long createdAt;
        public final long duration;

        Ping(UUID id, String type, double x, double y, double z, int color, ApolloIcon icon, boolean focus,
             long createdAt, long duration) {
            this.id = id;
            this.type = type;
            this.x = x;
            this.y = y;
            this.z = z;
            this.color = color;
            this.icon = icon;
            this.focus = focus;
            this.createdAt = createdAt;
            this.duration = duration;
        }
    }

    private static final Map<String, ApolloIcon> TYPES = new ConcurrentHashMap<>();
    private static final Map<UUID, Ping> PINGS = new ConcurrentHashMap<>();

    private final KeyBinding pingKey = new KeyBinding("artemis.key.ping", Keyboard.KEY_Z, "artemis.key.category");
    private long lastRequest;

    public static void register(ApolloNetwork network) {
        network.register(PREFIX + "SetPingMarkerTypeMessage", m -> putType(m.getValue()));
        network.register(PREFIX + "SetPingMarkerTypesMessage", PingMarkerModule::onSetTypes);
        network.register(PREFIX + "RemovePingMarkerTypeMessage", m -> {
            String id = readString(m.getValue(), 1);
            if (id != null) {
                TYPES.remove(id);
            }
        });
        network.register(PREFIX + "ResetPingMarkerTypesMessage", m -> TYPES.clear());
        network.register(PREFIX + "DisplayPlayerPingMessage", PingMarkerModule::onDisplay);
        network.register(PREFIX + "RemovePlayerPingMessage", PingMarkerModule::onRemove);
        network.register(PREFIX + "ResetPlayerPingMessage", m -> PINGS.clear());
        network.onDisconnect(() -> {
            TYPES.clear();
            PINGS.clear();
        });
    }

    public PingMarkerModule() {
        ClientRegistry.registerKeyBinding(this.pingKey);
    }

    public static Collection<Ping> pings() {
        return PINGS.values();
    }

    public static ApolloIcon typeIcon(String type) {
        return type == null ? null : TYPES.get(type);
    }

    /** Drops pings past their duration; called from the renderer. */
    public static void expire(long now) {
        PINGS.values().removeIf(ping -> now - ping.createdAt >= ping.duration);
    }

    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent event) {
        handleKey();
    }

    @SubscribeEvent
    public void onMouse(InputEvent.MouseInputEvent event) {
        handleKey();
    }

    private void handleKey() {
        while (this.pingKey.isPressed()) {
            long now = System.currentTimeMillis();
            if (now - this.lastRequest >= REQUEST_COOLDOWN_MILLIS && active()) {
                this.lastRequest = now;
                requestPing();
            }
        }
    }

    private static boolean active() {
        return !TYPES.isEmpty() || ConfigurableModule.isExplicitlyEnabled(MODULE_ID);
    }

    private static void requestPing() {
        Minecraft mc = Minecraft.getMinecraft();
        Entity viewer = mc.getRenderViewEntity();
        NetHandlerPlayClient handler = mc.getNetHandler();
        if (viewer == null || handler == null || mc.theWorld == null || mc.currentScreen != null) {
            return;
        }
        Vec3 target = null;
        MovingObjectPosition over = mc.objectMouseOver;
        if (over != null && over.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY && over.entityHit != null) {
            Entity hit = over.entityHit;
            target = new Vec3(hit.posX, hit.posY + hit.height / 2.0, hit.posZ);
        } else {
            MovingObjectPosition ray = viewer.rayTrace(REACH, 1.0F);
            if (ray != null && ray.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                target = ray.hitVec;
            }
        }
        if (target == null) {
            return;
        }
        Vec3 eye = viewer.getPositionEyes(1.0F);
        String world = PlayerWorld.current() == null ? "" : PlayerWorld.current();
        String type = TYPES.isEmpty() ? "" : TYPES.keySet().iterator().next();
        UUID id = UUID.randomUUID();
        Vec3 to = target;
        try {
            ByteString value = ApolloNetwork.encode(out -> {
                out.writeBytes(1, ApolloNetwork.encode(u -> {
                    u.writeFixed64(1, id.getMostSignificantBits());
                    u.writeFixed64(2, id.getLeastSignificantBits());
                }));
                if (!type.isEmpty()) {
                    out.writeString(2, type);
                }
                out.writeBytes(3, location(world, eye));
                out.writeBytes(4, location(world, to));
            });
            ApolloNetwork.getInstance().send(handler.getNetworkManager(), PREFIX + "RequestPlayerPingMessage", value);
        } catch (Exception e) {
            Artemis.LOGGER.warn("[PingMarker] request failed: {}", e.toString());
        }
    }

    private static ByteString location(String world, Vec3 at) throws java.io.IOException {
        return ApolloNetwork.encode(out -> writeLocation(out, world, at));
    }

    private static void writeLocation(CodedOutputStream out, String world, Vec3 at) throws java.io.IOException {
        if (!world.isEmpty()) {
            out.writeString(1, world);
        }
        out.writeDouble(2, at.xCoord);
        out.writeDouble(3, at.yCoord);
        out.writeDouble(4, at.zCoord);
    }

    private static void onSetTypes(Any message) throws Exception {
        CodedInputStream in = message.getValue().newCodedInput();
        int tag;
        while ((tag = in.readTag()) != 0) {
            if (WireFormat.getTagFieldNumber(tag) == 1) {
                putType(in.readBytes());
            } else {
                in.skipField(tag);
            }
        }
    }

    /** {@code SetPingMarkerTypeMessage}: id #1, icon #2. */
    private static void putType(ByteString data) throws Exception {
        CodedInputStream in = data.newCodedInput();
        String id = null;
        ApolloIcon icon = null;
        int tag;
        while ((tag = in.readTag()) != 0) {
            switch (WireFormat.getTagFieldNumber(tag)) {
                case 1:
                    id = in.readString();
                    break;
                case 2:
                    icon = ApolloIcon.parse(in.readBytes());
                    break;
                default:
                    in.skipField(tag);
            }
        }
        if (id != null && icon != null) {
            TYPES.put(id, icon);
        }
    }

    private static void onDisplay(Any message) throws Exception {
        CodedInputStream in = message.getValue().newCodedInput();
        UUID id = null;
        String type = null;
        double[] at = null;
        int color = 0xFFFFFFFF;
        ApolloIcon icon = null;
        String sound = null;
        long duration = 0L;
        boolean focus = false;
        int tag;
        while ((tag = in.readTag()) != 0) {
            switch (WireFormat.getTagFieldNumber(tag)) {
                case 1:
                    id = ApolloProtos.parseUuid(in.readBytes());
                    break;
                case 2:
                    type = in.readString();
                    break;
                case 3:
                    at = parseLocation(in.readBytes());
                    break;
                case 4:
                    color = ApolloProtos.parseColorArgb(in.readBytes());
                    break;
                case 5:
                    icon = ApolloIcon.parse(in.readBytes());
                    break;
                case 6:
                    sound = parseSound(in.readBytes());
                    break;
                case 7:
                    duration = ApolloProtos.parseDurationMillis(in.readBytes());
                    break;
                case 8:
                    focus = in.readBool();
                    break;
                default:
                    in.skipField(tag);
            }
        }
        if (id == null || at == null) {
            return;
        }
        if ((color >>> 24) == 0) {
            color |= 0xFF000000;
        }
        PINGS.put(id, new Ping(id, type, at[0], at[1], at[2], color, icon, focus, System.currentTimeMillis(),
            duration > 0L ? duration : DEFAULT_DURATION_MILLIS));
        if (sound != null && !sound.isEmpty()) {
            String location = sound;
            Minecraft mc = Minecraft.getMinecraft();
            mc.addScheduledTask(() -> mc.getSoundHandler().playSound(
                PositionedSoundRecord.create(new ResourceLocation(location), 1.0F)));
        }
    }

    private static void onRemove(Any message) throws Exception {
        CodedInputStream in = message.getValue().newCodedInput();
        int tag;
        while ((tag = in.readTag()) != 0) {
            if (WireFormat.getTagFieldNumber(tag) == 1) {
                PINGS.remove(ApolloProtos.parseUuid(in.readBytes()));
            } else {
                in.skipField(tag);
            }
        }
    }

    /** {@code common.v1.Location}: world #1, x #2, y #3, z #4. */
    private static double[] parseLocation(ByteString data) throws Exception {
        CodedInputStream in = data.newCodedInput();
        double[] xyz = new double[3];
        int tag;
        while ((tag = in.readTag()) != 0) {
            switch (WireFormat.getTagFieldNumber(tag)) {
                case 2:
                    xyz[0] = in.readDouble();
                    break;
                case 3:
                    xyz[1] = in.readDouble();
                    break;
                case 4:
                    xyz[2] = in.readDouble();
                    break;
                default:
                    in.skipField(tag);
            }
        }
        return xyz;
    }

    /** {@code common.v1.Sound}: simple_resource_location #1 → resource_location #1. */
    private static String parseSound(ByteString data) throws Exception {
        CodedInputStream in = data.newCodedInput();
        int tag;
        while ((tag = in.readTag()) != 0) {
            if (WireFormat.getTagFieldNumber(tag) == 1) {
                return readString(in.readBytes(), 1);
            }
            in.skipField(tag);
        }
        return null;
    }

    private static String readString(ByteString data, int field) throws Exception {
        CodedInputStream in = data.newCodedInput();
        int tag;
        while ((tag = in.readTag()) != 0) {
            if (WireFormat.getTagFieldNumber(tag) == field) {
                return in.readString();
            }
            in.skipField(tag);
        }
        return null;
    }
}
