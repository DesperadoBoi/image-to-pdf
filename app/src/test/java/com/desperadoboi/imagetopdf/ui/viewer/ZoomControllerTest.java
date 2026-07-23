package com.desperadoboi.imagetopdf.ui.viewer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
    public void zoomModesOnlyContainNormalAndManual() {
        assertEquals(2, ZoomController.ZoomMode.values().length);
        assertEquals(ZoomController.ZoomMode.MANUAL, ZoomController.ZoomMode.values()[0]);
        assertEquals(ZoomController.ZoomMode.ZOOM_100, ZoomController.ZoomMode.values()[1]);
    }

    @Test
    public void normalModeAlwaysUsesOneHundredPercent() {
        assertEquals(1f, ZoomController.clampZoom(
                0.25f,
                ZoomController.ZoomMode.ZOOM_100
        ), DELTA);
        assertEquals(1f, ZoomController.clampZoom(
                3f,
                ZoomController.ZoomMode.ZOOM_100
        ), DELTA);
    }

    @Test
    public void resetMenuVisibilityUsesOneHundredPercentEpsilon() {
        assertFalse(ZoomController.shouldShowResetAction(1f));
        assertTrue(ZoomController.shouldShowResetAction(0.60f));
        assertFalse(ZoomController.shouldShowResetAction(0.9995f));
        assertTrue(ZoomController.shouldShowResetAction(1.50f));
    }

    @Test
    public void oneHundredPercentHelperNormalizesModeScaleAndIndicator() {
        assertTrue(ZoomController.isAtOneHundredPercent(1f));
        assertTrue(ZoomController.isAtOneHundredPercent(0.9995f));
        assertFalse(ZoomController.isAtOneHundredPercent(0.998f));
        assertFalse(ZoomController.isAtOneHundredPercent(Float.NaN));
        assertEquals(1f, ZoomController.clampZoom(0.9995f), DELTA);
        assertEquals(
                ZoomController.ZoomMode.ZOOM_100,
                ZoomController.zoomModeForScale(0.9995f)
        );
        assertEquals(100, ZoomController.percentageForIndicator(0.9995f));
        assertEquals(60, ZoomController.percentageForIndicator(0.60f));
        assertEquals(150, ZoomController.percentageForIndicator(1.50f));
    }

}
