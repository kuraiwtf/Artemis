package fr.natsu.artemis.module.inventory;

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
import fr.natsu.artemis.module.button.ApolloButton;
import fr.natsu.artemis.net.ApolloNetwork;

/**
 * Inventory buttons ({@code lunarclient.apollo.inventory.v1}): buttons in two invisible 92x166 boxes
 * on the left and right of the player inventory (survival and creative).
 *
 * <p>{@code InventoryButton}: {@code button} (#1), {@code inventory_type} (#2, only PLAYER exists),
 * {@code box} (#3, 1 = left, 2 = right).</p>
 */
public final class InventoryButtonsModule {

    private static final String PREFIX = "lunarclient.apollo.inventory.v1.";

    public static final float BOX_WIDTH = 92.0F;
    public static final float BOX_HEIGHT = 166.0F;
    public static final int BOX_LEFT = 1;
    public static final int BOX_RIGHT = 2;
    private static final int MAX_PER_BOX = 25;

    public static final class Entry {
        public final ApolloButton button;
        public final int box;

        Entry(ApolloButton button, int box) {
            this.button = button;
            this.box = box;
        }
    }

    private static final Map<String, Entry> ENTRIES = new LinkedHashMap<>();
    private static volatile List<Entry> snapshot = Collections.emptyList();

    private InventoryButtonsModule() {
    }

    public static void register(ApolloNetwork network) {
        network.register(PREFIX + "DisplayInventoryButtonsMessage", InventoryButtonsModule::onDisplay);
        network.register(PREFIX + "RemoveInventoryButtonMessage", InventoryButtonsModule::onRemove);
        network.register(PREFIX + "ResetInventoryButtonsMessage", m -> reset());
        network.register(PREFIX + "UpdateInventoryButtonMessage", InventoryButtonsModule::onUpdate);
        network.onDisconnect(InventoryButtonsModule::reset);
    }

    public static List<Entry> entries() {
        return snapshot;
    }

    private static void onDisplay(Any message) throws Exception {
        CodedInputStream in = message.getValue().newCodedInput();
        int tag;
        while ((tag = in.readTag()) != 0) {
            if (WireFormat.getTagFieldNumber(tag) == 1) {
                put(in.readBytes());
            } else {
                in.skipField(tag);
            }
        }
    }

    private static void put(ByteString data) throws Exception {
        CodedInputStream in = data.newCodedInput();
        ApolloButton button = null;
        int box = BOX_LEFT;
        int tag;
        while ((tag = in.readTag()) != 0) {
            switch (WireFormat.getTagFieldNumber(tag)) {
                case 1:
                    button = ApolloButton.parse(in.readBytes());
                    break;
                case 3:
                    box = in.readEnum() == BOX_RIGHT ? BOX_RIGHT : BOX_LEFT;
                    break;
                default:
                    in.skipField(tag);
            }
        }
        if (button == null) {
            return;
        }
        synchronized (ENTRIES) {
            Entry previous = ENTRIES.get(button.id);
            if (previous == null || previous.box != box) {
                int inBox = 0;
                for (Entry entry : ENTRIES.values()) {
                    if (entry.box == box) {
                        inBox++;
                    }
                }
                if (inBox >= MAX_PER_BOX) {
                    Artemis.LOGGER.warn("[InventoryButtons] box full, {} ignored", button.id);
                    return;
                }
            }
            ENTRIES.put(button.id, new Entry(button, box));
            publish();
        }
    }

    private static void onRemove(Any message) throws Exception {
        CodedInputStream in = message.getValue().newCodedInput();
        int tag;
        while ((tag = in.readTag()) != 0) {
            if (WireFormat.getTagFieldNumber(tag) == 1) {
                String id = in.readString();
                synchronized (ENTRIES) {
                    if (ENTRIES.remove(id) != null) {
                        publish();
                    }
                }
            } else {
                in.skipField(tag);
            }
        }
    }

    private static void onUpdate(Any message) throws Exception {
        CodedInputStream in = message.getValue().newCodedInput();
        String id = null;
        ByteString update = null;
        int tag;
        while ((tag = in.readTag()) != 0) {
            switch (WireFormat.getTagFieldNumber(tag)) {
                case 1:
                    id = in.readString();
                    break;
                case 2:
                    update = in.readBytes();
                    break;
                default:
                    in.skipField(tag);
            }
        }
        Entry entry;
        synchronized (ENTRIES) {
            entry = id == null ? null : ENTRIES.get(id);
        }
        if (entry != null && update != null) {
            entry.button.update(update);
        }
    }

    private static void reset() {
        synchronized (ENTRIES) {
            ENTRIES.clear();
            publish();
        }
    }

    private static void publish() {
        snapshot = Collections.unmodifiableList(new ArrayList<>(ENTRIES.values()));
    }
}
