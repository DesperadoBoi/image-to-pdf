package com.desperadoboi.imagetopdf.document;

import com.desperadoboi.imagetopdf.document.spreadsheet.XlsxTestFixtures;
import com.desperadoboi.imagetopdf.document.word.DocxTestFixtures;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public final class DocumentTypeResolverTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private final DocumentTypeResolver resolver = new DocumentTypeResolver();

    @Test
    public void resolvesSupportedMimeTypes() {
        assertEquals(DocumentType.PDF, resolver.fromMimeType("application/pdf"));
        assertEquals(DocumentType.XLS, resolver.fromMimeType("application/vnd.ms-excel"));
        assertEquals(DocumentType.XLSX, resolver.fromMimeType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        ));
        assertEquals(DocumentType.XLSM, resolver.fromMimeType(
                "application/vnd.ms-excel.sheet.macroenabled.12"
        ));
        assertEquals(DocumentType.DOCX, resolver.fromMimeType(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        ));
        assertEquals(DocumentType.CSV, resolver.fromMimeType("application/csv"));
        assertEquals(DocumentType.TSV, resolver.fromMimeType("text/tab-separated-values"));
        assertEquals(DocumentType.TEXT, resolver.fromMimeType("text/plain"));
        assertEquals(DocumentType.JPEG, resolver.fromMimeType("image/jpeg"));
    }

    @Test
    public void resolvesStrongSignatures() {
        assertEquals(DocumentType.PDF, resolve(bytes("%PDF-1.7"), "unknown.bin"));
        assertEquals(DocumentType.XLS, resolve(new byte[]{
                (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
                (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1
        }, "unknown.bin"));
        assertEquals(DocumentType.PNG, resolve(new byte[]{
                (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A
        }, "unknown.bin"));
        assertEquals(DocumentType.JPEG, resolve(new byte[]{
                (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00
        }, "unknown.bin"));
        assertEquals(DocumentType.WEBP, resolve(bytes("RIFF1234WEBP"), "unknown.bin"));
        assertEquals(DocumentType.HEIC, resolve(new byte[]{
                0, 0, 0, 24, 'f', 't', 'y', 'p', 'h', 'e', 'i', 'c'
        }, "unknown.bin"));
        assertEquals(DocumentType.XLS, resolver.resolve(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                new byte[]{
                        (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
                        (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1
                },
                Collections.emptySet(),
                "msf:1000009229"
        ));
    }

    @Test
    public void resolvesOnlyStructurallyValidXlsx() throws Exception {
        Path xlsx = XlsxTestFixtures.minimalWorkbook(
                fixture("valid.xlsx").toPath(),
                XlsxTestFixtures.worksheet(
                        "<sheetData><row r=\"1\"><c r=\"A1\"><v>1</v></c></row></sheetData>"
                )
        );
        assertEquals(DocumentType.XLSX, resolveFile(
                xlsx.toFile(),
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "valid.xlsx"
        ));
        assertEquals(DocumentType.XLSX, resolveFile(
                xlsx.toFile(),
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "macro.xlsm"
        ));

        Map<String, byte[]> ordinaryEntries = new LinkedHashMap<>();
        ordinaryEntries.put("word/document.xml", XlsxTestFixtures.bytes("<document/>"));
        File ordinaryZip = fixture("ordinary.zip");
        XlsxTestFixtures.writeStoredZip(ordinaryZip.toPath(), ordinaryEntries);
        assertEquals(DocumentType.UNKNOWN, resolveFile(
                ordinaryZip,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "renamed.xlsx"
        ));

        Map<String, byte[]> incompleteEntries = new LinkedHashMap<>();
        incompleteEntries.put("[Content_Types].xml", XlsxTestFixtures.bytes("<Types/>"));
        File incomplete = fixture("incomplete.xlsx");
        XlsxTestFixtures.writeStoredZip(incomplete.toPath(), incompleteEntries);
        assertEquals(DocumentType.UNKNOWN, resolveFile(incomplete, null, "incomplete.xlsx"));

        Map<String, byte[]> invalidContentTypes = readEntries(xlsx.toFile());
        invalidContentTypes.put(
                "[Content_Types].xml",
                XlsxTestFixtures.bytes(
                        "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                                + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/xml\"/>"
                                + "</Types>"
                )
        );
        File wrongContentTypes = fixture("wrong-content-types.xlsx");
        XlsxTestFixtures.writeStoredZip(wrongContentTypes.toPath(), invalidContentTypes);
        assertDetectionFailure(
                wrongContentTypes,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "wrong-content-types.xlsx",
                OoxmlPackageDetector.Reason.CORRUPTED
        );

        Map<String, byte[]> externalWorkbookRelationship = readEntries(xlsx.toFile());
        externalWorkbookRelationship.put(
                "xl/_rels/workbook.xml.rels",
                XlsxTestFixtures.bytes(
                        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"https://example.invalid/sheet.xml\" TargetMode=\"External\"/>"
                                + "</Relationships>"
                )
        );
        File externalSheet = fixture("external-sheet.xlsx");
        XlsxTestFixtures.writeStoredZip(externalSheet.toPath(), externalWorkbookRelationship);
        assertEquals(DocumentType.UNKNOWN, resolveFile(
                externalSheet,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "external-sheet.xlsx"
        ));
    }

    @Test
    public void xlsxPackageContentsOverrideNameAndMimeHints() throws Exception {
        Path xlsx = XlsxTestFixtures.minimalWorkbook(
                fixture("content-wins.xlsx").toPath(),
                XlsxTestFixtures.worksheet(
                        "<sheetData><row r=\"1\"><c r=\"A1\"><v>1</v></c></row></sheetData>"
                )
        );
        assertEquals(DocumentType.XLSX, resolveFile(
                xlsx.toFile(),
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "content-wins.xlsx"
        ));
        assertEquals(DocumentType.XLSX, resolveFile(xlsx.toFile(), null, "content-wins"));
        assertEquals(DocumentType.XLSX, resolveFile(
                xlsx.toFile(),
                null,
                "msf:1000009229"
        ));
        assertEquals(DocumentType.XLSX, resolveFile(
                xlsx.toFile(),
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "wrong.docx"
        ));
        assertEquals(DocumentType.XLSX, resolveFile(
                xlsx.toFile(),
                "application/octet-stream",
                "msf:1000009229"
        ));
        assertEquals(DocumentType.XLSX, resolveFile(
                xlsx.toFile(),
                "application/vnd.ms-excel.sheet.macroenabled.12",
                "wrong.xlsm"
        ));
    }

    @Test
    public void xlsxMimeAndExtensionNeverOverrideWrongSignature() throws Exception {
        File text = fixture("wrong.xlsx");
        Files.write(text.toPath(), bytes("plain text"));
        assertEquals(DocumentType.UNKNOWN, resolveFile(
                text,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "wrong.xlsx"
        ));
        assertEquals(DocumentType.UNKNOWN, resolveFile(
                text,
                "application/vnd.ms-excel.sheet.macroenabled.12",
                "wrong.xlsm"
        ));
    }

    @Test
    public void resolvesOnlyStructurallyValidDocx() throws Exception {
        Path docx = DocxTestFixtures.minimalDocument(
                fixture("valid.docx").toPath(),
                DocxTestFixtures.paragraph("Привет, Word")
        );
        assertEquals(DocumentType.DOCX, resolveFile(
                docx.toFile(),
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "valid.docx"
        ));
        assertEquals(DocumentType.DOCX, resolveFile(
                docx.toFile(),
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "wrong.xlsx"
        ));

        Map<String, byte[]> ordinaryEntries = new LinkedHashMap<>();
        ordinaryEntries.put("note.txt", DocxTestFixtures.bytes("ordinary zip"));
        File ordinaryZip = fixture("ordinary-docx.zip");
        DocxTestFixtures.writeStoredZip(ordinaryZip.toPath(), ordinaryEntries);
        assertEquals(DocumentType.UNKNOWN, resolveFile(
                ordinaryZip,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "renamed.docx"
        ));

        LinkedHashMap<String, byte[]> missing =
                DocxTestFixtures.baseEntries(DocxTestFixtures.paragraph("x"), "", "");
        missing.remove("word/document.xml");
        File missingDocument = fixture("missing-document.docx");
        DocxTestFixtures.writeStoredZip(missingDocument.toPath(), missing);
        assertDetectionFailure(
                missingDocument,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "missing-document.docx",
                OoxmlPackageDetector.Reason.CORRUPTED
        );
    }

    @Test
    public void docxMimeAndExtensionNeverOverrideWrongSignature() throws Exception {
        File text = fixture("wrong.docx");
        Files.write(text.toPath(), bytes("plain text"));
        assertEquals(DocumentType.UNKNOWN, resolveFile(
                text,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "wrong.docx"
        ));
        assertEquals(DocumentType.UNKNOWN, resolveFile(
                text,
                null,
                "wrong.docx"
        ));
    }

    @Test
    public void extensionIsFallbackForTextFormats() {
        assertEquals(DocumentType.CSV, resolver.resolve(
                null,
                bytes("a,b"),
                Collections.emptySet(),
                "report.csv"
        ));
        assertEquals(DocumentType.TEXT, resolver.resolve(
                null,
                bytes("hello"),
                Collections.emptySet(),
                "note.txt"
        ));
    }

    @Test
    public void strongSignatureWinsMimeMismatch() {
        assertEquals(DocumentType.PDF, resolver.resolve(
                "image/png",
                bytes("%PDF-1.4"),
                Collections.emptySet(),
                "image.png"
        ));
    }

    @Test
    public void binaryUnknownFileRemainsUnknown() {
        assertEquals(DocumentType.UNKNOWN, resolver.resolve(
                null,
                new byte[]{0, 1, 2, 3, 4, 5},
                Collections.emptySet(),
                "unknown.bin"
        ));
    }

    @Test
    public void detectsMacroEnabledWorkbookFromPackageContents() throws Exception {
        Path xlsx = XlsxTestFixtures.minimalWorkbook(
                fixture("macro-source.xlsx").toPath(),
                XlsxTestFixtures.worksheet("<sheetData/>")
        );
        Map<String, byte[]> entries = readEntries(xlsx.toFile());
        String contentTypes = new String(
                entries.get("[Content_Types].xml"),
                StandardCharsets.UTF_8
        ).replace(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml",
                "application/vnd.ms-excel.sheet.macroEnabled.main+xml"
        );
        entries.put("[Content_Types].xml", contentTypes.getBytes(StandardCharsets.UTF_8));
        entries.put("xl/vbaProject.bin", new byte[]{1, 2, 3});
        File xlsm = fixture("macro-content.bin");
        XlsxTestFixtures.writeStoredZip(xlsm.toPath(), entries);

        assertEquals(DocumentType.XLSM, resolveFile(
                xlsm,
                "application/octet-stream",
                "msf:1000009229"
        ));
    }

    @Test
    public void detectsPresentationPackageWithoutTrustingSpreadsheetMime() throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put(
                "[Content_Types].xml",
                XlsxTestFixtures.bytes(
                        "<Types><Override PartName=\"/ppt/presentation.xml\" "
                                + "ContentType=\"application/vnd.openxmlformats-officedocument."
                                + "presentationml.presentation.main+xml\"/></Types>"
                )
        );
        entries.put(
                "_rels/.rels",
                XlsxTestFixtures.bytes(
                        "<Relationships><Relationship Id=\"rId1\" "
                                + "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/"
                                + "relationships/officeDocument\" "
                                + "Target=\"ppt/presentation.xml\"/></Relationships>"
                )
        );
        entries.put("ppt/presentation.xml", XlsxTestFixtures.bytes("<presentation/>"));
        File pptx = fixture("presentation.bin");
        XlsxTestFixtures.writeStoredZip(pptx.toPath(), entries);

        assertEquals(DocumentType.PPTX, resolveFile(
                pptx,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "wrong.xlsx"
        ));
    }

    @Test
    public void damagedZipIsReportedAsCorruptedPackage() throws Exception {
        File damaged = fixture("damaged.xlsx");
        Files.write(damaged.toPath(), new byte[]{'P', 'K', 3, 4, 1, 2, 3});
        try {
            resolveFile(
                    damaged,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "damaged.xlsx"
            );
            fail("Expected damaged ZIP detection failure");
        } catch (OoxmlPackageDetector.DetectionException exception) {
            assertEquals(OoxmlPackageDetector.Reason.CORRUPTED, exception.getReason());
        }
    }

    @Test
    public void maliciousZipPathIsRejectedDuringDetection() throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("../xl/workbook.xml", XlsxTestFixtures.bytes("<workbook/>"));
        entries.put("[Content_Types].xml", XlsxTestFixtures.bytes("<Types/>"));
        File malicious = fixture("malicious.xlsx");
        XlsxTestFixtures.writeStoredZip(malicious.toPath(), entries);
        try {
            resolveFile(
                    malicious,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "malicious.xlsx"
            );
            fail("Expected unsafe ZIP path failure");
        } catch (OoxmlPackageDetector.DetectionException exception) {
            assertEquals(OoxmlPackageDetector.Reason.CORRUPTED, exception.getReason());
        }
    }

    @Test
    public void emptyFileIsUnknown() throws Exception {
        File empty = fixture("empty.xlsx");
        assertEquals(DocumentType.UNKNOWN, resolveFile(
                empty,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "empty.xlsx"
        ));
    }

    private DocumentType resolveFile(File file, String mime, String displayName) throws Exception {
        byte[] contents = Files.readAllBytes(file.toPath());
        byte[] prefix = new byte[Math.min(8 * 1024, contents.length)];
        System.arraycopy(contents, 0, prefix, 0, prefix.length);
        return resolver.resolve(mime, prefix, file, displayName);
    }

    private void assertDetectionFailure(
            File file,
            String mime,
            String displayName,
            OoxmlPackageDetector.Reason expected
    ) throws Exception {
        try {
            resolveFile(file, mime, displayName);
            fail("Expected OOXML detection failure for " + file.getName());
        } catch (OoxmlPackageDetector.DetectionException exception) {
            assertEquals(expected, exception.getReason());
        }
    }

    private DocumentType resolve(byte[] signature, String displayName) {
        return resolver.resolve(null, signature, Collections.emptySet(), displayName);
    }

    private File fixture(String name) throws Exception {
        return temporaryFolder.newFile(name);
    }

    private Map<String, byte[]> readEntries(File file) throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipFile zipFile = new ZipFile(file)) {
            java.util.Enumeration<? extends ZipEntry> enumeration = zipFile.entries();
            while (enumeration.hasMoreElements()) {
                ZipEntry entry = enumeration.nextElement();
                entries.put(entry.getName(), zipFile.getInputStream(entry).readAllBytes());
            }
        }
        return entries;
    }

    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }
}
