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
        assertEquals(DocumentType.UNKNOWN, resolveFile(
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
        assertEquals(DocumentType.UNKNOWN, resolveFile(
                wrongContentTypes,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "wrong-content-types.xlsx"
        ));

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
    public void xlsxMimeAndExtensionNeverOverrideWrongSignature() throws Exception {
        File text = fixture("wrong.xlsx");
        Files.write(text.toPath(), bytes("plain text"));
        assertEquals(DocumentType.UNKNOWN, resolveFile(
                text,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "wrong.xlsx"
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
        assertEquals(DocumentType.UNKNOWN, resolveFile(
                missingDocument,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "missing-document.docx"
        ));
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

    private DocumentType resolveFile(File file, String mime, String displayName) throws Exception {
        byte[] contents = Files.readAllBytes(file.toPath());
        byte[] prefix = new byte[Math.min(8 * 1024, contents.length)];
        System.arraycopy(contents, 0, prefix, 0, prefix.length);
        return resolver.resolve(mime, prefix, file, displayName);
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
