package com.desperadoboi.imagetopdf.ui.viewer;

import com.desperadoboi.imagetopdf.document.word.WordTable;
import com.desperadoboi.imagetopdf.document.word.WordTableCell;
import com.desperadoboi.imagetopdf.document.word.WordTableRow;
import com.desperadoboi.imagetopdf.document.word.WordMeasurementConverter;
import com.desperadoboi.imagetopdf.document.word.WordBlock;
import com.desperadoboi.imagetopdf.document.word.WordParagraph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class WordTableGeometry {
    private static final float DEFAULT_COLUMN_POINTS = 90f;
    private static final float MINIMUM_DEFINED_COLUMN_POINTS = 1f;
    private static final float MAXIMUM_DEFINED_COLUMN_POINTS = 1_440f;

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

    static WordTableGeometry create(
            WordTable table,
            WordMeasurementConverter converter,
            float availableWidth
    ) {
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
            widths[column] = twips > 0
                    ? clamp(
                            converter.twipsToPixels(twips),
                            converter.pointsToPhysicalPixels(
                                    MINIMUM_DEFINED_COLUMN_POINTS
                            ),
                            converter.pointsToPhysicalPixels(
                                    MAXIMUM_DEFINED_COLUMN_POINTS
                            )
                    )
                    : converter.pointsToPhysicalPixels(DEFAULT_COLUMN_POINTS);
        }
        applyCellWidths(table, widths, converter, availableWidth);
        float requestedTableWidth = converter.tableWidthToPixels(
                table.getWidth(),
                availableWidth
        );
        float gridWidth = sum(widths);
        if (requestedTableWidth > 0f && gridWidth > 0f) {
            float scale = requestedTableWidth / gridWidth;
            for (int index = 0; index < widths.length; index++) {
                widths[index] *= scale;
            }
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
            float estimatedHeight = 0f;
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
                        estimatedTextHeight(
                                cell,
                                cellWidth,
                                table,
                                converter
                        )
                );
                column = lastColumn + 1;
            }
            int rowTwips = row.getHeightTwips();
            if (rowTwips > 0) {
                estimatedHeight = Math.max(
                        estimatedHeight,
                        converter.twipsToPixels(rowTwips)
                );
            }
            heights[rowIndex] = Math.max(1f, estimatedHeight);
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
            WordTableCell cell,
            float width,
            WordTable table,
            WordMeasurementConverter converter
    ) {
        float horizontalPadding = converter.twipsToPixels(
                table.getCellMarginStartTwips()
                        + table.getCellMarginEndTwips()
        );
        float verticalPadding = converter.twipsToPixels(
                table.getCellMarginTopTwips()
                        + table.getCellMarginBottomTwips()
        );
        float available = Math.max(1f, width - horizontalPadding);
        float height = verticalPadding;
        WordParagraph previous = null;
        for (int blockIndex = 0; blockIndex < cell.getBlocks().size(); blockIndex++) {
            WordBlock block = cell.getBlocks().get(blockIndex);
            if (!(block instanceof WordParagraph)) {
                height += converter.pointsToPhysicalPixels(12f);
                previous = null;
                continue;
            }
            WordParagraph paragraph = (WordParagraph) block;
            WordParagraph next = null;
            if (blockIndex + 1 < cell.getBlocks().size()
                    && cell.getBlocks().get(blockIndex + 1)
                    instanceof WordParagraph) {
                next = (WordParagraph) cell.getBlocks().get(blockIndex + 1);
            }
            ParagraphLayoutMetrics metrics = ParagraphLayoutMetrics.resolve(
                    paragraph,
                    previous,
                    next,
                    converter
            );
            float fontPixels = converter.fontPointsToPixels(
                    ParagraphLayoutMetrics.maximumFontPoints(paragraph)
            );
            float paragraphAvailable = Math.max(
                    1f,
                    available - metrics.getStartIndentPixels()
                            - metrics.getEndIndentPixels()
            );
            int charactersPerLine = Math.max(
                    1,
                    (int) Math.floor(
                            paragraphAvailable / Math.max(1f, fontPixels * 0.52f)
                    )
            );
            String text = paragraph.getPlainText();
            int lines = 1;
            int current = 0;
            for (int index = 0; index < text.length(); index++) {
                if (text.charAt(index) == '\n') {
                    lines++;
                    current = 0;
                } else if (++current >= charactersPerLine) {
                    lines++;
                    current = 0;
                }
            }
            float lineHeight = metrics.getMinimumHeightPixels()
                    * metrics.getLineSpacingMultiplier()
                    + metrics.getLineSpacingExtraPixels();
            height += metrics.getMarginTopPixels()
                    + metrics.getMarginBottomPixels()
                    + Math.max(metrics.getMinimumHeightPixels(), lines * lineHeight);
            previous = paragraph;
        }
        return Math.max(1f, height);
    }

    private static void applyCellWidths(
            WordTable table,
            float[] widths,
            WordMeasurementConverter converter,
            float availableWidth
    ) {
        for (WordTableRow row : table.getRows()) {
            int column = 0;
            for (WordTableCell cell : row.getCells()) {
                if (column >= widths.length) break;
                int span = Math.min(cell.getGridSpan(), widths.length - column);
                float requested = converter.tableWidthToPixels(
                        cell.getWidth(),
                        availableWidth
                );
                if (requested > 0f) {
                    float perColumn = requested / span;
                    for (int index = column; index < column + span; index++) {
                        widths[index] = Math.max(widths[index], perColumn);
                    }
                }
                column += span;
            }
        }
    }

    private static float sum(float[] values) {
        float total = 0f;
        for (float value : values) total += value;
        return total;
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
