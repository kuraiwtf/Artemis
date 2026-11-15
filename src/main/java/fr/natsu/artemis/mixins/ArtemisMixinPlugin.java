package fr.natsu.artemis.mixins;

import java.util.List;
import java.util.Set;

import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Filters out the Lunar-only mixin classes (suffix {@code Lunar}) when Artemis runs on a plain
 * Forge client. Mixin 0.7.11 aborts the whole game when an injector targets a method that does not
 * exist in the target class, even with {@code require = 0}, so hooks aimed at methods merged by
 * Lunar's own mixins ({@code lunar$...}) must never be applied outside Lunar Client.
 *
 * <p>Detection is self-contained on purpose: this runs during mixin application, long before mod
 * construction, and must not pull Forge-dependent classes such as {@code LunarHost} into this
 * class loader.
 */
public final class ArtemisMixinPlugin implements IMixinConfigPlugin {

    private static final String LUNAR_ONLY_SUFFIX = "Lunar";

    private static final String[] MARKER_CLASSES = {
        "com.lunarclient.apollo.ApolloManager",
        "com.moonsworth.lunar.client.Lunar"
    };

    private static Boolean lunar;

    private static boolean isLunar() {
        if (lunar != null) {
            return lunar;
        }
        String forced = System.getProperty("artemis.lunar");
        if (forced != null) {
            lunar = Boolean.parseBoolean(forced);
            return lunar;
        }
        ClassLoader[] loaders = {
            ArtemisMixinPlugin.class.getClassLoader(),
            Thread.currentThread().getContextClassLoader(),
            ClassLoader.getSystemClassLoader()
        };
        for (String name : MARKER_CLASSES) {
            for (ClassLoader loader : loaders) {
                if (loader == null) {
                    continue;
                }
                try {
                    Class.forName(name, false, loader);
                    lunar = Boolean.TRUE;
                    return true;
                } catch (Throwable ignored) {
                }
            }
        }
        lunar = Boolean.FALSE;
        return false;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith(LUNAR_ONLY_SUFFIX)) {
            return isLunar();
        }
        return true;
    }

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
