package com.desperadoboi.imagetopdf.document.spreadsheet;

import com.desperadoboi.imagetopdf.document.DocumentLimits;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

public final class XlsxPackageInspector {
    private static final String CONTENT_TYPE_WORKBOOK =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml";
    private static final String CONTENT_TYPE_WORKSHEET =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml";

    private XlsxPackageInspector() {
    }

    public static Inspection inspect(File file) throws XlsxParseException {
        if (file == null || !file.isFile()) {
            throw corrupted("XLSX package is unavailable", null);
        }
        if (file.length() > DocumentLimits.MAX_XLSX_BYTES) {
            throw tooLarge("XLSX source exceeds the file limit");
        }
        try {
            ensureCentralDirectoryIsSafe(file);
            try (ZipFile zipFile = new ZipFile(file)) {
                Map<String, ZipEntry> entries = collectEntries(zipFile);
                if (!hasRequiredParts(entries.keySet())) {
                    return Inspection.notXlsx(entries.keySet());
                }
                String workbookTarget = readRootWorkbookTarget(
                        zipFile,
                        entries.get("_rels/.rels")
                );
                if (!"xl/workbook.xml".equals(workbookTarget)) {
                    return Inspection.notXlsx(entries.keySet());
                }
                Set<String> worksheetTargets = readWorksheetTargets(
                        zipFile,
                        entries.get("xl/_rels/workbook.xml.rels"),
                        workbookTarget,
                        entries.keySet()
                );
                if (worksheetTargets.isEmpty()) {
                    return Inspection.notXlsx(entries.keySet());
                }
                Map<String, String> contentTypes = readContentTypes(
                        zipFile,
                        entries.get("[Content_Types].xml")
                );
                if (!CONTENT_TYPE_WORKBOOK.equals(contentTypes.get(workbookTarget))) {
                    return Inspection.notXlsx(entries.keySet());
                }
                boolean allWorksheetsTyped = true;
                for (String worksheetTarget : worksheetTargets) {
                    allWorksheetsTyped &= CONTENT_TYPE_WORKSHEET.equals(
                            contentTypes.get(worksheetTarget)
                    );
                }
                if (!allWorksheetsTyped) {
                    return Inspection.notXlsx(entries.keySet());
                }
                return new Inspection(true, entries.keySet());
            }
        } catch (XlsxParseException exception) {
            throw exception;
        } catch (ZipException exception) {
            throw corrupted("Invalid ZIP package", exception);
        } catch (IOException | XmlPullParserException | RuntimeException exception) {
            throw corrupted("Unable to inspect XLSX package", exception);
        }
    }

    public static String normalizeRelationshipTarget(String sourcePart, String target) {
        if (target == null || target.isEmpty() || target.indexOf('\\') >= 0) {
            return null;
        }
        String lower = target.toLowerCase(Locale.ROOT);
        if (lower.contains("://") || lower.startsWith("file:") || lower.startsWith("data:")) {
            return null;
        }
        String base = "";
        if (!target.startsWith("/") && sourcePart != null) {
            int slash = sourcePart.lastIndexOf('/');
            base = slash < 0 ? "" : sourcePart.substring(0, slash + 1);
        }
        String combined = target.startsWith("/") ? target.substring(1) : base + target;
        java.util.ArrayDeque<String> segments = new java.util.ArrayDeque<>();
        for (String segment : combined.split("/")) {
            if (segment.isEmpty() || ".".equals(segment)) continue;
            if ("..".equals(segment)) {
                if (segments.isEmpty()) return null;
                segments.removeLast();
            } else {
                if (segment.indexOf(':') >= 0) return null;
                segments.addLast(segment);
            }
        }
        if (segments.isEmpty()) return null;
        StringBuilder normalized = new StringBuilder();
        for (String segment : segments) {
            if (normalized.length() > 0) normalized.append('/');
            normalized.append(segment);
        }
        return normalized.toString();
    }

    private static Map<String, ZipEntry> collectEntries(ZipFile zipFile)
            throws XlsxParseException {
        Map<String, ZipEntry> entries = new HashMap<>();
        long totalSize = 0L;
        int count = 0;
        Enumeration<? extends ZipEntry> enumeration = zipFile.entries();
        while (enumeration.hasMoreElements()) {
            ZipEntry entry = enumeration.nextElement();
            count++;
            if (count > DocumentLimits.MAX_ZIP_ENTRIES) {
                throw tooLarge("ZIP entry count limit exceeded");
            }
            String name = entry.getName();
            if (!isSafeEntryName(name, entry.isDirectory())) {
                throw corrupted("Unsafe ZIP entry path", null);
            }
            if (entries.put(name, entry) != null) {
                throw corrupted("Duplicate ZIP entry", null);
            }
            if (entry.isDirectory()) continue;
            long size = entry.getSize();
            long compressedSize = entry.getCompressedSize();
            if (size < 0L || compressedSize < 0L) {
                throw corrupted("ZIP entry size is unavailable", null);
            }
            if (size > DocumentLimits.MAX_ZIP_ENTRY_BYTES) {
                throw tooLarge("ZIP entry size limit exceeded");
            }
            if ("xl/sharedStrings.xml".equals(name)
                    && size > DocumentLimits.MAX_SHARED_STRINGS_BYTES) {
                throw tooLarge("Shared strings size limit exceeded");
            }
            totalSize += size;
            if (totalSize > DocumentLimits.MAX_ZIP_UNCOMPRESSED_BYTES) {
                throw tooLarge("ZIP uncompressed size limit exceeded");
            }
            if (size > 1_024L
                    && (compressedSize == 0L
                    || size > compressedSize * DocumentLimits.MAX_ZIP_RATIO)) {
                throw tooLarge("ZIP compression ratio limit exceeded");
            }
            if (isForbiddenPart(name)) {
                throw new XlsxParseException(
                        XlsxParseException.Reason.UNSUPPORTED,
                        "Active or embedded content is not supported"
                );
            }
        }
        return entries;
    }

    private static boolean hasRequiredParts(Set<String> entries) {
        return entries.contains("[Content_Types].xml")
                && entries.contains("_rels/.rels")
                && entries.contains("xl/workbook.xml")
                && entries.contains("xl/_rels/workbook.xml.rels");
    }

    private static String readRootWorkbookTarget(ZipFile zipFile, ZipEntry entry)
            throws IOException, XmlPullParserException, XlsxParseException {
        try (InputStream inputStream = zipFile.getInputStream(entry)) {
            XmlPullParser parser = XlsxXml.newParser(inputStream);
            XlsxXml.Budget budget = new XlsxXml.Budget();
            int event;
            while ((event = XlsxXml.next(parser, budget)) != XmlPullParser.END_DOCUMENT) {
                if (event != XmlPullParser.START_TAG
                        || !"Relationship".equals(parser.getName())) continue;
                String type = XlsxXml.attribute(parser, "Type");
                String targetMode = XlsxXml.attribute(parser, "TargetMode");
                if (type != null
                        && type.endsWith("/officeDocument")
                        && !"External".equalsIgnoreCase(targetMode)) {
                    return normalizeRelationshipTarget(
                            null,
                            XlsxXml.attribute(parser, "Target")
                    );
                }
            }
            return null;
        }
    }

    private static Set<String> readWorksheetTargets(
            ZipFile zipFile,
            ZipEntry entry,
            String workbookPart,
            Set<String> entries
    ) throws IOException, XmlPullParserException, XlsxParseException {
        Set<String> targets = new HashSet<>();
        try (InputStream inputStream = zipFile.getInputStream(entry)) {
            XmlPullParser parser = XlsxXml.newParser(inputStream);
            XlsxXml.Budget budget = new XlsxXml.Budget();
            int event;
            while ((event = XlsxXml.next(parser, budget)) != XmlPullParser.END_DOCUMENT) {
                if (event != XmlPullParser.START_TAG
                        || !"Relationship".equals(parser.getName())) continue;
                String type = XlsxXml.attribute(parser, "Type");
                String targetMode = XlsxXml.attribute(parser, "TargetMode");
                if (type == null
                        || !type.endsWith("/worksheet")
                        || "External".equalsIgnoreCase(targetMode)) continue;
                String target = normalizeRelationshipTarget(
                        workbookPart,
                        XlsxXml.attribute(parser, "Target")
                );
                if (target == null || !entries.contains(target)) {
                    throw corrupted("Worksheet relationship is invalid", null);
                }
                targets.add(target);
            }
        }
        return targets;
    }

    private static Map<String, String> readContentTypes(ZipFile zipFile, ZipEntry entry)
            throws IOException, XmlPullParserException, XlsxParseException {
        Map<String, String> overrides = new HashMap<>();
        try (InputStream inputStream = zipFile.getInputStream(entry)) {
            XmlPullParser parser = XlsxXml.newParser(inputStream);
            XlsxXml.Budget budget = new XlsxXml.Budget();
            int event;
            while ((event = XlsxXml.next(parser, budget)) != XmlPullParser.END_DOCUMENT) {
                if (event != XmlPullParser.START_TAG || !"Override".equals(parser.getName())) {
                    continue;
                }
                String partName = XlsxXml.attribute(parser, "PartName");
                String contentType = XlsxXml.attribute(parser, "ContentType");
                if (partName != null && partName.startsWith("/") && contentType != null) {
                    overrides.put(partName.substring(1), contentType);
                }
            }
        }
        return overrides;
    }

    private static boolean isSafeEntryName(String name, boolean directory) {
        if (name == null || name.isEmpty() || name.length() > 512) return false;
        if (name.startsWith("/") || name.startsWith("\\") || name.indexOf('\\') >= 0) {
            return false;
        }
        if (name.length() >= 2 && Character.isLetter(name.charAt(0)) && name.charAt(1) == ':') {
            return false;
        }
        String path = directory && name.endsWith("/")
                ? name.substring(0, name.length() - 1)
                : name;
        for (String segment : path.split("/")) {
            if ("..".equals(segment) || ".".equals(segment) || segment.isEmpty()) return false;
        }
        return true;
    }

    private static boolean isForbiddenPart(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.startsWith("xl/externallinks/")
                || lower.startsWith("xl/embeddings/")
                || lower.startsWith("xl/activex/")
                || lower.startsWith("xl/macrosheets/")
                || lower.endsWith("vbaproject.bin")) {
            return true;
        }
        return lower.endsWith(".exe")
                || lower.endsWith(".dll")
                || lower.endsWith(".com")
                || lower.endsWith(".bat")
                || lower.endsWith(".cmd")
                || lower.endsWith(".scr")
                || lower.endsWith(".apk")
                || lower.endsWith(".jar");
    }

    private static void ensureCentralDirectoryIsSafe(File file)
            throws IOException, XlsxParseException {
        try (RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r")) {
            long length = randomAccessFile.length();
            int tailLength = (int) Math.min(length, 65_557L);
            byte[] tail = new byte[tailLength];
            randomAccessFile.seek(length - tailLength);
            randomAccessFile.readFully(tail);
            int eocd = findSignatureBackward(tail, 0x06054B50);
            if (eocd < 0 || eocd + 22 > tail.length) {
                throw corrupted("ZIP central directory is missing", null);
            }
            int entryCount = littleEndianShort(tail, eocd + 10);
            int entriesOnDisk = littleEndianShort(tail, eocd + 8);
            long centralSize = littleEndianInt(tail, eocd + 12);
            long centralOffset = littleEndianInt(tail, eocd + 16);
            if (littleEndianShort(tail, eocd + 4) != 0
                    || littleEndianShort(tail, eocd + 6) != 0
                    || entriesOnDisk != entryCount) {
                throw new XlsxParseException(
                        XlsxParseException.Reason.UNSUPPORTED,
                        "Multi-disk ZIP packages are not supported"
                );
            }
            if (entryCount == 0xFFFF || centralSize == 0xFFFFFFFFL
                    || centralOffset == 0xFFFFFFFFL) {
                throw new XlsxParseException(
                        XlsxParseException.Reason.UNSUPPORTED,
                        "ZIP64 XLSX packages are not supported"
                );
            }
            if (centralOffset < 0L || centralSize < 0L
                    || centralOffset + centralSize > length) {
                throw corrupted("ZIP central directory is invalid", null);
            }
            randomAccessFile.seek(centralOffset);
            byte[] header = new byte[46];
            for (int index = 0; index < entryCount; index++) {
                randomAccessFile.readFully(header);
                if (littleEndianInt(header, 0) != 0x02014B50L) {
                    throw corrupted("ZIP central directory entry is invalid", null);
                }
                int flags = littleEndianShort(header, 8);
                if ((flags & 1) != 0) {
                    throw new XlsxParseException(
                            XlsxParseException.Reason.UNSUPPORTED,
                            "Encrypted XLSX packages are not supported"
                    );
                }
                int method = littleEndianShort(header, 10);
                if (method != ZipEntry.STORED && method != ZipEntry.DEFLATED) {
                    throw new XlsxParseException(
                            XlsxParseException.Reason.UNSUPPORTED,
                            "Unsupported ZIP compression method"
                    );
                }
                long skip = (long) littleEndianShort(header, 28)
                        + littleEndianShort(header, 30)
                        + littleEndianShort(header, 32);
                long nextCentralEntry = randomAccessFile.getFilePointer() + skip;
                if (nextCentralEntry > centralOffset + centralSize) {
                    throw corrupted("ZIP central directory entry exceeds its bounds", null);
                }
                long localHeaderOffset = littleEndianInt(header, 42);
                if (localHeaderOffset + 30L > length) {
                    throw corrupted("ZIP local header is invalid", null);
                }
                randomAccessFile.seek(localHeaderOffset);
                byte[] localHeader = new byte[30];
                randomAccessFile.readFully(localHeader);
                if (littleEndianInt(localHeader, 0) != 0x04034B50L
                        || (littleEndianShort(localHeader, 6) & 1) != 0
                        || littleEndianShort(localHeader, 8) != method) {
                    throw corrupted("ZIP local header does not match central directory", null);
                }
                randomAccessFile.seek(nextCentralEntry);
            }
        }
    }

    private static int findSignatureBackward(byte[] bytes, int signature) {
        for (int index = bytes.length - 4; index >= 0; index--) {
            if (littleEndianInt(bytes, index) == (signature & 0xFFFFFFFFL)) return index;
        }
        return -1;
    }

    private static int littleEndianShort(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF) | ((bytes[offset + 1] & 0xFF) << 8);
    }

    private static long littleEndianInt(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFFL)
                | ((bytes[offset + 1] & 0xFFL) << 8)
                | ((bytes[offset + 2] & 0xFFL) << 16)
                | ((bytes[offset + 3] & 0xFFL) << 24);
    }

    private static XlsxParseException tooLarge(String message) {
        return new XlsxParseException(XlsxParseException.Reason.TOO_LARGE, message);
    }

    private static XlsxParseException corrupted(String message, Throwable cause) {
        return cause == null
                ? new XlsxParseException(XlsxParseException.Reason.CORRUPTED, message)
                : new XlsxParseException(
                        XlsxParseException.Reason.CORRUPTED,
                        message,
                        cause
                );
    }

    public static final class Inspection {
        private final boolean xlsx;
        private final Set<String> entryNames;

        private Inspection(boolean xlsx, Set<String> entryNames) {
            this.xlsx = xlsx;
            this.entryNames = Collections.unmodifiableSet(new HashSet<>(entryNames));
        }

        private static Inspection notXlsx(Set<String> entryNames) {
            return new Inspection(false, entryNames);
        }

        public boolean isXlsx() {
            return xlsx;
        }

        public Set<String> getEntryNames() {
            return entryNames;
        }
    }
}
