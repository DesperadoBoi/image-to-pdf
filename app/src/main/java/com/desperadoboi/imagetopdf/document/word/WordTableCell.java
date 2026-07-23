package com.desperadoboi.imagetopdf.document.word;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class WordTableCell {
    public enum VerticalMerge {
        NONE,
        RESTART,
        CONTINUE
    }

    public enum VerticalAlignment {
        TOP,
        CENTER,
        BOTTOM
    }

    private final List<WordBlock> blocks;
    private final int widthTwips;
    private final int gridSpan;
    private final VerticalMerge verticalMerge;
    private final VerticalAlignment verticalAlignment;
    private final Integer shadingColor;
    private final WordBorder leftBorder;
    private final WordBorder topBorder;
    private final WordBorder rightBorder;
    private final WordBorder bottomBorder;

    public WordTableCell(
            List<WordBlock> blocks,
            int widthTwips,
            int gridSpan,
            VerticalMerge verticalMerge,
            VerticalAlignment verticalAlignment,
            Integer shadingColor,
            WordBorder leftBorder,
            WordBorder topBorder,
            WordBorder rightBorder,
            WordBorder bottomBorder
    ) {
        this.blocks = Collections.unmodifiableList(new ArrayList<>(blocks));
        this.widthTwips = Math.max(0, widthTwips);
        this.gridSpan = Math.max(1, gridSpan);
        this.verticalMerge = verticalMerge == null ? VerticalMerge.NONE : verticalMerge;
        this.verticalAlignment = verticalAlignment == null
                ? VerticalAlignment.TOP
                : verticalAlignment;
        this.shadingColor = shadingColor;
        this.leftBorder = leftBorder == null ? WordBorder.NONE : leftBorder;
        this.topBorder = topBorder == null ? WordBorder.NONE : topBorder;
        this.rightBorder = rightBorder == null ? WordBorder.NONE : rightBorder;
        this.bottomBorder = bottomBorder == null ? WordBorder.NONE : bottomBorder;
    }

    public List<WordBlock> getBlocks() { return blocks; }
    public int getWidthTwips() { return widthTwips; }
    public int getGridSpan() { return gridSpan; }
    public VerticalMerge getVerticalMerge() { return verticalMerge; }
    public VerticalAlignment getVerticalAlignment() { return verticalAlignment; }
    public Integer getShadingColor() { return shadingColor; }
    public WordBorder getLeftBorder() { return leftBorder; }
    public WordBorder getTopBorder() { return topBorder; }
    public WordBorder getRightBorder() { return rightBorder; }
    public WordBorder getBottomBorder() { return bottomBorder; }

    public String getPlainText() {
        StringBuilder value = new StringBuilder();
        for (WordBlock block : blocks) {
            if (value.length() > 0) value.append('\n');
            if (block instanceof WordParagraph) {
                value.append(((WordParagraph) block).getPlainText());
            } else if (block instanceof WordTable) {
                value.append('\u25A6');
            }
        }
        return value.toString();
    }
}
