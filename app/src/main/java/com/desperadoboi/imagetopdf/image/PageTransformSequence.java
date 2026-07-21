package com.desperadoboi.imagetopdf.image;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class PageTransformSequence {
    private PageTransformSequence() {
    }

    public static List<Step> forMode(PageProcessingMode mode) {
        if (mode == null) {
            throw new NullPointerException("mode is required");
        }
        switch (mode) {
            case ORIENTED_ONLY:
                return immutable(Step.EXIF, Step.MANUAL_ROTATION, Step.SCALE);
            case BEFORE_CROP:
                return immutable(
                        Step.EXIF,
                        Step.MANUAL_ROTATION,
                        Step.PERSPECTIVE,
                        Step.SCALE
                );
            case FINAL:
            default:
                return immutable(
                        Step.EXIF,
                        Step.MANUAL_ROTATION,
                        Step.PERSPECTIVE,
                        Step.CROP,
                        Step.SCALE
                );
        }
    }

    private static List<Step> immutable(Step... steps) {
        return Collections.unmodifiableList(Arrays.asList(steps));
    }

    public enum Step {
        EXIF,
        MANUAL_ROTATION,
        PERSPECTIVE,
        CROP,
        SCALE
    }
}
