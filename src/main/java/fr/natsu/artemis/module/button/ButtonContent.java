package fr.natsu.artemis.module.button;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.protobuf.ByteString;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;

import fr.natsu.artemis.module.chat.HexTextRenderer;
import fr.natsu.artemis.module.nametag.AdventureText;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;

/**
 * {@code button.v1.ButtonContent}: an ordered row of text and icon parts ({@code parts} #1, each a
 * {@code ButtonContentPart} with {@code adventure_json_text} #1 or {@code icon} #2) drawn centered,
 * at {@code scale} (#2, 0.25 to 4, default 1).
 *
 * <p>Text parts are kept as legacy strings with Artemis' {@code §x} hex codes, so a single
 * {@link FontRenderer} call draws them with their colors.</p>
 */
public final class ButtonContent {

    public static final ButtonContent EMPTY = new ButtonContent(Collections.<Object>emptyList(), 1.0F);

    private static final int TEXT_HEIGHT = 8;
    private static final int PART_GAP = 2;

    /** Each part is a {@link String} (encoded text) or an {@link ApolloIcon}. */
    public final List<Object> parts;
    public final float scale;

    public ButtonContent(List<Object> parts, float scale) {
        this.parts = parts;
        this.scale = scale;
    }

    public static ButtonContent parse(ByteString data) throws Exception {
        CodedInputStream in = data.newCodedInput();
        List<Object> parts = new ArrayList<>();
        float scale = 1.0F;
        int tag;
        while ((tag = in.readTag()) != 0) {
            switch (WireFormat.getTagFieldNumber(tag)) {
                case 1:
                    Object part = parsePart(in.readBytes());
                    if (part != null) {
                        parts.add(part);
                    }
                    break;
                case 2:
                    scale = in.readFloat();
                    break;
                default:
                    in.skipField(tag);
            }
        }
        if (scale <= 0.0F) {
            scale = 1.0F;
        }
        return new ButtonContent(parts, Math.max(0.25F, Math.min(4.0F, scale)));
    }

    private static Object parsePart(ByteString data) throws Exception {
        CodedInputStream in = data.newCodedInput();
        Object part = null;
        int tag;
        while ((tag = in.readTag()) != 0) {
            switch (WireFormat.getTagFieldNumber(tag)) {
                case 1:
                    String text = encode(in.readString());
                    part = text.isEmpty() ? null : text;
                    break;
                case 2:
                    part = ApolloIcon.parse(in.readBytes());
                    break;
                default:
                    in.skipField(tag);
            }
        }
        return part;
    }

    /** An Adventure JSON component as one legacy string with {@code §x} hex colors. */
    public static String encode(String adventureJson) {
        StringBuilder sb = new StringBuilder();
        for (AdventureText.Run run : AdventureText.parse(adventureJson)) {
            sb.append(HexTextRenderer.hexPrefix(run.rgb)).append(run.display);
        }
        return sb.toString();
    }

    /** Unscaled row width; icons taller than {@code maxIcon} are shrunk to it. */
    public int width(FontRenderer font, float maxIcon) {
        int width = 0;
        Object previous = null;
        for (Object part : this.parts) {
            if (part instanceof ApolloIcon && !((ApolloIcon) part).drawable()) {
                continue;
            }
            if (previous != null && !(previous instanceof String && part instanceof String)) {
                width += PART_GAP;
            }
            width += part instanceof String ? font.getStringWidth((String) part) : (int) iconWidth((ApolloIcon) part, maxIcon);
            previous = part;
        }
        return width;
    }

    public int height(float maxIcon) {
        float height = TEXT_HEIGHT;
        for (Object part : this.parts) {
            if (part instanceof ApolloIcon && ((ApolloIcon) part).drawable()) {
                height = Math.max(height, Math.min(maxIcon, ((ApolloIcon) part).height));
            }
        }
        return (int) Math.ceil(height);
    }

    /**
     * Draws the row centered on ({@code cx}, {@code cy}) at {@code scale}; icons taller than
     * {@code maxIcon} (unscaled pixels) are shrunk to it.
     */
    public void draw(FontRenderer font, float cx, float cy, float scale, float maxIcon, float alpha) {
        if (this.parts.isEmpty()) {
            return;
        }
        int width = width(font, maxIcon);
        GlStateManager.pushMatrix();
        GlStateManager.translate(cx, cy, 0.0F);
        GlStateManager.scale(scale, scale, 1.0F);
        float x = -width / 2.0F;
        Object previous = null;
        int alphaBits = Math.max(5, (int) (alpha * 255.0F)) << 24;
        for (Object part : this.parts) {
            if (part instanceof ApolloIcon && !((ApolloIcon) part).drawable()) {
                continue;
            }
            if (previous != null && !(previous instanceof String && part instanceof String)) {
                x += PART_GAP;
            }
            if (part instanceof String) {
                String text = (String) part;
                font.drawString(text, x, -TEXT_HEIGHT / 2.0F, alphaBits | 0xFFFFFF, true);
                x += font.getStringWidth(text);
            } else {
                ApolloIcon icon = (ApolloIcon) part;
                float w = iconWidth(icon, maxIcon);
                float h = Math.min(maxIcon, icon.height);
                icon.draw(x, -h / 2.0F, w, h, alpha);
                x += w;
            }
            previous = part;
        }
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
    }

    private static float iconWidth(ApolloIcon icon, float maxIcon) {
        return icon.height > maxIcon ? icon.width * maxIcon / icon.height : icon.width;
    }
}
