package com.desperadoboi.imagetopdf.document.spreadsheet;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class XlsxTestFixtures {
    private XlsxTestFixtures() {
    }

    public static Path workbook(
            Path file,
            boolean date1904,
            String[] sheetNames,
            String[] sheetPaths,
            String[] sheets,
            String sharedStrings,
            String styles
    ) throws IOException {
        LinkedHashMap<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("[Content_Types].xml", bytes(contentTypes(sheetPaths,
                sharedStrings != null, styles != null)));
        entries.put("_rels/.rels", bytes(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                        + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                        + "<Relationship Id=\"root\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                        + "</Relationships>"
        ));
        entries.put("xl/workbook.xml", bytes(workbookXml(date1904, sheetNames)));
        entries.put("xl/_rels/workbook.xml.rels", bytes(workbookRelationships(
                sheetPaths,
                sharedStrings != null,
                styles != null
        )));
        for (int index = 0; index < sheets.length; index++) {
            entries.put("xl/" + sheetPaths[index], bytes(sheets[index]));
        }
        if (sharedStrings != null) entries.put("xl/sharedStrings.xml", bytes(sharedStrings));
        if (styles != null) entries.put("xl/styles.xml", bytes(styles));
        writeStoredZip(file, entries);
        return file;
    }

    public static Path minimalWorkbook(Path file, String sheetXml) throws IOException {
        return workbook(
                file,
                false,
                new String[]{"Sheet 1"},
                new String[]{"worksheets/sheet1.xml"},
                new String[]{sheetXml},
                null,
                null
        );
    }

    public static String worksheet(String body) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                + body
                + "</worksheet>";
    }

    public static String sharedStrings(String body) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                + body
                + "</sst>";
    }

    public static String styles(String body) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                + body
                + "</styleSheet>";
    }

    public static void writeStoredZip(Path file, Map<String, byte[]> entries) throws IOException {
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

    public static void writeDeflatedZip(Path file, Map<String, byte[]> entries) throws IOException {
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

    private static String contentTypes(
            String[] sheetPaths,
            boolean sharedStrings,
            boolean styles
    ) {
        StringBuilder xml = new StringBuilder(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                        + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                        + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                        + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                        + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
        );
        for (String sheetPath : sheetPaths) {
            xml.append("<Override PartName=\"/xl/")
                    .append(sheetPath)
                    .append("\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
        }
        if (sharedStrings) {
            xml.append("<Override PartName=\"/xl/sharedStrings.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml\"/>");
        }
        if (styles) {
            xml.append("<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>");
        }
        return xml.append("</Types>").toString();
    }

    private static String workbookXml(boolean date1904, String[] sheetNames) {
        StringBuilder xml = new StringBuilder(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                        + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
        );
        xml.append("<workbookPr date1904=\"").append(date1904 ? "1" : "0").append("\"/>");
        xml.append("<sheets>");
        for (int index = 0; index < sheetNames.length; index++) {
            xml.append("<sheet name=\"").append(sheetNames[index]).append("\" sheetId=\"")
                    .append(index + 1).append("\" r:id=\"rId").append(index + 1)
                    .append("\"/>");
        }
        return xml.append("</sheets></workbook>").toString();
    }

    private static String workbookRelationships(
            String[] sheetPaths,
            boolean sharedStrings,
            boolean styles
    ) {
        StringBuilder xml = new StringBuilder(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                        + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
        );
        int id = 1;
        for (String sheetPath : sheetPaths) {
            xml.append("<Relationship Id=\"rId").append(id++)
                    .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"")
                    .append(sheetPath).append("\"/>");
        }
        if (sharedStrings) {
            xml.append("<Relationship Id=\"shared\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings\" Target=\"sharedStrings.xml\"/>");
        }
        if (styles) {
            xml.append("<Relationship Id=\"styles\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>");
        }
        return xml.append("</Relationships>").toString();
    }
}
