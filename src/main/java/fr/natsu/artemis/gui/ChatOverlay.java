package fr.natsu.artemis.gui;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.input.Mouse;

import fr.natsu.artemis.module.button.ApolloButton;
import fr.natsu.artemis.module.button.ButtonContent;
import fr.natsu.artemis.module.chat.ChatPopups;
import fr.natsu.artemis.module.chat.ChatTabs;
import fr.natsu.artemis.module.chat.ChatUiModule;
import fr.natsu.artemis.render.ButtonPainter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.settings.GameSettings;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Draws and drives the Apollo chat UI.
 *
 * <p>While chat is open, the strip above the input field holds, from the bottom up: the tab bar
 * (one chat line high) and the 320x20 button box; the chat log is lifted by their height. Chat
 * popups are cards stacked up from the bottom right, always visible, clickable while chat is open
 * (the only time the cursor is free in game).</p>
 */
public final class ChatOverlay {

    private static final int INPUT_HEIGHT = 14;
    private static final int TAB_HEIGHT = 11;
    private static final int TAB_ROW = TAB_HEIGHT + 1;
    private static final int BUTTON_ROW = (int) ChatUiModule.BUTTON_BOX_HEIGHT + 2;
    private static final int TAB_PADDING = 4;
    private static final int TAB_BACKGROUND = 0x80000000;

    private static final int POPUP_WIDTH = 190;
    private static final int POPUP_MARGIN = 5;
    private static final int POPUP_PADDING = 5;
    private static final int POPUP_GAP = 4;
    private static final int POPUP_BOTTOM = 40;
    private static final int OPTION_HEIGHT = 14;
    private static final int LINE = 10;

    /** A laid-out clickable area, rebuilt every frame by whoever draws it. */
    private static final class Hit {
        final int x;
        final int y;
        final int w;
        final int h;
        final Runnable action;

        Hit(int x, int y, int w, int h, Runnable action) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.action = action;
        }

        boolean contains(int mx, int my) {
            return mx >= this.x && mx < this.x + this.w && my >= this.y && my < this.y + this.h;
        }
    }

    private static volatile List<Hit> popupHits = new ArrayList<>();

    /** How far the chat log is lifted while chat is open, in scaled pixels. */
    public static int chatShift() {
        if (!(Minecraft.getMinecraft().currentScreen instanceof GuiChat)) {
            return 0;
        }
        return (ChatTabs.tabs().isEmpty() ? 0 : TAB_ROW) + (ChatUiModule.buttons().isEmpty() ? 0 : BUTTON_ROW);
    }

    private static int tabsY(int height) {
        return height - INPUT_HEIGHT - TAB_ROW;
    }

    private static int buttonsY(int height) {
        int above = ChatTabs.tabs().isEmpty() ? height - INPUT_HEIGHT : tabsY(height);
        return above - BUTTON_ROW;
    }

    @SubscribeEvent
    public void onChatPosition(RenderGameOverlayEvent.Chat event) {
        event.posY -= chatShift();
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !ChatPopups.isEmpty()) {
            ChatPopups.tick(System.currentTimeMillis());
        }
    }

    // ---------------------------------------------------------------- HUD (popups, tab hint)

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution resolution = new ScaledResolution(mc);
        boolean chatOpen = mc.currentScreen instanceof GuiChat;
        int[] mouse = chatOpen ? mouse(mc, resolution) : null;

        String hint = ChatTabs.hint();
        if (hint != null && !chatOpen) {
            drawTabHint(mc.fontRendererObj, hint, resolution.getScaledHeight());
        }
        drawPopups(mc, resolution, chatOpen, mouse);
    }

    private static void drawTabHint(FontRenderer font, String tabId, int height) {
        ChatTabs.Tab tab = ChatTabs.ALL.equals(tabId) ? null : ChatTabs.tab(tabId);
        if (tab == null && !ChatTabs.ALL.equals(tabId)) {
            return;
        }
        drawTab(font, 2, height - 27, tab, true, -1, -1);
    }

    private static void drawPopups(Minecraft mc, ScaledResolution resolution, boolean chatOpen, int[] mouse) {
        List<Hit> hits = new ArrayList<>();
        List<ChatPopups.Popup> popups = ChatPopups.visible();
        if (popups.isEmpty()) {
            popupHits = hits;
            return;
        }
        FontRenderer font = mc.fontRendererObj;
        long now = System.currentTimeMillis();
        int x = resolution.getScaledWidth() - POPUP_MARGIN - POPUP_WIDTH;
        int bottom = resolution.getScaledHeight() - POPUP_BOTTOM;
        String respondHint = GameSettings.getKeyDisplayString(mc.gameSettings.keyBindChat.getKeyCode()) + " to respond";

        for (ChatPopups.Popup popup : popups) {
            int textWidth = POPUP_WIDTH - POPUP_PADDING * 2;
            List<String> title = font.listFormattedStringToWidth(popup.title, textWidth - 10);
            List<String> description = popup.description == null
                ? new ArrayList<String>() : font.listFormattedStringToWidth(popup.description, textWidth);
            boolean hasOptions = !popup.options.isEmpty();
            float remaining = popup.remainingFraction(now);

            int height = POPUP_PADDING + title.size() * LINE
                + (description.isEmpty() ? 0 : 2 + description.size() * LINE)
                + (hasOptions ? 4 + (chatOpen ? OPTION_HEIGHT : LINE) : 0)
                + POPUP_PADDING + (remaining >= 0.0F ? 3 : 0);
            int y = bottom - height;
            bottom = y - POPUP_GAP;

            ButtonPainter.roundedRect(x, y, POPUP_WIDTH, height, 0xD0101010, 0x80FFFFFF);
            int cursor = y + POPUP_PADDING;
            for (String line : title) {
                font.drawStringWithShadow(line, x + POPUP_PADDING, cursor, 0xFFFFFFFF);
                cursor += LINE;
            }
            if (!description.isEmpty()) {
                cursor += 2;
                for (String line : description) {
                    font.drawStringWithShadow(line, x + POPUP_PADDING, cursor, 0xFFAAAAAA);
                    cursor += LINE;
                }
            }

            int closeX = x + POPUP_WIDTH - POPUP_PADDING - 6;
            int closeY = y + POPUP_PADDING - 1;
            boolean closeHover = mouse != null && inside(mouse, closeX - 2, closeY - 1, 10, 10);
            font.drawStringWithShadow("x", closeX, closeY, closeHover ? 0xFFFF5555 : 0xFFAAAAAA);
            if (chatOpen) {
                hits.add(new Hit(closeX - 2, closeY - 1, 10, 10, () -> {
                    if (!popup.busy) {
                        ChatPopups.close(popup, null, ChatPopups.STATUS_DISMISSED);
                    }
                }));
            }

            if (hasOptions) {
                cursor += 4;
                if (chatOpen) {
                    int count = popup.options.size();
                    int optionWidth = (textWidth - (count - 1) * 4) / count;
                    for (int i = 0; i < count; i++) {
                        ChatPopups.Option option = popup.options.get(i);
                        int ox = x + POPUP_PADDING + i * (optionWidth + 4);
                        boolean hover = mouse != null && inside(mouse, ox, cursor, optionWidth, OPTION_HEIGHT);
                        ButtonPainter.roundedRect(ox, cursor, optionWidth, OPTION_HEIGHT,
                            hover ? 0x60FFFFFF : 0x30FFFFFF, hover ? 0xC0FFFFFF : 0x80252525);
                        String label = font.trimStringToWidth(option.label, optionWidth - 4);
                        font.drawStringWithShadow(label, ox + (optionWidth - font.getStringWidth(label)) / 2.0F,
                            cursor + 3, 0xFFFFFFFF);
                        hits.add(new Hit(ox, cursor, optionWidth, OPTION_HEIGHT, () -> pick(popup, option)));
                    }
                    cursor += OPTION_HEIGHT;
                } else {
                    font.drawStringWithShadow(respondHint, x + POPUP_PADDING, cursor, 0xFF777777);
                    cursor += LINE;
                }
            }

            if (remaining >= 0.0F) {
                int barY = y + height - 3;
                Gui.drawRect(x + 1, barY, x + POPUP_WIDTH - 1, barY + 2, 0x40FFFFFF);
                Gui.drawRect(x + 1, barY, x + 1 + (int) ((POPUP_WIDTH - 2) * remaining), barY + 2, 0xC0FFFFFF);
            }
        }
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        popupHits = hits;
    }

    private static void pick(ChatPopups.Popup popup, ChatPopups.Option option) {
        if (popup.busy) {
            return;
        }
        if (option.action == null) {
            ChatPopups.close(popup, option.id, ChatPopups.STATUS_PICKED);
            return;
        }
        popup.busy = true;
        option.action.perform(Minecraft.getMinecraft().currentScreen, done -> {
            popup.busy = false;
            ChatPopups.close(popup, option.id, done ? ChatPopups.STATUS_PICKED : ChatPopups.STATUS_DISMISSED);
        });
    }

    // ---------------------------------------------------------------- chat screen (tabs, buttons)

    @SubscribeEvent
    public void onDrawChat(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!(event.gui instanceof GuiChat)) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer font = mc.fontRendererObj;
        int height = event.gui.height;
        int mx = event.mouseX;
        int my = event.mouseY;
        List<String> tooltip = null;

        List<ChatTabs.Tab> tabs = ChatTabs.tabs();
        if (!tabs.isEmpty()) {
            int y = tabsY(height);
            int x = 2;
            String selected = ChatTabs.selected();
            x = drawTab(font, x, y, null, ChatTabs.ALL.equals(selected), mx, my);
            for (ChatTabs.Tab tab : tabs) {
                int left = x;
                x = drawTab(font, x, y, tab, tab.id.equals(selected), mx, my);
                if (tab.tooltip != null && mx >= left && mx < x - 1 && my >= y && my < y + TAB_HEIGHT) {
                    tooltip = tab.tooltip;
                }
            }
        }

        List<ApolloButton> buttons = ChatUiModule.buttons();
        if (!buttons.isEmpty()) {
            float boxX = 2.0F;
            float boxY = buttonsY(height);
            for (ApolloButton button : buttons) {
                boolean hover = button.contains(boxX, boxY, mx, my);
                ButtonPainter.paint(button, boxX, boxY, hover);
                if (hover && button.tooltip != null) {
                    tooltip = button.tooltip;
                }
            }
        }

        if (tooltip != null) {
            ButtonPainter.tooltip(tooltip, mx, my);
        }
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** Draws one tab ({@code null} = "All") and returns the x where the next one starts. */
    private static int drawTab(FontRenderer font, int x, int y, ChatTabs.Tab tab, boolean selected, int mx, int my) {
        ButtonContent name = tab == null ? null : tab.name;
        int contentWidth = name == null ? font.getStringWidth("All") : name.width(font, 8.0F);
        int width = contentWidth + TAB_PADDING * 2;
        boolean hover = mx >= x && mx < x + width && my >= y && my < y + TAB_HEIGHT;
        int background = tab != null && tab.background != null ? tab.background : TAB_BACKGROUND;
        Gui.drawRect(x, y, x + width, y + TAB_HEIGHT, background);
        if (selected) {
            Gui.drawRect(x, y, x + width, y + TAB_HEIGHT, 0x30FFFFFF);
            Gui.drawRect(x, y + TAB_HEIGHT - 1, x + width, y + TAB_HEIGHT, 0xFFFFFFFF);
        } else if (hover) {
            Gui.drawRect(x, y, x + width, y + TAB_HEIGHT, 0x18FFFFFF);
        }
        if (name == null) {
            font.drawStringWithShadow("All", x + TAB_PADDING, y + 2, selected ? 0xFFFFFFFF : 0xFFBBBBBB);
        } else {
            name.draw(font, x + width / 2.0F, y + TAB_HEIGHT / 2.0F + 0.5F, 1.0F, 8.0F, selected ? 1.0F : 0.8F);
        }
        return x + width + 1;
    }

    @SubscribeEvent
    public void onMouse(GuiScreenEvent.MouseInputEvent.Pre event) {
        if (!(event.gui instanceof GuiChat) || !Mouse.getEventButtonState() || Mouse.getEventButton() != 0) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        GuiScreen gui = event.gui;
        int mx = Mouse.getEventX() * gui.width / mc.displayWidth;
        int my = gui.height - Mouse.getEventY() * gui.height / mc.displayHeight - 1;

        for (Hit hit : popupHits) {
            if (hit.contains(mx, my)) {
                event.setCanceled(true);
                playClick(mc);
                hit.action.run();
                return;
            }
        }

        List<ApolloButton> buttons = ChatUiModule.buttons();
        if (!buttons.isEmpty()) {
            float boxY = buttonsY(gui.height);
            for (ApolloButton button : buttons) {
                if (button.contains(2.0F, boxY, mx, my)) {
                    event.setCanceled(true);
                    if (button.action != null) {
                        playClick(mc);
                        button.action.perform(gui, done -> { });
                    }
                    return;
                }
            }
        }

        List<ChatTabs.Tab> tabs = ChatTabs.tabs();
        if (!tabs.isEmpty()) {
            int y = tabsY(gui.height);
            if (my < y || my >= y + TAB_HEIGHT) {
                return;
            }
            FontRenderer font = mc.fontRendererObj;
            int x = 2;
            int width = font.getStringWidth("All") + TAB_PADDING * 2;
            if (mx >= x && mx < x + width) {
                event.setCanceled(true);
                playClick(mc);
                ChatTabs.select(ChatTabs.ALL, false);
                return;
            }
            x += width + 1;
            for (ChatTabs.Tab tab : tabs) {
                width = tab.name.width(font, 8.0F) + TAB_PADDING * 2;
                if (mx >= x && mx < x + width) {
                    event.setCanceled(true);
                    playClick(mc);
                    ChatTabs.select(tab.id, false);
                    return;
                }
                x += width + 1;
            }
        }
    }

    private static void playClick(Minecraft mc) {
        mc.getSoundHandler().playSound(net.minecraft.client.audio.PositionedSoundRecord.create(
            new net.minecraft.util.ResourceLocation("gui.button.press"), 1.0F));
    }

    private static int[] mouse(Minecraft mc, ScaledResolution resolution) {
        int mx = Mouse.getX() * resolution.getScaledWidth() / mc.displayWidth;
        int my = resolution.getScaledHeight() - Mouse.getY() * resolution.getScaledHeight() / mc.displayHeight - 1;
        return new int[] {mx, my};
    }

    private static boolean inside(int[] mouse, int x, int y, int w, int h) {
        return mouse[0] >= x && mouse[0] < x + w && mouse[1] >= y && mouse[1] < y + h;
    }
}
