package com.desperadoboi.imagetopdf.document.word;

public final class WordBorder {
    public enum Style {
        NONE,
        SINGLE,
        DOUBLE,
        DASHED,
        DOTTED,
        THICK
    }

    public static final WordBorder NONE = new WordBorder(Style.NONE, 0xFFD3DAE3, 0);

    private final Style style;
    private final int color;
    private final int sizeEighthPoints;

    public WordBorder(Style style, int color, int sizeEighthPoints) {
        this.style = style == null ? Style.NONE : style;
        this.color = color;
        this.sizeEighthPoints = Math.max(0, sizeEighthPoints);
    }

    public Style getStyle() { return style; }
    public int getColor() { return color; }
    public int getSizeEighthPoints() { return sizeEighthPoints; }
}
