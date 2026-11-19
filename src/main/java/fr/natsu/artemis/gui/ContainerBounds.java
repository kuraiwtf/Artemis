package fr.natsu.artemis.gui;

/**
 * Implemented on {@code GuiContainer} by {@code MixinGuiContainer}: the container's real on-screen
 * rectangle ({@code guiLeft} moves right when potion effects are shown).
 */
public interface ContainerBounds {

    int artemis$left();

    int artemis$top();

    int artemis$width();

    int artemis$height();
}
