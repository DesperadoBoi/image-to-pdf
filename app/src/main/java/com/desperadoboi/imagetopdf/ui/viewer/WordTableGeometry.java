package com.desperadoboi.imagetopdf.ui.viewer;

import com.desperadoboi.imagetopdf.document.word.WordTable;
import com.desperadoboi.imagetopdf.document.word.WordTableCell;
import com.desperadoboi.imagetopdf.document.word.WordTableRow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class WordTableGeometry {
    private static final float DEFAULT_COLUMN_DP = 112f;
    private static final float MINIMUM_COLUMN_DP = 64f;
    private static final float MAXIMUM_COLUMN_DP = 260f;
    private static final float MINIMUM_ROW_DP = 48f;
    private static final float MAXIMUM_ROW_DP = 240f;

    private final float[] columnOffsets;
    private final float[] rowOffsets;
    private final List<List<CellPlacement>> placementsByRow;
    private final int columnCount;

    private WordTableGeometry(
            float[] columnOffsets,
            float[] rowOffsets,
            List<List<CellPlacement>> placementsByRow,
            int columnCount
    ) {
        this.columnOffsets = columnOffsets;
        this.rowOffsets = rowOffsets;
        this.placementsByRow = placementsByRow;
        this.columnCount = columnCount;
    }

    static WordTableGeometry create(WordTable table, float density) {
        float safeDensity = Float.isFinite(density) && density > 0f ? density : 1f;
        int columns = table.getColumnWidthsTwips().size();
        for (WordTableRow row : table.getRows()) {
            int count = 0;
            for (WordTableCell cell : row.getCells()) count += cell.getGridSpan();
            columns = Math.max(columns, count);
        }
        columns = Math.max(1, columns);
        float[] widths = new float[columns];
        for (int column = 0; column < columns; column++) {
            int twips = column < table.getColumnWidthsTwips().size()
                    ? table.getColumnWidthsTwips().get(column)
                    : 0;
            float width = twips > 0 ? twips * safeDensity / 9f
                    : DEFAULT_COLUMN_DP * safeDensity;
            widths[column] = clamp(
                    width,
                    MINIMUM_COLUMN_DP * safeDensity,
                    MAXIMUM_COLUMN_DP * safeDensity
            );
        }
        float[] columnOffsets = prefix(widths);

        List<List<CellPlacement>> byRow = new ArrayList<>();
        List<CellPlacement> allPlacements = new ArrayList<>();
        Map<Integer, CellPlacement> activeVerticalMerges = new HashMap<>();
        float[] heights = new float[table.getRows().size()];
        int placementId = 0;
        for (int rowIndex = 0; rowIndex < table.getRows().size(); rowIndex++) {
            WordTableRow row = table.getRows().get(rowIndex);
            List<CellPlacement> rowPlacements = new ArrayList<>();
            byRow.add(rowPlacements);
            int column = 0;
            float estimatedHeight = MINIMUM_ROW_DP * safeDensity;
            for (WordTableCell cell : row.getCells()) {
                int firstColumn = Math.min(columns - 1, column);
                int lastColumn = Math.min(
                        columns - 1,
                        firstColumn + cell.getGridSpan() - 1
                );
                CellPlacement placement;
                if (cell.getVerticalMerge() == WordTableCell.VerticalMerge.CONTINUE) {
                    placement = activeVerticalMerges.get(firstColumn);
                    if (placement != null) {
                        placement.lastRow = rowIndex;
                        rowPlacements.add(placement);
                    } else {
                        placement = new CellPlacement(
                                placementId++,
                                cell,
                                rowIndex,
                                rowIndex,
                                firstColumn,
                                lastColumn
                        );
                        rowPlacements.add(placement);
                        allPlacements.add(placement);
                    }
                } else {
                    placement = new CellPlacement(
                            placementId++,
                            cell,
                            rowIndex,
                            rowIndex,
                            firstColumn,
                            lastColumn
                    );
                    rowPlacements.add(placement);
                    allPlacements.add(placement);
                    for (int occupied = firstColumn; occupied <= lastColumn; occupied++) {
                        activeVerticalMerges.remove(occupied);
                    }
                    if (cell.getVerticalMerge() == WordTableCell.VerticalMerge.RESTART) {
                        activeVerticalMerges.put(firstColumn, placement);
                    }
                }
                float cellWidth = columnOffsets[lastColumn + 1]
                        - columnOffsets[firstColumn];
                estimatedHeight = Math.max(
                        estimatedHeight,
                        estimatedTextHeight(cell.getPlainText(), cellWidth, safeDensity)
                );
                column = lastColumn + 1;
            }
            int rowTwips = row.getHeightTwips();
            if (rowTwips > 0) {
                estimatedHeight = Math.max(estimatedHeight, rowTwips * safeDensity / 9f);
            }
            heights[rowIndex] = Math.min(
                    MAXIMUM_ROW_DP * safeDensity,
                    estimatedHeight
            );
        }
        float[] rowOffsets = prefix(heights);
        List<List<CellPlacement>> immutableRows = new ArrayList<>();
        for (List<CellPlacement> placements : byRow) {
            immutableRows.add(Collections.unmodifiableList(placements));
        }
        return new WordTableGeometry(
                columnOffsets,
                rowOffsets,
                Collections.unmodifiableList(immutableRows),
                columns
        );
    }

    float getWidth() { return columnOffsets[columnOffsets.length - 1]; }
    float getHeight() { return rowOffsets[rowOffsets.length - 1]; }
    int getRowCount() { return rowOffsets.length - 1; }
    int getColumnCount() { return columnCount; }
    float getRowTop(int row) { return rowOffsets[clamp(row, 0, getRowCount())]; }
    float getRowBottom(int row) { return rowOffsets[clamp(row + 1, 0, getRowCount())]; }
    float getColumnLeft(int column) {
        return columnOffsets[clamp(column, 0, columnCount)];
    }
    float getColumnRight(int column) {
        return columnOffsets[clamp(column + 1, 0, columnCount)];
    }
    List<CellPlacement> getPlacements(int row) {
        return row >= 0 && row < placementsByRow.size()
                ? placementsByRow.get(row)
                : Collections.emptyList();
    }

    int firstVisibleRow(float top) {
        return Math.max(0, upperBound(rowOffsets, Math.max(0f, top)) - 1);
    }

    int lastVisibleRow(float bottom) {
        return Math.min(
                getRowCount() - 1,
                Math.max(0, lowerBound(rowOffsets, Math.max(0f, bottom)) - 1)
        );
    }

    private static float estimatedTextHeight(
            String text,
            float width,
            float density
    ) {
        int usableCharacters = Math.max(8, Math.round(width / (7f * density)));
        int lines = 1;
        int current = 0;
        for (int index = 0; index < text.length(); index++) {
            if (text.charAt(index) == '\n') {
                lines++;
                current = 0;
            } else {
                current++;
                if (current >= usableCharacters) {
                    lines++;
                    current = 0;
                }
            }
            if (lines >= 10) break;
        }
        return Math.min(
                MAXIMUM_ROW_DP * density,
                Math.max(MINIMUM_ROW_DP * density, (lines * 20f + 20f) * density)
        );
    }

    private static float[] prefix(float[] values) {
        float[] result = new float[values.length + 1];
        double sum = 0d;
        for (int index = 0; index < values.length; index++) {
            sum += values[index];
            result[index + 1] = sum >= Float.MAX_VALUE
                    ? Float.MAX_VALUE
                    : (float) sum;
        }
        return result;
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

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    static final class CellPlacement {
        private final int id;
        private final WordTableCell cell;
        private final int firstRow;
        private int lastRow;
        private final int firstColumn;
        private final int lastColumn;

        private CellPlacement(
                int id,
                WordTableCell cell,
                int firstRow,
                int lastRow,
                int firstColumn,
                int lastColumn
        ) {
            this.id = id;
            this.cell = cell;
            this.firstRow = firstRow;
            this.lastRow = lastRow;
            this.firstColumn = firstColumn;
            this.lastColumn = lastColumn;
        }

        int getId() { return id; }
        WordTableCell getCell() { return cell; }
        int getFirstRow() { return firstRow; }
        int getLastRow() { return lastRow; }
        int getFirstColumn() { return firstColumn; }
        int getLastColumn() { return lastColumn; }
    }
}
