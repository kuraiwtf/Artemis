package fr.natsu.artemis.module.button;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.lwjgl.opengl.GL11;

import com.google.protobuf.ByteString;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;

import fr.natsu.artemis.Artemis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

/**
 * {@code common.v1.Icon}, drawn in GUI space: an item stack (#1), or a texture: simple (#2, square
 * {@code size}), advanced (#3, {@code width}/{@code height} + UVs) or plain (#4, 16x16).
 *
 * <p>Item names come from Bukkit materials ({@code "NETHER_STAR"}), so they are matched lowercased
 * against the 1.8.9 registry. Textures missing from the resource packs (Lunar-only assets such as
 * {@code lunar:...}) are skipped instead of drawn as the missing-texture checkerboard.</p>
 */
public final class ApolloIcon {

    private static final Map<ResourceLocation, Boolean> TEXTURE_EXISTS = new ConcurrentHashMap<>();
    private static final Set<String> REPORTED = java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());

    /**
     * Bukkit 1.8 material names whose 1.8.9 registry name differs ({@code name().toLowerCase()} covers
     * the rest). Values are {@code registry name} or {@code registry name:metadata}.
     */
    private static final Map<String, String> BUKKIT_NAMES = new HashMap<>();

    static {
        String[] pairs = {
            "INK_SACK", "dye", "SKULL_ITEM", "skull:3", "SKULL", "skull:3", "EXP_BOTTLE", "experience_bottle",
            "WATCH", "clock", "WOOD_SWORD", "wooden_sword", "WOOD_AXE", "wooden_axe", "WOOD_PICKAXE", "wooden_pickaxe",
            "WOOD_SPADE", "wooden_shovel", "WOOD_HOE", "wooden_hoe", "GOLD_SWORD", "golden_sword",
            "GOLD_AXE", "golden_axe", "GOLD_PICKAXE", "golden_pickaxe", "GOLD_SPADE", "golden_shovel",
            "GOLD_HOE", "golden_hoe", "IRON_SPADE", "iron_shovel", "STONE_SPADE", "stone_shovel",
            "DIAMOND_SPADE", "diamond_shovel", "GOLD_HELMET", "golden_helmet", "GOLD_CHESTPLATE", "golden_chestplate",
            "GOLD_LEGGINGS", "golden_leggings", "GOLD_BOOTS", "golden_boots", "GOLD_INGOT", "gold_ingot",
            "GOLD_NUGGET", "gold_nugget", "GOLD_BLOCK", "gold_block", "GOLD_ORE", "gold_ore",
            "GOLDEN_APPLE", "golden_apple", "GOLDEN_CARROT", "golden_carrot", "WOOD", "planks", "LOG_2", "log2",
            "LEAVES_2", "leaves2", "WORKBENCH", "crafting_table", "BED", "bed", "BED_BLOCK", "bed",
            "BOOK_AND_QUILL", "writable_book", "EMPTY_MAP", "map", "MAP", "filled_map", "CARROT_ITEM", "carrot",
            "POTATO_ITEM", "potato", "SULPHUR", "gunpowder", "SNOW_BALL", "snowball", "FIREWORK", "fireworks",
            "FIREWORK_CHARGE", "firework_charge", "RAW_FISH", "fish", "COOKED_FISH", "cooked_fish", "PORK", "porkchop",
            "GRILLED_PORK", "cooked_porkchop", "RAW_BEEF", "beef", "RAW_CHICKEN", "chicken", "MUSHROOM_SOUP", "mushroom_stew",
            "SPECKLED_MELON", "speckled_melon", "NETHER_STALK", "nether_wart", "NETHER_BRICK_ITEM", "netherbrick",
            "CLAY_BRICK", "brick", "CLAY_BALL", "clay_ball", "HARD_CLAY", "hardened_clay", "STAINED_CLAY", "stained_hardened_clay",
            "THIN_GLASS", "glass_pane", "IRON_FENCE", "iron_bars", "FENCE", "fence", "FENCE_GATE", "fence_gate",
            "SMOOTH_BRICK", "stonebrick", "MOSSY_COBBLESTONE", "mossy_cobblestone", "ENDER_STONE", "end_stone",
            "ENCHANTMENT_TABLE", "enchanting_table", "ENDER_PORTAL_FRAME", "end_portal_frame", "EYE_OF_ENDER", "ender_eye",
            "MONSTER_EGG", "spawn_egg", "MONSTER_EGGS", "monster_egg", "REDSTONE_TORCH_ON", "redstone_torch",
            "DIODE", "repeater", "REDSTONE_COMPARATOR", "comparator", "PISTON_BASE", "piston", "PISTON_STICKY_BASE", "sticky_piston",
            "SIGN", "sign", "WOOD_DOOR", "wooden_door", "WOODEN_DOOR", "wooden_door", "IRON_DOOR", "iron_door", "TRAP_DOOR", "trapdoor",
            "WOOD_PLATE", "wooden_pressure_plate", "STONE_PLATE", "stone_pressure_plate", "WOOD_BUTTON", "wooden_button",
            "LEASH", "lead", "CARROT_STICK", "carrot_on_a_stick", "BOAT", "boat", "MINECART", "minecart",
            "STORAGE_MINECART", "chest_minecart", "POWERED_MINECART", "furnace_minecart", "EXPLOSIVE_MINECART", "tnt_minecart",
            "HOPPER_MINECART", "hopper_minecart", "COMMAND", "command_block", "COMMAND_MINECART", "command_block_minecart",
            "IRON_BARDING", "iron_horse_armor", "GOLD_BARDING", "golden_horse_armor", "DIAMOND_BARDING", "diamond_horse_armor",
            "RECORD_3", "record_blocks", "GREEN_RECORD", "record_cat", "GOLD_RECORD", "record_13", "NETHER_WARTS", "nether_wart",
            "QUARTZ", "quartz", "QUARTZ_ORE", "quartz_ore", "LONG_GRASS", "tallgrass", "YELLOW_FLOWER", "yellow_flower",
            "RED_ROSE", "red_flower", "DOUBLE_PLANT", "double_plant", "SUGAR_CANE", "reeds", "SUGAR_CANE_BLOCK", "reeds",
            "CAKE", "cake", "CAKE_BLOCK", "cake", "CAULDRON_ITEM", "cauldron", "BREWING_STAND_ITEM", "brewing_stand",
            "FLOWER_POT_ITEM", "flower_pot", "SKULL_ITEM:0", "skull:0", "WEB", "web", "SOIL", "farmland",
            "NETHER_FENCE", "nether_brick_fence", "SMOOTH_STAIRS", "stone_brick_stairs", "WOOD_STAIRS", "oak_stairs",
            "STEP", "stone_slab", "WOOD_STEP", "wooden_slab", "BANNER", "banner", "ARMOR_STAND", "armor_stand"
        };
        for (int i = 0; i < pairs.length; i += 2) {
            BUKKIT_NAMES.put(pairs[i], pairs[i + 1]);
        }
    }

    public final ItemStack item;
    public final ResourceLocation texture;
    public final float width;
    public final float height;
    private final float minU;
    private final float maxU;
    private final float minV;
    private final float maxV;

    private ApolloIcon(ItemStack item, ResourceLocation texture, float width, float height,
                       float minU, float maxU, float minV, float maxV) {
        this.item = item;
        this.texture = texture;
        this.width = width;
        this.height = height;
        this.minU = minU;
        this.maxU = maxU;
        this.minV = minV;
        this.maxV = maxV;
    }

    @Override
    public String toString() {
        return this.item != null ? "item " + Item.itemRegistry.getNameForObject(this.item.getItem())
            + ":" + this.item.getMetadata() : "texture " + this.texture + " " + this.width + "x" + this.height;
    }

    /** Parses an {@code Icon}; {@code null} when it names nothing drawable. Each distinct icon is logged once. */
    public static ApolloIcon parse(ByteString data) throws Exception {
        ApolloIcon icon = parseRaw(data);
        if (icon != null && REPORTED.add("seen:" + icon)) {
            Artemis.LOGGER.info("[Icon] received {}", icon);
        }
        return icon;
    }

    private static ApolloIcon parseRaw(ByteString data) throws Exception {
        CodedInputStream in = data.newCodedInput();
        ApolloIcon icon = null;
        int tag;
        while ((tag = in.readTag()) != 0) {
            switch (WireFormat.getTagFieldNumber(tag)) {
                case 1:
                    icon = parseItem(in.readBytes());
                    break;
                case 2:
                    icon = parseSimple(in.readBytes());
                    break;
                case 3:
                    icon = parseAdvanced(in.readBytes());
                    break;
                case 4:
                    icon = parseSimple(in.readBytes());
                    break;
                default:
                    in.skipField(tag);
            }
        }
        return icon;
    }

    private static ApolloIcon parseItem(ByteString data) throws Exception {
        CodedInputStream in = data.newCodedInput();
        int id = -1;
        String name = null;
        int tag;
        while ((tag = in.readTag()) != 0) {
            switch (WireFormat.getTagFieldNumber(tag)) {
                case 1:
                    id = in.readInt32();
                    break;
                case 2:
                    name = in.readString();
                    break;
                default:
                    in.skipField(tag);
            }
        }
        ItemStack stack = resolveItem(name, id);
        if (stack == null) {
            report("item:" + name + ":" + id, "[Icon] unknown item icon (name='" + name + "', id=" + id + ")");
            return null;
        }
        return new ApolloIcon(stack, null, 16.0F, 16.0F, 0, 1, 0, 1);
    }

    /** A Bukkit material name, a registry name (with or without namespace), or a numeric id. */
    private static ItemStack resolveItem(String name, int id) {
        if (name != null && !name.isEmpty()) {
            String upper = name.toUpperCase(Locale.ROOT);
            String alias = BUKKIT_NAMES.get(upper.startsWith("MINECRAFT:") ? upper.substring(10) : upper);
            int meta = 0;
            String registry = name;
            if (alias != null) {
                int colon = alias.indexOf(':');
                registry = colon < 0 ? alias : alias.substring(0, colon);
                meta = colon < 0 ? 0 : Integer.parseInt(alias.substring(colon + 1));
            }
            Item item = Item.getByNameOrId(registry);
            if (item == null) {
                item = Item.getByNameOrId(registry.toLowerCase(Locale.ROOT));
            }
            return item == null ? null : new ItemStack(item, 1, meta);
        }
        Item item = id >= 0 ? Item.getItemById(id) : null;
        return item == null ? null : new ItemStack(item);
    }

    private static void report(String key, String message) {
        if (REPORTED.add(key)) {
            Artemis.LOGGER.warn(message);
        }
    }

    /** Whether {@link #draw} will draw anything (a texture missing from the resource packs won't). */
    public boolean drawable() {
        if (this.item != null) {
            return true;
        }
        if (this.texture == null) {
            return false;
        }
        boolean exists = exists(Minecraft.getMinecraft(), this.texture);
        if (!exists) {
            report("tex:" + this.texture, "[Icon] texture icon " + this.texture
                + " is not in the loaded resource packs (Lunar-only asset?), skipped");
        }
        return exists;
    }

    /** Simple ({@code resource_location} #1, {@code size} #2) and plain ({@code resource_location} #1). */
    private static ApolloIcon parseSimple(ByteString data) throws Exception {
        CodedInputStream in = data.newCodedInput();
        String location = null;
        int size = 16;
        int tag;
        while ((tag = in.readTag()) != 0) {
            switch (WireFormat.getTagFieldNumber(tag)) {
                case 1:
                    location = in.readString();
                    break;
                case 2:
                    size = in.readInt32();
                    break;
                default:
                    in.skipField(tag);
            }
        }
        if (location == null || location.isEmpty()) {
            return null;
        }
        float s = size > 0 ? size : 16.0F;
        return new ApolloIcon(null, new ResourceLocation(location), s, s, 0, 1, 0, 1);
    }

    private static ApolloIcon parseAdvanced(ByteString data) throws Exception {
        CodedInputStream in = data.newCodedInput();
        String location = null;
        float width = 16.0F;
        float height = 16.0F;
        float minU = 0.0F;
        float maxU = 1.0F;
        float minV = 0.0F;
        float maxV = 1.0F;
        int tag;
        while ((tag = in.readTag()) != 0) {
            switch (WireFormat.getTagFieldNumber(tag)) {
                case 1:
                    location = in.readString();
                    break;
                case 2:
                    width = in.readFloat();
                    break;
                case 3:
                    height = in.readFloat();
                    break;
                case 4:
                    minU = in.readFloat();
                    break;
                case 5:
                    maxU = in.readFloat();
                    break;
                case 6:
                    minV = in.readFloat();
                    break;
                case 7:
                    maxV = in.readFloat();
                    break;
                default:
                    in.skipField(tag);
            }
        }
        if (location == null || location.isEmpty()) {
            return null;
        }
        return new ApolloIcon(null, new ResourceLocation(location), width > 0 ? width : 16.0F,
            height > 0 ? height : 16.0F, minU, maxU, minV, maxV);
    }

    /** Draws the icon with its top-left corner at ({@code x}, {@code y}), stretched to {@code w}x{@code h}. */
    public void draw(float x, float y, float w, float h, float alpha) {
        Minecraft mc = Minecraft.getMinecraft();
        if (this.item != null) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(x, y, 0.0F);
            GlStateManager.scale(w / 16.0F, h / 16.0F, 1.0F);
            GlStateManager.enableRescaleNormal();
            RenderHelper.enableGUIStandardItemLighting();
            mc.getRenderItem().renderItemAndEffectIntoGUI(this.item, 0, 0);
            RenderHelper.disableStandardItemLighting();
            GlStateManager.disableRescaleNormal();
            GlStateManager.disableLighting();
            GlStateManager.popMatrix();
            return;
        }
        if (!drawable()) {
            return;
        }
        mc.getTextureManager().bindTexture(this.texture);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(1.0F, 1.0F, 1.0F, alpha);
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer buffer = tessellator.getWorldRenderer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        buffer.pos(x, y + h, 0.0).tex(this.minU, this.maxV).endVertex();
        buffer.pos(x + w, y + h, 0.0).tex(this.maxU, this.maxV).endVertex();
        buffer.pos(x + w, y, 0.0).tex(this.maxU, this.minV).endVertex();
        buffer.pos(x, y, 0.0).tex(this.minU, this.minV).endVertex();
        tessellator.draw();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * Forgets which textures exist whenever resource packs reload: icons often live in the server's
     * own resource pack, which only finishes loading after the server sent the buttons.
     */
    public static void registerReloadListener() {
        net.minecraft.client.resources.IResourceManager manager = Minecraft.getMinecraft().getResourceManager();
        if (manager instanceof net.minecraft.client.resources.IReloadableResourceManager) {
            ((net.minecraft.client.resources.IReloadableResourceManager) manager).registerReloadListener(
                new net.minecraft.client.resources.IResourceManagerReloadListener() {
                    @Override
                    public void onResourceManagerReload(net.minecraft.client.resources.IResourceManager reloaded) {
                        TEXTURE_EXISTS.clear();
                        REPORTED.removeIf(key -> key.startsWith("tex:"));
                    }
                });
        }
    }

    public static boolean exists(Minecraft mc, ResourceLocation location) {
        Boolean known = TEXTURE_EXISTS.get(location);
        if (known == null) {
            try {
                mc.getResourceManager().getResource(location);
                known = Boolean.TRUE;
            } catch (Exception e) {
                known = Boolean.FALSE;
            }
            TEXTURE_EXISTS.put(location, known);
        }
        return known;
    }
}
