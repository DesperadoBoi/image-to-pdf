package com.desperadoboi.imagetopdf.ui.watermark;

public enum WatermarkPosition {
    TOP_LEFT(0, 0),
    TOP_CENTER(1, 0),
    TOP_RIGHT(2, 0),
    CENTER_LEFT(0, 1),
    CENTER(1, 1),
    CENTER_RIGHT(2, 1),
    BOTTOM_LEFT(0, 2),
    BOTTOM_CENTER(1, 2),
    BOTTOM_RIGHT(2, 2);

    private final int column;
    private final int row;

    WatermarkPosition(int column, int row) {
        this.column = column;
        this.row = row;
    }

    public int getColumn() {
        return column;
    }

    public int getRow() {
        return row;
    }
}
