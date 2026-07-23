package com.desperadoboi.imagetopdf.ui.viewer;

public final class ZoomController {
    public static final float MIN_ZOOM = 0.60f;
    public static final float MIN_OVERVIEW_ZOOM = 0.25f;
    public static final float NORMAL_ZOOM = 1f;
    public static final float MAX_ZOOM = 3f;

    enum ZoomMode {
        MANUAL,
        ZOOM_100,
        FIT_WIDTH,
        FIT_SHEET
    }

    private ZoomController() {
    }

    public static float clampZoom(float zoom) {
        if (!Float.isFinite(zoom)) return NORMAL_ZOOM;
        return Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom));
    }

    static float clampZoom(float zoom, ZoomMode mode) {
        if (mode == ZoomMode.ZOOM_100) return NORMAL_ZOOM;
        if (isOverview(mode)) return clampOverviewZoom(zoom);
        return clampZoom(zoom);
    }

    static boolean isOverview(ZoomMode mode) {
        return mode == ZoomMode.FIT_WIDTH || mode == ZoomMode.FIT_SHEET;
    }

    static float clampOverviewZoom(float zoom) {
        if (!Float.isFinite(zoom)) return NORMAL_ZOOM;
        return Math.max(MIN_OVERVIEW_ZOOM, Math.min(MAX_ZOOM, zoom));
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
            return MIN_OVERVIEW_ZOOM;
        }
        float fitLimit = clampOverviewZoom(maximumFitScale);
        return Math.max(
                MIN_OVERVIEW_ZOOM,
                Math.min(fitLimit, clampOverviewZoom(availableContentWidth / sheetWidth))
        );
    }

    public static float calculateFitSheetScale(
            float availableContentWidth,
            float availableContentHeight,
            float sheetWidth,
            float sheetHeight
    ) {
        float widthScale = calculateFitScale(availableContentWidth, sheetWidth);
        float heightScale = calculateFitScale(availableContentHeight, sheetHeight);
        return clampOverviewZoom(Math.min(widthScale, heightScale));
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
