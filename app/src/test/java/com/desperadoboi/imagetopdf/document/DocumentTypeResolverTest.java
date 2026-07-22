package com.desperadoboi.imagetopdf.document;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import static org.junit.Assert.assertEquals;

public class DocumentTypeResolverTest {
    private final DocumentTypeResolver resolver = new DocumentTypeResolver();

    @Test
    public void resolvesSupportedMimeTypes() {
        assertEquals(DocumentType.PDF, resolveMime("application/pdf"));
        assertEquals(DocumentType.XLS, resolveMime("application/vnd.ms-excel"));
        assertEquals(DocumentType.XLSX, resolveMime(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        ));
        assertEquals(DocumentType.CSV, resolveMime("application/csv"));
        assertEquals(DocumentType.TSV, resolveMime("text/tab-separated-values"));
        assertEquals(DocumentType.TEXT, resolveMime("text/plain"));
        assertEquals(DocumentType.JPEG, resolveMime("image/jpeg"));
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
    public void resolvesXlsxOnlyWithRequiredWorkbookEntries() {
        byte[] zip = new byte[]{'P', 'K', 3, 4};
        assertEquals(DocumentType.XLSX, resolver.resolve(
                null,
                zip,
                new HashSet<>(Arrays.asList("[Content_Types].xml", "xl/workbook.xml")),
                "unknown.bin"
        ));
        assertEquals(DocumentType.UNKNOWN, resolver.resolve(
                null,
                zip,
                Collections.singleton("word/document.xml"),
                "unknown.bin"
        ));
    }

    @Test
    public void extensionIsFallback() {
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

    private DocumentType resolveMime(String mimeType) {
        return resolver.resolve(
                mimeType,
                bytes("plain preview"),
                Collections.emptySet(),
                "file"
        );
    }

    private DocumentType resolve(byte[] signature, String displayName) {
        return resolver.resolve(null, signature, Collections.emptySet(), displayName);
    }

    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }
}
