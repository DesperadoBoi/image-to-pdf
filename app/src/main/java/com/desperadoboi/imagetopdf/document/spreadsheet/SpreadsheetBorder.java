package com.desperadoboi.imagetopdf.document.spreadsheet;

import java.util.Objects;

public final class SpreadsheetBorder {
    public enum Style {
        NONE,
        HAIR,
        THIN,
        MEDIUM,
        THICK,
        DASHED,
        DOTTED,
        DOUBLE
    }

    public static final SpreadsheetBorder NONE = new SpreadsheetBorder(Style.NONE, 0);

    private final Style style;
    private final int color;

    public SpreadsheetBorder(Style style, int color) {
        this.style = Objects.requireNonNull(style);
        this.color = color;
    }

    public Style getStyle() {
        return style;
    }

    public int getColor() {
        return color;
    }
}
