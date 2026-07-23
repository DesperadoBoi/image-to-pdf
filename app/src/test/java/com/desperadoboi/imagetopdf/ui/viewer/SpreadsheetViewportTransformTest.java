package com.desperadoboi.imagetopdf.ui.viewer;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class SpreadsheetViewportTransformTest {
    private static final float DELTA = 0.0001f;

    @Test
    public void panUsesSheetCoordinatesAndClampsBothAxes() {
        SpreadsheetViewportTransform transform = transform(200f, 100f, 1_000f, 500f);

        transform.panByScreen(150f, 80f);
        assertEquals(150f, transform.getOffsetX(), DELTA);
        assertEquals(80f, transform.getOffsetY(), DELTA);

        transform.panByScreen(10_000f, 10_000f);
        assertEquals(800f, transform.getOffsetX(), DELTA);
        assertEquals(400f, transform.getOffsetY(), DELTA);
    }

    @Test
    public void focalPointZoomKeepsSheetCoordinateUnderFingers() {
        SpreadsheetViewportTransform transform = transform(300f, 200f, 1_000f, 800f);
        transform.set(1f, 120f, 60f, ZoomController.ZoomMode.MANUAL);
        float beforeX = transform.getOffsetX() + 90f / transform.getScale();
        float beforeY = transform.getOffsetY() + 70f / transform.getScale();

        transform.zoomAround(2f, 90f, 70f, ZoomController.ZoomMode.MANUAL);

        assertEquals(beforeX, transform.getOffsetX() + 90f / transform.getScale(), DELTA);
        assertEquals(beforeY, transform.getOffsetY() + 70f / transform.getScale(), DELTA);
    }

    @Test
    public void manualAndOverviewZoomUseTheirOwnLimits() {
        SpreadsheetViewportTransform transform = transform(200f, 100f, 1_000f, 500f);

        transform.zoomAround(0.1f, 0f, 0f, ZoomController.ZoomMode.MANUAL);
        assertEquals(0.60f, transform.getScale(), DELTA);
        transform.zoomAround(9f, 0f, 0f, ZoomController.ZoomMode.MANUAL);
        assertEquals(3f, transform.getScale(), DELTA);
        transform.zoomAround(0.1f, 0f, 0f, ZoomController.ZoomMode.FIT_SHEET);
        assertEquals(0.25f, transform.getScale(), DELTA);
    }

    @Test
    public void fitModesAndOneHundredPercentAreDeterministic() {
        SpreadsheetViewportTransform transform = transform(360f, 240f, 720f, 960f);

        assertEquals(0.5f, transform.fitWidthScale(), DELTA);
        assertEquals(0.25f, transform.fitSheetScale(), DELTA);
        transform.set(2f, 0f, 0f, ZoomController.ZoomMode.ZOOM_100);
        assertEquals(1f, transform.getScale(), DELTA);
    }

    @Test
    public void rotationRestorationPreservesViewportCenter() {
        SpreadsheetViewportTransform transform = transform(300f, 200f, 1_000f, 800f);
        transform.set(1.5f, 240f, 180f, ZoomController.ZoomMode.MANUAL);
        float centerX = transform.getCenterSheetX();
        float centerY = transform.getCenterSheetY();

        transform.setBounds(500f, 300f, 1_000f, 800f);
        transform.restoreAroundCenter(
                1.5f,
                centerX,
                centerY,
                ZoomController.ZoomMode.MANUAL
        );

        assertEquals(centerX, transform.getCenterSheetX(), DELTA);
        assertEquals(centerY, transform.getCenterSheetY(), DELTA);
    }

    private SpreadsheetViewportTransform transform(
            float viewportWidth,
            float viewportHeight,
            float sheetWidth,
            float sheetHeight
    ) {
        SpreadsheetViewportTransform transform = new SpreadsheetViewportTransform();
        transform.setBounds(viewportWidth, viewportHeight, sheetWidth, sheetHeight);
        return transform;
    }
}
