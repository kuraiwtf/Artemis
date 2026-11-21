package fr.natsu.artemis.module.glint;

import net.minecraft.item.ItemStack;
import fr.natsu.artemis.LunarHost;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagString;

/**
 * Reads an item's glint color, following the Apollo glint module convention.
 *
 * <p>The server puts a {@code lunar.glint} NBT tag on the item, either an <b>ARGB integer</b>
 * (e.g. {@code -16711936} = {@code 0xFF00FF00}, green) or a <b>HEX string</b> (e.g.
 * {@code "#FFFF00"}, yellow). The tag travels with the item through the normal Minecraft packets.
 * Any item carrying it shows a glint, enchanted or not, and the color replaces the vanilla purple.
 * The rendering is done by {@code MixinRenderItem} (items) and {@code MixinLayerArmor} (worn armor).</p>
 *
 * @see <a href="https://lunarclient.dev/apollo/developers/modules/glint">Apollo glint module</a>
 */
public final class GlintState {

    public static final String LUNAR_TAG = "lunar";

    public static final String GLINT_KEY = "glint";

    /** Returned by {@link #glintArgb} when the item has no usable glint tag. */
    public static final int NONE = 0;

    private static final int TAG_STRING = 8;

    private static final int TAG_ANY_NUMERIC = 99;

    private GlintState() {
    }

    public static boolean hasGlint(ItemStack stack) {
        return glintArgb(stack) != NONE;
    }

    /**
     * The glint color of the item as opaque-capable ARGB, or {@link #NONE} when the item has no
     * (valid) {@code lunar.glint} tag. A color given without alpha (a 6-digit hex, or an integer
     * whose alpha byte is 0) is made fully opaque, otherwise it would never be visible.
     */
    public static int glintArgb(ItemStack stack) {
        if (LunarHost.isLunar() || stack == null || !stack.hasTagCompound()) {
            return NONE;
        }
        NBTTagCompound tag = stack.getTagCompound();
        if (!tag.hasKey(LUNAR_TAG, 10)) {
            return NONE;
        }
        NBTTagCompound lunar = tag.getCompoundTag(LUNAR_TAG);
        if (lunar.hasKey(GLINT_KEY, TAG_ANY_NUMERIC)) {
            return opaque((int) ((NBTBase.NBTPrimitive) lunar.getTag(GLINT_KEY)).getLong());
        }
        if (lunar.hasKey(GLINT_KEY, TAG_STRING)) {
            return parse(((NBTTagString) lunar.getTag(GLINT_KEY)).getString());
        }
        return NONE;
    }

    /** Parses {@code "#RRGGBB"}, {@code "#AARRGGBB"} (with or without {@code #}/{@code 0x}) or a decimal integer. */
    static int parse(String raw) {
        String value = raw.trim();
        try {
            if (value.startsWith("#") || value.regionMatches(true, 0, "0x", 0, 2)) {
                String hex = value.substring(value.startsWith("#") ? 1 : 2);
                if (hex.length() == 6) {
                    return 0xFF000000 | Integer.parseInt(hex, 16);
                }
                if (hex.length() == 8) {
                    return opaque((int) Long.parseLong(hex, 16));
                }
                return NONE;
            }
            return opaque((int) Long.parseLong(value));
        } catch (NumberFormatException e) {
            return NONE;
        }
    }

    private static int opaque(int argb) {
        if (argb == NONE) {
            return NONE;
        }
        return (argb >>> 24) == 0 ? argb | 0xFF000000 : argb;
    }
}
