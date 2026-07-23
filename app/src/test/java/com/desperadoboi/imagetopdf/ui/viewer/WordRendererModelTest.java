package com.desperadoboi.imagetopdf.ui.viewer;

import com.desperadoboi.imagetopdf.document.word.WordBlock;
import com.desperadoboi.imagetopdf.document.word.WordBorder;
import com.desperadoboi.imagetopdf.document.word.WordPageBreak;
import com.desperadoboi.imagetopdf.document.word.WordParagraph;
import com.desperadoboi.imagetopdf.document.word.WordParagraphStyle;
import com.desperadoboi.imagetopdf.document.word.WordRun;
import com.desperadoboi.imagetopdf.document.word.WordRunStyle;
import com.desperadoboi.imagetopdf.document.word.WordTable;
import com.desperadoboi.imagetopdf.document.word.WordTableCell;
import com.desperadoboi.imagetopdf.document.word.WordTableRow;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class WordRendererModelTest {
    @Test
    public void tableGeometryMapsGridSpanAndVerticalMergeWithoutCellViews() {
        WordTableCell merged = cell("merged", 2, WordTableCell.VerticalMerge.RESTART);
        WordTableCell right = cell("right", 1, WordTableCell.VerticalMerge.NONE);
        WordTableCell continued = cell("", 2, WordTableCell.VerticalMerge.CONTINUE);
        WordTableCell lowerRight = cell("lower", 1, WordTableCell.VerticalMerge.NONE);
        WordTable table = new WordTable(
                Arrays.asList(
                        new WordTableRow(Arrays.asList(merged, right), 0),
                        new WordTableRow(Arrays.asList(continued, lowerRight), 0)
                ),
                Arrays.asList(1_440, 1_440, 1_440),
                WordTable.Alignment.LEFT,
                80,
                WordBorder.NONE,
                WordBorder.NONE,
                WordBorder.NONE,
                WordBorder.NONE,
                WordBorder.NONE,
                WordBorder.NONE
        );

        WordTableGeometry geometry = WordTableGeometry.create(table, 1f);
        assertEquals(3, geometry.getColumnCount());
        assertEquals(2, geometry.getRowCount());
        WordTableGeometry.CellPlacement first = geometry.getPlacements(0).get(0);
        assertEquals(0, first.getFirstColumn());
        assertEquals(1, first.getLastColumn());
        assertEquals(0, first.getFirstRow());
        assertEquals(1, first.getLastRow());
        assertSame(first, geometry.getPlacements(1).get(0));
        assertEquals(0, geometry.firstVisibleRow(0f));
        assertTrue(geometry.lastVisibleRow(geometry.getHeight()) >= 1);
    }

    @Test
    public void imageSizingPreservesAspectRatioAndBounds() {
        assertEquals(200, WordImageSizeCalculator.calculateHeight(
                2_000,
                1_000,
                400,
                80,
                600
        ));
        assertEquals(600, WordImageSizeCalculator.calculateHeight(
                1,
                100,
                400,
                80,
                600
        ));
        assertEquals(300, WordImageSizeCalculator.calculateHeight(
                0,
                0,
                400,
                80,
                600
        ));
    }

    @Test
    public void restoredPositionClampsBlockAndKeepsPixelOffset() {
        WordViewerPosition position = new WordViewerPosition(50, -24)
                .clampToBlockCount(12);
        assertEquals(11, position.getBlockPosition());
        assertEquals(-24, position.getOffsetPixels());
        assertEquals(
                0,
                new WordViewerPosition(-3, 7)
                        .clampToBlockCount(0)
                        .getBlockPosition()
        );
    }

    @Test
    public void paragraphAndPageBreakRemainSeparateBlocks() {
        WordParagraph paragraph = paragraph("one");
        WordBlock pageBreak = new WordPageBreak();
        WordParagraph after = paragraph("two");
        assertEquals(WordBlock.Type.PARAGRAPH, paragraph.getType());
        assertEquals(WordBlock.Type.PAGE_BREAK, pageBreak.getType());
        assertEquals(WordBlock.Type.PARAGRAPH, after.getType());
    }

    private static WordTableCell cell(
            String value,
            int span,
            WordTableCell.VerticalMerge verticalMerge
    ) {
        return new WordTableCell(
                Collections.singletonList(paragraph(value)),
                0,
                span,
                verticalMerge,
                WordTableCell.VerticalAlignment.TOP,
                null,
                WordBorder.NONE,
                WordBorder.NONE,
                WordBorder.NONE,
                WordBorder.NONE
        );
    }

    private static WordParagraph paragraph(String value) {
        return new WordParagraph(
                Collections.singletonList(new WordRun(
                        value,
                        WordRunStyle.defaults(),
                        null
                )),
                WordParagraphStyle.defaults(),
                "",
                WordParagraph.Role.BODY
        );
    }
}
