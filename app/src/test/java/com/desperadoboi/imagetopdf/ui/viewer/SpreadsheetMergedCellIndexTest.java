package com.desperadoboi.imagetopdf.ui.viewer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.desperadoboi.imagetopdf.document.spreadsheet.SpreadsheetMergedRange;

import org.junit.Test;

import java.util.Arrays;

public final class SpreadsheetMergedCellIndexTest {
    @Test
    public void detectsAnchorsMembersAndBothMergeDirections() {
        SpreadsheetMergedCellIndex index = new SpreadsheetMergedCellIndex(
                8,
                8,
                Arrays.asList(
                        new SpreadsheetMergedRange(1, 1, 1, 3),
                        new SpreadsheetMergedRange(3, 6, 5, 5)
                )
        );

        assertTrue(index.isAnchor(1, 1));
        assertFalse(index.isAnchor(1, 2));
        assertEquals(index.getRangeId(1, 1), index.getRangeId(1, 3));
        assertEquals(index.getRangeId(3, 5), index.getRangeId(6, 5));
        assertEquals(-1, index.getRangeId(0, 0));
    }

    @Test
    public void combinedBoundsComeDirectlyFromPrefixSums() {
        SpreadsheetGeometry geometry = new SpreadsheetGeometry(
                new float[]{10f, 20f, 30f},
                new float[]{5f, 15f, 25f}
        );
        SpreadsheetMergedRange range = new SpreadsheetMergedRange(0, 2, 1, 2);

        assertEquals(10f, geometry.getColumnLeft(range.getFirstColumn()), 0.0001f);
        assertEquals(60f, geometry.getColumnRight(range.getLastColumn()), 0.0001f);
        assertEquals(0f, geometry.getRowTop(range.getFirstRow()), 0.0001f);
        assertEquals(45f, geometry.getRowBottom(range.getLastRow()), 0.0001f);
    }

    @Test
    public void internalMergedBoundariesAreSuppressed() {
        SpreadsheetMergedCellIndex index = new SpreadsheetMergedCellIndex(
                3,
                4,
                java.util.Collections.singletonList(
                        new SpreadsheetMergedRange(0, 1, 0, 2)
                )
        );

        assertTrue(index.suppressesBoundaryBetween(0, 0, 0, 1));
        assertTrue(index.suppressesBoundaryBetween(0, 2, 1, 2));
        assertFalse(index.suppressesBoundaryBetween(0, 2, 0, 3));
    }
}
