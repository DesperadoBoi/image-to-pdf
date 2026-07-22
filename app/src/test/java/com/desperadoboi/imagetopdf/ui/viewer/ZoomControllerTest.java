package com.desperadoboi.imagetopdf.ui.viewer;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class ZoomControllerTest {
    private static final float DELTA = 0.0001f;

    @Test
    public void zoomIsClampedToSupportedRange() {
        assertEquals(ZoomController.MIN_ZOOM, ZoomController.clampZoom(0.1f), DELTA);
        assertEquals(0.5f, ZoomController.clampZoom(0.5f), DELTA);
        assertEquals(1f, ZoomController.clampZoom(1f), DELTA);
        assertEquals(ZoomController.MAX_ZOOM, ZoomController.clampZoom(4f), DELTA);
    }

    @Test
    public void fitWidthUsesAvailableContentWidthAndDoesNotUpscaleByDefault() {
        assertEquals(0.5f, ZoomController.calculateFitScale(360f, 720f), DELTA);
        assertEquals(1f, ZoomController.calculateFitScale(360f, 300f), DELTA);
    }

    @Test
    public void fitWidthAccountsForRowHeaderAndZoomBounds() {
        assertEquals(0.5f, ZoomController.calculateFitScale(400f, 40f, 720f), DELTA);
        assertEquals(ZoomController.MIN_ZOOM,
                ZoomController.calculateFitScale(100f, 1_000f), DELTA);
        assertEquals(ZoomController.MAX_ZOOM,
                ZoomController.calculateFitScaleWithMaximum(1_200f, 100f, 10f), DELTA);
    }

    @Test
    public void fitWidthHandlesZeroDimensionsSafely() {
        assertEquals(ZoomController.NORMAL_ZOOM,
                ZoomController.calculateFitScale(360f, 0f), DELTA);
        assertEquals(ZoomController.MIN_ZOOM,
                ZoomController.calculateFitScale(0f, 720f), DELTA);
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
