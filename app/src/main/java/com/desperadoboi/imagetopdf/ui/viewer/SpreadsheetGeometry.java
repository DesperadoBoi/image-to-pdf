package com.desperadoboi.imagetopdf.ui.viewer;

final class SpreadsheetGeometry {
    private final float[] columnWidths;
    private final float[] rowHeights;
    private final float[] columnOffsets;
    private final float[] rowOffsets;

    SpreadsheetGeometry(float[] columnWidths, float[] rowHeights) {
        this.columnWidths = sanitizedCopy(columnWidths);
        this.rowHeights = sanitizedCopy(rowHeights);
        columnOffsets = prefixSums(this.columnWidths);
        rowOffsets = prefixSums(this.rowHeights);
    }

    int getColumnCount() {
        return columnWidths.length;
    }

    int getRowCount() {
        return rowHeights.length;
    }

    float getColumnWidth(int column) {
        return isValidColumn(column) ? columnWidths[column] : 0f;
    }

    float getRowHeight(int row) {
        return isValidRow(row) ? rowHeights[row] : 0f;
    }

    float getColumnLeft(int column) {
        return columnOffsets[clamp(column, 0, columnOffsets.length - 1)];
    }

    float getColumnRight(int column) {
        return columnOffsets[clamp(column + 1, 0, columnOffsets.length - 1)];
    }

    float getRowTop(int row) {
        return rowOffsets[clamp(row, 0, rowOffsets.length - 1)];
    }

    float getRowBottom(int row) {
        return rowOffsets[clamp(row + 1, 0, rowOffsets.length - 1)];
    }

    float getSheetWidth() {
        return columnOffsets[columnOffsets.length - 1];
    }

    float getSheetHeight() {
        return rowOffsets[rowOffsets.length - 1];
    }

    boolean isColumnHidden(int column) {
        return !isValidColumn(column) || columnWidths[column] <= 0f;
    }

    boolean isRowHidden(int row) {
        return !isValidRow(row) || rowHeights[row] <= 0f;
    }

    int columnAt(float sheetX) {
        return indexAt(columnOffsets, sheetX);
    }

    int rowAt(float sheetY) {
        return indexAt(rowOffsets, sheetY);
    }

    SpreadsheetVisibleRange visibleRange(
            float left,
            float top,
            float right,
            float bottom,
            int overscan
    ) {
        if (getRowCount() == 0
                || getColumnCount() == 0
                || right <= left
                || bottom <= top
                || getSheetWidth() <= 0f
                || getSheetHeight() <= 0f) {
            return SpreadsheetVisibleRange.EMPTY;
        }
        float boundedLeft = clamp(left, 0f, getSheetWidth());
        float boundedTop = clamp(top, 0f, getSheetHeight());
        float boundedRight = clamp(right, 0f, getSheetWidth());
        float boundedBottom = clamp(bottom, 0f, getSheetHeight());
        if (boundedRight <= boundedLeft || boundedBottom <= boundedTop) {
            return SpreadsheetVisibleRange.EMPTY;
        }

        int safeOverscan = Math.max(0, overscan);
        int firstColumn = Math.max(
                0,
                firstIntersecting(columnOffsets, boundedLeft) - safeOverscan
        );
        int lastColumn = Math.min(
                getColumnCount() - 1,
                lastIntersecting(columnOffsets, boundedRight) + safeOverscan
        );
        int firstRow = Math.max(0, firstIntersecting(rowOffsets, boundedTop) - safeOverscan);
        int lastRow = Math.min(
                getRowCount() - 1,
                lastIntersecting(rowOffsets, boundedBottom) + safeOverscan
        );
        return new SpreadsheetVisibleRange(firstRow, lastRow, firstColumn, lastColumn);
    }

    private boolean isValidColumn(int column) {
        return column >= 0 && column < columnWidths.length;
    }

    private boolean isValidRow(int row) {
        return row >= 0 && row < rowHeights.length;
    }

    private static int indexAt(float[] offsets, float coordinate) {
        if (!Float.isFinite(coordinate)
                || coordinate < 0f
                || offsets.length <= 1
                || coordinate >= offsets[offsets.length - 1]) {
            return -1;
        }
        int index = upperBound(offsets, coordinate) - 1;
        return clamp(index, 0, offsets.length - 2);
    }

    private static int firstIntersecting(float[] offsets, float coordinate) {
        return clamp(upperBound(offsets, coordinate) - 1, 0, offsets.length - 2);
    }

    private static int lastIntersecting(float[] offsets, float coordinate) {
        return clamp(lowerBound(offsets, coordinate) - 1, 0, offsets.length - 2);
    }

    private static int lowerBound(float[] values, float target) {
        int low = 0;
        int high = values.length;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (values[middle] < target) low = middle + 1;
            else high = middle;
        }
        return low;
    }

    private static int upperBound(float[] values, float target) {
        int low = 0;
        int high = values.length;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (values[middle] <= target) low = middle + 1;
            else high = middle;
        }
        return low;
    }

    private static float[] sanitizedCopy(float[] values) {
        float[] result = values == null ? new float[0] : values.clone();
        for (int index = 0; index < result.length; index++) {
            float value = result[index];
            result[index] = Float.isFinite(value) && value > 0f ? value : 0f;
        }
        return result;
    }

    private static float[] prefixSums(float[] values) {
        float[] offsets = new float[values.length + 1];
        double total = 0d;
        for (int index = 0; index < values.length; index++) {
            total += values[index];
            offsets[index + 1] = total >= Float.MAX_VALUE
                    ? Float.MAX_VALUE
                    : (float) total;
        }
        return offsets;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
