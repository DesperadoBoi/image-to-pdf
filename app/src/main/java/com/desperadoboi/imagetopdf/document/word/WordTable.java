package com.desperadoboi.imagetopdf.document.word;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class WordTable extends WordBlock {
    public enum Alignment {
        LEFT,
        CENTER,
        RIGHT
    }

    private final List<WordTableRow> rows;
    private final List<Integer> columnWidthsTwips;
    private final Alignment alignment;
    private final int cellMarginTwips;
    private final WordBorder leftBorder;
    private final WordBorder topBorder;
    private final WordBorder rightBorder;
    private final WordBorder bottomBorder;
    private final WordBorder insideHorizontalBorder;
    private final WordBorder insideVerticalBorder;

    public WordTable(
            List<WordTableRow> rows,
            List<Integer> columnWidthsTwips,
            Alignment alignment,
            int cellMarginTwips,
            WordBorder leftBorder,
            WordBorder topBorder,
            WordBorder rightBorder,
            WordBorder bottomBorder,
            WordBorder insideHorizontalBorder,
            WordBorder insideVerticalBorder
    ) {
        super(Type.TABLE);
        this.rows = Collections.unmodifiableList(new ArrayList<>(rows));
        this.columnWidthsTwips = Collections.unmodifiableList(
                new ArrayList<>(columnWidthsTwips)
        );
        this.alignment = alignment == null ? Alignment.LEFT : alignment;
        this.cellMarginTwips = Math.max(0, cellMarginTwips);
        this.leftBorder = leftBorder == null ? WordBorder.NONE : leftBorder;
        this.topBorder = topBorder == null ? WordBorder.NONE : topBorder;
        this.rightBorder = rightBorder == null ? WordBorder.NONE : rightBorder;
        this.bottomBorder = bottomBorder == null ? WordBorder.NONE : bottomBorder;
        this.insideHorizontalBorder = insideHorizontalBorder == null
                ? WordBorder.NONE
                : insideHorizontalBorder;
        this.insideVerticalBorder = insideVerticalBorder == null
                ? WordBorder.NONE
                : insideVerticalBorder;
    }

    public List<WordTableRow> getRows() { return rows; }
    public List<Integer> getColumnWidthsTwips() { return columnWidthsTwips; }
    public Alignment getAlignment() { return alignment; }
    public int getCellMarginTwips() { return cellMarginTwips; }
    public WordBorder getLeftBorder() { return leftBorder; }
    public WordBorder getTopBorder() { return topBorder; }
    public WordBorder getRightBorder() { return rightBorder; }
    public WordBorder getBottomBorder() { return bottomBorder; }
    public WordBorder getInsideHorizontalBorder() { return insideHorizontalBorder; }
    public WordBorder getInsideVerticalBorder() { return insideVerticalBorder; }
}
