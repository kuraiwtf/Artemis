package fr.natsu.artemis.module.stopwatch;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-driven stopwatches (count up) and timers (count down). Times are wall-clock milliseconds
 * accumulated across start/stop, so a stop freezes the shown value until the next start.
 */
public final class StopwatchState {

    public abstract static class Clock {

        public final String id;
        public final String name;
        public final boolean hideWhenStopped;
        public final String format;
        public final int rgb;
        /** Server HUD position, or {@code null} to auto-stack. */
        public final float[] position;

        long accumulated;
        long startedAt = -1L;

        Clock(String id, String name, boolean hideWhenStopped, String format, int rgb, float[] position) {
            this.id = id;
            this.name = name;
            this.hideWhenStopped = hideWhenStopped;
            this.format = format == null || format.isEmpty() ? "mm:ss" : format;
            this.rgb = rgb;
            this.position = position;
        }

        public synchronized boolean running() {
            return this.startedAt >= 0L;
        }

        synchronized long elapsed(long now) {
            return this.accumulated + (this.startedAt >= 0L ? now - this.startedAt : 0L);
        }

        synchronized void start(long now) {
            if (this.startedAt < 0L) {
                this.startedAt = now;
            }
        }

        synchronized void stop(long now) {
            if (this.startedAt >= 0L) {
                this.accumulated += now - this.startedAt;
                this.startedAt = -1L;
            }
        }

        synchronized void reset() {
            this.accumulated = 0L;
            this.startedAt = -1L;
        }

        /** Milliseconds to show. */
        public abstract long shown(long now);
    }

    public static final class Stopwatch extends Clock {

        final boolean resetOnStart;

        Stopwatch(String id, String name, boolean resetOnStart, boolean hideWhenStopped, String format, int rgb,
                  float[] position) {
            super(id, name, hideWhenStopped, format, rgb, position);
            this.resetOnStart = resetOnStart;
        }

        @Override
        public long shown(long now) {
            return elapsed(now);
        }
    }

    public static final class Timer extends Clock {

        final long duration;
        final boolean loop;
        final boolean inGameNotification;
        final String titleJson;

        Timer(String id, String name, long duration, boolean loop, boolean inGameNotification, boolean hideWhenStopped,
              String format, String titleJson, int rgb, float[] position) {
            super(id, name, hideWhenStopped, format, rgb, position);
            this.duration = duration;
            this.loop = loop;
            this.inGameNotification = inGameNotification;
            this.titleJson = titleJson;
        }

        @Override
        public long shown(long now) {
            return Math.max(0L, this.duration - elapsed(now));
        }

        /** True once, when a running timer reaches zero; then loops or stops at zero. */
        synchronized boolean pollFinished(long now) {
            if (this.startedAt < 0L || elapsed(now) < this.duration) {
                return false;
            }
            if (this.loop && this.duration > 0L) {
                long over = (elapsed(now) - this.duration) % this.duration;
                this.accumulated = 0L;
                this.startedAt = now - over;
            } else {
                this.accumulated = this.duration;
                this.startedAt = -1L;
            }
            return true;
        }
    }

    private static final Map<String, Stopwatch> STOPWATCHES = new ConcurrentHashMap<>();
    private static final Map<String, Timer> TIMERS = new ConcurrentHashMap<>();

    private StopwatchState() {
    }

    static void put(Stopwatch stopwatch) {
        STOPWATCHES.put(stopwatch.id, stopwatch);
    }

    static void put(Timer timer) {
        TIMERS.put(timer.id, timer);
    }

    static Map<String, Stopwatch> stopwatchMap() {
        return STOPWATCHES;
    }

    static Map<String, Timer> timerMap() {
        return TIMERS;
    }

    public static Collection<Stopwatch> stopwatches() {
        return STOPWATCHES.values();
    }

    public static Collection<Timer> timers() {
        return TIMERS.values();
    }

    public static boolean isEmpty() {
        return STOPWATCHES.isEmpty() && TIMERS.isEmpty();
    }

    static void resetAll() {
        STOPWATCHES.clear();
        TIMERS.clear();
    }

    /**
     * Formats {@code millis} with a small date-like pattern: {@code H}/{@code HH} hours, {@code m}/{@code mm}
     * minutes, {@code s}/{@code ss} seconds, {@code S..SSS} fractions; quoted text is literal. When the
     * pattern has no hour field, minutes are not wrapped at 60 (likewise seconds without minutes).
     */
    public static String format(String pattern, long millis) {
        boolean hasHours = pattern.indexOf('H') >= 0;
        boolean hasMinutes = pattern.indexOf('m') >= 0;
        long hours = millis / 3_600_000L;
        long minutes = hasHours ? (millis / 60_000L) % 60L : millis / 60_000L;
        long seconds = hasMinutes || hasHours ? (millis / 1000L) % 60L : millis / 1000L;
        long fraction = millis % 1000L;

        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < pattern.length()) {
            char c = pattern.charAt(i);
            if (c == '\'') {
                int end = pattern.indexOf('\'', i + 1);
                out.append(pattern, i + 1, end < 0 ? pattern.length() : end);
                i = end < 0 ? pattern.length() : end + 1;
                continue;
            }
            int run = 1;
            while (i + run < pattern.length() && pattern.charAt(i + run) == c) {
                run++;
            }
            switch (c) {
                case 'H':
                    out.append(pad(hours, run));
                    break;
                case 'm':
                    out.append(pad(minutes, run));
                    break;
                case 's':
                    out.append(pad(seconds, run));
                    break;
                case 'S':
                    String ms = pad(fraction, 3);
                    out.append(ms, 0, Math.min(run, 3));
                    break;
                default:
                    for (int k = 0; k < run; k++) {
                        out.append(c);
                    }
            }
            i += run;
        }
        return out.toString();
    }

    private static String pad(long value, int width) {
        String s = Long.toString(value);
        StringBuilder sb = new StringBuilder();
        for (int k = s.length(); k < width; k++) {
            sb.append('0');
        }
        return sb.append(s).toString();
    }
}
