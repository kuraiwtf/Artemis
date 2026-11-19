package fr.natsu.artemis.gui;

import java.io.IOException;
import java.util.List;

import org.lwjgl.input.Mouse;

import fr.natsu.artemis.module.button.ApolloIcon;
import fr.natsu.artemis.module.button.ButtonAction;
import fr.natsu.artemis.module.serverlink.ServerLinkModule;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;

/**
 * The server's links, one button each, opened from the pause menu. Links go through the vanilla
 * chat link settings and the "open this link?" prompt.
 */
public final class GuiServerLinks extends GuiScreen {

    private static final int DONE_ID = 0;
    private static final int LINK_ID_BASE = 100;
    private static final int ROW = 24;
    private static final int ICON_SIZE = 48;

    private final GuiScreen parent;
    private List<ServerLinkModule.Link> links;
    private int scroll;
    private int listTop;
    private int visibleRows;

    public GuiServerLinks(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        this.links = ServerLinkModule.links();
        boolean hasIcon = hasIcon();
        this.listTop = hasIcon ? 20 + ICON_SIZE + 10 : 40;
        this.visibleRows = Math.max(1, (this.height - 36 - this.listTop) / ROW);
        this.scroll = MathHelper.clamp_int(this.scroll, 0, Math.max(0, this.links.size() - this.visibleRows));
        this.buttonList.clear();
        for (int i = 0; i < this.visibleRows && this.scroll + i < this.links.size(); i++) {
            ServerLinkModule.Link link = this.links.get(this.scroll + i);
            String label = this.fontRendererObj.trimStringToWidth(link.name, 190);
            this.buttonList.add(new GuiButton(LINK_ID_BASE + this.scroll + i, this.width / 2 - 100,
                this.listTop + i * ROW, label));
        }
        this.buttonList.add(new GuiButton(DONE_ID, this.width / 2 - 100, this.height - 28, "Done"));
    }

    private boolean hasIcon() {
        ResourceLocation icon = ServerLinkModule.icon();
        return icon != null && ApolloIcon.exists(this.mc, icon);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == DONE_ID) {
            this.mc.displayGuiScreen(this.parent);
            return;
        }
        int index = button.id - LINK_ID_BASE;
        if (index >= 0 && index < this.links.size()) {
            ButtonAction.openUrl(this.mc, this, this.links.get(index).url, done -> { });
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0 && this.links.size() > this.visibleRows) {
            this.scroll = MathHelper.clamp_int(this.scroll + (wheel > 0 ? -1 : 1), 0,
                this.links.size() - this.visibleRows);
            this.initGui();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        ResourceLocation icon = ServerLinkModule.icon();
        if (icon != null && hasIcon()) {
            this.mc.getTextureManager().bindTexture(icon);
            GlStateManager.enableBlend();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            drawModalRectWithCustomSizedTexture(this.width / 2 - ICON_SIZE / 2, 20, 0.0F, 0.0F,
                ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
        } else {
            this.drawCenteredString(this.fontRendererObj, "Server Links", this.width / 2, 20, 0xFFFFFF);
        }
        if (this.links.isEmpty()) {
            this.drawCenteredString(this.fontRendererObj, "No links", this.width / 2, this.listTop + 6, 0xAAAAAA);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
