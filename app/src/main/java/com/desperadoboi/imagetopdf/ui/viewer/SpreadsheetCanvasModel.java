package com.desperadoboi.imagetopdf.ui.viewer;

import com.desperadoboi.imagetopdf.document.spreadsheet.ColumnLabelFormatter;
import com.desperadoboi.imagetopdf.document.spreadsheet.SpreadsheetCellStyle;
import com.desperadoboi.imagetopdf.document.spreadsheet.SpreadsheetData;
import com.desperadoboi.imagetopdf.document.spreadsheet.SpreadsheetSheetLayout;
import com.desperadoboi.imagetopdf.document.spreadsheet.XlsxSheet;

import java.util.Collections;
import java.util.List;

final class SpreadsheetCanvasModel {
    private static final int WIDTH_SAMPLE_ROW_COUNT = 80;
    private static final int WIDTH_SAMPLE_CHARACTER_COUNT = 64;
    private static final float EXCEL_CHARACTER_WIDTH_DP = 7f;
    private static final float EXCEL_COLUMN_PADDING_DP = 5f;
    private static final float POINTS_TO_DP = 4f / 3f;
    private static final float CSV_MINIMUM_COLUMN_WIDTH_DP = 104f;
    private static final float CSV_MAXIMUM_COLUMN_WIDTH_DP = 224f;
    private static final float CSV_ROW_HEIGHT_DP = 52f;
    private static final float CSV_HORIZONTAL_PADDING_DP = 24f;

    private final int sheetId;
    private final SpreadsheetData data;
    private final SpreadsheetSheetLayout layout;
    private final SpreadsheetGeometry geometry;
    private final SpreadsheetMergedCellIndex mergedCellIndex;
    private final String[] rowLabels;
    private final String[] columnLabels;

    private SpreadsheetCanvasModel(
            int sheetId,
            SpreadsheetData data,
            SpreadsheetSheetLayout layout,
            SpreadsheetGeometry geometry
    ) {
        this.sheetId = sheetId;
        this.data = data;
        this.layout = layout;
        this.geometry = geometry;
        mergedCellIndex = new SpreadsheetMergedCellIndex(
                geometry.getRowCount(),
                geometry.getColumnCount(),
                layout == null ? Collections.emptyList() : layout.getMergedRanges()
        );
        rowLabels = new String[geometry.getRowCount()];
        columnLabels = new String[geometry.getColumnCount()];
        for (int column = 0; column < columnLabels.length; column++) {
            columnLabels[column] = ColumnLabelFormatter.format(column);
        }
    }

    static SpreadsheetCanvasModel create(
            int sheetId,
            SpreadsheetData data,
            float density
    ) {
        return create(sheetId, data, null, density);
    }

    static SpreadsheetCanvasModel create(
            int sheetId,
            XlsxSheet sheet,
            float density
    ) {
        return create(sheetId, sheet.getData(), sheet.getLayout(), density);
    }

    private static SpreadsheetCanvasModel create(
            int sheetId,
            SpreadsheetData data,
            SpreadsheetSheetLayout layout,
            float density
    ) {
        float safeDensity = Float.isFinite(density) && density > 0f ? density : 1f;
        int rowCount = layout == null
                ? data.getRows().size()
                : Math.max(data.getRows().size(), layout.getRowCount());
        int columnCount = layout == null
                ? Math.max(1, data.getColumnCount())
                : Math.max(1, Math.max(data.getColumnCount(), layout.getColumnCount()));
        float[] widths = layout == null
                ? csvColumnWidths(data, columnCount, safeDensity)
                : xlsxColumnWidths(layout, columnCount, safeDensity);
        float[] heights = layout == null
                ? csvRowHeights(rowCount, safeDensity)
                : xlsxRowHeights(layout, rowCount, safeDensity);
        return new SpreadsheetCanvasModel(
                Math.max(0, sheetId),
                data,
                layout,
                new SpreadsheetGeometry(widths, heights)
        );
    }

    int getSheetId() {
        return sheetId;
    }

    SpreadsheetGeometry getGeometry() {
        return geometry;
    }

    SpreadsheetMergedCellIndex getMergedCellIndex() {
        return mergedCellIndex;
    }

    String getRowLabel(int row) {
        if (row < 0 || row >= rowLabels.length) return "";
        String label = rowLabels[row];
        if (label == null) {
            label = Integer.toString(row + 1);
            rowLabels[row] = label;
        }
        return label;
    }

    String getColumnLabel(int column) {
        return column >= 0 && column < columnLabels.length ? columnLabels[column] : "";
    }

    String getValue(int row, int column) {
        if (row < 0 || row >= data.getRows().size()) return "";
        List<String> values = data.getRows().get(row);
        if (column < 0 || column >= values.size()) return "";
        String value = values.get(column);
        return value == null ? "" : value;
    }

    boolean isEmpty(int row, int column) {
        return getValue(row, column).isEmpty();
    }

    SpreadsheetCellStyle getStyle(int row, int column) {
        return layout == null
                ? SpreadsheetCellStyle.DEFAULT
                : layout.getCellStyle(row, column);
    }

    private static float[] xlsxColumnWidths(
            SpreadsheetSheetLayout layout,
            int columnCount,
            float density
    ) {
        float[] result = new float[columnCount];
        float minimumWidth = 4f * density;
        float maximumWidth = 480f * density;
        for (int column = 0; column < columnCount; column++) {
            float characters = layout.getColumnWidthCharacters(column);
            if (characters <= 0f) {
                result[column] = 0f;
                continue;
            }
            float width = (characters * EXCEL_CHARACTER_WIDTH_DP
                    + EXCEL_COLUMN_PADDING_DP) * density;
            result[column] = clamp(width, minimumWidth, maximumWidth);
        }
        return result;
    }

    private static float[] xlsxRowHeights(
            SpreadsheetSheetLayout layout,
            int rowCount,
            float density
    ) {
        float[] result = new float[rowCount];
        for (int row = 0; row < rowCount; row++) {
            float points = layout.getRowHeightPoints(row);
            result[row] = points <= 0f ? 0f : Math.max(1f, points * POINTS_TO_DP * density);
        }
        return result;
    }

    private static float[] csvColumnWidths(
            SpreadsheetData data,
            int columnCount,
            float density
    ) {
        float[] result = new float[columnCount];
        int sampledRows = Math.min(WIDTH_SAMPLE_ROW_COUNT, data.getRows().size());
        float minimum = CSV_MINIMUM_COLUMN_WIDTH_DP * density;
        float maximum = CSV_MAXIMUM_COLUMN_WIDTH_DP * density;
        for (int column = 0; column < columnCount; column++) {
            int maximumCharacters = ColumnLabelFormatter.format(column).length();
            for (int row = 0; row < sampledRows; row++) {
                List<String> values = data.getRows().get(row);
                if (column >= values.size()) continue;
                maximumCharacters = Math.max(
                        maximumCharacters,
                        longestLineLength(values.get(column), WIDTH_SAMPLE_CHARACTER_COUNT)
                );
            }
            float estimatedWidth = (maximumCharacters * EXCEL_CHARACTER_WIDTH_DP
                    + CSV_HORIZONTAL_PADDING_DP) * density;
            result[column] = clamp(estimatedWidth, minimum, maximum);
        }
        return result;
    }

    private static float[] csvRowHeights(int rowCount, float density) {
        float[] result = new float[rowCount];
        java.util.Arrays.fill(result, CSV_ROW_HEIGHT_DP * density);
        return result;
    }

    private static int longestLineLength(String value, int maximum) {
        if (value == null || value.isEmpty()) return 0;
        int longest = 0;
        int current = 0;
        int length = Math.min(value.length(), maximum);
        for (int index = 0; index < length; index++) {
            if (value.charAt(index) == '\n') {
                longest = Math.max(longest, current);
                current = 0;
            } else {
                current++;
            }
        }
        return Math.max(longest, current);
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
