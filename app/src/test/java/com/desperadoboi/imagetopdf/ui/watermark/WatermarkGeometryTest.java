package com.desperadoboi.imagetopdf.ui.watermark;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class WatermarkGeometryTest {
    private static final float PAGE_W = 612f;
    private static final float PAGE_H = 792f;
    private static final float TEXT_W = 180f;
    private static final float TEXT_H = 52f;

    @Test public void centerIsAtPageCenter() {
        assertSingleCenter(WatermarkPosition.CENTER, PAGE_W / 2f, PAGE_H / 2f);
    }

    @Test public void allEightEdgePositionsRespectTheirQuadrants() {
        assertQuadrant(WatermarkPosition.TOP_LEFT, -1, -1);
        assertQuadrant(WatermarkPosition.TOP_CENTER, 0, -1);
        assertQuadrant(WatermarkPosition.TOP_RIGHT, 1, -1);
        assertQuadrant(WatermarkPosition.CENTER_LEFT, -1, 0);
        assertQuadrant(WatermarkPosition.CENTER_RIGHT, 1, 0);
        assertQuadrant(WatermarkPosition.BOTTOM_LEFT, -1, 1);
        assertQuadrant(WatermarkPosition.BOTTOM_CENTER, 0, 1);
        assertQuadrant(WatermarkPosition.BOTTOM_RIGHT, 1, 1);
    }

    @Test public void rotatedBoundsUseAxisAlignedEnvelope() {
        WatermarkGeometry.Bounds bounds = WatermarkGeometry.rotatedBounds(100f, 20f, 45f);
        float expected = (float) (120f / Math.sqrt(2d));
        assertEquals(expected, bounds.getWidth(), 0.001f);
        assertEquals(expected, bounds.getHeight(), 0.001f);
    }

    @Test public void edgePaddingKeepsRotatedBoundsInsidePortraitPage() {
        WatermarkGeometry.Layout layout = layout(
                PAGE_W, PAGE_H, 260f, 64f, -45f,
                WatermarkStyle.SINGLE, WatermarkPosition.BOTTOM_RIGHT
        );
        assertInside(layout, PAGE_W, PAGE_H);
    }

    @Test public void landscapePageUsesSameNormalizedRules() {
        WatermarkGeometry.Layout layout = layout(
                PAGE_H, PAGE_W, TEXT_W, TEXT_H, -45f,
                WatermarkStyle.SINGLE, WatermarkPosition.TOP_LEFT
        );
        assertInside(layout, PAGE_H, PAGE_W);
    }

    @Test public void tiledGridHasMultipleSpacedPlacements() {
        WatermarkGeometry.Layout layout = layout(
                PAGE_W, PAGE_H, TEXT_W, TEXT_H, -45f,
                WatermarkStyle.TILED, WatermarkPosition.CENTER
        );
        assertTrue(layout.getPlacements().size() >= 4);
        assertInside(layout, PAGE_W, PAGE_H);
        List<WatermarkGeometry.Placement> placements = layout.getPlacements();
        assertTrue(placements.get(1).getCenterX() > placements.get(0).getCenterX()
                || placements.get(1).getCenterY() > placements.get(0).getCenterY());
    }

    @Test public void longTiledTextStillRepeatsOnPortraitPage() {
        WatermarkGeometry.Layout layout = layout(
                PAGE_W, PAGE_H, 350f, 52f, -45f,
                WatermarkStyle.TILED, WatermarkPosition.CENTER
        );
        assertTrue(layout.getPlacements().size() >= 2);
        assertInside(layout, PAGE_W, PAGE_H);
    }

    @Test public void tinyTextDoesNotCreateUnboundedGrid() {
        WatermarkGeometry.Layout layout = layout(
                PAGE_W, PAGE_H, 2f, 2f, 0f,
                WatermarkStyle.TILED, WatermarkPosition.CENTER
        );
        assertTrue(layout.getPlacements().size() > 1);
        assertTrue(layout.getPlacements().size() <= 256);
    }

    @Test public void hugeTextScalesDownInsidePage() {
        WatermarkGeometry.Layout layout = layout(
                PAGE_W, PAGE_H, 2000f, 300f, -60f,
                WatermarkStyle.SINGLE, WatermarkPosition.CENTER
        );
        assertTrue(layout.getTextScale() < 1f);
        assertInside(layout, PAGE_W, PAGE_H);
    }

    @Test public void longTextHasIdenticalPreviewAndExportNormalizedGeometry() {
        WatermarkGeometry.Layout preview = layout(
                PAGE_W, PAGE_H, 760f, 52f, -45f,
                WatermarkStyle.SINGLE, WatermarkPosition.CENTER_RIGHT
        );
        WatermarkGeometry.Layout export = layout(
                PAGE_W, PAGE_H, 760f, 52f, -45f,
                WatermarkStyle.SINGLE, WatermarkPosition.CENTER_RIGHT
        );
        assertEquals(preview.getTextScale(), export.getTextScale(), 0f);
        assertEquals(preview.getPlacements().get(0).getCenterX(),
                export.getPlacements().get(0).getCenterX(), 0f);
        assertEquals(preview.getPlacements().get(0).getCenterY(),
                export.getPlacements().get(0).getCenterY(), 0f);
    }

    private void assertSingleCenter(WatermarkPosition position, float x, float y) {
        WatermarkGeometry.Placement placement = layout(
                PAGE_W, PAGE_H, TEXT_W, TEXT_H, 0f,
                WatermarkStyle.SINGLE, position
        ).getPlacements().get(0);
        assertEquals(x, placement.getCenterX(), 0.001f);
        assertEquals(y, placement.getCenterY(), 0.001f);
    }

    private void assertQuadrant(WatermarkPosition position, int horizontal, int vertical) {
        WatermarkGeometry.Placement placement = layout(
                PAGE_W, PAGE_H, TEXT_W, TEXT_H, -45f,
                WatermarkStyle.SINGLE, position
        ).getPlacements().get(0);
        if (horizontal < 0) assertTrue(placement.getCenterX() < PAGE_W / 2f);
        if (horizontal == 0) assertEquals(PAGE_W / 2f, placement.getCenterX(), 0.001f);
        if (horizontal > 0) assertTrue(placement.getCenterX() > PAGE_W / 2f);
        if (vertical < 0) assertTrue(placement.getCenterY() < PAGE_H / 2f);
        if (vertical == 0) assertEquals(PAGE_H / 2f, placement.getCenterY(), 0.001f);
        if (vertical > 0) assertTrue(placement.getCenterY() > PAGE_H / 2f);
    }

    private void assertInside(WatermarkGeometry.Layout layout, float width, float height) {
        float halfW = layout.getRotatedBounds().getWidth() / 2f;
        float halfH = layout.getRotatedBounds().getHeight() / 2f;
        for (WatermarkGeometry.Placement placement : layout.getPlacements()) {
            assertTrue(placement.getCenterX() - halfW >= layout.getEdgePadding() - 0.01f);
            assertTrue(placement.getCenterY() - halfH >= layout.getEdgePadding() - 0.01f);
            assertTrue(placement.getCenterX() + halfW <= width - layout.getEdgePadding() + 0.01f);
            assertTrue(placement.getCenterY() + halfH <= height - layout.getEdgePadding() + 0.01f);
        }
    }

    private WatermarkGeometry.Layout layout(
            float pageWidth, float pageHeight, float textWidth, float textHeight,
            float rotation, WatermarkStyle style, WatermarkPosition position
    ) {
        return WatermarkGeometry.calculate(
                pageWidth, pageHeight, textWidth, textHeight,
                rotation, style, position
        );
    }
}
