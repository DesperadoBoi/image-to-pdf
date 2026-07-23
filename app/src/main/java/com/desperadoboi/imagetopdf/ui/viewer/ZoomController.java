package com.desperadoboi.imagetopdf.ui.viewer;

public final class ZoomController {
    public static final float MIN_ZOOM = 0.60f;
    public static final float NORMAL_ZOOM = 1f;
    public static final float MAX_ZOOM = 3f;

    enum ZoomMode {
        MANUAL,
        ZOOM_100
    }

    private ZoomController() {
    }

    public static float clampZoom(float zoom) {
        if (!Float.isFinite(zoom)) return NORMAL_ZOOM;
        return Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom));
    }

    static float clampZoom(float zoom, ZoomMode mode) {
        if (mode == ZoomMode.ZOOM_100) return NORMAL_ZOOM;
        return clampZoom(zoom);
    }

}
