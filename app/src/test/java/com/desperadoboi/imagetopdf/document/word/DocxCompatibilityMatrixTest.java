package com.desperadoboi.imagetopdf.document.word;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Programmatic, PII-free compatibility fixtures A-H from the DOCX reliability matrix. */
public final class DocxCompatibilityMatrixTest {
    private static final String MC =
            "http://schemas.openxmlformats.org/markup-compatibility/2006";
    private static final String STRICT_WORD =
            "http://purl.oclc.org/ooxml/wordprocessingml/main";

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private final DocxDocumentParser parser = new DocxDocumentParser();

    @Test
    public void fixtureAMinimalPackageOpens() throws Exception {
        WordDocumentModel document = parse(DocxTestFixtures.minimalDocument(
                fixture("fixture-a.docx"),
                DocxTestFixtures.paragraph("Hello DOCX")
        ));

        assertEquals(1, document.getParagraphCount());
        assertEquals("Hello DOCX", paragraph(document, 0).getPlainText());
    }

    @Test
    public void fixtureBTextFormattingAndUnicodeOpen() throws Exception {
        String body = "<w:p><w:pPr><w:pStyle w:val=\"Heading1\"/></w:pPr>"
                + "<w:r><w:rPr><w:b/></w:rPr><w:t>Bold</w:t></w:r>"
                + "<w:r><w:rPr><w:i/><w:u w:val=\"single\"/></w:rPr>"
                + "<w:t xml:space=\"preserve\"> Привет, 世界 </w:t><w:br/>"
                + "<w:t>line</w:t></w:r></w:p>"
                + DocxTestFixtures.paragraph("Second paragraph");
        Map<String, byte[]> parts = new LinkedHashMap<>();
        parts.put("word/styles.xml", DocxTestFixtures.bytes(
                "<w:styles xmlns:w=\"" + DocxTestFixtures.WORD_NAMESPACE + "\">"
                        + "<w:style w:type=\"paragraph\" w:styleId=\"Heading1\">"
                        + "<w:name w:val=\"heading 1\"/><w:rPr><w:sz w:val=\"32\"/>"
                        + "</w:rPr></w:style></w:styles>"
        ));
        WordDocumentModel document = parse(document(
                "fixture-b.docx",
                body,
                DocxTestFixtures.relationship("styles", "styles", "styles.xml"),
                parts,
                ""
        ));

        WordParagraph heading = paragraph(document, 0);
        assertEquals("Bold Привет, 世界 \nline", heading.getPlainText());
        assertTrue(heading.getRuns().get(0).getStyle().isBold());
        assertTrue(heading.getRuns().get(1).getStyle().isItalic());
        assertTrue(heading.getRuns().get(1).getStyle().isUnderline());
        assertEquals(16f, heading.getDefaultRunStyle().getFontSizePoints(), 0.001f);
    }

    @Test
    public void fixtureCListsOpenWithNestedAndUnknownFallback() throws Exception {
        Map<String, byte[]> parts = new LinkedHashMap<>();
        parts.put("word/numbering.xml", DocxTestFixtures.bytes(
                "<w:numbering xmlns:w=\"" + DocxTestFixtures.WORD_NAMESPACE + "\">"
                        + "<w:abstractNum w:abstractNumId=\"1\">"
                        + "<w:lvl w:ilvl=\"0\"><w:start w:val=\"2\"/>"
                        + "<w:numFmt w:val=\"upperRoman\"/><w:lvlText w:val=\"%1.\"/>"
                        + "</w:lvl><w:lvl w:ilvl=\"1\"><w:numFmt w:val=\"mystery\"/>"
                        + "<w:lvlText w:val=\"%2)\"/></w:lvl></w:abstractNum>"
                        + "<w:num w:numId=\"9\"><w:abstractNumId w:val=\"1\"/></w:num>"
                        + "</w:numbering>"
        ));
        String body = listParagraph(9, 0, "Roman") + listParagraph(9, 1, "Nested");
        WordDocumentModel document = parse(document(
                "fixture-c.docx",
                body,
                DocxTestFixtures.relationship("n", "numbering", "numbering.xml"),
                parts,
                ""
        ));

        assertEquals("II. Roman", paragraph(document, 0).getPlainText());
        assertTrue(paragraph(document, 1).getPlainText().endsWith("Nested"));
    }

    @Test
    public void fixtureDThreeByThreeTableWithMergesAndStylingOpens() throws Exception {
        StringBuilder table = new StringBuilder(
                "<w:tbl><w:tblPr><w:tblBorders><w:top w:val=\"single\"/>"
                        + "<w:bottom w:val=\"single\"/></w:tblBorders></w:tblPr>"
        );
        for (int row = 0; row < 3; row++) {
            table.append("<w:tr>");
            for (int column = 0; column < 3; column++) {
                table.append("<w:tc><w:tcPr>");
                if (row == 0 && column == 0) {
                    table.append("<w:gridSpan w:val=\"2\"/><w:vMerge w:val=\"restart\"/>")
                            .append("<w:shd w:fill=\"D9EAF7\"/>");
                }
                table.append("</w:tcPr>")
                        .append(DocxTestFixtures.paragraph("R" + row + "C" + column))
                        .append("</w:tc>");
            }
            table.append("</w:tr>");
        }
        table.append("</w:tbl>");

        WordTable parsed = (WordTable) parse(DocxTestFixtures.minimalDocument(
                fixture("fixture-d.docx"),
                table.toString()
        )).getBlocks().get(0);
        assertEquals(3, parsed.getRows().size());
        assertEquals(3, parsed.getRows().get(1).getCells().size());
        assertEquals(2, parsed.getRows().get(0).getCells().get(0).getGridSpan());
        assertEquals(Integer.valueOf(0xFFD9EAF7),
                parsed.getRows().get(0).getCells().get(0).getShadingColor());
    }

    @Test
    public void fixtureERasterAndBrokenOrExternalImagesDegrade() throws Exception {
        Map<String, byte[]> parts = new LinkedHashMap<>();
        parts.put("word/media/pixel.png", DocxTestFixtures.ONE_PIXEL_PNG);
        parts.put("word/media/pixel.jpg", DocxTestFixtures.ONE_PIXEL_JPEG);
        String relationships = DocxTestFixtures.relationship(
                "local",
                "image",
                "media/pixel.png"
        ) + DocxTestFixtures.relationship(
                "broken",
                "image",
                "media/not-there.png"
        ) + DocxTestFixtures.relationship(
                "jpeg",
                "image",
                "media/pixel.jpg"
        ) + DocxTestFixtures.externalRelationship(
                "remote",
                "image",
                "https://example.invalid/never-loaded.png"
        );
        String body = drawing("local", "Local")
                + drawing("jpeg", "JPEG")
                + drawing("broken", "Broken")
                + drawing("remote", "Remote")
                + drawing("missing", "Missing");
        WordDocumentModel document = parse(document(
                "fixture-e.docx",
                body,
                relationships,
                parts,
                "<Default Extension=\"jpg\" ContentType=\"image/jpeg\"/>"
        ));

        assertEquals(3, document.getImageCount());
        assertEquals("word/media/pixel.png", firstImage(document).getPackagePath());
    }

    @Test
    public void fixtureFSectionsMarginsOrientationAndPageBreakOpen() throws Exception {
        String body = "<w:p><w:r><w:t>Portrait</w:t><w:br w:type=\"page\"/>"
                + "</w:r></w:p><w:sectPr><w:pgSz w:w=\"15840\" w:h=\"12240\"/>"
                + "<w:pgMar w:top=\"720\" w:right=\"900\" w:bottom=\"720\""
                + " w:left=\"900\"/></w:sectPr>";
        WordDocumentModel document = parse(DocxTestFixtures.minimalDocument(
                fixture("fixture-f.docx"),
                body
        ));

        assertEquals(1, document.getSections().size());
        assertEquals(15_840, document.getSections().get(0).getPageWidthTwips());
        assertEquals(12_240, document.getSections().get(0).getPageHeightTwips());
        assertTrue(document.getBlocks().get(1) instanceof WordPageBreak);
    }

    @Test
    public void fixtureGTypicalWordPartsAndDuplicateStylesOpen() throws Exception {
        Map<String, byte[]> parts = new LinkedHashMap<>();
        parts.put("word/styles.xml", DocxTestFixtures.bytes(
                "<w:styles xmlns:w=\"" + DocxTestFixtures.WORD_NAMESPACE + "\">"
                        + "<w:style w:type=\"paragraph\" w:styleId=\"Normal\">"
                        + "<w:rPr><w:b/></w:rPr></w:style>"
                        + "<w:style w:type=\"paragraph\" w:styleId=\"Normal\">"
                        + "<w:rPr><w:i/></w:rPr></w:style></w:styles>"
        ));
        parts.put("word/settings.xml", DocxTestFixtures.bytes(
                "<w:settings xmlns:w=\"" + DocxTestFixtures.WORD_NAMESPACE
                        + "\"><w:compat/><w:zoom w:percent=\"100\"/></w:settings>"
        ));
        parts.put("word/theme/theme1.xml", DocxTestFixtures.bytes(
                "<a:theme xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\"/>"
        ));
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
                        + "<w:footnote w:id=\"1\">" + DocxTestFixtures.paragraph("Note")
                        + "</w:footnote></w:footnotes>"
        ));
        parts.put("docProps/core.xml", DocxTestFixtures.bytes("<coreProperties/>"));
        parts.put("docProps/app.xml", DocxTestFixtures.bytes("<Properties/>"));
        parts.put("customXml/item1.xml", DocxTestFixtures.bytes("<safeMetadata/>"));
        parts.put("word/comments.xml", DocxTestFixtures.bytes(
                "<w:comments xmlns:w=\"" + DocxTestFixtures.WORD_NAMESPACE + "\"/>"
        ));
        String relationships = DocxTestFixtures.relationship("s", "styles", "styles.xml")
                + DocxTestFixtures.relationship("settings", "settings", "settings.xml")
                + DocxTestFixtures.relationship("theme", "theme", "theme/theme1.xml")
                + DocxTestFixtures.relationship("header", "header", "header1.xml")
                + DocxTestFixtures.relationship("footer", "footer", "footer1.xml")
                + DocxTestFixtures.relationship("notes", "footnotes", "footnotes.xml")
                + DocxTestFixtures.relationship("comments", "comments", "comments.xml")
                + DocxTestFixtures.relationship("custom", "customXml", "../customXml/item1.xml");
        WordDocumentModel document = parse(document(
                "fixture-g.docx",
                "<w:sdt><w:sdtPr/><w:sdtContent><w:p><w:pPr>"
                        + "<w:pStyle w:val=\"Normal\"/></w:pPr>"
                        + "<w:r><w:t>Body</w:t></w:r></w:p>"
                        + "</w:sdtContent></w:sdt>",
                relationships,
                parts,
                ""
        ));

        assertTrue(DocxPackageInspector.inspect(
                fixturePath("fixture-g.docx").toFile()
        ).isDocx());
        assertEquals("Header", paragraph(document, 0).getPlainText());
        assertEquals("Body", paragraph(document, 1).getPlainText());
        assertTrue(paragraph(document, 1).getRuns().get(0).getStyle().isItalic());
        assertFalse(paragraph(document, 1).getRuns().get(0).getStyle().isBold());
        assertEquals("Footer", paragraph(document, 2).getPlainText());
        assertEquals("1. Note", paragraph(document, 3).getPlainText());
    }

    @Test
    public void fixtureHStrictNamespacesUnknownExtensionsAndAlternateContentOpen()
            throws Exception {
        LinkedHashMap<String, byte[]> entries = DocxTestFixtures.baseEntries("", "", "");
        entries.put("_rels/.rels", DocxTestFixtures.bytes(
                "<Relationships xmlns=\"http://purl.oclc.org/ooxml/package/relationships\">"
                        + "<Relationship Id=\"root\" Type=\"http://purl.oclc.org/ooxml/"
                        + "officeDocument/relationships/officeDocument\" "
                        + "Target=\"word/document.xml\"/></Relationships>"
        ));
        entries.put("word/document.xml", DocxTestFixtures.bytes(
                "<w:document xmlns:w=\"" + STRICT_WORD + "\" xmlns:mc=\"" + MC + "\""
                        + " xmlns:w14=\"http://schemas.microsoft.com/office/word/2010/wordml\""
                        + " mc:Ignorable=\"w14\"><w:body>"
                        + "<w:sdt><w:sdtContent><w:future><w:p>"
                        + "<w:r><w:t>BLOCK_WRONG</w:t></w:r></w:p></w:future>"
                        + "<w:p><w:r><w:t>Wrapped</w:t></w:r></w:p>"
                        + "</w:sdtContent></w:sdt><w:p>"
                        + "<w14:r><w:r><w:t>IGNORED</w:t></w:r></w14:r>"
                        + "<mc:AlternateContent><mc:Choice Requires=\"w\">"
                        + "<w:r><w:t>Choice</w:t></w:r></mc:Choice>"
                        + "<mc:Fallback><w:r><w:t>WRONG</w:t></w:r></mc:Fallback>"
                        + "</mc:AlternateContent>"
                        + "<mc:AlternateContent><mc:Choice Requires=\"w14\">"
                        + "<w14:future/></mc:Choice><mc:Fallback>"
                        + "<w:r><w:t>Fallback</w:t></w:r></mc:Fallback>"
                        + "</mc:AlternateContent><w14:future/>"
                        + "<w:r><w:rPr><w:color w14:val=\"FF0000\"/></w:rPr>"
                        + "<w:t>Safe</w:t></w:r></w:p>"
                        + "</w:body></w:document>"
        ));
        Path file = fixture("fixture-h.docx");
        DocxTestFixtures.writeDeflatedZip(file, entries);
        WordDocumentModel document = parse(file);

        assertEquals("Wrapped", paragraph(document, 0).getPlainText());
        WordParagraph paragraph = paragraph(document, 1);
        assertEquals("ChoiceFallbackSafe", paragraph.getPlainText());
        assertEquals(Integer.valueOf(0xFF1D2633),
                paragraph.getRuns().get(2).getStyle().getColor());
    }

    @Test(timeout = 20_000L)
    public void largeSyntheticHundredPageEquivalentOpensWithinBounds() throws Exception {
        StringBuilder body = new StringBuilder();
        for (int page = 0; page < 100; page++) {
            body.append("<w:p><w:r><w:rPr><w:b/></w:rPr><w:t>Page ")
                    .append(page + 1)
                    .append("</w:t></w:r></w:p>");
            for (int paragraph = 0; paragraph < 20; paragraph++) {
                body.append(DocxTestFixtures.paragraph(
                        "Synthetic paragraph " + page + "." + paragraph
                                + " with bounded local reader content."
                ));
            }
            if (page % 10 == 0) {
                body.append("<w:tbl><w:tr><w:tc>")
                        .append(DocxTestFixtures.paragraph("Table " + page))
                        .append("</w:tc></w:tr></w:tbl>")
                        .append(drawing("image", "Synthetic image"));
            }
            if (page < 99) {
                body.append("<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>");
            }
        }
        LinkedHashMap<String, byte[]> entries = DocxTestFixtures.baseEntries(
                body.toString(),
                DocxTestFixtures.relationship("image", "image", "media/pixel.png"),
                ""
        );
        entries.put("word/media/pixel.png", DocxTestFixtures.ONE_PIXEL_PNG);
        Path file = fixture("fixture-performance.docx");
        DocxTestFixtures.writeDeflatedZip(file, entries);

        WordDocumentModel document = parse(file);
        assertEquals(2_219, document.getParagraphCount());
        assertEquals(10, document.getTableCount());
        assertEquals(10, document.getImageCount());
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

    private WordDocumentModel parse(Path path) throws Exception {
        return parser.parse(path.toFile(), null);
    }

    private Path fixture(String name) throws Exception {
        return temporaryFolder.newFile(name).toPath();
    }

    private Path fixturePath(String name) {
        return temporaryFolder.getRoot().toPath().resolve(name);
    }

    private WordParagraph paragraph(WordDocumentModel document, int index) {
        return (WordParagraph) document.getBlocks().get(index);
    }

    private WordImage firstImage(WordDocumentModel document) {
        for (WordBlock block : document.getBlocks()) {
            if (block instanceof WordImage) return (WordImage) block;
        }
        throw new AssertionError("Expected image block");
    }

    private String listParagraph(int numberId, int level, String text) {
        return "<w:p><w:pPr><w:numPr><w:ilvl w:val=\"" + level
                + "\"/><w:numId w:val=\"" + numberId
                + "\"/></w:numPr></w:pPr><w:r><w:t>" + text
                + "</w:t></w:r></w:p>";
    }

    private String drawing(String relationshipId, String description) {
        return "<w:p><w:r><w:drawing><wp:inline><wp:extent cx=\"914400\""
                + " cy=\"457200\"/><wp:docPr id=\"1\" name=\"Image\" descr=\""
                + description + "\"/><a:graphic><a:graphicData><pic:pic>"
                + "<pic:blipFill><a:blip r:embed=\"" + relationshipId
                + "\"/></pic:blipFill></pic:pic></a:graphicData></a:graphic>"
                + "</wp:inline></w:drawing></w:r></w:p>";
    }
}
