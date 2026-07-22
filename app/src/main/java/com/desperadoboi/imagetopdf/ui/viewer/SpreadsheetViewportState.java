package com.desperadoboi.imagetopdf.ui.viewer;

final class SpreadsheetViewportState {
    private final float scale;
    private final float horizontalOffset;
    private final float verticalOffset;
    private final float centerContentX;
    private final float centerContentY;
    private final boolean fitWidth;
    private final boolean hasViewportPosition;

    private SpreadsheetViewportState(
            float scale,
            float horizontalOffset,
            float verticalOffset,
            float centerContentX,
            float centerContentY,
            boolean fitWidth,
            boolean hasViewportPosition
    ) {
        this.scale = ZoomController.clampZoom(scale);
        this.horizontalOffset = Math.max(0f, horizontalOffset);
        this.verticalOffset = Math.max(0f, verticalOffset);
        this.centerContentX = Math.max(0f, centerContentX);
        this.centerContentY = Math.max(0f, centerContentY);
        this.fitWidth = fitWidth;
        this.hasViewportPosition = hasViewportPosition;
    }

    static SpreadsheetViewportState initialFitWidth() {
        return new SpreadsheetViewportState(1f, 0f, 0f, 0f, 0f, true, false);
    }

    static SpreadsheetViewportState positioned(
            float scale,
            float horizontalOffset,
            float verticalOffset,
            float centerContentX,
            float centerContentY,
            boolean fitWidth
    ) {
        return new SpreadsheetViewportState(
                scale,
                horizontalOffset,
                verticalOffset,
                centerContentX,
                centerContentY,
                fitWidth,
                true
        );
    }

    SpreadsheetViewportState withManualScale(float newScale) {
        return new SpreadsheetViewportState(
                newScale,
                horizontalOffset,
                verticalOffset,
                centerContentX,
                centerContentY,
                false,
                hasViewportPosition
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

    boolean isFitWidth() {
        return fitWidth;
    }

    boolean hasViewportPosition() {
        return hasViewportPosition;
    }
}
