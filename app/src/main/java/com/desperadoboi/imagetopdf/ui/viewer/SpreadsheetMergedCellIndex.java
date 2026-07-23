package com.desperadoboi.imagetopdf.ui.viewer;

import com.desperadoboi.imagetopdf.document.spreadsheet.SpreadsheetMergedRange;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class SpreadsheetMergedCellIndex {
    private final int rowCount;
    private final int columnCount;
    private final List<SpreadsheetMergedRange> ranges;
    private final char[] rangeIdsByCell;

    SpreadsheetMergedCellIndex(
            int rowCount,
            int columnCount,
            List<SpreadsheetMergedRange> sourceRanges
    ) {
        this.rowCount = Math.max(0, rowCount);
        this.columnCount = Math.max(0, columnCount);
        ranges = sanitizedRanges(sourceRanges);
        rangeIdsByCell = buildMembership();
    }

    int getRangeCount() {
        return ranges.size();
    }

    SpreadsheetMergedRange getRange(int rangeId) {
        return rangeId >= 0 && rangeId < ranges.size() ? ranges.get(rangeId) : null;
    }

    int getRangeId(int row, int column) {
        if (row < 0 || row >= rowCount || column < 0 || column >= columnCount) return -1;
        if (rangeIdsByCell.length == 0) return -1;
        return rangeIdsByCell[row * columnCount + column] - 1;
    }

    SpreadsheetMergedRange getRangeAt(int row, int column) {
        return getRange(getRangeId(row, column));
    }

    boolean isAnchor(int row, int column) {
        SpreadsheetMergedRange range = getRangeAt(row, column);
        return range != null && range.isAnchor(row, column);
    }

    boolean suppressesBoundaryBetween(
            int firstRow,
            int firstColumn,
            int secondRow,
            int secondColumn
    ) {
        int rangeId = getRangeId(firstRow, firstColumn);
        return rangeId >= 0 && rangeId == getRangeId(secondRow, secondColumn);
    }

    boolean intersects(int rangeId, SpreadsheetVisibleRange visibleRange) {
        SpreadsheetMergedRange range = getRange(rangeId);
        return range != null
                && !visibleRange.isEmpty()
                && range.getLastRow() >= visibleRange.getFirstRow()
                && range.getFirstRow() <= visibleRange.getLastRow()
                && range.getLastColumn() >= visibleRange.getFirstColumn()
                && range.getFirstColumn() <= visibleRange.getLastColumn();
    }

    private List<SpreadsheetMergedRange> sanitizedRanges(
            List<SpreadsheetMergedRange> sourceRanges
    ) {
        if (sourceRanges == null || sourceRanges.isEmpty()
                || rowCount == 0 || columnCount == 0) {
            return Collections.emptyList();
        }
        List<SpreadsheetMergedRange> result = new ArrayList<>();
        for (SpreadsheetMergedRange range : sourceRanges) {
            if (range == null
                    || range.getFirstRow() >= rowCount
                    || range.getFirstColumn() >= columnCount) {
                continue;
            }
            result.add(new SpreadsheetMergedRange(
                    range.getFirstRow(),
                    Math.min(rowCount - 1, range.getLastRow()),
                    range.getFirstColumn(),
                    Math.min(columnCount - 1, range.getLastColumn())
            ));
        }
        return Collections.unmodifiableList(result);
    }

    private char[] buildMembership() {
        if (ranges.isEmpty() || rowCount == 0 || columnCount == 0) return new char[0];
        char[] membership = new char[rowCount * columnCount];
        int assignedCells = 0;
        long attempts = 0L;
        long maximumAttempts = Math.max(1L, membership.length * 4L);
        rangeLoop:
        for (int rangeId = 0; rangeId < ranges.size(); rangeId++) {
            if (assignedCells == membership.length) break;
            SpreadsheetMergedRange range = ranges.get(rangeId);
            for (int row = range.getFirstRow(); row <= range.getLastRow(); row++) {
                int rowStart = row * columnCount;
                for (int column = range.getFirstColumn();
                        column <= range.getLastColumn();
                        column++) {
                    if (++attempts > maximumAttempts) break rangeLoop;
                    int index = rowStart + column;
                    if (membership[index] != 0) continue;
                    membership[index] = (char) (rangeId + 1);
                    assignedCells++;
                }
            }
        }
        return membership;
    }
}
