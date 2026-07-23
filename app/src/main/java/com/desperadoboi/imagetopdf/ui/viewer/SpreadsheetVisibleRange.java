package com.desperadoboi.imagetopdf.ui.viewer;

final class SpreadsheetVisibleRange {
    static final SpreadsheetVisibleRange EMPTY =
            new SpreadsheetVisibleRange(0, -1, 0, -1);

    private final int firstRow;
    private final int lastRow;
    private final int firstColumn;
    private final int lastColumn;

    SpreadsheetVisibleRange(
            int firstRow,
            int lastRow,
            int firstColumn,
            int lastColumn
    ) {
        this.firstRow = firstRow;
        this.lastRow = lastRow;
        this.firstColumn = firstColumn;
        this.lastColumn = lastColumn;
    }

    int getFirstRow() {
        return firstRow;
    }

    int getLastRow() {
        return lastRow;
    }

    int getFirstColumn() {
        return firstColumn;
    }

    int getLastColumn() {
        return lastColumn;
    }

    boolean isEmpty() {
        return lastRow < firstRow || lastColumn < firstColumn;
    }

    boolean contains(int row, int column) {
        return !isEmpty()
                && row >= firstRow
                && row <= lastRow
                && column >= firstColumn
                && column <= lastColumn;
    }
}
