package fr.natsu.artemis.module.chat;

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
 * The interactive half of the Apollo chat module ({@code lunarclient.apollo.chat.v1}): chat buttons,
 * chat tabs and chat popups. State lives here and in {@link ChatTabs} / {@link ChatPopups};
 * {@code gui.ChatOverlay} draws it and handles clicks.
 */
public final class ChatUiModule {

    private static final String PREFIX = "lunarclient.apollo.chat.v1.";

    public static final float BUTTON_BOX_WIDTH = 320.0F;
    public static final float BUTTON_BOX_HEIGHT = 20.0F;
    private static final int MAX_BUTTONS = 25;

    private static final Map<String, ApolloButton> BUTTONS = new LinkedHashMap<>();
    private static volatile List<ApolloButton> buttons = Collections.emptyList();

    private ChatUiModule() {
    }

    public static void register(ApolloNetwork network) {
        network.register(PREFIX + "DisplayChatButtonsMessage", ChatUiModule::onDisplayButtons);
        network.register(PREFIX + "RemoveChatButtonMessage", m -> withId(m, ChatUiModule::removeButton));
        network.register(PREFIX + "ResetChatButtonsMessage", m -> resetButtons());
        network.register(PREFIX + "UpdateChatButtonMessage", m -> withUpdate(m, (id, update) -> {
            ApolloButton button;
            synchronized (BUTTONS) {
                button = BUTTONS.get(id);
            }
            if (button != null) {
                button.update(update);
            }
        }));

        network.register(PREFIX + "DisplayChatTabsMessage", m -> repeated(m, ChatTabs::display));
        network.register(PREFIX + "RemoveChatTabMessage", m -> withId(m, ChatTabs::remove));
        network.register(PREFIX + "ResetChatTabsMessage", m -> ChatTabs.reset());
        network.register(PREFIX + "UpdateChatTabMessage", m -> withUpdate(m, ChatTabs::update));

        network.register(PREFIX + "DisplayChatPopupMessage", m -> repeated(m, ChatPopups::display));
        network.register(PREFIX + "RemoveChatPopupMessage", m -> withId(m, ChatPopups::remove));
        network.register(PREFIX + "ResetChatPopupsMessage", m -> ChatPopups.reset());

        network.onDisconnect(() -> {
            resetButtons();
            ChatTabs.reset();
            ChatPopups.reset();
        });
    }

    public static List<ApolloButton> buttons() {
        return buttons;
    }

    /** {@code DisplayChatButtonsMessage}: {@code chat_buttons} (#1), each a ChatButton with {@code button} (#1). */
    private static void onDisplayButtons(Any message) throws Exception {
        repeated(message, chatButton -> {
            CodedInputStream in = chatButton.newCodedInput();
            int tag;
            while ((tag = in.readTag()) != 0) {
                if (WireFormat.getTagFieldNumber(tag) == 1) {
                    ApolloButton button = ApolloButton.parse(in.readBytes());
                    if (button != null) {
                        putButton(button);
                    }
                } else {
                    in.skipField(tag);
                }
            }
        });
    }

    private static void putButton(ApolloButton button) {
        synchronized (BUTTONS) {
            if (!BUTTONS.containsKey(button.id) && BUTTONS.size() >= MAX_BUTTONS) {
                Artemis.LOGGER.warn("[ChatButtons] more than {} buttons, {} ignored", MAX_BUTTONS, button.id);
                return;
            }
            BUTTONS.put(button.id, button);
            buttons = Collections.unmodifiableList(new ArrayList<>(BUTTONS.values()));
        }
    }

    private static void removeButton(String id) {
        synchronized (BUTTONS) {
            if (BUTTONS.remove(id) != null) {
                buttons = Collections.unmodifiableList(new ArrayList<>(BUTTONS.values()));
            }
        }
    }

    private static void resetButtons() {
        synchronized (BUTTONS) {
            BUTTONS.clear();
            buttons = Collections.emptyList();
        }
    }

    @FunctionalInterface
    interface BytesHandler {
        void accept(ByteString data) throws Exception;
    }

    @FunctionalInterface
    interface StringHandler {
        void accept(String value) throws Exception;
    }

    @FunctionalInterface
    interface UpdateHandler {
        void accept(String id, ByteString update) throws Exception;
    }

    /** Every occurrence of field #1 (a repeated or single sub-message). */
    private static void repeated(Any message, BytesHandler handler) throws Exception {
        CodedInputStream in = message.getValue().newCodedInput();
        int tag;
        while ((tag = in.readTag()) != 0) {
            if (WireFormat.getTagFieldNumber(tag) == 1) {
                handler.accept(in.readBytes());
            } else {
                in.skipField(tag);
            }
        }
    }

    private static void withId(Any message, StringHandler handler) throws Exception {
        CodedInputStream in = message.getValue().newCodedInput();
        int tag;
        while ((tag = in.readTag()) != 0) {
            if (WireFormat.getTagFieldNumber(tag) == 1) {
                handler.accept(in.readString());
            } else {
                in.skipField(tag);
            }
        }
    }

    /** {@code id} (#1) + {@code update} (#2, ButtonUpdate). */
    private static void withUpdate(Any message, UpdateHandler handler) throws Exception {
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
        if (id != null && update != null) {
            handler.accept(id, update);
        }
    }
}
