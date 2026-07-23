package com.desperadoboi.imagetopdf.document.word;

import com.desperadoboi.imagetopdf.document.DocumentLimits;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class DocxDocumentParserTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private final DocxDocumentParser parser = new DocxDocumentParser();

    @Test
    public void parsesUnicodeRunsWhitespaceTabsBreaksAndDirectFormatting()
            throws Exception {
        String body = "<w:p><w:pPr><w:jc w:val=\"center\"/>"
                + "<w:ind w:left=\"720\" w:firstLine=\"360\"/>"
                + "<w:spacing w:before=\"120\" w:after=\"240\" w:line=\"360\"/>"
                + "<w:pageBreakBefore/></w:pPr>"
                + "<w:r><w:rPr><w:b/><w:i/><w:u w:val=\"single\"/>"
                + "<w:color w:val=\"FF0000\"/><w:sz w:val=\"28\"/>"
                + "</w:rPr><w:t xml:space=\"preserve\"> Привет </w:t>"
                + "<w:tab/><w:t>Word</w:t><w:br/><w:t>line</w:t>"
                + "<w:softHyphen/><w:noBreakHyphen/></w:r>"
                + "<w:r><w:rPr><w:vanish/></w:rPr><w:t>hidden</w:t></w:r>"
                + "</w:p>";
        WordDocumentModel document = parse("runs.docx", body);

        assertEquals(2, document.getBlocks().size());
        assertTrue(document.getBlocks().get(0) instanceof WordPageBreak);
        WordParagraph paragraph = (WordParagraph) document.getBlocks().get(1);
        assertEquals(" Привет \tWord\nline\u00AD\u2011", paragraph.getPlainText());
        assertEquals(1, paragraph.getRuns().size());
        WordRunStyle style = paragraph.getRuns().get(0).getStyle();
        assertTrue(style.isBold());
        assertTrue(style.isItalic());
        assertTrue(style.isUnderline());
        assertEquals(Integer.valueOf(0xFFFF0000), style.getColor());
        assertEquals(14f, style.getFontSizePoints(), 0.001f);
        assertEquals(
                WordParagraphStyle.Alignment.CENTER,
                paragraph.getStyle().getAlignment()
        );
        assertEquals(720, paragraph.getStyle().getLeftIndentTwips());
        assertEquals(360, paragraph.getStyle().getFirstLineIndentTwips());
        assertEquals(120, paragraph.getStyle().getSpaceBeforeTwips());
        assertEquals(240, paragraph.getStyle().getSpaceAfterTwips());
    }

    @Test
    public void resolvesDocumentParagraphAndCharacterStyleInheritance()
            throws Exception {
        Map<String, byte[]> parts = new LinkedHashMap<>();
        parts.put("word/styles.xml", DocxTestFixtures.bytes(
                "<w:styles xmlns:w=\"" + DocxTestFixtures.WORD_NAMESPACE + "\">"
                        + "<w:docDefaults><w:rPrDefault><w:rPr><w:sz w:val=\"22\"/>"
                        + "</w:rPr></w:rPrDefault></w:docDefaults>"
                        + "<w:style w:type=\"paragraph\" w:styleId=\"Base\">"
                        + "<w:pPr><w:jc w:val=\"right\"/></w:pPr>"
                        + "<w:rPr><w:b/></w:rPr></w:style>"
                        + "<w:style w:type=\"paragraph\" w:styleId=\"Heading1\">"
                        + "<w:name w:val=\"heading 1\"/><w:basedOn w:val=\"Base\"/>"
                        + "<w:rPr><w:color w:val=\"1F4E79\"/></w:rPr></w:style>"
                        + "<w:style w:type=\"character\" w:styleId=\"Emphasis\">"
                        + "<w:rPr><w:i/><w:u/></w:rPr></w:style>"
                        + "</w:styles>"
        ));
        String rels = DocxTestFixtures.relationship("styles", "styles", "styles.xml");
        String body = "<w:p><w:pPr><w:pStyle w:val=\"Heading1\"/></w:pPr>"
                + "<w:r><w:rPr><w:rStyle w:val=\"Emphasis\"/></w:rPr>"
                + "<w:t>Styled</w:t></w:r></w:p>";
        WordDocumentModel document = parse(
                "styles.docx",
                body,
                rels,
                parts,
                ""
        );

        WordParagraph paragraph = (WordParagraph) document.getBlocks().get(0);
        assertEquals(1, paragraph.getStyle().getHeadingLevel());
        assertEquals(WordParagraphStyle.Alignment.RIGHT,
                paragraph.getStyle().getAlignment());
        WordRunStyle style = paragraph.getRuns().get(0).getStyle();
        assertTrue(style.isBold());
        assertTrue(style.isItalic());
        assertTrue(style.isUnderline());
        assertEquals(11f, style.getFontSizePoints(), 0.001f);
        assertEquals(Integer.valueOf(0xFF1F4E79), style.getColor());
    }

    @Test
    public void rejectsCyclicStyleInheritance() throws Exception {
        Map<String, byte[]> parts = new LinkedHashMap<>();
        parts.put("word/styles.xml", DocxTestFixtures.bytes(
                "<w:styles xmlns:w=\"" + DocxTestFixtures.WORD_NAMESPACE + "\">"
                        + "<w:style w:type=\"paragraph\" w:styleId=\"A\">"
                        + "<w:basedOn w:val=\"B\"/></w:style>"
                        + "<w:style w:type=\"paragraph\" w:styleId=\"B\">"
                        + "<w:basedOn w:val=\"A\"/></w:style></w:styles>"
        ));
        Path file = document(
                "style-cycle.docx",
                "<w:p><w:pPr><w:pStyle w:val=\"A\"/></w:pPr>"
                        + "<w:r><w:t>x</w:t></w:r></w:p>",
                DocxTestFixtures.relationship("styles", "styles", "styles.xml"),
                parts,
                ""
        );
        assertReason(file, WordParseException.Reason.CORRUPTED);
    }

    @Test
    public void parsesNumberedBulletAndMultilevelLists() throws Exception {
        Map<String, byte[]> parts = new LinkedHashMap<>();
        parts.put("word/numbering.xml", DocxTestFixtures.bytes(
                "<w:numbering xmlns:w=\"" + DocxTestFixtures.WORD_NAMESPACE + "\">"
                        + "<w:abstractNum w:abstractNumId=\"1\">"
                        + "<w:lvl w:ilvl=\"0\"><w:start w:val=\"3\"/>"
                        + "<w:numFmt w:val=\"decimal\"/><w:lvlText w:val=\"%1.\"/>"
                        + "<w:pPr><w:ind w:left=\"720\"/></w:pPr></w:lvl>"
                        + "<w:lvl w:ilvl=\"1\"><w:numFmt w:val=\"lowerLetter\"/>"
                        + "<w:lvlText w:val=\"%1.%2)\"/></w:lvl></w:abstractNum>"
                        + "<w:abstractNum w:abstractNumId=\"2\"><w:lvl w:ilvl=\"0\">"
                        + "<w:numFmt w:val=\"bullet\"/><w:lvlText w:val=\"•\"/>"
                        + "</w:lvl></w:abstractNum>"
                        + "<w:num w:numId=\"7\"><w:abstractNumId w:val=\"1\"/></w:num>"
                        + "<w:num w:numId=\"8\"><w:abstractNumId w:val=\"2\"/></w:num>"
                        + "</w:numbering>"
        ));
        String rels = DocxTestFixtures.relationship(
                "numbering",
                "numbering",
                "numbering.xml"
        );
        String body = listParagraph(7, 0, "first")
                + listParagraph(7, 1, "nested")
                + listParagraph(7, 0, "next")
                + listParagraph(8, 0, "bullet");
        WordDocumentModel document = parse(
                "lists.docx",
                body,
                rels,
                parts,
                ""
        );
        List<WordBlock> blocks = document.getBlocks();
        assertEquals("3. first", ((WordParagraph) blocks.get(0)).getPlainText());
        assertEquals("3.a) nested", ((WordParagraph) blocks.get(1)).getPlainText());
        assertEquals("4. next", ((WordParagraph) blocks.get(2)).getPlainText());
        assertEquals("• bullet", ((WordParagraph) blocks.get(3)).getPlainText());
        assertEquals(720, ((WordParagraph) blocks.get(0))
                .getStyle().getLeftIndentTwips());
    }

    @Test
    public void parsesTablesMergesBordersShadingAndNestedTable() throws Exception {
        String cellProperties = "<w:tcPr><w:tcW w:w=\"2400\"/>"
                + "<w:gridSpan w:val=\"2\"/><w:vMerge w:val=\"restart\"/>"
                + "<w:vAlign w:val=\"center\"/><w:shd w:fill=\"D9EAF7\"/>"
                + "<w:tcBorders><w:top w:val=\"double\" w:color=\"0000FF\""
                + " w:sz=\"8\"/></w:tcBorders></w:tcPr>";
        String nested = "<w:tbl><w:tblGrid><w:gridCol w:w=\"1200\"/>"
                + "</w:tblGrid><w:tr><w:tc>"
                + DocxTestFixtures.paragraph("nested")
                + "</w:tc></w:tr></w:tbl>";
        String table = "<w:tbl><w:tblPr><w:jc w:val=\"center\"/>"
                + "<w:tblCellMar><w:left w:w=\"100\"/><w:right w:w=\"100\"/>"
                + "</w:tblCellMar><w:tblBorders><w:left w:val=\"single\"/>"
                + "<w:insideH w:val=\"dotted\"/></w:tblBorders></w:tblPr>"
                + "<w:tblGrid><w:gridCol w:w=\"1200\"/><w:gridCol w:w=\"1200\"/>"
                + "</w:tblGrid><w:tr><w:trPr><w:trHeight w:val=\"480\"/></w:trPr>"
                + "<w:tc>" + cellProperties + DocxTestFixtures.paragraph("merged")
                + nested + "</w:tc></w:tr>"
                + "<w:tr><w:tc><w:tcPr><w:gridSpan w:val=\"2\"/><w:vMerge/>"
                + "</w:tcPr>" + DocxTestFixtures.paragraph("") + "</w:tc></w:tr>"
                + "</w:tbl>";
        WordDocumentModel document = parse("table.docx", table);
        WordTable parsed = (WordTable) document.getBlocks().get(0);

        assertEquals(2, parsed.getRows().size());
        assertEquals(WordTable.Alignment.CENTER, parsed.getAlignment());
        assertEquals(100, parsed.getCellMarginTwips());
        WordTableCell first = parsed.getRows().get(0).getCells().get(0);
        assertEquals(2, first.getGridSpan());
        assertEquals(WordTableCell.VerticalMerge.RESTART, first.getVerticalMerge());
        assertEquals(WordTableCell.VerticalAlignment.CENTER,
                first.getVerticalAlignment());
        assertEquals(Integer.valueOf(0xFFD9EAF7), first.getShadingColor());
        assertEquals(WordBorder.Style.DOUBLE, first.getTopBorder().getStyle());
        assertTrue(first.getBlocks().get(1) instanceof WordTable);
        assertEquals(WordTableCell.VerticalMerge.CONTINUE,
                parsed.getRows().get(1).getCells().get(0).getVerticalMerge());
    }

    @Test
    public void parsesInlineImageAndKeepsUnsupportedVectorAsPlaceholder()
            throws Exception {
        Map<String, byte[]> parts = new LinkedHashMap<>();
        parts.put("word/media/pixel.png", DocxTestFixtures.ONE_PIXEL_PNG);
        parts.put("word/media/vector.svg", DocxTestFixtures.bytes("<svg/>"));
        String rels = DocxTestFixtures.relationship(
                "img1",
                "image",
                "media/pixel.png"
        ) + DocxTestFixtures.relationship("img2", "image", "media/vector.svg");
        String body = drawing("img1", 914400, 457200, "Pixel")
                + drawing("img2", 914400, 914400, "Vector");
        WordDocumentModel document = parse(
                "images.docx",
                body,
                rels,
                parts,
                ""
        );

        assertEquals(2, document.getImageCount());
        WordImage raster = firstImage(document, 0);
        WordImage vector = firstImage(document, 1);
        assertEquals("word/media/pixel.png", raster.getPackagePath());
        assertEquals("Pixel", raster.getAltText());
        assertFalse(raster.isVectorPlaceholder());
        assertTrue(vector.isVectorPlaceholder());
    }

    @Test
    public void externalHttpsHyperlinkIsExplicitMetadataAndInternalTextRemains()
            throws Exception {
        String rels = DocxTestFixtures.externalRelationship(
                "link",
                "hyperlink",
                "https://example.com/read"
        );
        String body = "<w:p><w:hyperlink r:id=\"link\"><w:r><w:t>External</w:t>"
                + "</w:r></w:hyperlink><w:hyperlink w:anchor=\"local\">"
                + "<w:r><w:t> Internal</w:t></w:r></w:hyperlink></w:p>";
        WordParagraph paragraph = (WordParagraph) parse(
                "links.docx",
                body,
                rels,
                new LinkedHashMap<>(),
                ""
        ).getBlocks().get(0);

        assertEquals("External Internal", paragraph.getPlainText());
        assertEquals("https://example.com/read",
                paragraph.getRuns().get(0).getHyperlink());
        assertNull(paragraph.getRuns().get(1).getHyperlink());
    }

    @Test
    public void parsesHeadersFootersFootnotesAndTrackChanges() throws Exception {
        Map<String, byte[]> parts = new LinkedHashMap<>();
        parts.put("word/header1.xml", DocxTestFixtures.bytes(
                "<w:hdr xmlns:w=\"" + DocxTestFixtures.WORD_NAMESPACE + "\">"
                        + DocxTestFixtures.paragraph("Header") + "</w:hdr>"
        ));
        parts.put("word/footer1.xml", DocxTestFixtures.bytes(
                "<w:ftr xmlns:w=\"" + DocxTestFixtures.WORD_NAMESPACE + "\">"
                        + DocxTestFixtures.paragraph("Footer") + "</w:ftr>"
        ));
        parts.put("word/footnotes.xml", DocxTestFixtures.bytes(
                "<w:footnotes xmlns:w=\"" + DocxTestFixtures.WORD_NAMESPACE + "\">"
                        + "<w:footnote w:id=\"-1\">"
                        + DocxTestFixtures.paragraph("separator")
                        + "</w:footnote><w:footnote w:id=\"2\">"
                        + DocxTestFixtures.paragraph("Note") + "</w:footnote>"
                        + "</w:footnotes>"
        ));
        String rels = DocxTestFixtures.relationship("h", "header", "header1.xml")
                + DocxTestFixtures.relationship("f", "footer", "footer1.xml")
                + DocxTestFixtures.relationship("n", "footnotes", "footnotes.xml");
        String body = "<w:p><w:del><w:r><w:delText>Deleted</w:delText></w:r>"
                + "</w:del><w:ins><w:r><w:t>Inserted</w:t></w:r></w:ins></w:p>";
        WordDocumentModel document = parse(
                "stories.docx",
                body,
                rels,
                parts,
                ""
        );

        assertEquals("Header", ((WordParagraph) document.getBlocks().get(0))
                .getPlainText());
        assertEquals(WordParagraph.Role.HEADER,
                ((WordParagraph) document.getBlocks().get(0)).getRole());
        assertEquals("Inserted", ((WordParagraph) document.getBlocks().get(1))
                .getPlainText());
        assertEquals("Footer", ((WordParagraph) document.getBlocks().get(2))
                .getPlainText());
        assertEquals("2. Note", ((WordParagraph) document.getBlocks().get(3))
                .getPlainText());
        assertEquals(WordParagraph.Role.FOOTNOTE,
                ((WordParagraph) document.getBlocks().get(3)).getRole());
    }

    @Test
    public void rejectsCorruptedXmlExcessiveParagraphsAndHonorsCancellation()
            throws Exception {
        Path corrupted = DocxTestFixtures.minimalDocument(
                fixture("corrupted.docx"),
                "<w:p><w:r><w:t>broken"
        );
        assertReason(corrupted, WordParseException.Reason.CORRUPTED);

        StringBuilder excessive = new StringBuilder();
        for (int index = 0; index <= 20_000; index++) {
            excessive.append(DocxTestFixtures.paragraph("x"));
        }
        Path tooLarge = DocxTestFixtures.minimalDocument(
                fixture("paragraph-limit.docx"),
                excessive.toString()
        );
        assertReason(tooLarge, WordParseException.Reason.TOO_LARGE);

        Path cancelled = DocxTestFixtures.minimalDocument(
                fixture("cancelled.docx"),
                DocxTestFixtures.paragraph("x")
        );
        try {
            parser.parse(cancelled.toFile(), new AtomicBoolean(true));
            fail("Expected cancellation");
        } catch (WordParseException exception) {
            assertEquals(WordParseException.Reason.CANCELLED, exception.getReason());
        }
    }

    @Test(timeout = 20_000L)
    public void enforcesRunTableImageTextAndXmlDepthLimits() throws Exception {
        StringBuilder runs = new StringBuilder("<w:p>");
        for (int index = 0; index <= DocumentLimits.MAX_WORD_RUNS; index++) {
            runs.append("<w:r><w:t>x</w:t></w:r>");
        }
        runs.append("</w:p>");
        assertReason(
                DocxTestFixtures.minimalDocument(
                        fixture("run-limit.docx"),
                        runs.toString()
                ),
                WordParseException.Reason.TOO_LARGE
        );

        StringBuilder tables = new StringBuilder();
        for (int index = 0; index <= DocumentLimits.MAX_WORD_TABLES; index++) {
            tables.append("<w:tbl><w:tr><w:tc>")
                    .append(DocxTestFixtures.paragraph("x"))
                    .append("</w:tc></w:tr></w:tbl>");
        }
        assertReason(
                DocxTestFixtures.minimalDocument(
                        fixture("table-limit.docx"),
                        tables.toString()
                ),
                WordParseException.Reason.TOO_LARGE
        );

        Map<String, byte[]> imageParts = new LinkedHashMap<>();
        imageParts.put("word/media/pixel.png", DocxTestFixtures.ONE_PIXEL_PNG);
        String imageRelationship = DocxTestFixtures.relationship(
                "image",
                "image",
                "media/pixel.png"
        );
        StringBuilder images = new StringBuilder();
        for (int index = 0; index <= DocumentLimits.MAX_WORD_IMAGES; index++) {
            images.append(drawing("image", 914400, 914400, ""));
        }
        assertReason(
                document(
                        "image-model-limit.docx",
                        images.toString(),
                        imageRelationship,
                        imageParts,
                        ""
                ),
                WordParseException.Reason.TOO_LARGE
        );

        assertReason(
                DocxTestFixtures.minimalDocument(
                        fixture("run-text-limit.docx"),
                        DocxTestFixtures.paragraph(
                                "x".repeat(DocumentLimits.MAX_WORD_RUN_CHARS + 1)
                        )
                ),
                WordParseException.Reason.TOO_LARGE
        );

        StringBuilder deep = new StringBuilder();
        for (int depth = 0; depth <= DocumentLimits.MAX_XML_DEPTH; depth++) {
            deep.append("<w:sdt>");
        }
        deep.append(DocxTestFixtures.paragraph("deep"));
        for (int depth = 0; depth <= DocumentLimits.MAX_XML_DEPTH; depth++) {
            deep.append("</w:sdt>");
        }
        assertReason(
                DocxTestFixtures.minimalDocument(
                        fixture("xml-depth-limit.docx"),
                        deep.toString()
                ),
                WordParseException.Reason.TOO_LARGE
        );
    }

    @Test(timeout = 20_000L)
    public void parsesSyntheticPerformanceShapesWithoutMaterializingViews()
            throws Exception {
        StringBuilder body = new StringBuilder();
        for (int paragraph = 0; paragraph < 2_000; paragraph++) {
            body.append("<w:p>");
            for (int run = 0; run < 10; run++) {
                body.append("<w:r><w:t>x</w:t></w:r>");
            }
            body.append("</w:p>");
        }
        for (int table = 0; table < 100; table++) {
            body.append("<w:tbl><w:tr><w:tc>")
                    .append(DocxTestFixtures.paragraph("t"))
                    .append("</w:tc></w:tr></w:tbl>");
        }
        body.append("<w:tbl><w:tblGrid>");
        for (int column = 0; column < 20; column++) {
            body.append("<w:gridCol w:w=\"720\"/>");
        }
        body.append("</w:tblGrid>");
        for (int row = 0; row < 500; row++) {
            body.append("<w:tr>");
            for (int column = 0; column < 20; column++) {
                body.append("<w:tc>")
                        .append(DocxTestFixtures.paragraph("c"))
                        .append("</w:tc>");
            }
            body.append("</w:tr>");
        }
        body.append("</w:tbl>");

        Map<String, byte[]> parts = new LinkedHashMap<>();
        parts.put("word/media/pixel.png", DocxTestFixtures.ONE_PIXEL_PNG);
        String rels = DocxTestFixtures.relationship(
                "img",
                "image",
                "media/pixel.png"
        );
        for (int image = 0; image < 100; image++) {
            body.append(drawing("img", 914400, 914400, ""));
        }

        WordDocumentModel document = parse(
                "performance.docx",
                body.toString(),
                rels,
                parts,
                ""
        );
        assertEquals(12_200, document.getParagraphCount());
        assertEquals(101, document.getTableCount());
        assertEquals(100, document.getImageCount());
        assertTrue(document.getBlocks().size() < 2_500);
    }

    private WordImage firstImage(WordDocumentModel document, int index) {
        int found = 0;
        for (WordBlock block : document.getBlocks()) {
            if (block instanceof WordImage) {
                if (found == index) return (WordImage) block;
                found++;
            }
        }
        throw new AssertionError("Missing image " + index);
    }

    private String listParagraph(int numId, int level, String text) {
        return "<w:p><w:pPr><w:numPr><w:ilvl w:val=\"" + level
                + "\"/><w:numId w:val=\"" + numId
                + "\"/></w:numPr></w:pPr><w:r><w:t>" + text
                + "</w:t></w:r></w:p>";
    }

    private String drawing(String relationshipId, long width, long height, String alt) {
        return "<w:p><w:r><w:drawing><wp:inline>"
                + "<wp:extent cx=\"" + width + "\" cy=\"" + height + "\"/>"
                + "<wp:docPr id=\"1\" name=\"image\" descr=\""
                + DocxTestFixtures.escape(alt) + "\"/>"
                + "<a:graphic><a:graphicData><pic:pic><pic:blipFill>"
                + "<a:blip r:embed=\"" + relationshipId + "\"/>"
                + "</pic:blipFill></pic:pic></a:graphicData></a:graphic>"
                + "</wp:inline></w:drawing></w:r></w:p>";
    }

    private WordDocumentModel parse(String name, String body) throws Exception {
        return parser.parse(
                DocxTestFixtures.minimalDocument(fixture(name), body).toFile(),
                new AtomicBoolean(false)
        );
    }

    private WordDocumentModel parse(
            String name,
            String body,
            String relationships,
            Map<String, byte[]> parts,
            String contentTypes
    ) throws Exception {
        return parser.parse(
                document(name, body, relationships, parts, contentTypes).toFile(),
                new AtomicBoolean(false)
        );
    }

    private Path document(
            String name,
            String body,
            String relationships,
            Map<String, byte[]> parts,
            String contentTypes
    ) throws Exception {
        return DocxTestFixtures.document(
                fixture(name),
                body,
                relationships,
                parts,
                contentTypes
        );
    }

    private Path fixture(String name) throws Exception {
        return temporaryFolder.newFile(name).toPath();
    }

    private void assertReason(Path file, WordParseException.Reason expected)
            throws Exception {
        try {
            parser.parse(file.toFile(), new AtomicBoolean(false));
            fail("Expected parser failure");
        } catch (WordParseException exception) {
            assertEquals(expected, exception.getReason());
        }
    }
}
