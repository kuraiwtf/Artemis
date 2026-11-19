package fr.natsu.artemis.module.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import com.google.protobuf.ByteString;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;

import fr.natsu.artemis.Artemis;
import fr.natsu.artemis.module.button.ApolloButton;
import fr.natsu.artemis.module.button.ButtonContent;
import fr.natsu.artemis.net.ApolloProtos;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

/**
 * Server chat tabs, shown in a bar above the chat input while chat is open. Messages go into a tab
 * when one of its patterns matches their plain text, and stay in the "All" tab unless the tab hides
 * them from it. Live messages sent to a tab ({@code tab_id}) only ever show in that tab.
 *
 * <p>Filtering happens in {@code MixinGuiNewChat}: every line is still stored, only the drawn lines
 * depend on the selected tab, so switching tabs is a {@code refreshChat()}.</p>
 */
public final class ChatTabs {

    public static final String ALL = "all";
    public static final int MAX_TABS = 16;
    private static final long HINT_MILLIS = 2000L;

    public static final class Tab {
        public final String id;
        public volatile ButtonContent name;
        public volatile List<String> tooltip;
        public final Integer background;
        final List<TextMatcher> patterns;
        final String inputPrefix;
        final boolean hideFromAll;

        Tab(String id, ButtonContent name, List<String> tooltip, Integer background, List<TextMatcher> patterns,
            String inputPrefix, boolean hideFromAll) {
            this.id = id;
            this.name = name;
            this.tooltip = tooltip;
            this.background = background;
            this.patterns = patterns;
            this.inputPrefix = inputPrefix;
            this.hideFromAll = hideFromAll;
        }

        boolean matches(String plain) {
            for (TextMatcher pattern : this.patterns) {
                if (pattern.matches(plain)) {
                    return true;
                }
            }
            return false;
        }
    }

    /** {@code common.v1.TextPattern}: regex #1 | contains #2 | starts_with #3, case_sensitive #4. */
    static final class TextMatcher {
        private final Pattern regex;
        private final String contains;
        private final String startsWith;
        private final boolean caseSensitive;

        private TextMatcher(Pattern regex, String contains, String startsWith, boolean caseSensitive) {
            this.regex = regex;
            this.contains = contains;
            this.startsWith = startsWith;
            this.caseSensitive = caseSensitive;
        }

        static TextMatcher parse(ByteString data) throws Exception {
            CodedInputStream in = data.newCodedInput();
            String regex = null;
            String contains = null;
            String startsWith = null;
            boolean caseSensitive = false;
            int tag;
            while ((tag = in.readTag()) != 0) {
                switch (WireFormat.getTagFieldNumber(tag)) {
                    case 1:
                        regex = in.readString();
                        break;
                    case 2:
                        contains = in.readString();
                        break;
                    case 3:
                        startsWith = in.readString();
                        break;
                    case 4:
                        caseSensitive = in.readBool();
                        break;
                    default:
                        in.skipField(tag);
                }
            }
            Pattern compiled = null;
            if (regex != null) {
                try {
                    compiled = Pattern.compile(regex, caseSensitive ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
                } catch (Exception e) {
                    Artemis.LOGGER.warn("[ChatTabs] invalid pattern {}: {}", regex, e.getMessage());
                    return null;
                }
            }
            if (compiled == null && contains == null && startsWith == null) {
                return null;
            }
            return new TextMatcher(compiled, caseSensitive || contains == null ? contains : contains.toLowerCase(),
                caseSensitive || startsWith == null ? startsWith : startsWith.toLowerCase(), caseSensitive);
        }

        boolean matches(String plain) {
            if (this.regex != null) {
                return this.regex.matcher(plain).find();
            }
            String text = this.caseSensitive ? plain : plain.toLowerCase();
            if (this.contains != null) {
                return text.contains(this.contains);
            }
            return text.startsWith(this.startsWith);
        }
    }

    private static final Map<String, Tab> TABS = new LinkedHashMap<>();
    private static volatile List<Tab> snapshot = Collections.emptyList();
    /** Live chat message id → the tab it was sent to. */
    private static final Map<Integer, String> TAB_MESSAGES = new ConcurrentHashMap<>();

    private static volatile String selected = ALL;
    private static volatile String hintTab;
    private static volatile long hintUntil;

    private ChatTabs() {
    }

    public static List<Tab> tabs() {
        return snapshot;
    }

    public static String selected() {
        return selected;
    }

    public static Tab tab(String id) {
        synchronized (TABS) {
            return TABS.get(id);
        }
    }

    /** {@code ChatTab}: id #1, display_name #2, patterns #3, input_prefix #4, hide_from_all #5, tooltip #6, background_color #7. */
    static void display(ByteString data) throws Exception {
        CodedInputStream in = data.newCodedInput();
        String id = null;
        ButtonContent name = ButtonContent.EMPTY;
        List<TextMatcher> patterns = new ArrayList<>();
        String prefix = "";
        boolean hideFromAll = false;
        List<String> tooltip = null;
        Integer background = null;
        int tag;
        while ((tag = in.readTag()) != 0) {
            switch (WireFormat.getTagFieldNumber(tag)) {
                case 1:
                    id = in.readString();
                    break;
                case 2:
                    name = ButtonContent.parse(in.readBytes());
                    break;
                case 3:
                    TextMatcher matcher = TextMatcher.parse(in.readBytes());
                    if (matcher != null) {
                        patterns.add(matcher);
                    }
                    break;
                case 4:
                    prefix = in.readString();
                    break;
                case 5:
                    hideFromAll = in.readBool();
                    break;
                case 6:
                    tooltip = ApolloButton.parseTooltip(in.readBytes());
                    break;
                case 7:
                    background = ApolloProtos.parseColorArgb(in.readBytes());
                    break;
                default:
                    in.skipField(tag);
            }
        }
        if (id == null || id.isEmpty() || ALL.equals(id)) {
            return;
        }
        synchronized (TABS) {
            if (!TABS.containsKey(id) && TABS.size() >= MAX_TABS) {
                Artemis.LOGGER.warn("[ChatTabs] more than {} tabs, {} ignored", MAX_TABS, id);
                return;
            }
            TABS.put(id, new Tab(id, name, tooltip, background, patterns, prefix, hideFromAll));
            publish();
        }
        refresh();
    }

    static void update(String id, ByteString data) throws Exception {
        Tab tab = tab(id);
        if (tab == null) {
            return;
        }
        CodedInputStream in = data.newCodedInput();
        int tag;
        while ((tag = in.readTag()) != 0) {
            switch (WireFormat.getTagFieldNumber(tag)) {
                case 1:
                    tab.name = ButtonContent.parse(in.readBytes());
                    break;
                case 2:
                    tab.tooltip = ApolloButton.parseTooltip(in.readBytes());
                    break;
                default:
                    in.skipField(tag);
            }
        }
    }

    static void remove(String id) {
        synchronized (TABS) {
            if (TABS.remove(id) == null) {
                return;
            }
            publish();
        }
        if (id.equals(selected)) {
            selected = ALL;
        }
        refresh();
    }

    public static void reset() {
        synchronized (TABS) {
            TABS.clear();
            publish();
        }
        TAB_MESSAGES.clear();
        selected = ALL;
        hintTab = null;
        refresh();
    }

    private static void publish() {
        snapshot = Collections.unmodifiableList(new ArrayList<>(TABS.values()));
    }

    /** Routes a live message to a tab; an unknown tab leaves it in the normal chat. */
    static void assign(int messageId, String tabId) {
        if (tabId == null || tabId.isEmpty() || tab(tabId) == null) {
            TAB_MESSAGES.remove(messageId);
        } else {
            TAB_MESSAGES.put(messageId, tabId);
        }
    }

    static void unassign(int messageId) {
        TAB_MESSAGES.remove(messageId);
    }

    /** Selects a tab ({@link #ALL} or a server tab id); does nothing for an unknown tab. */
    public static void select(String id, boolean openChat) {
        Minecraft mc = Minecraft.getMinecraft();
        mc.addScheduledTask(() -> {
            boolean known = ALL.equals(id) || tab(id) != null;
            if (known && !id.equals(selected)) {
                selected = id;
                mc.ingameGUI.getChatGUI().refreshChat();
            }
            if (openChat) {
                if (!(mc.currentScreen instanceof GuiChat) && mc.theWorld != null) {
                    mc.displayGuiScreen(new GuiChat());
                }
            } else if (known && !(mc.currentScreen instanceof GuiChat)) {
                hintTab = id;
                hintUntil = System.currentTimeMillis() + HINT_MILLIS;
            }
        });
    }

    /** The tab whose name flashes above the closed chat after a switch, or {@code null}. */
    public static String hint() {
        return System.currentTimeMillis() < hintUntil ? hintTab : null;
    }

    /** Whether the line belongs in the drawn chat for the selected tab. */
    public static boolean accepts(IChatComponent component, int lineId) {
        List<Tab> tabs = snapshot;
        if (tabs.isEmpty()) {
            return true;
        }
        String current = selected;
        if (lineId != 0) {
            String dedicated = TAB_MESSAGES.get(lineId);
            if (dedicated != null && tab(dedicated) != null) {
                return dedicated.equals(current);
            }
        }
        String plain = EnumChatFormatting.getTextWithoutFormattingCodes(component.getUnformattedText());
        if (plain == null) {
            plain = "";
        }
        if (ALL.equals(current)) {
            for (Tab tab : tabs) {
                if (tab.hideFromAll && tab.matches(plain)) {
                    return false;
                }
            }
            return true;
        }
        Tab tab = tab(current);
        return tab == null || tab.matches(plain);
    }

    /** The message to actually send from the chat screen: the selected tab's prefix, unless a command. */
    public static String applyPrefix(String message) {
        if (message == null || message.startsWith("/")) {
            return message;
        }
        Tab tab = ALL.equals(selected) ? null : tab(selected);
        if (tab == null || tab.inputPrefix.isEmpty()) {
            return message;
        }
        String prefix = tab.inputPrefix;
        if (prefix.startsWith("/") && !prefix.endsWith(" ")) {
            prefix += " ";
        }
        return prefix + message;
    }

    private static void refresh() {
        Minecraft mc = Minecraft.getMinecraft();
        mc.addScheduledTask(() -> {
            if (mc.ingameGUI != null) {
                mc.ingameGUI.getChatGUI().refreshChat();
            }
        });
    }
}
