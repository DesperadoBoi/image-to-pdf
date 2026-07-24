package com.desperadoboi.imagetopdf.ui.viewer;

import androidx.annotation.Nullable;

import com.desperadoboi.imagetopdf.document.word.WordBlock;
import com.desperadoboi.imagetopdf.document.word.WordBorder;
import com.desperadoboi.imagetopdf.document.word.WordDocumentModel;
import com.desperadoboi.imagetopdf.document.word.WordImage;
import com.desperadoboi.imagetopdf.document.word.WordParagraph;
import com.desperadoboi.imagetopdf.document.word.WordParagraphStyle;
import com.desperadoboi.imagetopdf.document.word.WordRun;
import com.desperadoboi.imagetopdf.document.word.WordRunStyle;
import com.desperadoboi.imagetopdf.document.word.WordSectionProperties;
import com.desperadoboi.imagetopdf.document.word.WordTable;
import com.desperadoboi.imagetopdf.document.word.WordTableCell;
import com.desperadoboi.imagetopdf.document.word.WordTableRow;
import com.desperadoboi.imagetopdf.document.word.WordTableWidth;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

final class DocxHtmlRenderer {
    private static final double EMU_PER_POINT = 12_700d;
    private static final double DEFAULT_IMAGE_POINTS = 144d;
    private static final double DEFAULT_TABLE_CELL_PADDING_POINTS = 5d;

    private final DocxImageSource imageSource;
    private final Labels labels;

    DocxHtmlRenderer(DocxImageSource imageSource, Labels labels) {
        this.imageSource = imageSource;
        this.labels = labels == null ? Labels.empty() : labels;
    }

    Result render(WordDocumentModel document) {
        if (document == null) throw new IllegalArgumentException("document");
        List<Page> pages = paginate(document);
        StringBuilder html = new StringBuilder(estimateCapacity(document));
        html.append("<!doctype html><html><head><meta charset=\"utf-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width,")
                .append("initial-scale=1,minimum-scale=0.25,maximum-scale=5,")
                .append("user-scalable=yes\">")
                .append("<meta http-equiv=\"Content-Security-Policy\" content=\"")
                .append("default-src 'none'; img-src data:; style-src 'unsafe-inline'; ")
                .append("script-src 'none'; connect-src 'none'; font-src 'none'; ")
                .append("media-src 'none'; object-src 'none'; frame-src 'none'; ")
                .append("base-uri 'none'; form-action 'none'\">")
                .append("<style>")
                .append(DocxCssBuilder.build())
                .append("</style></head><body><main class=\"docx-document\">");
        for (int index = 0; index < pages.size(); index++) {
            Page page = pages.get(index);
            html.append("<section class=\"docx-page\" data-page=\"")
                    .append(index + 1)
                    .append("\" data-orientation=\"")
                    .append(page.style.orientation())
                    .append("\" style=\"")
                    .append(page.style.inlineCss())
                    .append("\"><div class=\"docx-page-content\">");
            renderPage(page, html);
            html.append("</div></section>");
        }
        html.append("</main></body></html>");
        return new Result(html.toString(), pages.size());
    }

    private int estimateCapacity(WordDocumentModel document) {
        long estimated = 8_192L + document.getParagraphCount() * 160L
                + document.getTableCount() * 512L
                + document.getImageCount() * 256L;
        return (int) Math.min(2_000_000L, estimated);
    }

    private List<Page> paginate(WordDocumentModel document) {
        List<WordSectionProperties> sections = document.getSections();
        int sectionIndex = 0;
        DocxPageStyle style = pageStyle(sections, sectionIndex);
        List<Page> pages = new ArrayList<>();
        Page page = new Page(style);
        List<WordBlock> blocks = document.getBlocks();
        for (int index = 0; index < blocks.size(); index++) {
            WordBlock block = blocks.get(index);
            if (block.getType() == WordBlock.Type.PAGE_BREAK) {
                if (!page.pieces.isEmpty()) {
                    pages.add(page);
                    if (sectionIndex + 1 < sections.size()) sectionIndex++;
                    style = pageStyle(sections, sectionIndex);
                    page = new Page(style);
                }
                continue;
            }
            List<Piece> pieces = splitIntoPieces(block, style);
            for (Piece piece : pieces) {
                double height = estimatedHeight(piece, style);
                double keepWithNext = 0d;
                if (isHeading(piece) && index + 1 < blocks.size()) {
                    WordBlock next = blocks.get(index + 1);
                    if (next.getType() != WordBlock.Type.PAGE_BREAK) {
                        keepWithNext = Math.min(
                                style.contentHeightPoints() * 0.35d,
                                estimatedHeight(new Piece(next), style)
                        );
                    }
                }
                if (!page.pieces.isEmpty()
                        && page.usedPoints + height + keepWithNext
                        > style.contentHeightPoints()) {
                    pages.add(page);
                    page = new Page(style);
                }
                page.pieces.add(piece);
                page.usedPoints += height;
            }
        }
        if (!page.pieces.isEmpty() || pages.isEmpty()) pages.add(page);
        return pages;
    }

    private DocxPageStyle pageStyle(List<WordSectionProperties> sections, int index) {
        if (sections == null || sections.isEmpty()) return DocxPageStyle.from(null);
        return DocxPageStyle.from(sections.get(Math.max(0, Math.min(index, sections.size() - 1))));
    }

    private List<Piece> splitIntoPieces(WordBlock block, DocxPageStyle style) {
        if (!(block instanceof WordTable)) {
            return Collections.singletonList(new Piece(block));
        }
        WordTable table = (WordTable) block;
        if (table.getRows().isEmpty()) return Collections.singletonList(new Piece(block));
        double fullHeight = estimatedTableHeight(table, 0, table.getRows().size(), style);
        if (fullHeight <= style.contentHeightPoints()) {
            return Collections.singletonList(new Piece(table, 0, table.getRows().size()));
        }
        List<Piece> pieces = new ArrayList<>();
        int start = 0;
        while (start < table.getRows().size()) {
            int end = start;
            double used = 0d;
            while (end < table.getRows().size()) {
                double rowHeight = estimatedTableRowHeight(table, end, style);
                if (end > start && used + rowHeight > style.contentHeightPoints()) break;
                used += rowHeight;
                end++;
            }
            if (end == start) end++;
            pieces.add(new Piece(table, start, end));
            start = end;
        }
        return pieces;
    }

    private double estimatedHeight(Piece piece, DocxPageStyle style) {
        WordBlock block = piece.block;
        if (block instanceof WordParagraph) {
            return estimatedParagraphHeight((WordParagraph) block, style.contentWidthPoints());
        }
        if (block instanceof WordImage) {
            return estimatedImageHeight((WordImage) block, style.contentWidthPoints()) + 4d;
        }
        if (block instanceof WordTable) {
            WordTable table = (WordTable) block;
            int end = piece.rowEnd < 0 ? table.getRows().size() : piece.rowEnd;
            return estimatedTableHeight(table, Math.max(0, piece.rowStart), end, style);
        }
        return 0d;
    }

    private double estimatedParagraphHeight(WordParagraph paragraph, double availableWidth) {
        WordParagraphStyle style = paragraph.getStyle();
        double left = Math.max(0d, DocxPageStyle.twips(style.getLeftIndentTwips()));
        double right = Math.max(0d, DocxPageStyle.twips(style.getRightIndentTwips()));
        double lineWidth = Math.max(36d, availableWidth - left - right);
        float largestFont = paragraph.getDefaultRunStyle().getFontSizePoints();
        double textUnits = visualTextUnits(paragraph.getListMarker());
        int explicitLines = 1;
        for (WordRun run : paragraph.getRuns()) {
            largestFont = Math.max(largestFont, run.getStyle().getFontSizePoints());
            textUnits += visualTextUnits(run.getText());
            explicitLines += count(run.getText(), '\n');
        }
        double averageCharacterWidth = Math.max(2d, largestFont * 0.52d);
        int wrappedLines = Math.max(
                explicitLines,
                (int) Math.ceil(textUnits * averageCharacterWidth / lineWidth)
        );
        double lineHeight = lineHeightPoints(style, largestFont);
        return Math.max(lineHeight, wrappedLines * lineHeight)
                + spacingBefore(style) + spacingAfter(style);
    }

    private double lineHeightPoints(WordParagraphStyle style, float fontPoints) {
        int value = style.getLineSpacingValue();
        switch (style.getLineRule()) {
            case EXACT:
                return value > 0 ? Math.max(fontPoints, DocxPageStyle.twips(value)) : fontPoints * 1.15d;
            case AT_LEAST:
                return value > 0
                        ? Math.max(fontPoints * 1.15d, DocxPageStyle.twips(value))
                        : fontPoints * 1.15d;
            case AUTO:
            default:
                return fontPoints * (value > 0 ? Math.max(0.5d, value / 240d) : 1.15d);
        }
    }

    private double visualTextUnits(String value) {
        if (value == null || value.isEmpty()) return 0d;
        double units = 0d;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '\n') continue;
            if (character == '\t') {
                units += 4d;
            } else if (character >= 0x2E80) {
                units += 1.8d;
            } else {
                units += 1d;
            }
        }
        return units;
    }

    private int count(String value, char target) {
        if (value == null) return 0;
        int result = 0;
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) == target) result++;
        }
        return result;
    }

    private double estimatedImageHeight(WordImage image, double availableWidth) {
        double width = image.getWidthEmu() > 0L
                ? image.getWidthEmu() / EMU_PER_POINT
                : DEFAULT_IMAGE_POINTS;
        double height = image.getHeightEmu() > 0L
                ? image.getHeightEmu() / EMU_PER_POINT
                : DEFAULT_IMAGE_POINTS;
        if (width > availableWidth && width > 0d) height *= availableWidth / width;
        return Math.max(24d, height);
    }

    private double estimatedTableHeight(
            WordTable table,
            int start,
            int end,
            DocxPageStyle style
    ) {
        double height = 0d;
        int safeEnd = Math.min(end, table.getRows().size());
        for (int row = Math.max(0, start); row < safeEnd; row++) {
            height += estimatedTableRowHeight(table, row, style);
        }
        return Math.max(18d, height);
    }

    private double estimatedTableRowHeight(
            WordTable table,
            int rowIndex,
            DocxPageStyle style
    ) {
        WordTableRow row = table.getRows().get(rowIndex);
        double cellWidth = style.contentWidthPoints()
                / Math.max(1, columnCount(table));
        double height = DocxPageStyle.twips(row.getHeightTwips());
        for (WordTableCell cell : row.getCells()) {
            double cellHeight = table.getCellMarginTopTwips() / 20d
                    + table.getCellMarginBottomTwips() / 20d;
            for (WordBlock block : cell.getBlocks()) {
                if (block instanceof WordParagraph) {
                    cellHeight += estimatedParagraphHeight(
                            (WordParagraph) block,
                            cellWidth * cell.getGridSpan()
                    );
                } else if (block instanceof WordImage) {
                    cellHeight += estimatedImageHeight(
                            (WordImage) block,
                            cellWidth * cell.getGridSpan()
                    );
                } else if (block instanceof WordTable) {
                    cellHeight += estimatedTableHeight(
                            (WordTable) block,
                            0,
                            ((WordTable) block).getRows().size(),
                            style
                    );
                }
            }
            height = Math.max(height, cellHeight);
        }
        return Math.max(18d, height);
    }

    private boolean isHeading(Piece piece) {
        return piece.block instanceof WordParagraph
                && ((WordParagraph) piece.block).getStyle().getHeadingLevel() > 0;
    }

    private void renderPage(Page page, StringBuilder html) {
        int index = 0;
        while (index < page.pieces.size()) {
            Piece piece = page.pieces.get(index);
            if (piece.block instanceof WordParagraph
                    && !((WordParagraph) piece.block).getListMarker().isEmpty()) {
                List<WordParagraph> paragraphs = new ArrayList<>();
                while (index < page.pieces.size()) {
                    Piece listPiece = page.pieces.get(index);
                    if (!(listPiece.block instanceof WordParagraph)) break;
                    WordParagraph paragraph = (WordParagraph) listPiece.block;
                    if (paragraph.getListMarker().isEmpty()) break;
                    paragraphs.add(paragraph);
                    index++;
                }
                renderList(paragraphs, html);
                continue;
            }
            renderPiece(piece, html);
            index++;
        }
    }

    private void renderPiece(Piece piece, StringBuilder html) {
        if (piece.block instanceof WordParagraph) {
            renderParagraph((WordParagraph) piece.block, html);
        } else if (piece.block instanceof WordTable) {
            WordTable table = (WordTable) piece.block;
            renderTable(
                    table,
                    Math.max(0, piece.rowStart),
                    piece.rowEnd < 0 ? table.getRows().size() : piece.rowEnd,
                    html
            );
        } else if (piece.block instanceof WordImage) {
            renderImage((WordImage) piece.block, html);
        }
    }

    private void renderParagraph(WordParagraph paragraph, StringBuilder html) {
        int heading = Math.max(0, Math.min(6, paragraph.getStyle().getHeadingLevel()));
        String tag = heading > 0 ? "h" + heading : "p";
        html.append('<').append(tag).append(" class=\"word-paragraph ")
                .append(roleClass(paragraph));
        if (heading > 0) html.append(" word-heading");
        if (paragraph.getStyle().isKeepTogether()) html.append(" word-keep");
        html.append("\" style=\"")
                .append(paragraphCss(paragraph, true))
                .append("\">");
        renderRuns(paragraph, html);
        html.append("</").append(tag).append('>');
    }

    private String roleClass(WordParagraph paragraph) {
        switch (paragraph.getRole()) {
            case HEADER:
                return "word-story-header";
            case FOOTER:
                return "word-story-footer";
            case FOOTNOTE:
                return "word-story-footnote";
            case ENDNOTE:
                return "word-story-endnote";
            case BODY:
            default:
                return "word-story-body";
        }
    }

    private String paragraphCss(WordParagraph paragraph, boolean includeIndent) {
        WordParagraphStyle style = paragraph.getStyle();
        StringBuilder css = new StringBuilder();
        appendDefaultRunCss(css, paragraph.getDefaultRunStyle());
        css.append("text-align:").append(alignment(style)).append(';')
                .append("margin-top:").append(DocxPageStyle.points(spacingBefore(style))).append(';')
                .append("margin-bottom:").append(DocxPageStyle.points(spacingAfter(style))).append(';')
                .append("line-height:").append(lineHeightCss(style)).append(';');
        if (includeIndent) {
            if (style.hasLeftIndent()) {
                css.append("margin-left:")
                        .append(DocxPageStyle.points(DocxPageStyle.twips(
                                style.getLeftIndentTwips()
                        )))
                        .append(';');
            } else if (style.getStartIndentCharacters() != 0) {
                css.append("margin-left:")
                        .append(format(style.getStartIndentCharacters() / 100d))
                        .append("ch;");
            }
            if (style.hasRightIndent()) {
                css.append("margin-right:")
                        .append(DocxPageStyle.points(DocxPageStyle.twips(
                                style.getRightIndentTwips()
                        )))
                        .append(';');
            } else if (style.getEndIndentCharacters() != 0) {
                css.append("margin-right:")
                        .append(format(style.getEndIndentCharacters() / 100d))
                        .append("ch;");
            }
            if (style.hasFirstLineIndent()) {
                css.append("text-indent:")
                        .append(DocxPageStyle.points(DocxPageStyle.twips(
                                style.getFirstLineIndentTwips()
                        )))
                        .append(';');
            } else if (style.hasHangingIndent()) {
                css.append("text-indent:-")
                        .append(DocxPageStyle.points(DocxPageStyle.twips(
                                style.getHangingIndentTwips()
                        )))
                        .append(';');
            }
        }
        if (style.isBidirectional()) css.append("direction:rtl;");
        return css.toString();
    }

    private double spacingBefore(WordParagraphStyle style) {
        if (style.isBeforeAutoSpacing() && style.getSpaceBeforeTwips() <= 0) return 5d;
        return Math.max(0d, DocxPageStyle.twips(style.getSpaceBeforeTwips()));
    }

    private double spacingAfter(WordParagraphStyle style) {
        if (style.isAfterAutoSpacing() && style.getSpaceAfterTwips() <= 0) return 5d;
        return Math.max(0d, DocxPageStyle.twips(style.getSpaceAfterTwips()));
    }

    private String lineHeightCss(WordParagraphStyle style) {
        int value = style.getLineSpacingValue();
        if (style.getLineRule() == WordParagraphStyle.LineRule.AUTO) {
            return format(value > 0 ? Math.max(0.5d, value / 240d) : 1.15d);
        }
        return DocxPageStyle.points(value > 0 ? DocxPageStyle.twips(value) : 12.65d);
    }

    private String alignment(WordParagraphStyle style) {
        switch (style.getAlignment()) {
            case CENTER:
                return "center";
            case RIGHT:
                return "right";
            case JUSTIFY:
                return "justify";
            case LEFT:
            default:
                return "left";
        }
    }

    private void renderRuns(WordParagraph paragraph, StringBuilder html) {
        if (!paragraph.getListMarker().isEmpty()) {
            html.append("<span class=\"list-marker\">")
                    .append(DocxHtmlEscaper.text(paragraph.getListMarker()))
                    .append("</span>");
        }
        for (WordRun run : paragraph.getRuns()) {
            if (run.getStyle().isHidden()) continue;
            String safeHyperlink = safeHttps(run.getHyperlink());
            if (safeHyperlink != null) {
                html.append("<a href=\"")
                        .append(DocxHtmlEscaper.attribute(safeHyperlink))
                        .append("\" rel=\"external nofollow\" style=\"")
                        .append(runCss(run.getStyle()))
                        .append("\">")
                        .append(DocxHtmlEscaper.text(run.getText()))
                        .append("</a>");
            } else {
                html.append("<span style=\"")
                        .append(runCss(run.getStyle()))
                        .append("\">")
                        .append(DocxHtmlEscaper.text(run.getText()))
                        .append("</span>");
            }
        }
    }

    private void renderRunsWithoutMarker(WordParagraph paragraph, StringBuilder html) {
        for (WordRun run : paragraph.getRuns()) {
            if (run.getStyle().isHidden()) continue;
            String safeHyperlink = safeHttps(run.getHyperlink());
            String tag = safeHyperlink == null ? "span" : "a";
            html.append('<').append(tag);
            if (safeHyperlink != null) {
                html.append(" href=\"")
                        .append(DocxHtmlEscaper.attribute(safeHyperlink))
                        .append("\" rel=\"external nofollow\"");
            }
            html.append(" style=\"").append(runCss(run.getStyle())).append("\">")
                    .append(DocxHtmlEscaper.text(run.getText()))
                    .append("</").append(tag).append('>');
        }
    }

    private void appendDefaultRunCss(StringBuilder css, WordRunStyle style) {
        css.append("font-family:").append(style.getFontFamily()).append(';')
                .append("font-size:").append(format(style.getFontSizePoints())).append("pt;")
                .append("color:").append(color(style.getColor(), 0xFF1D2633)).append(';')
                .append("font-weight:").append(style.isBold() ? "700" : "400").append(';')
                .append("font-style:").append(style.isItalic() ? "italic" : "normal").append(';');
    }

    private String runCss(WordRunStyle style) {
        StringBuilder css = new StringBuilder();
        appendDefaultRunCss(css, style);
        if (style.isUnderline() || style.isStrike()) {
            css.append("text-decoration-line:");
            if (style.isUnderline()) css.append("underline");
            if (style.isUnderline() && style.isStrike()) css.append(' ');
            if (style.isStrike()) css.append("line-through");
            css.append(';');
        } else {
            css.append("text-decoration:none;");
        }
        if (style.getHighlight() != null) {
            css.append("background-color:")
                    .append(color(style.getHighlight(), 0x00FFFFFF))
                    .append(';');
        }
        switch (style.getVerticalPosition()) {
            case SUBSCRIPT:
                css.append("vertical-align:sub;font-size:.8em;");
                break;
            case SUPERSCRIPT:
                css.append("vertical-align:super;font-size:.8em;");
                break;
            case BASELINE:
            default:
                css.append("vertical-align:baseline;");
                break;
        }
        float shift = style.getBaselineShiftPoints();
        if (shift != 0f) {
            css.append("position:relative;top:")
                    .append(format(-shift))
                    .append("pt;");
        }
        return css.toString();
    }

    private void renderList(List<WordParagraph> paragraphs, StringBuilder html) {
        if (paragraphs.isEmpty()) return;
        List<ListNode> roots = new ArrayList<>();
        List<ListNode> lastAtDepth = new ArrayList<>();
        int baseDepth = inferredListDepth(paragraphs.get(0));
        for (WordParagraph paragraph : paragraphs) {
            int depth = Math.max(0, inferredListDepth(paragraph) - baseDepth);
            depth = Math.min(depth, lastAtDepth.size());
            ListNode node = new ListNode(paragraph);
            if (depth == 0 || lastAtDepth.isEmpty()) {
                roots.add(node);
                depth = 0;
            } else {
                lastAtDepth.get(depth - 1).children.add(node);
            }
            while (lastAtDepth.size() > depth) {
                lastAtDepth.remove(lastAtDepth.size() - 1);
            }
            lastAtDepth.add(node);
        }
        renderListGroups(roots, 0, html);
    }

    private void renderListGroups(List<ListNode> nodes, int nestingDepth, StringBuilder html) {
        int index = 0;
        while (index < nodes.size()) {
            boolean bullet = isBullet(nodes.get(index).paragraph.getListMarker());
            int end = index + 1;
            while (end < nodes.size()
                    && isBullet(nodes.get(end).paragraph.getListMarker()) == bullet) {
                end++;
            }
            String tag = bullet ? "ul" : "ol";
            html.append('<').append(tag).append(" class=\"word-list\"");
            if (!bullet) {
                int start = orderedValue(nodes.get(index).paragraph.getListMarker());
                if (start > 0) html.append(" start=\"").append(start).append('"');
            }
            double margin = nestingDepth == 0
                    ? Math.max(0d, DocxPageStyle.twips(
                            nodes.get(index).paragraph.getStyle().getLeftIndentTwips()
                    ))
                    : 18d;
            html.append(" style=\"margin-left:")
                    .append(DocxPageStyle.points(margin))
                    .append("\">");
            for (int nodeIndex = index; nodeIndex < end; nodeIndex++) {
                renderListNode(nodes.get(nodeIndex), bullet, nestingDepth, html);
            }
            html.append("</").append(tag).append('>');
            index = end;
        }
    }

    private void renderListNode(
            ListNode node,
            boolean bullet,
            int nestingDepth,
            StringBuilder html
    ) {
        int value = orderedValue(node.paragraph.getListMarker());
        html.append("<li");
        if (!bullet && value > 0) html.append(" value=\"").append(value).append('"');
        html.append("><div class=\"list-line ")
                .append(roleClass(node.paragraph))
                .append("\" style=\"")
                .append(paragraphCss(node.paragraph, false))
                .append("\"><span class=\"list-marker\">")
                .append(DocxHtmlEscaper.text(node.paragraph.getListMarker()))
                .append("</span><span class=\"list-content\">");
        renderRunsWithoutMarker(node.paragraph, html);
        html.append("</span></div>");
        if (!node.children.isEmpty()) {
            renderListGroups(node.children, nestingDepth + 1, html);
        }
        html.append("</li>");
    }

    private int inferredListDepth(WordParagraph paragraph) {
        int left = Math.max(0, paragraph.getStyle().getLeftIndentTwips());
        if (left <= 0) return 0;
        return Math.max(0, Math.min(8, Math.round(left / 720f) - 1));
    }

    private boolean isBullet(String marker) {
        if (marker == null) return true;
        String trimmed = marker.trim();
        if (trimmed.isEmpty()) return true;
        int first = trimmed.codePointAt(0);
        return !Character.isLetterOrDigit(first);
    }

    private int orderedValue(String marker) {
        if (marker == null) return -1;
        String trimmed = marker.trim();
        int value = 0;
        int digits = 0;
        for (int index = 0; index < trimmed.length(); index++) {
            char character = trimmed.charAt(index);
            if (!Character.isDigit(character)) break;
            value = Math.min(1_000_000, value * 10 + (character - '0'));
            digits++;
        }
        return digits == 0 ? -1 : value;
    }

    private void renderTable(WordTable table, int rowStart, int rowEnd, StringBuilder html) {
        int columns = columnCount(table);
        int safeEnd = Math.max(rowStart, Math.min(rowEnd, table.getRows().size()));
        html.append("<div class=\"table-overflow\"><table class=\"word-table\" style=\"")
                .append(tableCss(table))
                .append("\"><colgroup>");
        List<Integer> widths = table.getColumnWidthsTwips();
        for (int column = 0; column < columns; column++) {
            html.append("<col");
            if (column < widths.size() && widths.get(column) > 0) {
                html.append(" style=\"width:")
                        .append(DocxPageStyle.points(DocxPageStyle.twips(widths.get(column))))
                        .append('"');
            }
            html.append('>');
        }
        html.append("</colgroup><tbody>");
        for (int rowIndex = rowStart; rowIndex < safeEnd; rowIndex++) {
            WordTableRow row = table.getRows().get(rowIndex);
            html.append("<tr");
            if (row.getHeightTwips() > 0) {
                html.append(" style=\"height:")
                        .append(DocxPageStyle.points(DocxPageStyle.twips(
                                row.getHeightTwips()
                        )))
                        .append('"');
            }
            html.append('>');
            int column = 0;
            for (WordTableCell cell : row.getCells()) {
                int firstColumn = column;
                column += cell.getGridSpan();
                boolean continuation = cell.getVerticalMerge()
                        == WordTableCell.VerticalMerge.CONTINUE;
                if (continuation && mergeStartsWithinSlice(
                        table,
                        rowIndex,
                        firstColumn,
                        rowStart
                )) {
                    continue;
                }
                int rowSpan = cell.getVerticalMerge() == WordTableCell.VerticalMerge.RESTART
                        || continuation
                        ? verticalSpan(table, rowIndex, firstColumn, safeEnd)
                        : 1;
                html.append("<td");
                if (cell.getGridSpan() > 1) {
                    html.append(" colspan=\"").append(cell.getGridSpan()).append('"');
                }
                if (rowSpan > 1) html.append(" rowspan=\"").append(rowSpan).append('"');
                html.append(" style=\"")
                        .append(cellCss(
                                table,
                                cell,
                                rowIndex,
                                safeEnd,
                                firstColumn,
                                columns
                        ))
                        .append("\">");
                renderCellBlocks(cell.getBlocks(), html);
                html.append("</td>");
            }
            html.append("</tr>");
        }
        html.append("</tbody></table></div>");
    }

    private String tableCss(WordTable table) {
        StringBuilder css = new StringBuilder();
        WordTableWidth width = table.getWidth();
        if (width.getType() == WordTableWidth.Type.DXA && width.getValue() > 0) {
            css.append("width:")
                    .append(DocxPageStyle.points(DocxPageStyle.twips(width.getValue())))
                    .append(';');
        } else if (width.getType() == WordTableWidth.Type.PERCENT && width.getValue() > 0) {
            css.append("width:")
                    .append(format(Math.min(5000, width.getValue()) / 50d))
                    .append("%;");
        } else {
            int totalTwips = 0;
            for (Integer column : table.getColumnWidthsTwips()) {
                if (column != null) totalTwips += Math.max(0, column);
            }
            if (totalTwips > 0) {
                css.append("width:")
                        .append(DocxPageStyle.points(DocxPageStyle.twips(totalTwips)))
                        .append(';');
            } else {
                css.append("width:100%;");
            }
        }
        if (table.getAlignment() == WordTable.Alignment.CENTER) {
            css.append("margin-left:auto;margin-right:auto;");
        } else if (table.getAlignment() == WordTable.Alignment.RIGHT) {
            css.append("margin-left:auto;margin-right:0;");
        }
        return css.toString();
    }

    private String cellCss(
            WordTable table,
            WordTableCell cell,
            int row,
            int rowEnd,
            int column,
            int columnCount
    ) {
        StringBuilder css = new StringBuilder();
        double top = table.getCellMarginTopTwips() > 0
                ? DocxPageStyle.twips(table.getCellMarginTopTwips())
                : DEFAULT_TABLE_CELL_PADDING_POINTS;
        double right = table.getCellMarginEndTwips() > 0
                ? DocxPageStyle.twips(table.getCellMarginEndTwips())
                : DEFAULT_TABLE_CELL_PADDING_POINTS;
        double bottom = table.getCellMarginBottomTwips() > 0
                ? DocxPageStyle.twips(table.getCellMarginBottomTwips())
                : DEFAULT_TABLE_CELL_PADDING_POINTS;
        double left = table.getCellMarginStartTwips() > 0
                ? DocxPageStyle.twips(table.getCellMarginStartTwips())
                : DEFAULT_TABLE_CELL_PADDING_POINTS;
        css.append("padding:")
                .append(DocxPageStyle.points(top)).append(' ')
                .append(DocxPageStyle.points(right)).append(' ')
                .append(DocxPageStyle.points(bottom)).append(' ')
                .append(DocxPageStyle.points(left)).append(';')
                .append("vertical-align:")
                .append(verticalAlignment(cell))
                .append(';');
        if (cell.getShadingColor() != null) {
            css.append("background-color:")
                    .append(color(cell.getShadingColor(), 0x00FFFFFF))
                    .append(';');
        }
        appendBorder(css, "left", effectiveBorder(
                cell.getLeftBorder(),
                column == 0 ? table.getLeftBorder() : table.getInsideVerticalBorder()
        ));
        appendBorder(css, "right", effectiveBorder(
                cell.getRightBorder(),
                column + cell.getGridSpan() >= columnCount
                        ? table.getRightBorder()
                        : table.getInsideVerticalBorder()
        ));
        appendBorder(css, "top", effectiveBorder(
                cell.getTopBorder(),
                row == 0 ? table.getTopBorder() : table.getInsideHorizontalBorder()
        ));
        appendBorder(css, "bottom", effectiveBorder(
                cell.getBottomBorder(),
                row + 1 >= rowEnd
                        ? table.getBottomBorder()
                        : table.getInsideHorizontalBorder()
        ));
        WordTableWidth width = cell.getWidth();
        if (width.getType() == WordTableWidth.Type.DXA && width.getValue() > 0) {
            css.append("width:")
                    .append(DocxPageStyle.points(DocxPageStyle.twips(width.getValue())))
                    .append(';');
        } else if (width.getType() == WordTableWidth.Type.PERCENT && width.getValue() > 0) {
            css.append("width:")
                    .append(format(Math.min(5000, width.getValue()) / 50d))
                    .append("%;");
        }
        return css.toString();
    }

    private WordBorder effectiveBorder(WordBorder direct, WordBorder fallback) {
        return direct != null && direct.getStyle() != WordBorder.Style.NONE
                ? direct
                : fallback;
    }

    private void appendBorder(StringBuilder css, String side, WordBorder border) {
        if (border == null || border.getStyle() == WordBorder.Style.NONE) return;
        double width = Math.max(
                border.getStyle() == WordBorder.Style.DOUBLE ? 1.5d : 0.5d,
                border.getSizeEighthPoints() / 8d
        );
        css.append("border-").append(side).append(':')
                .append(DocxPageStyle.points(width)).append(' ')
                .append(borderStyle(border.getStyle())).append(' ')
                .append(color(border.getColor(), 0xFFD3DAE3)).append(';');
    }

    private String borderStyle(WordBorder.Style style) {
        switch (style) {
            case DOUBLE:
                return "double";
            case DASHED:
                return "dashed";
            case DOTTED:
                return "dotted";
            case SINGLE:
            case THICK:
            default:
                return "solid";
        }
    }

    private String verticalAlignment(WordTableCell cell) {
        switch (cell.getVerticalAlignment()) {
            case CENTER:
                return "middle";
            case BOTTOM:
                return "bottom";
            case TOP:
            default:
                return "top";
        }
    }

    private void renderCellBlocks(List<WordBlock> blocks, StringBuilder html) {
        int index = 0;
        while (index < blocks.size()) {
            WordBlock block = blocks.get(index);
            if (block instanceof WordParagraph
                    && !((WordParagraph) block).getListMarker().isEmpty()) {
                List<WordParagraph> list = new ArrayList<>();
                while (index < blocks.size()
                        && blocks.get(index) instanceof WordParagraph
                        && !((WordParagraph) blocks.get(index)).getListMarker().isEmpty()) {
                    list.add((WordParagraph) blocks.get(index));
                    index++;
                }
                renderList(list, html);
                continue;
            }
            renderPiece(new Piece(block), html);
            index++;
        }
    }

    private int columnCount(WordTable table) {
        int columns = table.getColumnWidthsTwips().size();
        for (WordTableRow row : table.getRows()) {
            int rowColumns = 0;
            for (WordTableCell cell : row.getCells()) rowColumns += cell.getGridSpan();
            columns = Math.max(columns, rowColumns);
        }
        return Math.max(1, columns);
    }

    private boolean mergeStartsWithinSlice(
            WordTable table,
            int row,
            int column,
            int sliceStart
    ) {
        for (int candidate = row - 1; candidate >= sliceStart; candidate--) {
            WordTableCell above = cellAt(table.getRows().get(candidate), column);
            if (above == null) return false;
            if (above.getVerticalMerge() == WordTableCell.VerticalMerge.RESTART) return true;
            if (above.getVerticalMerge() != WordTableCell.VerticalMerge.CONTINUE) return false;
        }
        return false;
    }

    private int verticalSpan(WordTable table, int row, int column, int rowEnd) {
        int span = 1;
        for (int candidate = row + 1;
                candidate < rowEnd && candidate < table.getRows().size();
                candidate++) {
            WordTableCell below = cellAt(table.getRows().get(candidate), column);
            if (below == null
                    || below.getVerticalMerge() != WordTableCell.VerticalMerge.CONTINUE) {
                break;
            }
            span++;
        }
        return span;
    }

    @Nullable
    private WordTableCell cellAt(WordTableRow row, int requestedColumn) {
        int column = 0;
        for (WordTableCell cell : row.getCells()) {
            if (column == requestedColumn) return cell;
            column += cell.getGridSpan();
        }
        return null;
    }

    private void renderImage(WordImage image, StringBuilder html) {
        double width = image.getWidthEmu() > 0
                ? image.getWidthEmu() / EMU_PER_POINT
                : DEFAULT_IMAGE_POINTS;
        double height = image.getHeightEmu() > 0
                ? image.getHeightEmu() / EMU_PER_POINT
                : DEFAULT_IMAGE_POINTS;
        String alt = image.getAltText().isEmpty() ? labels.imageDescription : image.getAltText();
        String dataUri = imageSource == null ? null : imageSource.dataUri(image);
        html.append("<figure class=\"word-image\" style=\"width:")
                .append(DocxPageStyle.points(width))
                .append(";max-width:100%\">");
        if (isSafeImageDataUri(dataUri)) {
            html.append("<img src=\"")
                    .append(dataUri)
                    .append("\" alt=\"")
                    .append(DocxHtmlEscaper.attribute(alt))
                    .append("\" width=\"")
                    .append(format(width))
                    .append("\" height=\"")
                    .append(format(height))
                    .append("\">");
        } else {
            String placeholder = image.isVectorPlaceholder()
                    ? labels.vectorPlaceholder
                    : labels.imageFailed;
            html.append("<div class=\"image-placeholder\" role=\"img\" aria-label=\"")
                    .append(DocxHtmlEscaper.attribute(alt))
                    .append("\">")
                    .append(DocxHtmlEscaper.text(placeholder))
                    .append("</div>");
        }
        html.append("</figure>");
    }

    private boolean isSafeImageDataUri(@Nullable String value) {
        if (value == null) return false;
        String[] prefixes = {
                "data:image/png;base64,",
                "data:image/jpeg;base64,",
                "data:image/webp;base64,",
                "data:image/gif;base64,",
                "data:image/bmp;base64,"
        };
        int payload = -1;
        for (String prefix : prefixes) {
            if (value.startsWith(prefix)) {
                payload = prefix.length();
                break;
            }
        }
        if (payload < 0 || payload == value.length()) return false;
        for (int index = payload; index < value.length(); index++) {
            char character = value.charAt(index);
            boolean base64 = character >= 'A' && character <= 'Z'
                    || character >= 'a' && character <= 'z'
                    || character >= '0' && character <= '9'
                    || character == '+' || character == '/' || character == '=';
            if (!base64) return false;
        }
        return true;
    }

    @Nullable
    static String safeHttps(@Nullable String value) {
        if (value == null || value.isEmpty()) return null;
        try {
            URI uri = new URI(value);
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getHost() == null
                    || uri.getHost().isEmpty()
                    || uri.getUserInfo() != null) {
                return null;
            }
            return uri.toASCIIString();
        } catch (URISyntaxException | RuntimeException ignored) {
            return null;
        }
    }

    private String color(@Nullable Integer value, int fallback) {
        int resolved = value == null ? fallback : value;
        return String.format(Locale.US, "#%06X", resolved & 0x00FFFFFF);
    }

    private String format(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    static final class Result {
        private final String html;
        private final int pageCount;

        private Result(String html, int pageCount) {
            this.html = html;
            this.pageCount = pageCount;
        }

        String getHtml() {
            return html;
        }

        int getPageCount() {
            return pageCount;
        }
    }

    static final class Labels {
        private final String imageDescription;
        private final String vectorPlaceholder;
        private final String imageFailed;

        Labels(String imageDescription, String vectorPlaceholder, String imageFailed) {
            this.imageDescription = safe(imageDescription);
            this.vectorPlaceholder = safe(vectorPlaceholder);
            this.imageFailed = safe(imageFailed);
        }

        private static Labels empty() {
            return new Labels("", "", "");
        }

        private static String safe(String value) {
            return value == null ? "" : value;
        }
    }

    private static final class Piece {
        private final WordBlock block;
        private final int rowStart;
        private final int rowEnd;

        private Piece(WordBlock block) {
            this(block, -1, -1);
        }

        private Piece(WordBlock block, int rowStart, int rowEnd) {
            this.block = block;
            this.rowStart = rowStart;
            this.rowEnd = rowEnd;
        }
    }

    private static final class Page {
        private final DocxPageStyle style;
        private final List<Piece> pieces = new ArrayList<>();
        private double usedPoints;

        private Page(DocxPageStyle style) {
            this.style = style;
        }
    }

    private static final class ListNode {
        private final WordParagraph paragraph;
        private final List<ListNode> children = new ArrayList<>();

        private ListNode(WordParagraph paragraph) {
            this.paragraph = paragraph;
        }
    }
}
