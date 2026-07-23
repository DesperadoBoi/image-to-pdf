package com.desperadoboi.imagetopdf.ui.viewer;

final class SpreadsheetRenderStats {
    int visibleRows;
    int visibleColumns;
    int visibleCells;
    int drawnTextCells;
    int skippedTextCells;
    long textLayoutCacheHits;
    long textLayoutCacheMisses;
    long drawDurationNanos;

    void reset(SpreadsheetRenderPlan plan) {
        visibleRows = plan.getVisibleRows();
        visibleColumns = plan.getVisibleColumns();
        visibleCells = plan.getVisibleCells();
        drawnTextCells = 0;
        skippedTextCells = 0;
        textLayoutCacheHits = 0L;
        textLayoutCacheMisses = 0L;
        drawDurationNanos = 0L;
    }
}
