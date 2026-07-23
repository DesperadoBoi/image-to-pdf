package com.desperadoboi.imagetopdf.ui.viewer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public final class SpreadsheetGeometryTest {
    private static final float DELTA = 0.0001f;

    @Test
    public void prefixSumsPreserveRealSizesAndHiddenAxes() {
        SpreadsheetGeometry geometry = new SpreadsheetGeometry(
                new float[]{10f, 0f, 20f},
                new float[]{5f, 0f, 15f}
        );

        assertEquals(0f, geometry.getColumnLeft(0), DELTA);
        assertEquals(10f, geometry.getColumnRight(0), DELTA);
        assertEquals(10f, geometry.getColumnLeft(2), DELTA);
        assertEquals(30f, geometry.getSheetWidth(), DELTA);
        assertEquals(20f, geometry.getSheetHeight(), DELTA);
        assertTrue(geometry.isColumnHidden(1));
        assertTrue(geometry.isRowHidden(1));
        assertEquals(2, geometry.columnAt(10f));
        assertEquals(2, geometry.rowAt(5f));
    }

    @Test
    public void visibleRangeUsesBinarySearchAndOverscan() {
        SpreadsheetGeometry geometry = new SpreadsheetGeometry(
                new float[]{10f, 10f, 10f, 10f},
                new float[]{8f, 8f, 8f, 8f}
        );

        SpreadsheetVisibleRange exact = geometry.visibleRange(
                10f,
                8f,
                20f,
                16f,
                0
        );
        assertEquals(1, exact.getFirstColumn());
        assertEquals(1, exact.getLastColumn());
        assertEquals(1, exact.getFirstRow());
        assertEquals(1, exact.getLastRow());

        SpreadsheetVisibleRange overscanned = geometry.visibleRange(
                10f,
                8f,
                20f,
                16f,
                1
        );
        assertEquals(0, overscanned.getFirstColumn());
        assertEquals(2, overscanned.getLastColumn());
        assertEquals(0, overscanned.getFirstRow());
        assertEquals(2, overscanned.getLastRow());
    }

    @Test
    public void fiveThousandByOneHundredFindsSmallViewportWithoutFullIteration() {
        float[] columns = new float[100];
        float[] rows = new float[5_000];
        Arrays.fill(columns, 12f);
        Arrays.fill(rows, 10f);
        SpreadsheetGeometry geometry = new SpreadsheetGeometry(columns, rows);

        SpreadsheetVisibleRange range = geometry.visibleRange(
                600f,
                25_000f,
                720f,
                25_100f,
                1
        );

        assertTrue(range.getLastColumn() - range.getFirstColumn() + 1 <= 12);
        assertTrue(range.getLastRow() - range.getFirstRow() + 1 <= 12);
        assertEquals(100, geometry.getColumnCount());
        assertEquals(5_000, geometry.getRowCount());
    }
}
