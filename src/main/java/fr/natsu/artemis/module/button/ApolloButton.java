package fr.natsu.artemis.module.button;

import java.util.ArrayList;
import java.util.List;

import com.google.protobuf.ByteString;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;

import fr.natsu.artemis.net.ApolloProtos;

/**
 * {@code button.v1.Button}, shared by chat and inventory buttons: an id, a position and size inside
 * the surface's box (GUI-scaled pixels from its top-left corner), a shape, colors, content, tooltip
 * and click action.
 *
 * <p>Fields: id #1, position #2 (HudPosition), size #3 (width #1, height #2), shape #4
 * (1 = rounded square, 2 = circle), background_color #5, border_color #6, content #7, tooltip #8
 * ({@code adventure_json_lines} #1, repeated), run_command #9 | open_url #10 | client_action #11 |
 * select_chat_tab #14, hovered_background_color #12, hovered_border_color #13.</p>
 */
public final class ApolloButton {

    public static final int SHAPE_CIRCLE = 2;

    public static final int DEFAULT_BACKGROUND = 0x28FFFFFF;
    public static final int DEFAULT_BORDER = 0x80252525;

    public final String id;
    public final float x;
    public final float y;
    public final float width;
    public final float height;
    public final int shape;
    public final int background;
    public final int border;
    public final int hoveredBackground;
    public final int hoveredBorder;
    public final ButtonAction action;

    /** Updatable through {@code ButtonUpdate}. */
    public volatile ButtonContent content;
    /** Encoded lines, or {@code null} for no tooltip. */
    public volatile List<String> tooltip;

    private ApolloButton(String id, float x, float y, float width, float height, int shape, int background,
                         int border, int hoveredBackground, int hoveredBorder, ButtonAction action,
                         ButtonContent content, List<String> tooltip) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.shape = shape;
        this.background = background;
        this.border = border;
        this.hoveredBackground = hoveredBackground;
        this.hoveredBorder = hoveredBorder;
        this.action = action;
        this.content = content;
        this.tooltip = tooltip;
    }

    public static ApolloButton parse(ByteString data) throws Exception {
        CodedInputStream in = data.newCodedInput();
        String id = null;
        float[] position = {0.0F, 0.0F};
        float[] size = {16.0F, 16.0F};
        int shape = 1;
        Integer background = null;
        Integer border = null;
        Integer hoveredBackground = null;
        Integer hoveredBorder = null;
        ButtonAction action = null;
        ButtonContent content = ButtonContent.EMPTY;
        List<String> tooltip = null;
        int tag;
        while ((tag = in.readTag()) != 0) {
            switch (WireFormat.getTagFieldNumber(tag)) {
                case 1:
                    id = in.readString();
                    break;
                case 2:
                    position = ApolloProtos.parseHudPosition(in.readBytes());
                    break;
                case 3:
                    size = ApolloProtos.parseHudPosition(in.readBytes());
                    break;
                case 4:
                    shape = in.readEnum();
                    break;
                case 5:
                    background = ApolloProtos.parseColorArgb(in.readBytes());
                    break;
                case 6:
                    border = ApolloProtos.parseColorArgb(in.readBytes());
                    break;
                case 7:
                    content = ButtonContent.parse(in.readBytes());
                    break;
                case 8:
                    tooltip = parseTooltip(in.readBytes());
                    break;
                case 9:
                    action = ButtonAction.runCommand(in.readString());
                    break;
                case 10:
                    action = ButtonAction.openUrl(in.readString());
                    break;
                case 11:
                    action = ButtonAction.clientAction(in.readEnum());
                    break;
                case 12:
                    hoveredBackground = ApolloProtos.parseColorArgb(in.readBytes());
                    break;
                case 13:
                    hoveredBorder = ApolloProtos.parseColorArgb(in.readBytes());
                    break;
                case 14:
                    action = ButtonAction.parseSelectChatTab(in.readBytes());
                    break;
                default:
                    in.skipField(tag);
            }
        }
        if (id == null || id.isEmpty()) {
            return null;
        }
        int bg = background != null ? background : DEFAULT_BACKGROUND;
        int bd = border != null ? border : DEFAULT_BORDER;
        return new ApolloButton(id, position[0], position[1], Math.max(1.0F, size[0]), Math.max(1.0F, size[1]),
            shape, bg, bd,
            hoveredBackground != null ? hoveredBackground : brighten(bg),
            hoveredBorder != null ? hoveredBorder : 0xC0FFFFFF,
            action, content, tooltip);
    }

    /**
     * Applies a {@code ButtonUpdate}: {@code content} (#1) and {@code tooltip} (#2) replace the current
     * ones only when present; a present tooltip without lines clears it.
     */
    public void update(ByteString data) throws Exception {
        CodedInputStream in = data.newCodedInput();
        int tag;
        while ((tag = in.readTag()) != 0) {
            switch (WireFormat.getTagFieldNumber(tag)) {
                case 1:
                    this.content = ButtonContent.parse(in.readBytes());
                    break;
                case 2:
                    this.tooltip = parseTooltip(in.readBytes());
                    break;
                default:
                    in.skipField(tag);
            }
        }
    }

    /** {@code ButtonTooltip}: {@code adventure_json_lines} (#1, repeated); empty → {@code null}. */
    public static List<String> parseTooltip(ByteString data) throws Exception {
        CodedInputStream in = data.newCodedInput();
        List<String> lines = new ArrayList<>();
        int tag;
        while ((tag = in.readTag()) != 0) {
            if (WireFormat.getTagFieldNumber(tag) == 1) {
                lines.add(ButtonContent.encode(in.readString()));
            } else {
                in.skipField(tag);
            }
        }
        return lines.isEmpty() ? null : lines;
    }

    public boolean contains(float boxX, float boxY, int mouseX, int mouseY) {
        float left = boxX + this.x;
        float top = boxY + this.y;
        if (this.shape == SHAPE_CIRCLE) {
            float radius = Math.min(this.width, this.height) / 2.0F;
            float dx = mouseX + 0.5F - (left + this.width / 2.0F);
            float dy = mouseY + 0.5F - (top + this.height / 2.0F);
            return dx * dx + dy * dy <= radius * radius;
        }
        return mouseX >= left && mouseX < left + this.width && mouseY >= top && mouseY < top + this.height;
    }

    private static int brighten(int argb) {
        int alpha = Math.min(255, ((argb >>> 24) & 0xFF) + 48);
        return (alpha << 24) | (argb & 0xFFFFFF);
    }
}
