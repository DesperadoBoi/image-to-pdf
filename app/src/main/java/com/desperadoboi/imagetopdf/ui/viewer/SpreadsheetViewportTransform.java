package com.desperadoboi.imagetopdf.ui.viewer;

final class SpreadsheetViewportTransform {
    private float offsetX;
    private float offsetY;
    private float scale = ZoomController.NORMAL_ZOOM;
    private float viewportWidth;
    private float viewportHeight;
    private float sheetWidth;
    private float sheetHeight;

    void setBounds(
            float viewportWidth,
            float viewportHeight,
            float sheetWidth,
            float sheetHeight
    ) {
        this.viewportWidth = finiteNonNegative(viewportWidth);
        this.viewportHeight = finiteNonNegative(viewportHeight);
        this.sheetWidth = finiteNonNegative(sheetWidth);
        this.sheetHeight = finiteNonNegative(sheetHeight);
        clampOffsets();
    }

    void set(float scale, float offsetX, float offsetY, ZoomController.ZoomMode mode) {
        this.scale = ZoomController.clampZoom(scale, mode);
        this.offsetX = finiteNonNegative(offsetX);
        this.offsetY = finiteNonNegative(offsetY);
        clampOffsets();
    }

    void restoreAroundCenter(
            float scale,
            float centerSheetX,
            float centerSheetY,
            ZoomController.ZoomMode mode
    ) {
        this.scale = ZoomController.clampZoom(scale, mode);
        offsetX = finiteNonNegative(centerSheetX) - viewportWidth / (2f * this.scale);
        offsetY = finiteNonNegative(centerSheetY) - viewportHeight / (2f * this.scale);
        clampOffsets();
    }

    void panByScreen(float distanceX, float distanceY) {
        if (!Float.isFinite(distanceX) || !Float.isFinite(distanceY)) return;
        offsetX += distanceX / scale;
        offsetY += distanceY / scale;
        clampOffsets();
    }

    void zoomAround(
            float requestedScale,
            float focalScreenX,
            float focalScreenY,
            ZoomController.ZoomMode mode
    ) {
        float nextScale = ZoomController.clampZoom(requestedScale, mode);
        float safeFocalX = clamp(focalScreenX, 0f, viewportWidth);
        float safeFocalY = clamp(focalScreenY, 0f, viewportHeight);
        float focalSheetX = offsetX + safeFocalX / scale;
        float focalSheetY = offsetY + safeFocalY / scale;
        scale = nextScale;
        offsetX = focalSheetX - safeFocalX / scale;
        offsetY = focalSheetY - safeFocalY / scale;
        clampOffsets();
    }

    float fitWidthScale() {
        return ZoomController.calculateFitScale(viewportWidth, sheetWidth);
    }

    float fitSheetScale() {
        return ZoomController.calculateFitSheetScale(
                viewportWidth,
                viewportHeight,
                sheetWidth,
                sheetHeight
        );
    }

    void moveToOrigin() {
        offsetX = 0f;
        offsetY = 0f;
    }

    float getOffsetX() {
        return offsetX;
    }

    float getOffsetY() {
        return offsetY;
    }

    float getScale() {
        return scale;
    }

    float getVisibleRight() {
        return offsetX + viewportWidth / scale;
    }

    float getVisibleBottom() {
        return offsetY + viewportHeight / scale;
    }

    float getCenterSheetX() {
        return offsetX + viewportWidth / (2f * scale);
    }

    float getCenterSheetY() {
        return offsetY + viewportHeight / (2f * scale);
    }

    float getMaximumOffsetX() {
        return Math.max(0f, sheetWidth - viewportWidth / scale);
    }

    float getMaximumOffsetY() {
        return Math.max(0f, sheetHeight - viewportHeight / scale);
    }

    private void clampOffsets() {
        offsetX = clamp(offsetX, 0f, getMaximumOffsetX());
        offsetY = clamp(offsetY, 0f, getMaximumOffsetY());
    }

    private static float finiteNonNegative(float value) {
        return Float.isFinite(value) ? Math.max(0f, value) : 0f;
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
