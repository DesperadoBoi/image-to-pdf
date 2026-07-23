package com.desperadoboi.imagetopdf.ui.viewer;

final class SpreadsheetViewportState {
    private final float scale;
    private final float horizontalOffset;
    private final float verticalOffset;
    private final float centerContentX;
    private final float centerContentY;
    private final ZoomController.ZoomMode zoomMode;
    private final boolean hasViewportPosition;
    private final boolean applyDefaultZoomPolicy;

    private SpreadsheetViewportState(
            float scale,
            float horizontalOffset,
            float verticalOffset,
            float centerContentX,
            float centerContentY,
            ZoomController.ZoomMode zoomMode,
            boolean hasViewportPosition,
            boolean applyDefaultZoomPolicy
    ) {
        this.zoomMode = zoomMode;
        this.scale = ZoomController.clampZoom(scale, zoomMode);
        this.horizontalOffset = Math.max(0f, horizontalOffset);
        this.verticalOffset = Math.max(0f, verticalOffset);
        this.centerContentX = Math.max(0f, centerContentX);
        this.centerContentY = Math.max(0f, centerContentY);
        this.hasViewportPosition = hasViewportPosition;
        this.applyDefaultZoomPolicy = applyDefaultZoomPolicy;
    }

    static SpreadsheetViewportState initialNormal() {
        return new SpreadsheetViewportState(
                ZoomController.NORMAL_ZOOM,
                0f,
                0f,
                0f,
                0f,
                ZoomController.ZoomMode.ZOOM_100,
                false,
                false
        );
    }

    static SpreadsheetViewportState initialFitSheet() {
        return new SpreadsheetViewportState(
                ZoomController.NORMAL_ZOOM,
                0f,
                0f,
                0f,
                0f,
                ZoomController.ZoomMode.FIT_SHEET,
                false,
                true
        );
    }

    static SpreadsheetViewportState positioned(
            float scale,
            float horizontalOffset,
            float verticalOffset,
            float centerContentX,
            float centerContentY,
            ZoomController.ZoomMode zoomMode
    ) {
        return new SpreadsheetViewportState(
                scale,
                horizontalOffset,
                verticalOffset,
                centerContentX,
                centerContentY,
                zoomMode,
                true,
                false
        );
    }

    SpreadsheetViewportState withManualScale(float newScale) {
        return new SpreadsheetViewportState(
                newScale,
                horizontalOffset,
                verticalOffset,
                centerContentX,
                centerContentY,
                ZoomController.ZoomMode.MANUAL,
                hasViewportPosition,
                false
        );
    }

    float getScale() {
        return scale;
    }

    float getHorizontalOffset() {
        return horizontalOffset;
    }

    float getVerticalOffset() {
        return verticalOffset;
    }

    float getCenterContentX() {
        return centerContentX;
    }

    float getCenterContentY() {
        return centerContentY;
    }

    ZoomController.ZoomMode getZoomMode() {
        return zoomMode;
    }

    boolean hasViewportPosition() {
        return hasViewportPosition;
    }

    boolean shouldApplyDefaultZoomPolicy() {
        return applyDefaultZoomPolicy;
    }
}
