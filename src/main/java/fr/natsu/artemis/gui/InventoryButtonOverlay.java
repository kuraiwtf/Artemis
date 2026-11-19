package fr.natsu.artemis.gui;

import java.util.List;

import org.lwjgl.input.Mouse;

import fr.natsu.artemis.module.button.ApolloButton;
import fr.natsu.artemis.module.inventory.InventoryButtonsModule;
import fr.natsu.artemis.render.ButtonPainter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Draws the Apollo inventory buttons beside the player inventory (survival and creative) and runs
 * their actions. The 92x166 boxes sit flush against the container, vertically centered on it.
 */
public final class InventoryButtonOverlay {

    private static final int GAP = 2;

    private static boolean applies(GuiScreen gui) {
        return (gui instanceof GuiInventory || gui instanceof GuiContainerCreative) && gui instanceof ContainerBounds;
    }

    /** {left box x, right box x, box y}. */
    private static float[] boxes(GuiScreen gui) {
        ContainerBounds bounds = (ContainerBounds) gui;
        float y = bounds.artemis$top() + (bounds.artemis$height() - InventoryButtonsModule.BOX_HEIGHT) / 2.0F;
        float left = bounds.artemis$left() - GAP - InventoryButtonsModule.BOX_WIDTH;
        float right = bounds.artemis$left() + bounds.artemis$width() + GAP;
        return new float[] {left, right, y};
    }

    @SubscribeEvent
    public void onDraw(GuiScreenEvent.DrawScreenEvent.Post event) {
        List<InventoryButtonsModule.Entry> entries = InventoryButtonsModule.entries();
        if (entries.isEmpty() || !applies(event.gui)) {
            return;
        }
        float[] boxes = boxes(event.gui);
        List<String> tooltip = null;
        GlStateManager.pushMatrix();
        GlStateManager.translate(0.0F, 0.0F, 300.0F);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        for (InventoryButtonsModule.Entry entry : entries) {
            float boxX = entry.box == InventoryButtonsModule.BOX_RIGHT ? boxes[1] : boxes[0];
            boolean hover = entry.button.contains(boxX, boxes[2], event.mouseX, event.mouseY);
            ButtonPainter.paint(entry.button, boxX, boxes[2], hover);
            if (hover && entry.button.tooltip != null) {
                tooltip = entry.button.tooltip;
            }
        }
        if (tooltip != null) {
            ButtonPainter.tooltip(tooltip, event.mouseX, event.mouseY);
        }
        GlStateManager.enableDepth();
        GlStateManager.popMatrix();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @SubscribeEvent
    public void onMouse(GuiScreenEvent.MouseInputEvent.Pre event) {
        if (!Mouse.getEventButtonState() || Mouse.getEventButton() != 0) {
            return;
        }
        List<InventoryButtonsModule.Entry> entries = InventoryButtonsModule.entries();
        if (entries.isEmpty() || !applies(event.gui)) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        GuiScreen gui = event.gui;
        int mx = Mouse.getEventX() * gui.width / mc.displayWidth;
        int my = gui.height - Mouse.getEventY() * gui.height / mc.displayHeight - 1;
        float[] boxes = boxes(gui);
        for (InventoryButtonsModule.Entry entry : entries) {
            float boxX = entry.box == InventoryButtonsModule.BOX_RIGHT ? boxes[1] : boxes[0];
            ApolloButton button = entry.button;
            if (button.contains(boxX, boxes[2], mx, my)) {
                event.setCanceled(true);
                if (button.action != null) {
                    mc.getSoundHandler().playSound(
                        PositionedSoundRecord.create(new ResourceLocation("gui.button.press"), 1.0F));
                    button.action.perform(gui, done -> { });
                }
                return;
            }
        }
    }
}
