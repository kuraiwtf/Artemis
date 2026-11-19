package fr.natsu.artemis.gui;

import java.util.function.Consumer;

import net.minecraft.client.gui.GuiYesNoCallback;

/**
 * {@link GuiYesNoCallback} from a {@link Consumer}. Minecraft interfaces must be implemented by real
 * (anonymous) classes, never lambdas: on Fabric + Forge-compat clients the interface methods are
 * renamed at runtime, which remaps declared methods but not a lambda's target name, so a lambda
 * fails with {@code AbstractMethodError}.
 */
public final class YesNo {

    private YesNo() {
    }

    public static GuiYesNoCallback of(Consumer<Boolean> onAnswer) {
        return new GuiYesNoCallback() {
            @Override
            public void confirmClicked(boolean result, int id) {
                onAnswer.accept(result);
            }
        };
    }
}
