package fr.natsu.artemis.module.stopwatch;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import com.google.protobuf.Any;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;

import fr.natsu.artemis.Artemis;
import fr.natsu.artemis.module.nametag.AdventureText;
import fr.natsu.artemis.module.nametag.AdventureText.Run;
import fr.natsu.artemis.module.notification.NotificationState;
import fr.natsu.artemis.module.title.TitleState;
import fr.natsu.artemis.net.ApolloNetwork;
import fr.natsu.artemis.net.ApolloProtos;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Stopwatch module ({@code lunarclient.apollo.stopwatch.v1}): server-controlled stopwatches and
 * timers on the HUD.
 *
 * <ul>
 *   <li>{@code AddStopwatchMessage}: id #1, name #2, reset_on_start #3, prevent_modification #4 (no
 *       client editing on Forge anyway), hide_when_stopped #5, display_format #6, text_color #7,
 *       hud_position #8</li>
 *   <li>{@code AddTimerMessage}: id #1, name #2, duration #3, loop #4, prevent_modification #5,
 *       in_game_notification #6, hide_when_stopped #7, display_format #8,
 *       title_text_adventure_json_lines #9, text_color #10, hud_position #11</li>
 *   <li>Remove/Start/Stop/Reset{Stopwatch,Timer}Message: id #1; Reset{Stopwatches,Timers}Message: empty</li>
 * </ul>
 *
 * <p>Also registered on the Forge event bus: the tick handler fires timer completions.</p>
 */
public final class StopwatchModule {

    private static final String PREFIX = "lunarclient.apollo.stopwatch.v1.";
    private static final int DEFAULT_RGB = 0xFFFFFF;
    private static final long NOTIFICATION_MILLIS = 5000L;

    public static void register(ApolloNetwork network) {
        network.register(PREFIX + "AddStopwatchMessage", StopwatchModule::onAddStopwatch);
        network.register(PREFIX + "RemoveStopwatchMessage",
            m -> withId(m, id -> StopwatchState.stopwatchMap().remove(id)));
        network.register(PREFIX + "StartStopwatchMessage", m -> withId(m, id -> {
            StopwatchState.Stopwatch stopwatch = StopwatchState.stopwatchMap().get(id);
            if (stopwatch != null) {
                if (stopwatch.resetOnStart) {
                    stopwatch.reset();
                }
                stopwatch.start(System.currentTimeMillis());
            }
        }));
        network.register(PREFIX + "StopStopwatchMessage", m -> withId(m, id -> {
            StopwatchState.Stopwatch stopwatch = StopwatchState.stopwatchMap().get(id);
            if (stopwatch != null) {
                stopwatch.stop(System.currentTimeMillis());
            }
        }));
        network.register(PREFIX + "ResetStopwatchMessage", m -> withId(m, id -> {
            StopwatchState.Stopwatch stopwatch = StopwatchState.stopwatchMap().get(id);
            if (stopwatch != null) {
                stopwatch.reset();
            }
        }));
        network.register(PREFIX + "ResetStopwatchesMessage", m -> StopwatchState.stopwatchMap().clear());

        network.register(PREFIX + "AddTimerMessage", StopwatchModule::onAddTimer);
        network.register(PREFIX + "RemoveTimerMessage",
            m -> withId(m, id -> StopwatchState.timerMap().remove(id)));
        network.register(PREFIX + "StartTimerMessage", m -> withId(m, id -> {
            StopwatchState.Timer timer = StopwatchState.timerMap().get(id);
            if (timer != null) {
                if (timer.shown(System.currentTimeMillis()) <= 0L) {
                    timer.reset();
                }
                timer.start(System.currentTimeMillis());
            }
        }));
        network.register(PREFIX + "StopTimerMessage", m -> withId(m, id -> {
            StopwatchState.Timer timer = StopwatchState.timerMap().get(id);
            if (timer != null) {
                timer.stop(System.currentTimeMillis());
            }
        }));
        network.register(PREFIX + "ResetTimerMessage", m -> withId(m, id -> {
            StopwatchState.Timer timer = StopwatchState.timerMap().get(id);
            if (timer != null) {
                timer.reset();
            }
        }));
        network.register(PREFIX + "ResetTimersMessage", m -> StopwatchState.timerMap().clear());

        network.onDisconnect(StopwatchState::resetAll);
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || StopwatchState.timers().isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (StopwatchState.Timer timer : StopwatchState.timers()) {
            if (timer.pollFinished(now)) {
                onTimerFinished(timer, now);
            }
        }
    }

    private static void onTimerFinished(StopwatchState.Timer timer, long now) {
        List<Run> titleRuns = timer.titleJson == null || timer.titleJson.isEmpty()
            ? Collections.<Run>emptyList() : AdventureText.parse(timer.titleJson);
        if (!titleRuns.isEmpty()) {
            TitleState.set(false, new TitleState.Title(titleRuns, 1.0F, 0.0F, 0.0F, 250L, 2000L, 500L, now));
        }
        if (timer.inGameNotification) {
            String name = timer.name == null || timer.name.isEmpty() ? timer.id : timer.name;
            NotificationState.add(new NotificationState.Notification(
                Collections.singletonList(Collections.singletonList(new Run(name, timer.rgb))),
                Collections.singletonList(Collections.singletonList(new Run("Timer finished", 0xAAAAAA))),
                null, null, now, NOTIFICATION_MILLIS));
        }
        Artemis.LOGGER.info("[Stopwatch] timer {} finished{}", timer.id, timer.loop ? " (looping)" : "");
    }

    private static void onAddStopwatch(Any message) throws Exception {
        CodedInputStream in = message.getValue().newCodedInput();
        String id = null;
        String name = "";
        boolean resetOnStart = false;
        boolean hideWhenStopped = false;
        String format = null;
        int rgb = DEFAULT_RGB;
        float[] position = null;
        int tag;
        while ((tag = in.readTag()) != 0) {
            switch (WireFormat.getTagFieldNumber(tag)) {
                case 1:
                    id = in.readString();
                    break;
                case 2:
                    name = in.readString();
                    break;
                case 3:
                    resetOnStart = in.readBool();
                    break;
                case 5:
                    hideWhenStopped = in.readBool();
                    break;
                case 6:
                    format = in.readString();
                    break;
                case 7:
                    rgb = ApolloProtos.parseColorArgb(in.readBytes()) & 0xFFFFFF;
                    break;
                case 8:
                    position = ApolloProtos.parseHudPosition(in.readBytes());
                    break;
                default:
                    in.skipField(tag);
            }
        }
        if (id == null) {
            return;
        }
        StopwatchState.put(new StopwatchState.Stopwatch(id, name, resetOnStart, hideWhenStopped, format, rgb,
            position));
        Artemis.LOGGER.info("[Stopwatch] added stopwatch {}", id);
    }

    private static void onAddTimer(Any message) throws Exception {
        CodedInputStream in = message.getValue().newCodedInput();
        String id = null;
        String name = "";
        long duration = 0L;
        boolean loop = false;
        boolean notification = false;
        boolean hideWhenStopped = false;
        String format = null;
        String titleJson = null;
        int rgb = DEFAULT_RGB;
        float[] position = null;
        int tag;
        while ((tag = in.readTag()) != 0) {
            switch (WireFormat.getTagFieldNumber(tag)) {
                case 1:
                    id = in.readString();
                    break;
                case 2:
                    name = in.readString();
                    break;
                case 3:
                    duration = ApolloProtos.parseDurationMillis(in.readBytes());
                    break;
                case 4:
                    loop = in.readBool();
                    break;
                case 6:
                    notification = in.readBool();
                    break;
                case 7:
                    hideWhenStopped = in.readBool();
                    break;
                case 8:
                    format = in.readString();
                    break;
                case 9:
                    titleJson = in.readString();
                    break;
                case 10:
                    rgb = ApolloProtos.parseColorArgb(in.readBytes()) & 0xFFFFFF;
                    break;
                case 11:
                    position = ApolloProtos.parseHudPosition(in.readBytes());
                    break;
                default:
                    in.skipField(tag);
            }
        }
        if (id == null) {
            return;
        }
        StopwatchState.put(new StopwatchState.Timer(id, name, duration, loop, notification, hideWhenStopped,
            format, titleJson, rgb, position));
        Artemis.LOGGER.info("[Stopwatch] added timer {} ({}ms)", id, duration);
    }

    private static void withId(Any message, Consumer<String> action) throws Exception {
        CodedInputStream in = message.getValue().newCodedInput();
        int tag;
        while ((tag = in.readTag()) != 0) {
            if (WireFormat.getTagFieldNumber(tag) == 1) {
                action.accept(in.readString());
            } else {
                in.skipField(tag);
            }
        }
    }
}
