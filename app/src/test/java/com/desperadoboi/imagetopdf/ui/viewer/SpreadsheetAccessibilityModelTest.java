package com.desperadoboi.imagetopdf.ui.viewer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.desperadoboi.imagetopdf.document.spreadsheet.SpreadsheetData;
import com.desperadoboi.imagetopdf.document.spreadsheet.SpreadsheetMergedRange;
import com.desperadoboi.imagetopdf.document.spreadsheet.SpreadsheetSheetLayout;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class SpreadsheetAccessibilityModelTest {
    @Test
    public void exposesOnlyVisibleNonEmptyCellsAndMergedCellOnce() {
        SpreadsheetData data = new SpreadsheetData(
                Arrays.asList(
                        Arrays.asList("Merged", "", ""),
                        Arrays.asList("", "Visible", ""),
                        Arrays.asList("", "", "Offscreen")
                ),
                false,
                '\0'
        );
        SpreadsheetSheetLayout layout = new SpreadsheetSheetLayout(
                3,
                3,
                10f,
                15f,
                new float[]{10f, 10f, 10f},
                new float[]{15f, 15f, 15f},
                Collections.emptyMap(),
                Collections.singletonList(new SpreadsheetMergedRange(0, 0, 0, 1))
        );
        SpreadsheetCanvasModel model = SpreadsheetCanvasModel.create(
                0,
                new com.desperadoboi.imagetopdf.document.spreadsheet.XlsxSheet(
                        "Sheet",
                        data,
                        "A1:C3",
                        Collections.singletonList("A1:B1"),
                        layout
                ),
                1f
        );
        SpreadsheetRenderPlan plan = new SpreadsheetRenderPlan(
                new SpreadsheetVisibleRange(0, 1, 0, 2),
                SpreadsheetLevelOfDetailPolicy.Detail.FULL,
                2,
                3
        );

        List<Integer> ids = SpreadsheetAccessibilityModel.visibleCellIds(model, plan);
        int mergedId = SpreadsheetAccessibilityModel.cellId(0, 0, 3);
        int visibleId = SpreadsheetAccessibilityModel.cellId(1, 1, 3);
        int offscreenId = SpreadsheetAccessibilityModel.cellId(2, 2, 3);

        assertEquals(2, ids.size());
        assertTrue(ids.contains(mergedId));
        assertTrue(ids.contains(visibleId));
        assertFalse(ids.contains(offscreenId));
    }
}
