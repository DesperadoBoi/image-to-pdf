package com.desperadoboi.imagetopdf.ui.viewer;

public final class ZoomController {
    public static final float MIN_ZOOM = 0.25f;
    public static final float NORMAL_ZOOM = 1f;
    public static final float MAX_ZOOM = 3f;

    private ZoomController() {
    }

    public static float clampZoom(float zoom) {
        if (!Float.isFinite(zoom)) return NORMAL_ZOOM;
        return Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom));
    }

    public static float calculateFitScale(float availableContentWidth, float sheetWidth) {
        return calculateFitScaleWithMaximum(
                availableContentWidth,
                sheetWidth,
                NORMAL_ZOOM
        );
    }

    public static float calculateFitScale(
            float viewportWidth,
            float rowHeaderWidth,
            float sheetWidth
    ) {
        return calculateFitScale(
                Math.max(0f, viewportWidth - Math.max(0f, rowHeaderWidth)),
                sheetWidth
        );
    }

    public static float calculateFitScaleWithMaximum(
            float availableContentWidth,
            float sheetWidth,
            float maximumFitScale
    ) {
        if (!Float.isFinite(sheetWidth) || sheetWidth <= 0f) return NORMAL_ZOOM;
        if (!Float.isFinite(availableContentWidth) || availableContentWidth <= 0f) {
            return MIN_ZOOM;
        }
        float fitLimit = clampZoom(maximumFitScale);
        return Math.max(
                MIN_ZOOM,
                Math.min(fitLimit, clampZoom(availableContentWidth / sheetWidth))
        );
    }

    public static float preserveFocalPoint(
            float oldOffset,
            float oldScale,
            float newScale,
            float focalPosition
    ) {
        if (!Float.isFinite(oldOffset)
                || !Float.isFinite(oldScale)
                || oldScale <= 0f
                || !Float.isFinite(newScale)
                || newScale <= 0f
                || !Float.isFinite(focalPosition)) {
            return 0f;
        }
        float contentCoordinate = (oldOffset + focalPosition) / oldScale;
        return (contentCoordinate * newScale) - focalPosition;
    }
}
