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
    private final WordTableWidth width;
    private final Alignment alignment;
    private final int cellMarginTopTwips;
    private final int cellMarginEndTwips;
    private final int cellMarginBottomTwips;
    private final int cellMarginStartTwips;
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
        this(
                rows,
                columnWidthsTwips,
                WordTableWidth.AUTO,
                alignment,
                0,
                cellMarginTwips,
                0,
                cellMarginTwips,
                leftBorder,
                topBorder,
                rightBorder,
                bottomBorder,
                insideHorizontalBorder,
                insideVerticalBorder
        );
    }

    public WordTable(
            List<WordTableRow> rows,
            List<Integer> columnWidthsTwips,
            WordTableWidth width,
            Alignment alignment,
            int cellMarginTopTwips,
            int cellMarginEndTwips,
            int cellMarginBottomTwips,
            int cellMarginStartTwips,
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
        this.width = width == null ? WordTableWidth.AUTO : width;
        this.alignment = alignment == null ? Alignment.LEFT : alignment;
        this.cellMarginTopTwips = Math.max(0, cellMarginTopTwips);
        this.cellMarginEndTwips = Math.max(0, cellMarginEndTwips);
        this.cellMarginBottomTwips = Math.max(0, cellMarginBottomTwips);
        this.cellMarginStartTwips = Math.max(0, cellMarginStartTwips);
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
    public WordTableWidth getWidth() { return width; }
    public Alignment getAlignment() { return alignment; }
    public int getCellMarginTwips() {
        return (cellMarginStartTwips + cellMarginEndTwips) / 2;
    }
    public int getCellMarginTopTwips() { return cellMarginTopTwips; }
    public int getCellMarginEndTwips() { return cellMarginEndTwips; }
    public int getCellMarginBottomTwips() { return cellMarginBottomTwips; }
    public int getCellMarginStartTwips() { return cellMarginStartTwips; }
    public WordBorder getLeftBorder() { return leftBorder; }
    public WordBorder getTopBorder() { return topBorder; }
    public WordBorder getRightBorder() { return rightBorder; }
    public WordBorder getBottomBorder() { return bottomBorder; }
    public WordBorder getInsideHorizontalBorder() { return insideHorizontalBorder; }
    public WordBorder getInsideVerticalBorder() { return insideVerticalBorder; }
}
