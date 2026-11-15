package fr.natsu.artemis.render;

import net.minecraft.client.Minecraft;

public final class TeamColors {

    private TeamColors() {
    }

    /** RGB of a team format string: hex-tolerant ({@code §x§r§r§g§g§b§b}) or a vanilla colour code. */
    public static int rgbFromFormat(String format) {
        if (format.length() >= 14 && Character.toLowerCase(format.charAt(1)) == 'x') {
            int rgb = 0;
            for (int i = 0; i < 6; i++) {
                int digit = Character.digit(format.charAt(3 + i * 2), 16);
                if (digit < 0) {
                    return 0xFFFFFF;
                }
                rgb = rgb << 4 | digit;
            }
            return rgb;
        }
        if (format.length() >= 2) {
            int index = "0123456789abcdef".indexOf(Character.toLowerCase(format.charAt(1)));
            if (index >= 0) {
                return Minecraft.getMinecraft().fontRendererObj.getColorCode(format.charAt(1)) & 0xFFFFFF;
            }
        }
        return 0xFFFFFF;
    }
}
