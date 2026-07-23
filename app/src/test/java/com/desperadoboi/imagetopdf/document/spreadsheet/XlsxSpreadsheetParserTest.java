package com.desperadoboi.imagetopdf.document.spreadsheet;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class XlsxSpreadsheetParserTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private final XlsxSpreadsheetParser parser = new XlsxSpreadsheetParser();

    @Test
    public void parsesSheetsStringsNumbersBooleansDatesFormulaBlankAndMerge() throws Exception {
        String firstSheet = XlsxTestFixtures.worksheet(
                "<dimension ref=\"A1:J3\"/><sheetData>"
                        + "<row r=\"1\">"
                        + "<c r=\"A1\" t=\"s\"><v>0</v></c>"
                        + "<c r=\"B1\" t=\"inlineStr\"><is><r><t>Inline </t></r><r><t>rich</t></r></is></c>"
                        + "<c r=\"C1\"><v>42</v></c>"
                        + "<c r=\"D1\"><v>3.1400</v></c>"
                        + "<c r=\"E1\" t=\"b\"><v>1</v></c>"
                        + "<c r=\"F1\" t=\"e\"><v>#N/A</v></c>"
                        + "<c r=\"G1\"><f>SUM(C1:D1)</f><v>45.14</v></c>"
                        + "<c r=\"H1\"/>"
                        + "<c r=\"J1\" t=\"s\"><v>1</v></c>"
                        + "</row>"
                        + "<row r=\"2\"><c r=\"A2\" s=\"1\"><v>61</v></c></row>"
                        + "<row r=\"3\"><c r=\"A3\" t=\"inlineStr\"><is><t>Merged</t></is></c>"
                        + "<c r=\"B3\" t=\"inlineStr\"><is><t>hidden</t></is></c></row>"
                        + "</sheetData><mergeCells count=\"1\"><mergeCell ref=\"A3:B3\"/></mergeCells>"
        );
        String secondSheet = XlsxTestFixtures.worksheet(
                "<sheetData><row r=\"1\"><c r=\"A1\" t=\"str\"><v>Second</v></c>"
                        + "</row></sheetData>"
        );
        Path file = XlsxTestFixtures.workbook(
                fixture("values.xlsx"),
                false,
                new String[]{"Summary", "Raw data"},
                new String[]{"worksheets/sheet7.xml", "worksheets/sheet2.xml"},
                new String[]{firstSheet, secondSheet},
                XlsxTestFixtures.sharedStrings(
                        "<si><t>Shared</t></si><si><r><t>Rich </t></r><r><t>text</t></r></si>"
                ),
                XlsxTestFixtures.styles(
                        "<cellXfs count=\"2\"><xf numFmtId=\"0\"/><xf numFmtId=\"14\"/></cellXfs>"
                )
        );

        XlsxWorkbook workbook = parser.parse(file.toFile());
        assertEquals(2, workbook.getSheets().size());
        assertEquals("Summary", workbook.getSheets().get(0).getName());
        assertEquals("Raw data", workbook.getSheets().get(1).getName());
        List<List<String>> rows = workbook.getSheets().get(0).getData().getRows();
        assertEquals("Shared", rows.get(0).get(0));
        assertEquals("Inline rich", rows.get(0).get(1));
        assertEquals("42", rows.get(0).get(2));
        assertEquals("3.14", rows.get(0).get(3));
        assertEquals("TRUE", rows.get(0).get(4));
        assertEquals("#N/A", rows.get(0).get(5));
        assertEquals("45.14", rows.get(0).get(6));
        assertEquals("", rows.get(0).get(7));
        assertEquals("Rich text", rows.get(0).get(9));
        assertEquals("1900-03-01", rows.get(1).get(0));
        assertEquals("Merged", rows.get(2).get(0));
        assertEquals("", rows.get(2).get(1));
        assertEquals("A1:J3", workbook.getSheets().get(0).getUsedRange());
        assertEquals("Second", workbook.getSheets().get(1).getData().getRows().get(0).get(0));
    }

    @Test
    public void supportsDate1904CustomDateTimeAndUnknownStyle() throws Exception {
        Path file = XlsxTestFixtures.workbook(
                fixture("dates.xlsx"),
                true,
                new String[]{"Dates"},
                new String[]{"worksheets/sheet1.xml"},
                new String[]{XlsxTestFixtures.worksheet(
                        "<sheetData><row r=\"1\">"
                                + "<c r=\"A1\" s=\"1\"><v>0</v></c>"
                                + "<c r=\"B1\" s=\"2\"><v>1.5</v></c>"
                                + "<c r=\"C1\" s=\"99\"><v>2.5</v></c>"
                                + "<c r=\"D1\" s=\"3\"><v>42</v></c>"
                                + "</row></sheetData>"
                )},
                null,
                XlsxTestFixtures.styles(
                        "<numFmts count=\"2\"><numFmt numFmtId=\"165\" formatCode=\"yyyy-mm-dd hh:mm\"/>"
                                + "<numFmt numFmtId=\"166\" formatCode=\"[Red]0\"/></numFmts>"
                                + "<cellXfs count=\"4\"><xf numFmtId=\"0\"/><xf numFmtId=\"14\"/>"
                                + "<xf numFmtId=\"165\"/><xf numFmtId=\"166\"/></cellXfs>"
                )
        );

        XlsxWorkbook workbook = parser.parse(file.toFile());
        List<String> row = workbook.getSheets().get(0).getData().getRows().get(0);
        assertTrue(workbook.usesDate1904());
        assertEquals("1904-01-01", row.get(0));
        assertEquals("1904-01-02 12:00:00", row.get(1));
        assertEquals("2.5", row.get(2));
        assertEquals("42", row.get(3));
    }

    @Test
    public void preservesVisualStylesRealDimensionsAndMergedGeometry() throws Exception {
        Path file = XlsxTestFixtures.workbook(
                fixture("styled.xlsx"),
                false,
                new String[]{"Styled"},
                new String[]{"worksheets/sheet1.xml"},
                new String[]{XlsxTestFixtures.worksheet(
                        "<dimension ref=\"A1:B2\"/>"
                                + "<sheetFormatPr defaultColWidth=\"9\" defaultRowHeight=\"16\"/>"
                                + "<cols><col min=\"1\" max=\"1\" width=\"22.5\" "
                                + "customWidth=\"1\"/></cols>"
                                + "<sheetData><row r=\"1\" ht=\"30\" customHeight=\"1\">"
                                + "<c r=\"A1\" s=\"1\" t=\"inlineStr\"><is><t>Green title</t>"
                                + "</is></c></row><row r=\"2\"><c r=\"B2\"><v>7</v></c>"
                                + "</row></sheetData>"
                                + "<mergeCells count=\"1\"><mergeCell ref=\"A1:B1\"/>"
                                + "</mergeCells>"
                )},
                null,
                XlsxTestFixtures.styles(
                        "<fonts count=\"2\"><font><sz val=\"11\"/></font>"
                                + "<font><b/><i/><u/><sz val=\"12\"/>"
                                + "<color rgb=\"FFFFFFFF\"/></font></fonts>"
                                + "<fills count=\"3\"><fill><patternFill patternType=\"none\"/>"
                                + "</fill><fill><patternFill patternType=\"gray125\"/></fill>"
                                + "<fill><patternFill patternType=\"solid\">"
                                + "<fgColor rgb=\"0000B050\"/></patternFill></fill></fills>"
                                + "<borders count=\"2\"><border/><border>"
                                + "<left style=\"thin\"><color rgb=\"FF008000\"/></left>"
                                + "<top style=\"medium\"><color rgb=\"FF008000\"/></top>"
                                + "<right style=\"thin\"><color rgb=\"FF008000\"/></right>"
                                + "<bottom style=\"medium\"><color rgb=\"FF008000\"/></bottom>"
                                + "</border></borders>"
                                + "<cellXfs count=\"2\"><xf numFmtId=\"0\" fontId=\"0\" "
                                + "fillId=\"0\" borderId=\"0\"/><xf numFmtId=\"0\" fontId=\"1\" "
                                + "fillId=\"2\" borderId=\"1\"><alignment horizontal=\"center\" "
                                + "vertical=\"center\" wrapText=\"1\"/></xf></cellXfs>"
                )
        );

        XlsxSheet sheet = parser.parse(file.toFile()).getSheets().get(0);
        SpreadsheetSheetLayout layout = sheet.getLayout();
        SpreadsheetCellStyle style = layout.getCellStyle(0, 0);

        assertEquals(2, layout.getRowCount());
        assertEquals(2, layout.getColumnCount());
        assertEquals(22.5f, layout.getColumnWidthCharacters(0), 0.001f);
        assertEquals(9f, layout.getColumnWidthCharacters(1), 0.001f);
        assertEquals(30f, layout.getRowHeightPoints(0), 0.001f);
        assertEquals(16f, layout.getRowHeightPoints(1), 0.001f);
        assertEquals(Integer.valueOf(0xFF00B050), style.getFillColor());
        assertEquals(Integer.valueOf(0xFFFFFFFF), style.getFontColor());
        assertTrue(style.isBold());
        assertTrue(style.isItalic());
        assertTrue(style.isUnderline());
        assertTrue(style.isWrapText());
        assertEquals(12f, style.getFontSizePoints(), 0.001f);
        assertEquals(
                SpreadsheetCellStyle.HorizontalAlignment.CENTER,
                style.getHorizontalAlignment()
        );
        assertEquals(
                SpreadsheetCellStyle.VerticalAlignment.CENTER,
                style.getVerticalAlignment()
        );
        assertEquals(SpreadsheetBorder.Style.MEDIUM,
                style.getBottomBorder().getStyle());
        assertEquals(1, layout.getMergedRanges().size());
        SpreadsheetMergedRange merged = layout.findMergedRange(0, 1);
        assertNotNull(merged);
        assertTrue(merged.isAnchor(0, 0));
    }

    @Test
    public void handlesFormulaWithoutCacheUnevenRowsAndMissingOptionalParts() throws Exception {
        Path file = XlsxTestFixtures.minimalWorkbook(
                fixture("minimal.xlsx"),
                XlsxTestFixtures.worksheet(
                        "<sheetData><row r=\"1\"><c r=\"A1\"><f>1+1</f></c></row>"
                                + "<row r=\"3\"><c r=\"C3\" t=\"str\"><v>tail</v></c></row>"
                                + "</sheetData>"
                )
        );

        XlsxSheet sheet = parser.parse(file.toFile()).getSheets().get(0);
        assertEquals("=1+1", sheet.getData().getRows().get(0).get(0));
        assertTrue(sheet.getData().getRows().get(1).isEmpty());
        assertEquals("tail", sheet.getData().getRows().get(2).get(2));
    }

    @Test
    public void excessiveRowsAndColumnsReturnPartialPreview() throws Exception {
        Path file = XlsxTestFixtures.minimalWorkbook(
                fixture("large-grid.xlsx"),
                XlsxTestFixtures.worksheet(
                        "<sheetData><row r=\"1\"><c r=\"A1\"><v>1</v></c>"
                                + "<c r=\"CW1\"><v>outside</v></c></row>"
                                + "<row r=\"5001\"><c r=\"A5001\"><v>outside</v></c></row>"
                                + "</sheetData>"
                )
        );

        XlsxWorkbook workbook = parser.parse(file.toFile());
        assertTrue(workbook.isTruncated());
        assertEquals(1, workbook.getSheets().get(0).getData().getRows().size());
        assertEquals("1", workbook.getSheets().get(0).getData().getRows().get(0).get(0));
    }

    @Test
    public void truncatesCellTextAtSafeLimit() throws Exception {
        String longText = "x".repeat(5_000);
        Path file = XlsxTestFixtures.minimalWorkbook(
                fixture("long-cell.xlsx"),
                XlsxTestFixtures.worksheet(
                        "<sheetData><row r=\"1\"><c r=\"A1\" t=\"inlineStr\"><is><t>"
                                + longText
                                + "</t></is></c></row></sheetData>"
                )
        );

        XlsxWorkbook workbook = parser.parse(file.toFile());
        assertTrue(workbook.isTruncated());
        assertEquals(
                4_096,
                workbook.getSheets().get(0).getData().getRows().get(0).get(0).length()
        );
    }

    @Test
    public void rejectsDamagedXmlMissingWorkbookFakeZipAndTraversal() throws Exception {
        Path damaged = XlsxTestFixtures.minimalWorkbook(
                fixture("damaged.xlsx"),
                "<worksheet><sheetData>"
        );
        assertReason(damaged, XlsxParseException.Reason.CORRUPTED);

        Map<String, byte[]> fakeEntries = new LinkedHashMap<>();
        fakeEntries.put("note.txt", XlsxTestFixtures.bytes("not a workbook"));
        Path fake = fixture("fake.xlsx");
        XlsxTestFixtures.writeStoredZip(fake, fakeEntries);
        assertReason(fake, XlsxParseException.Reason.CORRUPTED);

        Map<String, byte[]> missingEntries = new LinkedHashMap<>();
        missingEntries.put("[Content_Types].xml", XlsxTestFixtures.bytes("<Types/>"));
        Path missing = fixture("missing-workbook.xlsx");
        XlsxTestFixtures.writeStoredZip(missing, missingEntries);
        assertReason(missing, XlsxParseException.Reason.CORRUPTED);

        Map<String, byte[]> traversalEntries = new LinkedHashMap<>();
        traversalEntries.put("../outside.xml", XlsxTestFixtures.bytes("x"));
        Path traversal = fixture("traversal.xlsx");
        XlsxTestFixtures.writeStoredZip(traversal, traversalEntries);
        assertReason(traversal, XlsxParseException.Reason.CORRUPTED);
    }

    @Test
    public void rejectsExcessiveCompressionRatio() throws Exception {
        Path base = XlsxTestFixtures.minimalWorkbook(
                fixture("base.xlsx"),
                XlsxTestFixtures.worksheet(
                        "<sheetData><row r=\"1\"><c r=\"A1\"><v>1</v></c></row></sheetData>"
                )
        );
        Map<String, byte[]> entries = readEntries(base);
        entries.put("xl/media/padding.txt", new byte[2 * 1024 * 1024]);
        Path bomb = fixture("ratio.xlsx");
        XlsxTestFixtures.writeDeflatedZip(bomb, entries);

        assertReason(bomb, XlsxParseException.Reason.TOO_LARGE);
    }

    @Test
    public void rejectsMacrosExternalLinksOleAndExecutables() throws Exception {
        Path base = XlsxTestFixtures.minimalWorkbook(
                fixture("safe-base.xlsx"),
                XlsxTestFixtures.worksheet(
                        "<sheetData><row r=\"1\"><c r=\"A1\"><v>1</v></c></row></sheetData>"
                )
        );
        String[] forbiddenParts = {
                "xl/vbaProject.bin",
                "xl/externalLinks/externalLink1.xml",
                "xl/embeddings/oleObject1.bin",
                "xl/media/payload.exe"
        };
        for (int index = 0; index < forbiddenParts.length; index++) {
            Map<String, byte[]> entries = readEntries(base);
            entries.put(forbiddenParts[index], new byte[]{1, 2, 3});
            Path file = fixture("forbidden-" + index + ".xlsx");
            XlsxTestFixtures.writeStoredZip(file, entries);
            assertReason(file, XlsxParseException.Reason.UNSUPPORTED);
        }
    }

    @Test
    public void rejectsEncryptedZipFlag() throws Exception {
        Path base = XlsxTestFixtures.minimalWorkbook(
                fixture("unencrypted.xlsx"),
                XlsxTestFixtures.worksheet(
                        "<sheetData><row r=\"1\"><c r=\"A1\"><v>1</v></c></row></sheetData>"
                )
        );
        byte[] archive = Files.readAllBytes(base);
        for (int index = 0; index + 10 < archive.length; index++) {
            if (archive[index] == 'P' && archive[index + 1] == 'K'
                    && archive[index + 2] == 3 && archive[index + 3] == 4) {
                archive[index + 6] |= 1;
            } else if (archive[index] == 'P' && archive[index + 1] == 'K'
                    && archive[index + 2] == 1 && archive[index + 3] == 2) {
                archive[index + 8] |= 1;
            }
        }
        Path encrypted = fixture("encrypted.xlsx");
        Files.write(encrypted, archive);
        assertReason(encrypted, XlsxParseException.Reason.UNSUPPORTED);
    }

    private Path fixture(String name) throws Exception {
        return temporaryFolder.newFile(name).toPath();
    }

    private void assertReason(Path file, XlsxParseException.Reason expected) throws Exception {
        try {
            parser.parse(file.toFile());
            fail("Expected parser failure for " + file);
        } catch (XlsxParseException exception) {
            assertEquals(expected, exception.getReason());
        }
    }

    private Map<String, byte[]> readEntries(Path file) throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipFile zipFile = new ZipFile(file.toFile())) {
            java.util.Enumeration<? extends ZipEntry> enumeration = zipFile.entries();
            while (enumeration.hasMoreElements()) {
                ZipEntry entry = enumeration.nextElement();
                entries.put(entry.getName(), zipFile.getInputStream(entry).readAllBytes());
            }
        }
        return entries;
    }
}
