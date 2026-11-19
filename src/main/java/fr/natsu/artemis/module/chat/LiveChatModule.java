package fr.natsu.artemis.module.chat;

import com.google.protobuf.Any;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;

import fr.natsu.artemis.net.ApolloNetwork;

/**
 * Apollo chat module, live messages only ({@code lunarclient.apollo.chat.v1}): a server-assigned id
 * lets a chat line be replaced in place or removed. Lines go through {@link ArtemisChat}, so they get
 * the same hex rendering as the {@code artemis:chat} channel.
 *
 * <ul>
 *   <li>{@code DisplayLiveChatMessageMessage}: {@code message_id} (#1, int32),
 *       {@code adventure_json_lines} (#2, string), {@code tab_id} (#3, the chat tab it only shows in)</li>
 *   <li>{@code RemoveLiveChatMessageMessage}: {@code message_id} (#1, int32)</li>
 * </ul>
 */
public final class LiveChatModule {

    private static final String DISPLAY = "lunarclient.apollo.chat.v1.DisplayLiveChatMessageMessage";
    private static final String REMOVE = "lunarclient.apollo.chat.v1.RemoveLiveChatMessageMessage";

    private LiveChatModule() {
    }

    public static void register(ApolloNetwork network) {
        network.register(DISPLAY, LiveChatModule::onDisplay);
        network.register(REMOVE, LiveChatModule::onRemove);
    }

    private static void onDisplay(Any message) throws Exception {
        CodedInputStream in = message.getValue().newCodedInput();
        int id = 0;
        String json = null;
        String tab = null;
        int tag;
        while ((tag = in.readTag()) != 0) {
            switch (WireFormat.getTagFieldNumber(tag)) {
                case 1:
                    id = in.readInt32();
                    break;
                case 2:
                    json = in.readString();
                    break;
                case 3:
                    tab = in.readString();
                    break;
                default:
                    in.skipField(tag);
            }
        }
        if (json != null && !json.isEmpty()) {
            ChatTabs.assign(id, tab);
            ArtemisChat.display(id, json);
        }
    }

    private static void onRemove(Any message) throws Exception {
        CodedInputStream in = message.getValue().newCodedInput();
        int tag;
        while ((tag = in.readTag()) != 0) {
            if (WireFormat.getTagFieldNumber(tag) == 1) {
                int id = in.readInt32();
                ChatTabs.unassign(id);
                ArtemisChat.remove(id);
            } else {
                in.skipField(tag);
            }
        }
    }
}
