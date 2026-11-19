package fr.natsu.artemis.module.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.protobuf.ByteString;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;

import fr.natsu.artemis.Artemis;
import fr.natsu.artemis.module.button.ButtonAction;
import fr.natsu.artemis.module.button.ButtonContent;
import fr.natsu.artemis.net.ApolloNetwork;
import fr.natsu.artemis.net.ApolloProtos;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;

/**
 * Chat popups: notification-style cards with up to three options (accept / deny a friend request).
 * The client answers every popup it closes on its own with a {@code ChatPopupResponseMessage}
 * ({@code popup_id} #1, {@code option_id} #2, {@code status} #3); popups the server removes get no
 * answer. Up to {@link #VISIBLE} are on screen, the rest wait; past {@link #MAX_POPUPS} the oldest
 * waiting popup is dropped.
 */
public final class ChatPopups {

    public static final int VISIBLE = 3;
    private static final int MAX_POPUPS = 20;
    private static final int MAX_OPTIONS = 3;
    private static final String RESPONSE = "lunarclient.apollo.chat.v1.ChatPopupResponseMessage";

    public static final int STATUS_PICKED = 1;
    public static final int STATUS_DISMISSED = 2;
    public static final int STATUS_EXPIRED = 3;
    public static final int STATUS_DROPPED = 4;

    public static final class Option {
        public final String id;
        public final String label;
        public final ButtonAction action;

        Option(String id, String label, ButtonAction action) {
            this.id = id;
            this.label = label;
            this.action = action;
        }
    }

    public static final class Popup {
        public final String id;
        public final String title;
        public final String description;
        public final long receivedAt;
        public final long displayMillis;
        public final List<Option> options;
        /** Set while an option's prompt is open, so the popup cannot expire or be picked twice. */
        public volatile boolean busy;

        Popup(String id, String title, String description, long receivedAt, long displayMillis, List<Option> options) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.receivedAt = receivedAt;
            this.displayMillis = displayMillis;
            this.options = options;
        }

        /** 1 → 0 over the display time, or -1 without one. */
        public float remainingFraction(long now) {
            if (this.displayMillis <= 0L) {
                return -1.0F;
            }
            return Math.max(0.0F, 1.0F - (now - this.receivedAt) / (float) this.displayMillis);
        }
    }

    private static final List<Popup> POPUPS = new ArrayList<>();

    private ChatPopups() {
    }

    /** The popups on screen, oldest first. */
    public static List<Popup> visible() {
        synchronized (POPUPS) {
            return new ArrayList<>(POPUPS.subList(0, Math.min(VISIBLE, POPUPS.size())));
        }
    }

    public static boolean isEmpty() {
        synchronized (POPUPS) {
            return POPUPS.isEmpty();
        }
    }

    /** {@code ChatPopup}: id #1, title #2, description #3, display_time #4, options #5. */
    static void display(ByteString data) throws Exception {
        CodedInputStream in = data.newCodedInput();
        String id = null;
        String title = "";
        String description = null;
        long display = 0L;
        List<Option> options = new ArrayList<>();
        int tag;
        while ((tag = in.readTag()) != 0) {
            switch (WireFormat.getTagFieldNumber(tag)) {
                case 1:
                    id = in.readString();
                    break;
                case 2:
                    title = ButtonContent.encode(in.readString());
                    break;
                case 3:
                    description = ButtonContent.encode(in.readString());
                    break;
                case 4:
                    display = ApolloProtos.parseDurationMillis(in.readBytes());
                    break;
                case 5:
                    Option option = parseOption(in.readBytes());
                    if (option != null && options.size() < MAX_OPTIONS) {
                        options.add(option);
                    }
                    break;
                default:
                    in.skipField(tag);
            }
        }
        if (id == null || id.isEmpty()) {
            return;
        }
        Popup popup = new Popup(id, title, description == null || description.isEmpty() ? null : description,
            System.currentTimeMillis(), display, Collections.unmodifiableList(options));
        Popup dropped = null;
        synchronized (POPUPS) {
            int existing = indexOf(id);
            if (existing >= 0) {
                POPUPS.set(existing, popup);
            } else {
                POPUPS.add(popup);
                if (POPUPS.size() > MAX_POPUPS) {
                    dropped = POPUPS.remove(POPUPS.size() > VISIBLE ? VISIBLE : 0);
                }
            }
        }
        if (dropped != null) {
            respond(dropped, null, STATUS_DROPPED);
        }
        Artemis.LOGGER.info("[ChatPopup] display {} ({} option(s))", id, options.size());
    }

    /** {@code ChatPopupOption}: id #1, label #2, run_command #3 | open_url #4 | client_action #5 | select_chat_tab #6. */
    private static Option parseOption(ByteString data) throws Exception {
        CodedInputStream in = data.newCodedInput();
        String id = null;
        String label = "";
        ButtonAction action = null;
        int tag;
        while ((tag = in.readTag()) != 0) {
            switch (WireFormat.getTagFieldNumber(tag)) {
                case 1:
                    id = in.readString();
                    break;
                case 2:
                    label = ButtonContent.encode(in.readString());
                    break;
                case 3:
                    action = ButtonAction.runCommand(in.readString());
                    break;
                case 4:
                    action = ButtonAction.openUrl(in.readString());
                    break;
                case 5:
                    action = ButtonAction.clientAction(in.readEnum());
                    break;
                case 6:
                    action = ButtonAction.parseSelectChatTab(in.readBytes());
                    break;
                default:
                    in.skipField(tag);
            }
        }
        return id == null || id.isEmpty() ? null : new Option(id, label, action);
    }

    /** The server removed it: no response. */
    static void remove(String id) {
        synchronized (POPUPS) {
            int index = indexOf(id);
            if (index >= 0) {
                POPUPS.remove(index);
            }
        }
    }

    public static void reset() {
        synchronized (POPUPS) {
            POPUPS.clear();
        }
    }

    /** Closes the popup on the client's side and answers the server. */
    public static void close(Popup popup, String optionId, int status) {
        boolean removed;
        synchronized (POPUPS) {
            removed = POPUPS.remove(popup);
        }
        if (removed) {
            respond(popup, optionId, status);
        }
    }

    /** Expires popups whose display time ran out. */
    public static void tick(long now) {
        List<Popup> expired = new ArrayList<>();
        synchronized (POPUPS) {
            for (Popup popup : POPUPS) {
                if (!popup.busy && popup.displayMillis > 0L && now - popup.receivedAt >= popup.displayMillis) {
                    expired.add(popup);
                }
            }
        }
        for (Popup popup : expired) {
            close(popup, null, STATUS_EXPIRED);
        }
    }

    private static int indexOf(String id) {
        for (int i = 0; i < POPUPS.size(); i++) {
            if (POPUPS.get(i).id.equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private static void respond(Popup popup, String optionId, int status) {
        NetHandlerPlayClient handler = Minecraft.getMinecraft().getNetHandler();
        if (handler == null) {
            return;
        }
        try {
            ByteString value = ApolloNetwork.encode(out -> {
                out.writeString(1, popup.id);
                if (optionId != null) {
                    out.writeString(2, optionId);
                }
                out.writeEnum(3, status);
            });
            ApolloNetwork.getInstance().send(handler.getNetworkManager(), RESPONSE, value);
            Artemis.LOGGER.info("[ChatPopup] {} -> status {}{}", popup.id, status,
                optionId == null ? "" : " (" + optionId + ")");
        } catch (Exception e) {
            Artemis.LOGGER.warn("[ChatPopup] could not answer {}: {}", popup.id, e.toString());
        }
    }
}
