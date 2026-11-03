package fr.natsu.artemis.module.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import fr.natsu.artemis.Artemis;

public final class ModelAnimator {

    private static final double SIGN_X = -1.0D;
    private static final double SIGN_Y = -1.0D;
    private static final double SIGN_Z = 1.0D;

    private static final Set<String> REPORTED = new HashSet<>();

    private ModelAnimator() {
    }

    public static void apply(JemModel model, List<ModelRegistry.AnimationLayer> layers, double seconds,
                             double limbSwing, double limbSpeed) {
        for (ModelRegistry.AnimationLayer layer : layers) {
            JemAnimation animation = ModelRegistry.getAnimation(layer);
            if (animation == null) {
                continue;
            }
            double weight = layer.limbSpeedWeighted ? Math.max(0.0D, Math.min(1.0D, limbSpeed)) : 1.0D;
            if (weight <= 0.0D) {
                continue;
            }
            double driver = layer.limbSwingDriven ? limbSwing : seconds;
            double cycle = layer.cycle > 0.0D ? layer.cycle : animation.length();
            double scaled = cycle > 0.0D ? driver / cycle * animation.length() : driver;
            apply(model, animation, animation.time(scaled), weight);
        }
    }

    private static void apply(JemModel model, JemAnimation animation, double time, double weight) {
        int matched = 0;
        List<String> missing = new ArrayList<>();

        for (String bone : animation.boneNames()) {
            JemPart part = model.resolve(bone);
            if (part == null) {
                missing.add(bone);
                continue;
            }
            matched++;

            double[] rotation = animation.sampleRotation(bone, time);
            if (rotation != null) {
                part.animRotateX += (float) (Math.toRadians(SIGN_X * rotation[0]) * weight);
                part.animRotateY += (float) (Math.toRadians(SIGN_Y * rotation[1]) * weight);
                part.animRotateZ += (float) (Math.toRadians(SIGN_Z * rotation[2]) * weight);
            }

            double[] position = animation.samplePosition(bone, time);
            if (position != null) {
                part.animTranslateX += (float) (SIGN_X * position[0] * weight);
                part.animTranslateY += (float) (SIGN_Y * position[1] * weight);
                part.animTranslateZ += (float) (SIGN_Z * position[2] * weight);
            }

            double[] scale = animation.sampleScale(bone, time);
            if (scale != null) {
                part.animScaleX *= (float) (1.0D + (scale[0] - 1.0D) * weight);
                part.animScaleY *= (float) (1.0D + (scale[1] - 1.0D) * weight);
                part.animScaleZ *= (float) (1.0D + (scale[2] - 1.0D) * weight);
            }

            Boolean visible = animation.sampleVisible(bone, time);
            if (visible != null) {
                part.hidden = !visible.booleanValue();
            }
        }

        report(model, animation, matched, missing);
    }

    private static void report(JemModel model, JemAnimation animation, int matched, List<String> missing) {
        String key = System.identityHashCode(model) + "/" + animation.name();
        if (!REPORTED.add(key)) {
            return;
        }
        int total = matched + missing.size();
        if (matched == 0) {
            Artemis.LOGGER.warn("[Model] animation '{}' matched 0 of {} bone(s): no part id matches",
                animation.name(), total);
        } else {
            Artemis.LOGGER.info("[Model] animation '{}' bound to {}/{} bone(s)",
                animation.name(), matched, total);
        }
        if (!missing.isEmpty()) {
            Artemis.LOGGER.warn("[Model] unmatched animation bone(s): {}",
                missing.size() > 8 ? missing.subList(0, 8) + " ..." : missing);
        }
    }
}
