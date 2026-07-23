package com.desperadoboi.imagetopdf.ui.viewer;

import com.desperadoboi.imagetopdf.document.spreadsheet.SpreadsheetMergedRange;

final class SpreadsheetRenderPlan {
    private final SpreadsheetVisibleRange visibleRange;
    private final SpreadsheetLevelOfDetailPolicy.Detail detail;
    private final int visibleRows;
    private final int visibleColumns;
    private final int visibleCells;

    SpreadsheetRenderPlan(
            SpreadsheetVisibleRange visibleRange,
            SpreadsheetLevelOfDetailPolicy.Detail detail,
            int visibleRows,
            int visibleColumns
    ) {
        this.visibleRange = visibleRange;
        this.detail = detail;
        this.visibleRows = visibleRows;
        this.visibleColumns = visibleColumns;
        visibleCells = visibleRows * visibleColumns;
    }

    SpreadsheetVisibleRange getVisibleRange() {
        return visibleRange;
    }

    SpreadsheetLevelOfDetailPolicy.Detail getDetail() {
        return detail;
    }

    int getVisibleRows() {
        return visibleRows;
    }

    int getVisibleColumns() {
        return visibleColumns;
    }

    int getVisibleCells() {
        return visibleCells;
    }

    boolean intersects(SpreadsheetMergedRange range) {
        return range != null
                && !visibleRange.isEmpty()
                && range.getLastRow() >= visibleRange.getFirstRow()
                && range.getFirstRow() <= visibleRange.getLastRow()
                && range.getLastColumn() >= visibleRange.getFirstColumn()
                && range.getFirstColumn() <= visibleRange.getLastColumn();
    }
}
