package com.desperadoboi.imagetopdf.ui.viewer;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class ZoomControllerTest {
    private static final float DELTA = 0.0001f;

    @Test
    public void manualZoomIsClampedToReadableRange() {
        assertEquals(0.60f, ZoomController.clampZoom(0.25f), DELTA);
        assertEquals(0.60f, ZoomController.clampZoom(0.60f), DELTA);
        assertEquals(1f, ZoomController.clampZoom(1f), DELTA);
        assertEquals(3f, ZoomController.clampZoom(4f), DELTA);
    }

    @Test
    public void fitWidthUsesAvailableContentWidthAndDoesNotUpscaleByDefault() {
        assertEquals(0.5f, ZoomController.calculateFitScale(360f, 720f), DELTA);
        assertEquals(1f, ZoomController.calculateFitScale(360f, 300f), DELTA);
    }

    @Test
    public void fitWidthAccountsForRowHeaderAndZoomBounds() {
        assertEquals(0.5f, ZoomController.calculateFitScale(400f, 40f, 720f), DELTA);
        assertEquals(ZoomController.MIN_OVERVIEW_ZOOM,
                ZoomController.calculateFitScale(100f, 1_000f), DELTA);
        assertEquals(ZoomController.MAX_ZOOM,
                ZoomController.calculateFitScaleWithMaximum(1_200f, 100f, 10f), DELTA);
    }

    @Test
    public void fitWidthHandlesZeroDimensionsSafely() {
        assertEquals(ZoomController.NORMAL_ZOOM,
                ZoomController.calculateFitScale(360f, 0f), DELTA);
        assertEquals(ZoomController.MIN_OVERVIEW_ZOOM,
                ZoomController.calculateFitScale(0f, 720f), DELTA);
    }

    @Test
    public void fitSheetUsesTheMoreRestrictiveAxis() {
        assertEquals(0.5f, ZoomController.calculateFitSheetScale(
                400f,
                300f,
                800f,
                400f
        ), DELTA);
        assertEquals(0.25f, ZoomController.calculateFitSheetScale(
                400f,
                200f,
                800f,
                2_000f
        ), DELTA);
    }

    @Test
    public void defaultXlsxModeUsesFitSheetOnlyAtReadableThreshold() {
        assertEquals(ZoomController.ZoomMode.FIT_SHEET, ZoomController.defaultXlsxMode(0.75f));
        assertEquals(ZoomController.ZoomMode.FIT_SHEET, ZoomController.defaultXlsxMode(0.60f));
        assertEquals(ZoomController.ZoomMode.ZOOM_100, ZoomController.defaultXlsxMode(0.59f));
    }

    @Test
    public void zoomModesKeepOverviewSeparateFromManualAndNormal() {
        assertEquals(0.60f, ZoomController.clampZoom(
                0.25f,
                ZoomController.ZoomMode.MANUAL
        ), DELTA);
        assertEquals(1f, ZoomController.clampZoom(
                2f,
                ZoomController.ZoomMode.ZOOM_100
        ), DELTA);
        assertEquals(0.25f, ZoomController.clampZoom(
                0.25f,
                ZoomController.ZoomMode.FIT_WIDTH
        ), DELTA);
        assertEquals(0.25f, ZoomController.clampZoom(
                0.25f,
                ZoomController.ZoomMode.FIT_SHEET
        ), DELTA);
    }

    @Test
    public void zoomPreservesContentCoordinateAtFocalPoint() {
        float oldOffset = 120f;
        float oldScale = 1f;
        float newScale = 2.25f;
        float focalPoint = 180f;

        float newOffset = ZoomController.preserveFocalPoint(
                oldOffset,
                oldScale,
                newScale,
                focalPoint
        );

        float before = (oldOffset + focalPoint) / oldScale;
        float after = (newOffset + focalPoint) / newScale;
        assertEquals(before, after, DELTA);
    }
}
