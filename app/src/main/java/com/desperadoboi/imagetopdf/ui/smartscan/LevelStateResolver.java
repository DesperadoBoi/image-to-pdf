package com.desperadoboi.imagetopdf.ui.smartscan;

public final class LevelStateResolver {
    public static final float LEVEL_ENTER_DEGREES = 3f;
    public static final float LEVEL_EXIT_DEGREES = 5f;
    public static final float ALMOST_LEVEL_DEGREES = 9f;

    private static final float MINIMUM_GRAVITY = 1f;

    private LevelStateResolver() {
    }

    public static LevelState resolve(
            float x,
            float y,
            float z,
            LevelState previousState
    ) {
        float magnitude = (float) Math.sqrt((x * x) + (y * y) + (z * z));
        if (!Float.isFinite(magnitude) || magnitude < MINIMUM_GRAVITY) {
            return LevelState.UNAVAILABLE;
        }
        float horizontal = (float) Math.sqrt((x * x) + (y * y));
        float tiltDegrees = (float) Math.toDegrees(Math.atan2(horizontal, Math.abs(z)));
        float levelThreshold = previousState == LevelState.LEVEL
                ? LEVEL_EXIT_DEGREES
                : LEVEL_ENTER_DEGREES;
        if (tiltDegrees <= levelThreshold) {
            return LevelState.LEVEL;
        }
        if (tiltDegrees <= ALMOST_LEVEL_DEGREES) {
            return LevelState.ALMOST_LEVEL;
        }
        return LevelState.TILTED;
    }

    public static boolean shouldTriggerHaptic(LevelState previous, LevelState current) {
        return previous != LevelState.LEVEL && current == LevelState.LEVEL;
    }

    public static float horizontalOffset(float x, float y, float z) {
        float magnitude = (float) Math.sqrt((x * x) + (y * y) + (z * z));
        if (!Float.isFinite(magnitude) || magnitude < MINIMUM_GRAVITY) {
            return 0f;
        }
        return Math.max(-1f, Math.min(1f, x / magnitude * 4f));
    }
}
