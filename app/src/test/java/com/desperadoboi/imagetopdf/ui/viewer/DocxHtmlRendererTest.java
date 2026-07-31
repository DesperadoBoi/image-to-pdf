package com.desperadoboi.imagetopdf.ui.viewer;

import com.desperadoboi.imagetopdf.document.word.WordBlock;
import com.desperadoboi.imagetopdf.document.word.WordBorder;
import com.desperadoboi.imagetopdf.document.word.WordDocumentModel;
import com.desperadoboi.imagetopdf.document.word.WordImage;
import com.desperadoboi.imagetopdf.document.word.WordPageBreak;
import com.desperadoboi.imagetopdf.document.word.WordParagraph;
import com.desperadoboi.imagetopdf.document.word.WordParagraphStyle;
import com.desperadoboi.imagetopdf.document.word.WordRun;
import com.desperadoboi.imagetopdf.document.word.WordRunStyle;
import com.desperadoboi.imagetopdf.document.word.WordSectionProperties;
import com.desperadoboi.imagetopdf.document.word.WordTable;
import com.desperadoboi.imagetopdf.document.word.WordTableCell;
import com.desperadoboi.imagetopdf.document.word.WordTableRow;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class DocxHtmlRendererTest {
    @Test
    public void bodyParagraphIsTransparentAndElevenPointsStayElevenPoints() {
        String html = render(Collections.singletonList(paragraph(
                "Text",
                new WordParagraphStyle.Builder().build(),
                runStyle(11f, false, false, false),
                ""
        )));

        assertTrue(html.contains(".word-paragraph,.list-line{background:transparent"));
        assertFalse(html.contains(".word-paragraph{background:#fff"));
        assertTrue(html.contains("font-size:11.00pt"));
        assertFalse(html.contains("bodyLarge"));
        assertFalse(html.contains("border-radius"));
    }

    @Test
    public void cssUsesExactResetAndWhitePagesOnNeutralCanvas() {
        String css = DocxCssBuilder.build();

        assertTrue(css.contains("margin:0;padding:0;background:transparent;"
                + "box-shadow:none;border:0"));
        assertTrue(css.contains("body{background:#e7eaee"));
        assertTrue(css.contains(".docx-page{position:relative;flex:none;background:#fff"));
        assertTrue(css.contains("box-sizing:border-box;overflow:hidden"));
        assertTrue(css.contains("-webkit-text-size-adjust:100%"));
    }

    @Test
    public void sectionPageSizeMarginsAndLandscapeOrientationBecomePageCss() {
        WordSectionProperties landscape = new WordSectionProperties(
                15_840,
                12_240,
                720,
                1_080,
                1_440,
                1_800,
                Collections.emptyList(),
                Collections.emptyList()
        );
        String html = renderer(image -> null).render(new WordDocumentModel(
                Collections.singletonList(paragraph("Landscape")),
                Collections.singletonList(landscape),
                1,
                0,
                0
        )).getHtml();

        assertTrue(html.contains("data-orientation=\"landscape\""));
        assertTrue(html.contains("width:792.00pt;height:612.00pt"));
        assertTrue(html.contains("padding:36.00pt 54.00pt 72.00pt 90.00pt"));
    }

    @Test
    public void explicitBreakAdvancesToNextSectionPageGeometry() {
        WordSectionProperties portrait = new WordSectionProperties(
                12_240,
                15_840,
                1_440,
                1_440,
                1_440,
                1_440,
                Collections.emptyList(),
                Collections.emptyList()
        );
        WordSectionProperties landscape = new WordSectionProperties(
                15_840,
                12_240,
                720,
                720,
                720,
                720,
                Collections.emptyList(),
                Collections.emptyList()
        );
        String html = renderer(image -> null).render(new WordDocumentModel(
                Arrays.asList(paragraph("One"), new WordPageBreak(), paragraph("Two")),
                Arrays.asList(portrait, landscape),
                2,
                0,
                0
        )).getHtml();

        assertTrue(html.contains("data-orientation=\"portrait\""));
        assertTrue(html.contains("data-orientation=\"landscape\""));
        assertTrue(html.contains("width:612.00pt;height:792.00pt"));
        assertTrue(html.contains("width:792.00pt;height:612.00pt"));
    }

    @Test
    public void runAndParagraphFormattingIsGeneratedFromResolvedWordStyles() {
        WordParagraphStyle paragraphStyle = new WordParagraphStyle.Builder()
                .setAlignment(WordParagraphStyle.Alignment.JUSTIFY)
                .setSpaceBeforeTwips(120)
                .setSpaceAfterTwips(240)
                .setLeftIndentTwips(720)
                .setRightIndentTwips(360)
                .setFirstLineIndentTwips(240)
                .setLineSpacingValue(360)
                .setLineRule(WordParagraphStyle.LineRule.AUTO)
                .build();
        WordRunStyle style = new WordRunStyle.Builder()
                .setFontSizePoints(14f)
                .setBold(true)
                .setItalic(true)
                .setUnderline(true)
                .setStrike(true)
                .setColor(0xFF123456)
                .setHighlight(0xFFFFFF00)
                .setVerticalPosition(WordRunStyle.VerticalPosition.SUPERSCRIPT)
                .build();
        String html = render(Collections.singletonList(paragraph(
                "Styled",
                paragraphStyle,
                style,
                ""
        )));

        assertTrue(html.contains("text-align:justify"));
        assertTrue(html.contains("margin-top:6.00pt"));
        assertTrue(html.contains("margin-bottom:12.00pt"));
        assertTrue(html.contains("margin-left:36.00pt"));
        assertTrue(html.contains("margin-right:18.00pt"));
        assertTrue(html.contains("text-indent:12.00pt"));
        assertTrue(html.contains("line-height:1.50"));
        assertTrue(html.contains("font-size:14.00pt"));
        assertTrue(html.contains("font-weight:700"));
        assertTrue(html.contains("font-style:italic"));
        assertTrue(html.contains("text-decoration-line:underline line-through"));
        assertTrue(html.contains("color:#123456"));
        assertTrue(html.contains("background-color:#FFFF00"));
        assertTrue(html.contains("vertical-align:super"));
    }

    @Test
    public void preservedSpacesTabsBreaksAndMaliciousTextRemainEscaped() {
        String html = render(Collections.singletonList(paragraph(
                "<b>not html</b>\n  A\tB"
        )));

        assertTrue(html.contains("&lt;b&gt;not html&lt;/b&gt;\n  A\tB"));
        assertTrue(html.contains("white-space:pre-wrap"));
        assertTrue(html.contains("tab-size:4"));
        assertFalse(html.contains("<b>not html</b>"));
    }

    @Test
    public void hangingIndentIsNegativeAndDoesNotBecomeAParagraphBackground() {
        String html = render(Collections.singletonList(paragraph(
                "Hanging",
                new WordParagraphStyle.Builder()
                        .setLeftIndentTwips(720)
                        .setHangingIndentTwips(360)
                        .build(),
                WordRunStyle.defaults(),
                ""
        )));

        assertTrue(html.contains("margin-left:36.00pt"));
        assertTrue(html.contains("text-indent:-18.00pt"));
        assertFalse(html.contains("word-story-body\" style=\"background"));
    }

    @Test
    public void explicitPageBreakCreatesASecondPageWithoutVisibleLabel() {
        DocxHtmlRenderer.Result result = renderer(image -> null).render(document(
                Arrays.asList(paragraph("One"), new WordPageBreak(), paragraph("Two"))
        ));

        assertEquals(2, result.getPageCount());
        assertEquals(2, occurrences(result.getHtml(), "class=\"docx-page\""));
        assertFalse(result.getHtml().contains("Разрыв страницы"));
        assertFalse(result.getHtml().contains("Page break"));
    }

    @Test
    public void orderedBulletNestedAndStartListsUseSemanticHtmlAndKeepMarkers() {
        WordParagraph first = paragraph(
                "Third",
                new WordParagraphStyle.Builder().setLeftIndentTwips(720).build(),
                runStyle(11f, false, false, false),
                "3. "
        );
        WordParagraph child = paragraph(
                "Nested",
                new WordParagraphStyle.Builder().setLeftIndentTwips(1_440).build(),
                runStyle(11f, false, false, false),
                "a) "
        );
        WordParagraph bullet = paragraph(
                "Bullet",
                new WordParagraphStyle.Builder().setLeftIndentTwips(720).build(),
                runStyle(11f, false, false, false),
                "• "
        );
        String html = render(Arrays.asList(first, child, bullet));

        assertTrue(html.contains("<ol class=\"word-list\" start=\"3\""));
        assertTrue(html.contains("<li value=\"3\""));
        assertTrue(occurrences(html, "<ol class=\"word-list\"") >= 2);
        assertTrue(html.contains("<ul class=\"word-list\""));
        assertTrue(html.contains("<span class=\"list-marker\">3. </span>"));
        assertTrue(html.contains("<span class=\"list-marker\">a) </span>"));
        assertTrue(html.contains("<span class=\"list-marker\">• </span>"));
    }

    @Test
    public void letterAndRomanMarkersRemainExactOrderedListMarkers() {
        String html = render(Arrays.asList(
                paragraph(
                        "Letter",
                        new WordParagraphStyle.Builder().setLeftIndentTwips(720).build(),
                        WordRunStyle.defaults(),
                        "b) "
                ),
                paragraph(
                        "Roman",
                        new WordParagraphStyle.Builder().setLeftIndentTwips(720).build(),
                        WordRunStyle.defaults(),
                        "IV. "
                )
        ));

        assertTrue(html.contains("<ol class=\"word-list\""));
        assertTrue(html.contains(">b) </span>"));
        assertTrue(html.contains(">IV. </span>"));
    }

    @Test
    public void tableUsesColspanRowspanBordersShadingPaddingAndCellParagraphCss() {
        WordBorder border = new WordBorder(WordBorder.Style.SINGLE, 0xFF123456, 8);
        WordTableCell merged = cell(
                paragraph("Merged", new WordParagraphStyle.Builder()
                        .setSpaceAfterTwips(120)
                        .build(), runStyle(10f, true, false, false), ""),
                2,
                WordTableCell.VerticalMerge.RESTART,
                0xFFD9EAF7,
                border
        );
        WordTableCell right = cell(
                paragraph("Right"),
                1,
                WordTableCell.VerticalMerge.NONE,
                null,
                border
        );
        WordTableCell continued = cell(
                paragraph(""),
                2,
                WordTableCell.VerticalMerge.CONTINUE,
                null,
                border
        );
        WordTableCell lower = cell(
                paragraph("Lower"),
                1,
                WordTableCell.VerticalMerge.NONE,
                null,
                border
        );
        WordTable table = new WordTable(
                Arrays.asList(
                        new WordTableRow(Arrays.asList(merged, right), 0),
                        new WordTableRow(Arrays.asList(continued, lower), 0)
                ),
                Arrays.asList(1_440, 1_440, 1_440),
                WordTable.Alignment.LEFT,
                100,
                border,
                border,
                border,
                border,
                border,
                border
        );
        String html = render(Collections.singletonList(table));

        assertTrue(html.contains("<table class=\"word-table\""));
        assertTrue(html.contains("border-collapse:collapse"));
        assertTrue(html.contains("colspan=\"2\""));
        assertTrue(html.contains("rowspan=\"2\""));
        assertTrue(html.contains("border-left:1.00pt solid #123456"));
        assertTrue(html.contains("background-color:#D9EAF7"));
        assertTrue(html.contains("padding:5.00pt 5.00pt 5.00pt 5.00pt"));
        assertTrue(html.contains("margin-bottom:6.00pt"));
        assertTrue(html.contains("font-size:10.00pt"));
    }

    @Test
    public void rasterImageUsesOnlySafeDataUriAndWordEmuDimensions() {
        WordImage image = new WordImage(
                "rId1",
                "word/media/image.png",
                914_400,
                457_200,
                "A <safe> image",
                false
        );
        String html = renderer(ignored -> "data:image/png;base64,AAAA")
                .render(document(Collections.singletonList(image)))
                .getHtml();

        assertTrue(html.contains("src=\"data:image/png;base64,AAAA\""));
        assertTrue(html.contains("width:72.00pt"));
        assertTrue(html.contains("width=\"72.00\" height=\"36.00\""));
        assertTrue(html.contains("alt=\"A &lt;safe&gt; image\""));
        assertFalse(html.contains("src=\"http"));
        assertFalse(html.contains("src=\"file:"));
        assertFalse(html.contains("src=\"content:"));
    }

    @Test
    public void unsupportedOrUntrustedImageGetsLocalPlaceholder() {
        WordImage vector = new WordImage(
                "rId2",
                "word/media/drawing.svg",
                0,
                0,
                "",
                true
        );
        String html = renderer(ignored -> "https://evil.example/image.png")
                .render(document(Collections.singletonList(vector)))
                .getHtml();

        assertTrue(html.contains("class=\"image-placeholder\""));
        assertTrue(html.contains("Vector unavailable"));
        assertFalse(html.contains("evil.example"));
    }

    @Test
    public void onlyEscapedHttpsHyperlinksAreEmitted() {
        WordRun safe = new WordRun(
                "safe",
                WordRunStyle.defaults(),
                "https://example.com/path?a=1&b=2"
        );
        WordRun unsafe = new WordRun(
                "unsafe",
                WordRunStyle.defaults(),
                "http://example.com/"
        );
        WordParagraph paragraph = new WordParagraph(
                Arrays.asList(safe, unsafe),
                WordParagraphStyle.defaults(),
                "",
                WordParagraph.Role.BODY
        );
        String html = render(Collections.singletonList(paragraph));

        assertTrue(html.contains("href=\"https://example.com/path?a=1&amp;b=2\""));
        assertFalse(html.contains("href=\"http://"));
        assertTrue(html.contains(">unsafe</span>"));
    }

    private static String render(List<WordBlock> blocks) {
        return renderer(image -> null).render(document(blocks)).getHtml();
    }

    private static DocxHtmlRenderer renderer(DocxImageSource imageSource) {
        return new DocxHtmlRenderer(
                imageSource,
                new DocxHtmlRenderer.Labels(
                        "Document image",
                        "Vector unavailable",
                        "Image unavailable"
                )
        );
    }

    private static WordDocumentModel document(List<WordBlock> blocks) {
        int paragraphs = 0;
        int tables = 0;
        int images = 0;
        for (WordBlock block : blocks) {
            if (block instanceof WordParagraph) paragraphs++;
            if (block instanceof WordTable) tables++;
            if (block instanceof WordImage) images++;
        }
        return new WordDocumentModel(
                blocks,
                Collections.emptyList(),
                paragraphs,
                tables,
                images
        );
    }

    private static WordParagraph paragraph(String text) {
        return paragraph(
                text,
                WordParagraphStyle.defaults(),
                WordRunStyle.defaults(),
                ""
        );
    }

    private static WordParagraph paragraph(
            String text,
            WordParagraphStyle paragraphStyle,
            WordRunStyle runStyle,
            String marker
    ) {
        return new WordParagraph(
                Collections.singletonList(new WordRun(text, runStyle, null)),
                paragraphStyle,
                runStyle,
                marker,
                WordParagraph.Role.BODY
        );
    }

    private static WordRunStyle runStyle(
            float points,
            boolean bold,
            boolean italic,
            boolean underline
    ) {
        return new WordRunStyle.Builder()
                .setFontSizePoints(points)
                .setBold(bold)
                .setItalic(italic)
                .setUnderline(underline)
                .build();
    }

    private static WordTableCell cell(
            WordParagraph paragraph,
            int span,
            WordTableCell.VerticalMerge merge,
            Integer shading,
            WordBorder border
    ) {
        return new WordTableCell(
                Collections.singletonList(paragraph),
                0,
                span,
                merge,
                WordTableCell.VerticalAlignment.TOP,
                shading,
                border,
                border,
                border,
                border
        );
    }

    private static int occurrences(String value, String needle) {
        int count = 0;
        int index = 0;
        while ((index = value.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
