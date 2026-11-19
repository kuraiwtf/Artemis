package fr.natsu.artemis.net;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.CodedOutputStream;

import fr.natsu.artemis.Artemis;
import fr.natsu.artemis.LunarHost;
import fr.natsu.artemis.module.glow.GlowModule;
import fr.natsu.artemis.module.vignette.VignetteModule;
import fr.natsu.artemis.module.chat.ArtemisChat;
import fr.natsu.artemis.module.events.ArtemisEvents;
import fr.natsu.artemis.module.lightning.ArtemisLightning;
import io.netty.buffer.Unpooled;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.C17PacketCustomPayload;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public final class ApolloNetwork {

    private static final ApolloNetwork INSTANCE = new ApolloNetwork();

    private static final String REGISTER_SEPARATOR = String.valueOf((char) 0);

    private final Map<String, ApolloPacketHandler> handlers = new ConcurrentHashMap<>();
    private final java.util.Set<String> passedToLunar = java.util.Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

    private final java.util.List<Runnable> disconnectHooks = new java.util.concurrent.CopyOnWriteArrayList<>();

    private boolean logAllIncoming = true;

    private ApolloNetwork() {
    }

    public static ApolloNetwork getInstance() {
        return INSTANCE;
    }

    public void register(String messageType, ApolloPacketHandler handler) {
        this.handlers.put(messageType, handler);
        Artemis.LOGGER.info("[Apollo] handler registered for {}", messageType);
    }

    /** Runs on disconnect, on the network thread: per-server module state is dropped here. */
    public void onDisconnect(Runnable hook) {
        this.disconnectHooks.add(hook);
    }

    public void setLogAllIncoming(boolean logAllIncoming) {
        this.logAllIncoming = logAllIncoming;
    }

    @SubscribeEvent
    public void onConnect(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        this.sendChannelRegistration(event.manager);
        if (!LunarHost.isLunar()) {
            this.sendHandshake(event.manager);
        }
    }

    @SubscribeEvent
    public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        this.resetSession("disconnected from the server");
    }

    /**
     * Drops everything servers sent through Apollo. Runs on disconnect, and on every
     * {@code S01PacketJoinGame}: behind a proxy (BungeeCord, Velocity) a server switch is only a new
     * join-game packet, never a disconnect, and the new server's Apollo messages come after it.
     */
    public void resetSession(String reason) {
        Artemis.LOGGER.info("[Apollo] {}: clearing the Apollo state", reason);
        for (Runnable hook : this.disconnectHooks) {
            try {
                hook.run();
            } catch (Throwable e) {
                Artemis.LOGGER.error("[Apollo] error while resetting module state", e);
            }
        }
    }

    /**
     * {@code player.v1.PlayerHandshakeMessage}, which Lunar Client sends right after registering its
     * channels. Only the Minecraft version is filled in ({@code minecraft_version} #1 →
     * {@code MinecraftVersion.enum} #1): Artemis does not claim a Lunar Client build and does not
     * report the installed mods.
     */
    private void sendHandshake(NetworkManager manager) {
        if (manager == null) {
            return;
        }
        try {
            ByteString version = encode(out -> out.writeString(1, "v1_8"));
            ByteString handshake = encode(out -> out.writeBytes(1, version));
            this.send(manager, "lunarclient.apollo.player.v1.PlayerHandshakeMessage", handshake);
            Artemis.LOGGER.info("[Apollo] handshake sent (v1_8)");
        } catch (Exception e) {
            Artemis.LOGGER.warn("[Apollo] could not send the handshake: {}", e.toString());
        }
    }

    @FunctionalInterface
    public interface ProtoWriter {
        void write(CodedOutputStream out) throws java.io.IOException;
    }

    public static ByteString encode(ProtoWriter writer) throws java.io.IOException {
        ByteString.Output bytes = ByteString.newOutput();
        CodedOutputStream out = CodedOutputStream.newInstance(bytes);
        writer.write(out);
        out.flush();
        return bytes.toByteString();
    }

    /** Wraps {@code value} in a {@code google.protobuf.Any} and sends it on {@code lunar:apollo}. */
    public void send(NetworkManager manager, String messageType, ByteString value) {
        byte[] payload = Any.newBuilder()
            .setTypeUrl(ApolloProtocol.TYPE_URL_PREFIX + messageType)
            .setValue(value)
            .build()
            .toByteArray();
        PacketBuffer buffer = new PacketBuffer(Unpooled.wrappedBuffer(payload));
        manager.sendPacket(new C17PacketCustomPayload(ApolloProtocol.CHANNEL, buffer));
    }

    private void sendChannelRegistration(NetworkManager manager) {
        if (manager == null) {
            return;
        }
        String channels = LunarHost.isLunar()
            ? String.join(REGISTER_SEPARATOR, ArtemisChat.CHANNEL, ArtemisLightning.CHANNEL)
            : String.join(REGISTER_SEPARATOR,
                ApolloProtocol.CHANNEL, ArtemisChat.CHANNEL, ArtemisLightning.CHANNEL, ArtemisEvents.CHANNEL);
        PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
        buffer.writeBytes(channels.getBytes(StandardCharsets.UTF_8));
        manager.sendPacket(new C17PacketCustomPayload(ApolloProtocol.REGISTER_CHANNEL, buffer));
        Artemis.LOGGER.info("[Apollo] announced channels to the server: {}",
            channels.replace(REGISTER_SEPARATOR, ", "));
    }

    public boolean handleIncomingIfOwned(byte[] payload) {
        if (!LunarHost.isLunar()) {
            this.handleIncoming(payload);
            return true;
        }
        Any any;
        try {
            any = Any.parseFrom(payload);
        } catch (Exception e) {
            return false;
        }
        String type = ApolloProtocol.messageType(any.getTypeUrl());
        if (!type.startsWith(GlowModule.MESSAGE_PREFIX) && !type.startsWith(VignetteModule.MESSAGE_PREFIX)) {
            if (this.passedToLunar.add(type)) {
                Artemis.LOGGER.info("[Apollo] left to Lunar Client: {}", type);
            }
            return false;
        }
        this.handleIncoming(payload);
        return true;
    }

    public void handleIncoming(byte[] payload) {
        Any any;
        try {
            any = Any.parseFrom(payload);
        } catch (Exception e) {
            Artemis.LOGGER.warn("[Apollo] unreadable payload ({} bytes): {}", payload.length, e.toString());
            return;
        }

        String type = ApolloProtocol.messageType(any.getTypeUrl());

        if (this.logAllIncoming) {
            Artemis.LOGGER.info("[Apollo] received {} ({} bytes)", type, payload.length);
        }

        ApolloPacketHandler handler = this.handlers.get(type);
        if (handler == null) {
            return;
        }

        try {
            handler.handle(any);
        } catch (Throwable e) {
            Artemis.LOGGER.error("[Apollo] error in the handler for {}", type, e);
        }
    }
}
