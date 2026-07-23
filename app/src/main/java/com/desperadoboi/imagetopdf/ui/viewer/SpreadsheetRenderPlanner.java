package com.desperadoboi.imagetopdf.ui.viewer;

final class SpreadsheetRenderPlanner {
    private static final int DEFAULT_OVERSCAN_CELLS = 1;

    SpreadsheetRenderPlan plan(
            SpreadsheetGeometry geometry,
            SpreadsheetViewportTransform transform
    ) {
        return plan(geometry, transform, DEFAULT_OVERSCAN_CELLS);
    }

    SpreadsheetRenderPlan plan(
            SpreadsheetGeometry geometry,
            SpreadsheetViewportTransform transform,
            int overscan
    ) {
        SpreadsheetVisibleRange range = geometry.visibleRange(
                transform.getOffsetX(),
                transform.getOffsetY(),
                transform.getVisibleRight(),
                transform.getVisibleBottom(),
                overscan
        );
        int rows = 0;
        int columns = 0;
        if (!range.isEmpty()) {
            for (int row = range.getFirstRow(); row <= range.getLastRow(); row++) {
                if (!geometry.isRowHidden(row)) rows++;
            }
            for (int column = range.getFirstColumn();
                    column <= range.getLastColumn();
                    column++) {
                if (!geometry.isColumnHidden(column)) columns++;
            }
        }
        return new SpreadsheetRenderPlan(
                range,
                SpreadsheetLevelOfDetailPolicy.detailForScale(transform.getScale()),
                rows,
                columns
        );
    }
}
