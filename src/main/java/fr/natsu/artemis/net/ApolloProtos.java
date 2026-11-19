package fr.natsu.artemis.net;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.google.protobuf.ByteString;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;

/**
 * Manual protobuf readers for the common Apollo types.
 *
 * <p>The simple Apollo messages are decoded by hand through {@link CodedInputStream} rather than with
 * the generated classes: no descriptor-bootstrap dependency, and forward-compat tolerance of unknown
 * fields through {@link CodedInputStream#skipField(int)}.</p>
 *
 * <p>Encodings (see {@code com.lunarclient.apollo.common.v1}):</p>
 * <ul>
 *   <li>{@code Uuid}: {@code high64} (#1, fixed64) + {@code low64} (#2, fixed64)</li>
 *   <li>{@code Color}: {@code color} (#1, int32) in ARGB ({@code java.awt.Color#getRGB()})</li>
 * </ul>
 */
public final class ApolloProtos {

    private ApolloProtos() {
    }

    public static UUID parseUuid(ByteString data) throws IOException {
        CodedInputStream in = data.newCodedInput();
        long high = 0L;
        long low = 0L;
        int tag;
        while ((tag = in.readTag()) != 0) {
            switch (WireFormat.getTagFieldNumber(tag)) {
                case 1:
                    high = in.readFixed64();
                    break;
                case 2:
                    low = in.readFixed64();
                    break;
                default:
                    in.skipField(tag);
            }
        }
        return new UUID(high, low);
    }

    public static long parseDurationMillis(ByteString data) throws IOException {
        CodedInputStream in = data.newCodedInput();
        long seconds = 0L;
        int nanos = 0;
        int tag;
        while ((tag = in.readTag()) != 0) {
            switch (WireFormat.getTagFieldNumber(tag)) {
                case 1:
                    seconds = in.readInt64();
                    break;
                case 2:
                    nanos = in.readInt32();
                    break;
                default:
                    in.skipField(tag);
            }
        }
        return seconds * 1000L + nanos / 1_000_000L;
    }

    public static int parseColorArgb(ByteString data) throws IOException {
        CodedInputStream in = data.newCodedInput();
        int argb = 0;
        int tag;
        while ((tag = in.readTag()) != 0) {
            if (WireFormat.getTagFieldNumber(tag) == 1) {
                argb = in.readInt32();
            } else {
                in.skipField(tag);
            }
        }
        return argb;
    }

    /**
     * {@code common.v1.EntityId}: {@code entity_id} (#1, int32) + {@code entity_uuid} (#2, Uuid).
     * Either may be absent; a missing id is {@code -1}, a missing uuid {@code null}.
     */
    public static final class EntityRef {
        public final int id;
        public final UUID uuid;

        public EntityRef(int id, UUID uuid) {
            this.id = id;
            this.uuid = uuid;
        }

        public boolean matches(net.minecraft.entity.Entity entity) {
            return (this.id >= 0 && entity.getEntityId() == this.id)
                || (this.uuid != null && this.uuid.equals(entity.getUniqueID()));
        }
    }

    public static EntityRef parseEntityId(ByteString data) throws IOException {
        CodedInputStream in = data.newCodedInput();
        int id = -1;
        UUID uuid = null;
        int tag;
        while ((tag = in.readTag()) != 0) {
            switch (WireFormat.getTagFieldNumber(tag)) {
                case 1:
                    id = in.readInt32();
                    break;
                case 2:
                    uuid = parseUuid(in.readBytes());
                    break;
                default:
                    in.skipField(tag);
            }
        }
        return new EntityRef(id, uuid);
    }

    /**
     * {@code google.protobuf.Value} into a plain Java value: {@code null}, {@link Double},
     * {@link String}, {@link Boolean} or a {@link List} of those. Structs are not used by Apollo's
     * options and come back as {@code null}.
     */
    public static Object parseValue(ByteString data) throws IOException {
        CodedInputStream in = data.newCodedInput();
        Object value = null;
        int tag;
        while ((tag = in.readTag()) != 0) {
            switch (WireFormat.getTagFieldNumber(tag)) {
                case 2:
                    value = in.readDouble();
                    break;
                case 3:
                    value = in.readString();
                    break;
                case 4:
                    value = in.readBool();
                    break;
                case 6:
                    value = parseListValue(in.readBytes());
                    break;
                default:
                    in.skipField(tag);
            }
        }
        return value;
    }

    private static List<Object> parseListValue(ByteString data) throws IOException {
        CodedInputStream in = data.newCodedInput();
        List<Object> values = new ArrayList<>();
        int tag;
        while ((tag = in.readTag()) != 0) {
            if (WireFormat.getTagFieldNumber(tag) == 1) {
                values.add(parseValue(in.readBytes()));
            } else {
                in.skipField(tag);
            }
        }
        return values;
    }

    /** {@code hud.v1.HudPosition}: {@code x} (#1, float) + {@code y} (#2, float). */
    public static float[] parseHudPosition(ByteString data) throws IOException {
        CodedInputStream in = data.newCodedInput();
        float[] xy = new float[2];
        int tag;
        while ((tag = in.readTag()) != 0) {
            switch (WireFormat.getTagFieldNumber(tag)) {
                case 1:
                    xy[0] = in.readFloat();
                    break;
                case 2:
                    xy[1] = in.readFloat();
                    break;
                default:
                    in.skipField(tag);
            }
        }
        return xy;
    }
}
