package com.desperadoboi.imagetopdf.ui.viewer;

public final class ZoomController {
    public static final float MIN_ZOOM = 0.60f;
    public static final float NORMAL_ZOOM = 1f;
    public static final float MAX_ZOOM = 3f;
    static final float ONE_HUNDRED_PERCENT_EPSILON = 0.001f;

    enum ZoomMode {
        MANUAL,
        ZOOM_100
    }

    private ZoomController() {
    }

    public static float clampZoom(float zoom) {
        if (!Float.isFinite(zoom)) return NORMAL_ZOOM;
        float clampedZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom));
        return isAtOneHundredPercent(clampedZoom) ? NORMAL_ZOOM : clampedZoom;
    }

    static float clampZoom(float zoom, ZoomMode mode) {
        if (mode == ZoomMode.ZOOM_100) return NORMAL_ZOOM;
        return clampZoom(zoom);
    }

    static boolean isAtOneHundredPercent(float scale) {
        return Float.isFinite(scale)
                && Math.abs(scale - NORMAL_ZOOM) < ONE_HUNDRED_PERCENT_EPSILON;
    }

    static ZoomMode zoomModeForScale(float scale) {
        return isAtOneHundredPercent(scale) ? ZoomMode.ZOOM_100 : ZoomMode.MANUAL;
    }

    static boolean shouldShowResetAction(float scale) {
        return !isAtOneHundredPercent(scale);
    }

    static int percentageForIndicator(float scale) {
        return isAtOneHundredPercent(scale)
                ? 100
                : Math.round(clampZoom(scale) * 100f);
    }

}
