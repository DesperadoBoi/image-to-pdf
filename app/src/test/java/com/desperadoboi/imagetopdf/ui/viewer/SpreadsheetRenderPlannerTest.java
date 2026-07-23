package com.desperadoboi.imagetopdf.ui.viewer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.desperadoboi.imagetopdf.document.spreadsheet.SpreadsheetMergedRange;

import org.junit.Test;

public final class SpreadsheetRenderPlannerTest {
    @Test
    public void plannerCountsOnlyVisibleNonHiddenCells() {
        SpreadsheetGeometry geometry = new SpreadsheetGeometry(
                new float[]{10f, 0f, 10f, 10f},
                new float[]{10f, 0f, 10f, 10f}
        );
        SpreadsheetViewportTransform transform = new SpreadsheetViewportTransform();
        transform.setBounds(20f, 20f, geometry.getSheetWidth(), geometry.getSheetHeight());
        transform.set(1f, 0f, 0f, ZoomController.ZoomMode.ZOOM_100);

        SpreadsheetRenderPlan plan =
                new SpreadsheetRenderPlanner().plan(geometry, transform, 0);

        assertEquals(2, plan.getVisibleRows());
        assertEquals(2, plan.getVisibleColumns());
        assertEquals(4, plan.getVisibleCells());
    }

    @Test
    public void overviewSkipsTextWhileReadableZoomAllowsIt() {
        assertEquals(
                SpreadsheetLevelOfDetailPolicy.Detail.FULL,
                SpreadsheetLevelOfDetailPolicy.detailForScale(0.60f)
        );
        assertEquals(
                SpreadsheetLevelOfDetailPolicy.Detail.REDUCED,
                SpreadsheetLevelOfDetailPolicy.detailForScale(0.59f)
        );
        assertEquals(
                SpreadsheetLevelOfDetailPolicy.Detail.REDUCED,
                SpreadsheetLevelOfDetailPolicy.detailForScale(0.40f)
        );
        assertEquals(
                SpreadsheetLevelOfDetailPolicy.Detail.OVERVIEW,
                SpreadsheetLevelOfDetailPolicy.detailForScale(0.39f)
        );
        assertFalse(SpreadsheetLevelOfDetailPolicy.shouldDrawText(
                0.25f,
                200f,
                80f,
                false,
                false
        ));
        assertTrue(SpreadsheetLevelOfDetailPolicy.shouldDrawText(
                1f,
                80f,
                24f,
                false,
                false
        ));
    }

    @Test
    public void offscreenMergedRangeIsRejectedByPlan() {
        SpreadsheetRenderPlan plan = new SpreadsheetRenderPlan(
                new SpreadsheetVisibleRange(0, 4, 0, 4),
                SpreadsheetLevelOfDetailPolicy.Detail.FULL,
                5,
                5
        );

        assertTrue(plan.intersects(new SpreadsheetMergedRange(3, 7, 3, 7)));
        assertFalse(plan.intersects(new SpreadsheetMergedRange(10, 12, 10, 12)));
    }
}
