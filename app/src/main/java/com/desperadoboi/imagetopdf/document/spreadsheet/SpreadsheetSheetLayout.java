package com.desperadoboi.imagetopdf.document.spreadsheet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SpreadsheetSheetLayout {
    public static final float DEFAULT_COLUMN_WIDTH_CHARACTERS = 8.43f;
    public static final float DEFAULT_ROW_HEIGHT_POINTS = 15f;

    private final int rowCount;
    private final int columnCount;
    private final float defaultColumnWidthCharacters;
    private final float defaultRowHeightPoints;
    private final float[] columnWidthsCharacters;
    private final float[] rowHeightsPoints;
    private final Map<Long, SpreadsheetCellStyle> cellStyles;
    private final List<SpreadsheetMergedRange> mergedRanges;

    public SpreadsheetSheetLayout(
            int rowCount,
            int columnCount,
            float defaultColumnWidthCharacters,
            float defaultRowHeightPoints,
            float[] columnWidthsCharacters,
            float[] rowHeightsPoints,
            Map<Long, SpreadsheetCellStyle> cellStyles,
            List<SpreadsheetMergedRange> mergedRanges
    ) {
        this.rowCount = Math.max(0, rowCount);
        this.columnCount = Math.max(0, columnCount);
        this.defaultColumnWidthCharacters = positiveOrDefault(
                defaultColumnWidthCharacters,
                DEFAULT_COLUMN_WIDTH_CHARACTERS
        );
        this.defaultRowHeightPoints = positiveOrDefault(
                defaultRowHeightPoints,
                DEFAULT_ROW_HEIGHT_POINTS
        );
        this.columnWidthsCharacters = columnWidthsCharacters.clone();
        this.rowHeightsPoints = rowHeightsPoints.clone();
        this.cellStyles = Collections.unmodifiableMap(new HashMap<>(cellStyles));
        this.mergedRanges = Collections.unmodifiableList(new ArrayList<>(mergedRanges));
    }

    public static SpreadsheetSheetLayout empty(int rowCount, int columnCount) {
        return new SpreadsheetSheetLayout(
                rowCount,
                columnCount,
                DEFAULT_COLUMN_WIDTH_CHARACTERS,
                DEFAULT_ROW_HEIGHT_POINTS,
                new float[0],
                new float[0],
                Collections.emptyMap(),
                Collections.emptyList()
        );
    }

    public int getRowCount() {
        return rowCount;
    }

    public int getColumnCount() {
        return columnCount;
    }

    public float getColumnWidthCharacters(int column) {
        if (column >= 0 && column < columnWidthsCharacters.length
                && columnWidthsCharacters[column] >= 0f) {
            return columnWidthsCharacters[column];
        }
        return defaultColumnWidthCharacters;
    }

    public float getRowHeightPoints(int row) {
        if (row >= 0 && row < rowHeightsPoints.length && rowHeightsPoints[row] >= 0f) {
            return rowHeightsPoints[row];
        }
        return defaultRowHeightPoints;
    }

    public SpreadsheetCellStyle getCellStyle(int row, int column) {
        SpreadsheetCellStyle style = cellStyles.get(cellKey(row, column));
        return style == null ? SpreadsheetCellStyle.DEFAULT : style;
    }

    public List<SpreadsheetMergedRange> getMergedRanges() {
        return mergedRanges;
    }

    public SpreadsheetMergedRange findMergedRange(int row, int column) {
        for (SpreadsheetMergedRange range : mergedRanges) {
            if (range.contains(row, column)) return range;
        }
        return null;
    }

    public static long cellKey(int row, int column) {
        return ((long) row << 32) | (column & 0xffffffffL);
    }

    private static float positiveOrDefault(float value, float fallback) {
        return Float.isFinite(value) && value > 0f ? value : fallback;
    }
}
