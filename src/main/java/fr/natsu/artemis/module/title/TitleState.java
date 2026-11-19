package fr.natsu.artemis.module.title;

import java.util.List;

import fr.natsu.artemis.module.nametag.AdventureText.Run;

public final class TitleState {

    private static final long TICK_MILLIS = 50L;

    public static final class Title {

        public final List<Run> runs;
        public final float scale;
        public final float interpolationScale;
        public final float interpolationRate;
        public final long fadeIn;
        public final long display;
        public final long fadeOut;
        public final long createdAt;

        public Title(List<Run> runs, float scale, float interpolationScale, float interpolationRate,
              long fadeIn, long display, long fadeOut, long createdAt) {
            this.runs = runs;
            this.scale = scale;
            this.interpolationScale = interpolationScale;
            this.interpolationRate = interpolationRate;
            this.fadeIn = Math.max(0L, fadeIn);
            this.display = Math.max(0L, display);
            this.fadeOut = Math.max(0L, fadeOut);
            this.createdAt = createdAt;
        }

        public boolean expired(long now) {
            return now - this.createdAt >= this.fadeIn + this.display + this.fadeOut;
        }

        /** 0..1 opacity from the fade-in / fade-out windows. */
        public float alpha(long now) {
            long elapsed = now - this.createdAt;
            if (this.fadeIn > 0L && elapsed < this.fadeIn) {
                return elapsed / (float) this.fadeIn;
            }
            long fadeOutStart = this.fadeIn + this.display;
            if (this.fadeOut > 0L && elapsed > fadeOutStart) {
                return Math.max(0.0F, 1.0F - (elapsed - fadeOutStart) / (float) this.fadeOut);
            }
            return 1.0F;
        }

        /** Current scale: moves from {@code scale} toward {@code interpolationScale} by {@code rate} per tick. */
        public float currentScale(long now) {
            if (this.interpolationRate <= 0.0F || this.interpolationScale <= 0.0F
                    || this.interpolationScale == this.scale) {
                return this.scale;
            }
            float ticks = (now - this.createdAt) / (float) TICK_MILLIS;
            float delta = this.interpolationRate * ticks;
            if (this.interpolationScale > this.scale) {
                return Math.min(this.interpolationScale, this.scale + delta);
            }
            return Math.max(this.interpolationScale, this.scale - delta);
        }
    }

    private static volatile Title title;
    private static volatile Title subtitle;

    private TitleState() {
    }

    public static void set(boolean isSubtitle, Title value) {
        if (isSubtitle) {
            subtitle = value;
        } else {
            title = value;
        }
    }

    public static void reset() {
        title = null;
        subtitle = null;
    }

    public static Title title(long now) {
        Title current = title;
        if (current != null && current.expired(now)) {
            title = null;
            return null;
        }
        return current;
    }

    public static Title subtitle(long now) {
        Title current = subtitle;
        if (current != null && current.expired(now)) {
            subtitle = null;
            return null;
        }
        return current;
    }
}
