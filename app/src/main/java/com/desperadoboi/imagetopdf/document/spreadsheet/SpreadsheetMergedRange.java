package com.desperadoboi.imagetopdf.document.spreadsheet;

public final class SpreadsheetMergedRange {
    private final int firstRow;
    private final int lastRow;
    private final int firstColumn;
    private final int lastColumn;

    public SpreadsheetMergedRange(
            int firstRow,
            int lastRow,
            int firstColumn,
            int lastColumn
    ) {
        this.firstRow = Math.max(0, Math.min(firstRow, lastRow));
        this.lastRow = Math.max(0, Math.max(firstRow, lastRow));
        this.firstColumn = Math.max(0, Math.min(firstColumn, lastColumn));
        this.lastColumn = Math.max(0, Math.max(firstColumn, lastColumn));
    }

    public int getFirstRow() {
        return firstRow;
    }

    public int getLastRow() {
        return lastRow;
    }

    public int getFirstColumn() {
        return firstColumn;
    }

    public int getLastColumn() {
        return lastColumn;
    }

    public boolean contains(int row, int column) {
        return row >= firstRow && row <= lastRow
                && column >= firstColumn && column <= lastColumn;
    }

    public boolean isAnchor(int row, int column) {
        return row == firstRow && column == firstColumn;
    }
}
