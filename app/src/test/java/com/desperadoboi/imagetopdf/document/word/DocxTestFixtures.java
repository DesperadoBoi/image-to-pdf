package com.desperadoboi.imagetopdf.document.word;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class DocxTestFixtures {
    public static final String WORD_NAMESPACE =
            "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    public static final String RELATIONSHIP_NAMESPACE =
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    public static final byte[] ONE_PIXEL_PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk"
                    + "YAAAAAYAAjCB0C8AAAAASUVORK5CYII="
    );

    private DocxTestFixtures() {
    }

    public static Path minimalDocument(Path file, String body) throws IOException {
        return document(
                file,
                body,
                "",
                new LinkedHashMap<>(),
                ""
        );
    }

    public static Path document(
            Path file,
            String body,
            String documentRelationships,
            Map<String, byte[]> extraEntries,
            String extraContentTypes
    ) throws IOException {
        LinkedHashMap<String, byte[]> entries = baseEntries(
                body,
                documentRelationships,
                extraContentTypes
        );
        entries.putAll(extraEntries);
        writeStoredZip(file, entries);
        return file;
    }

    public static LinkedHashMap<String, byte[]> baseEntries(
            String body,
            String documentRelationships,
            String extraContentTypes
    ) {
        LinkedHashMap<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("[Content_Types].xml", bytes(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                        + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                        + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                        + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                        + "<Default Extension=\"png\" ContentType=\"image/png\"/>"
                        + "<Override PartName=\"/word/document.xml\" ContentType=\""
                        + DocxPackageInspector.CONTENT_TYPE_DOCUMENT
                        + "\"/>"
                        + extraContentTypes
                        + "</Types>"
        ));
        entries.put("_rels/.rels", bytes(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                        + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                        + "<Relationship Id=\"rRoot\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>"
                        + "</Relationships>"
        ));
        entries.put("word/document.xml", bytes(wordDocument(body)));
        if (!documentRelationships.isEmpty()) {
            entries.put("word/_rels/document.xml.rels", bytes(
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                            + documentRelationships
                            + "</Relationships>"
            ));
        }
        return entries;
    }

    public static String wordDocument(String body) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<w:document xmlns:w=\"" + WORD_NAMESPACE + "\""
                + " xmlns:r=\"" + RELATIONSHIP_NAMESPACE + "\""
                + " xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\""
                + " xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\""
                + " xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">"
                + "<w:body>" + body + "</w:body></w:document>";
    }

    public static String paragraph(String text) {
        return "<w:p><w:r><w:t xml:space=\"preserve\">"
                + escape(text)
                + "</w:t></w:r></w:p>";
    }

    public static String relationship(
            String id,
            String typeSuffix,
            String target
    ) {
        return "<Relationship Id=\"" + id + "\" Type=\""
                + RELATIONSHIP_NAMESPACE + "/" + typeSuffix
                + "\" Target=\"" + target + "\"/>";
    }

    public static String externalRelationship(
            String id,
            String typeSuffix,
            String target
    ) {
        return "<Relationship Id=\"" + id + "\" Type=\""
                + RELATIONSHIP_NAMESPACE + "/" + typeSuffix
                + "\" Target=\"" + target + "\" TargetMode=\"External\"/>";
    }

    public static void writeStoredZip(Path file, Map<String, byte[]> entries)
            throws IOException {
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(file))) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                byte[] value = entry.getValue();
                CRC32 crc = new CRC32();
                crc.update(value);
                ZipEntry zipEntry = new ZipEntry(entry.getKey());
                zipEntry.setMethod(ZipEntry.STORED);
                zipEntry.setSize(value.length);
                zipEntry.setCompressedSize(value.length);
                zipEntry.setCrc(crc.getValue());
                output.putNextEntry(zipEntry);
                output.write(value);
                output.closeEntry();
            }
        }
    }

    public static void writeDeflatedZip(Path file, Map<String, byte[]> entries)
            throws IOException {
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(file))) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                output.putNextEntry(new ZipEntry(entry.getKey()));
                output.write(entry.getValue());
                output.closeEntry();
            }
        }
    }

    public static byte[] zipBytes(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream output = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                output.putNextEntry(new ZipEntry(entry.getKey()));
                output.write(entry.getValue());
                output.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    public static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    public static String escape(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
