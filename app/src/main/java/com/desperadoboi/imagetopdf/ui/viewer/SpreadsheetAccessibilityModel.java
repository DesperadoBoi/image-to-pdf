package com.desperadoboi.imagetopdf.ui.viewer;

import com.desperadoboi.imagetopdf.document.spreadsheet.SpreadsheetMergedRange;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class SpreadsheetAccessibilityModel {
    private SpreadsheetAccessibilityModel() {
    }

    static List<Integer> visibleCellIds(
            SpreadsheetCanvasModel model,
            SpreadsheetRenderPlan plan
    ) {
        SpreadsheetVisibleRange range = plan.getVisibleRange();
        if (range.isEmpty()) return Collections.emptyList();
        SpreadsheetMergedCellIndex mergedIndex = model.getMergedCellIndex();
        boolean[] emittedMerges = new boolean[mergedIndex.getRangeCount()];
        List<Integer> result = new ArrayList<>();
        for (int row = range.getFirstRow(); row <= range.getLastRow(); row++) {
            if (model.getGeometry().isRowHidden(row)) continue;
            for (int column = range.getFirstColumn();
                    column <= range.getLastColumn();
                    column++) {
                if (model.getGeometry().isColumnHidden(column)) continue;
                int rangeId = mergedIndex.getRangeId(row, column);
                int valueRow = row;
                int valueColumn = column;
                if (rangeId >= 0) {
                    if (emittedMerges[rangeId]) continue;
                    SpreadsheetMergedRange mergedRange = mergedIndex.getRange(rangeId);
                    valueRow = mergedRange.getFirstRow();
                    valueColumn = mergedRange.getFirstColumn();
                    if (model.isEmpty(valueRow, valueColumn)) continue;
                    emittedMerges[rangeId] = true;
                } else if (model.isEmpty(row, column)) {
                    continue;
                }
                result.add(cellId(
                        valueRow,
                        valueColumn,
                        model.getGeometry().getColumnCount()
                ));
            }
        }
        return result;
    }

    static List<Integer> visibleRows(
            SpreadsheetGeometry geometry,
            SpreadsheetRenderPlan plan
    ) {
        SpreadsheetVisibleRange range = plan.getVisibleRange();
        if (range.isEmpty()) return Collections.emptyList();
        List<Integer> result = new ArrayList<>();
        for (int row = range.getFirstRow(); row <= range.getLastRow(); row++) {
            if (!geometry.isRowHidden(row)) result.add(row);
        }
        return result;
    }

    static List<Integer> visibleColumns(
            SpreadsheetGeometry geometry,
            SpreadsheetRenderPlan plan
    ) {
        SpreadsheetVisibleRange range = plan.getVisibleRange();
        if (range.isEmpty()) return Collections.emptyList();
        List<Integer> result = new ArrayList<>();
        for (int column = range.getFirstColumn();
                column <= range.getLastColumn();
                column++) {
            if (!geometry.isColumnHidden(column)) result.add(column);
        }
        return result;
    }

    static int cellId(int row, int column, int columnCount) {
        return 1 + row * Math.max(1, columnCount) + column;
    }

    static int rowFromCellId(int virtualId, int columnCount) {
        return Math.max(0, virtualId - 1) / Math.max(1, columnCount);
    }

    static int columnFromCellId(int virtualId, int columnCount) {
        return Math.max(0, virtualId - 1) % Math.max(1, columnCount);
    }
}
