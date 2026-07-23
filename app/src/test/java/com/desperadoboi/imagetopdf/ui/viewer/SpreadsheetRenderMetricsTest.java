package com.desperadoboi.imagetopdf.ui.viewer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.desperadoboi.imagetopdf.document.spreadsheet.SpreadsheetCellStyle;

import org.junit.Test;

public final class SpreadsheetRenderMetricsTest {
    private static final float DELTA = 0.0001f;

    @Test
    public void manualNormalUsesWorkbookFontSize() {
        assertEquals(14f, SpreadsheetRenderMetrics.effectiveFontSize(
                14f,
                1f,
                ZoomController.ZoomMode.MANUAL,
                10.5f
        ), DELTA);
    }

    @Test
    public void manualMinimumZoomKeepsReadableFontSize() {
        assertEquals(10.5f, SpreadsheetRenderMetrics.effectiveFontSize(
                14f,
                0.60f,
                ZoomController.ZoomMode.MANUAL,
                10.5f
        ), DELTA);
    }

    @Test
    public void fitSheetScalesFontWithoutReadableClamp() {
        assertEquals(3.5f, SpreadsheetRenderMetrics.effectiveFontSize(
                14f,
                0.25f,
                ZoomController.ZoomMode.FIT_SHEET,
                10.5f
        ), DELTA);
    }

    @Test
    public void fitWidthScalesFontWithoutReadableClamp() {
        assertEquals(7f, SpreadsheetRenderMetrics.effectiveFontSize(
                14f,
                0.50f,
                ZoomController.ZoomMode.FIT_WIDTH,
                10.5f
        ), DELTA);
    }

    @Test
    public void overviewFontCanBeReducedToFitCellHeight() {
        float fitted = SpreadsheetRenderMetrics.fitFontSizeToHeight(3.5f, 4.2f, 3f);

        assertEquals(2.5f, fitted, DELTA);
        assertTrue(4.2f * fitted / 3.5f <= 3f + DELTA);
    }

    @Test
    public void manualRowHeightAccountsForFontMetricsAndPadding() {
        assertEquals(58, SpreadsheetRenderMetrics.rowHeight(
                20,
                1f,
                ZoomController.ZoomMode.MANUAL,
                false,
                50f,
                4,
                40
        ));
    }

    @Test
    public void fitSheetUsesOnlyScaledWorkbookHeight() {
        assertEquals(5, SpreadsheetRenderMetrics.rowHeight(
                20,
                0.25f,
                ZoomController.ZoomMode.FIT_SHEET,
                false,
                100f,
                4,
                40
        ));
    }

    @Test
    public void hiddenRowAlwaysHasZeroHeight() {
        assertEquals(0, SpreadsheetRenderMetrics.rowHeight(
                20,
                1f,
                ZoomController.ZoomMode.MANUAL,
                true,
                50f,
                4,
                40
        ));
    }

    @Test
    public void wrappedTextExpandsManualRowButNotOverviewRow() {
        assertEquals(53, SpreadsheetRenderMetrics.rowHeight(
                20,
                1f,
                ZoomController.ZoomMode.MANUAL,
                false,
                45f,
                4,
                40
        ));
        assertEquals(5, SpreadsheetRenderMetrics.rowHeight(
                20,
                0.25f,
                ZoomController.ZoomMode.FIT_SHEET,
                false,
                45f,
                4,
                40
        ));
    }

    @Test
    public void baselinesUseActualFontMetricsForAllVerticalAlignments() {
        float ascent = -8f;
        float descent = 2f;
        float leading = 1f;

        assertEquals(10f, SpreadsheetRenderMetrics.firstBaseline(
                2f,
                38f,
                2,
                ascent,
                descent,
                leading,
                SpreadsheetCellStyle.VerticalAlignment.TOP
        ), DELTA);
        assertEquals(17f, SpreadsheetRenderMetrics.firstBaseline(
                2f,
                38f,
                2,
                ascent,
                descent,
                leading,
                SpreadsheetCellStyle.VerticalAlignment.CENTER
        ), DELTA);
        assertEquals(24f, SpreadsheetRenderMetrics.firstBaseline(
                2f,
                38f,
                2,
                ascent,
                descent,
                leading,
                SpreadsheetCellStyle.VerticalAlignment.BOTTOM
        ), DELTA);
    }

    @Test
    public void normalCellClipNeverReachesNeighbourBounds() {
        SpreadsheetRenderMetrics.ClipBounds clip =
                SpreadsheetRenderMetrics.cellClip(100, 20, 5, 2);

        assertEquals(5f, clip.left, DELTA);
        assertEquals(95f, clip.right, DELTA);
        assertTrue(clip.right <= 100f);
        assertTrue(clip.bottom <= 20f);
        assertFalse(clip.isEmpty());
    }

    @Test
    public void mergedCellClipUsesCombinedMergedBounds() {
        SpreadsheetRenderMetrics.ClipBounds clip =
                SpreadsheetRenderMetrics.cellClip(240, 60, 4, 2);

        assertEquals(236f, clip.right, DELTA);
        assertEquals(58f, clip.bottom, DELTA);
        assertEquals(232f, clip.width(), DELTA);
        assertEquals(56f, clip.height(), DELTA);
    }

    @Test
    public void emptyContentBoundsAreNotDrawable() {
        assertTrue(SpreadsheetRenderMetrics.cellClip(4, 4, 3, 3).isEmpty());
    }
}
