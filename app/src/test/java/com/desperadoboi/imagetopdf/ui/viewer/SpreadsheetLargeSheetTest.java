package com.desperadoboi.imagetopdf.ui.viewer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.desperadoboi.imagetopdf.document.spreadsheet.SpreadsheetCellStyle;
import com.desperadoboi.imagetopdf.document.spreadsheet.SpreadsheetData;
import com.desperadoboi.imagetopdf.document.spreadsheet.SpreadsheetMergedRange;
import com.desperadoboi.imagetopdf.document.spreadsheet.SpreadsheetSheetLayout;
import com.desperadoboi.imagetopdf.document.spreadsheet.XlsxSheet;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SpreadsheetLargeSheetTest {
    @Test
    public void fiveThousandByOneHundredPlansBoundedWorkAtMinimumAndNormalZoom() {
        SpreadsheetCanvasModel model = largeModel();
        SpreadsheetGeometry geometry = model.getGeometry();
        SpreadsheetViewportTransform transform = new SpreadsheetViewportTransform();
        transform.setBounds(
                360f,
                640f,
                geometry.getSheetWidth(),
                geometry.getSheetHeight()
        );

        transform.set(0.60f, 0f, 0f, ZoomController.ZoomMode.MANUAL);
        SpreadsheetRenderPlan minimumZoom =
                new SpreadsheetRenderPlanner().plan(geometry, transform);
        assertEquals(
                SpreadsheetLevelOfDetailPolicy.Detail.FULL,
                minimumZoom.getDetail()
        );
        assertTrue(minimumZoom.getVisibleCells() <= 1_000);

        transform.set(1f, 0f, 0f, ZoomController.ZoomMode.ZOOM_100);
        SpreadsheetRenderPlan normal =
                new SpreadsheetRenderPlanner().plan(geometry, transform);
        assertEquals(SpreadsheetLevelOfDetailPolicy.Detail.FULL, normal.getDetail());
        assertEquals(33, normal.getVisibleRows());
        assertEquals(7, normal.getVisibleColumns());
        assertEquals(231, normal.getVisibleCells());
    }

    private SpreadsheetCanvasModel largeModel() {
        List<List<String>> rows = new ArrayList<>(5_000);
        List<String> heading = new ArrayList<>(Collections.nCopies(100, ""));
        heading.set(0, "Long heading that remains clipped to its merged block");
        heading.set(20, "Styled");
        rows.add(heading);
        for (int row = 1; row < 5_000; row++) rows.add(Collections.emptyList());

        float[] widths = new float[100];
        float[] heights = new float[5_000];
        Arrays.fill(widths, -1f);
        Arrays.fill(heights, -1f);
        widths[5] = 0f;
        heights[10] = 0f;
        Map<Long, SpreadsheetCellStyle> styles = new HashMap<>();
        styles.put(
                SpreadsheetSheetLayout.cellKey(0, 0),
                new SpreadsheetCellStyle.Builder()
                        .setStyleId(17)
                        .setFillColor(0xFFB7DEE8)
                        .setBold(true)
                        .setWrapText(true)
                        .build()
        );
        styles.put(
                SpreadsheetSheetLayout.cellKey(0, 20),
                new SpreadsheetCellStyle.Builder()
                        .setStyleId(18)
                        .setFillColor(0xFFFCD5B4)
                        .setItalic(true)
                        .build()
        );
        List<SpreadsheetMergedRange> merges = Arrays.asList(
                new SpreadsheetMergedRange(0, 1, 0, 3),
                new SpreadsheetMergedRange(100, 140, 10, 10)
        );
        SpreadsheetData data = new SpreadsheetData(rows, false, '\0');
        SpreadsheetSheetLayout layout = new SpreadsheetSheetLayout(
                5_000,
                100,
                8.43f,
                15f,
                widths,
                heights,
                styles,
                merges
        );
        return SpreadsheetCanvasModel.create(
                0,
                new XlsxSheet(
                        "Large",
                        data,
                        "A1:CV5000",
                        Arrays.asList("A1:D2", "K101:K141"),
                        layout
                ),
                1f
        );
    }
}
