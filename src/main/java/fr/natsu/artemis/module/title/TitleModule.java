package fr.natsu.artemis.module.title;

import java.util.ArrayList;
import java.util.List;

import com.google.protobuf.Any;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;

import fr.natsu.artemis.Artemis;
import fr.natsu.artemis.module.nametag.AdventureText;
import fr.natsu.artemis.module.nametag.NametagModule;
import fr.natsu.artemis.net.ApolloNetwork;
import fr.natsu.artemis.net.ApolloProtos;

/**
 * Title module ({@code lunarclient.apollo.title.v1}): titles and subtitles with their own scale, an
 * optional per-tick scale interpolation, and fade timings.
 *
 * <p>{@code DisplayTitleMessage}: {@code title_type} (#1, 1 = title, 2 = subtitle), {@code message}
 * (#2, legacy Component), {@code scale} (#3), {@code fade_in_time} (#4), {@code display_time} (#5),
 * {@code fade_out_time} (#6), {@code adventure_json_message} (#7), {@code interpolation_scale} (#8),
 * {@code interpolation_rate} (#9, scale change per 50 ms tick).</p>
 */
public final class TitleModule {

    private static final String DISPLAY = "lunarclient.apollo.title.v1.DisplayTitleMessage";
    private static final String RESET = "lunarclient.apollo.title.v1.ResetTitlesMessage";

    private static final int TYPE_SUBTITLE = 2;

    private TitleModule() {
    }

    public static void register(ApolloNetwork network) {
        network.register(DISPLAY, TitleModule::onDisplay);
        network.register(RESET, message -> TitleState.reset());
        network.onDisconnect(TitleState::reset);
    }

    private static void onDisplay(Any message) throws Exception {
        CodedInputStream in = message.getValue().newCodedInput();
        int type = 1;
        List<AdventureText.Run> legacy = new ArrayList<>();
        String json = null;
        float scale = 1.0F;
        long fadeIn = 0L;
        long display = 0L;
        long fadeOut = 0L;
        float interpolationScale = 0.0F;
        float interpolationRate = 0.0F;

        int tag;
        while ((tag = in.readTag()) != 0) {
            switch (WireFormat.getTagFieldNumber(tag)) {
                case 1:
                    type = in.readEnum();
                    break;
                case 2:
                    NametagModule.parseComponent(in.readBytes(), -1, "", legacy);
                    break;
                case 3:
                    scale = in.readFloat();
                    break;
                case 4:
                    fadeIn = ApolloProtos.parseDurationMillis(in.readBytes());
                    break;
                case 5:
                    display = ApolloProtos.parseDurationMillis(in.readBytes());
                    break;
                case 6:
                    fadeOut = ApolloProtos.parseDurationMillis(in.readBytes());
                    break;
                case 7:
                    json = in.readString();
                    break;
                case 8:
                    interpolationScale = in.readFloat();
                    break;
                case 9:
                    interpolationRate = in.readFloat();
                    break;
                default:
                    in.skipField(tag);
            }
        }

        List<AdventureText.Run> runs = json != null && !json.isEmpty() ? AdventureText.parse(json) : legacy;
        if (runs.isEmpty()) {
            return;
        }
        if (scale <= 0.0F) {
            scale = 1.0F;
        }
        TitleState.Title title = new TitleState.Title(runs, scale, interpolationScale, interpolationRate,
            fadeIn, display, fadeOut, System.currentTimeMillis());
        boolean subtitle = type == TYPE_SUBTITLE;
        TitleState.set(subtitle, title);
        Artemis.LOGGER.info("[Title] display {} scale={} ({}+{}+{}ms)",
            subtitle ? "subtitle" : "title", scale, fadeIn, display, fadeOut);
    }
}
