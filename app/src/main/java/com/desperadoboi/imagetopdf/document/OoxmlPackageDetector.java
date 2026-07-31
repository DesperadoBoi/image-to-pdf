package com.desperadoboi.imagetopdf.document;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

public final class OoxmlPackageDetector {
    private static final String CONTENT_TYPES = "[Content_Types].xml";
    private static final String ROOT_RELATIONSHIPS = "_rels/.rels";
    private static final String XLSX_MAIN =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml";
    private static final String XLSM_MAIN =
            "application/vnd.ms-excel.sheet.macroenabled.main+xml";
    private static final String DOCX_MAIN =
            "application/vnd.openxmlformats-officedocument."
                    + "wordprocessingml.document.main+xml";
    private static final String PPTX_MAIN =
            "application/vnd.openxmlformats-officedocument."
                    + "presentationml.presentation.main+xml";
    private static final String PROCESS_DOCDECL =
            "http://xmlpull.org/v1/doc/features.html#process-docdecl";

    private OoxmlPackageDetector() {
    }

    public static Kind detect(File file) throws DetectionException {
        if (file == null || !file.isFile() || file.length() <= 0L) {
            throw corrupted(Family.UNKNOWN, "OOXML package is unavailable or empty", null);
        }
        try {
            ensureCentralDirectoryIsSafe(file);
            try (ZipFile zipFile = new ZipFile(file)) {
                Map<String, ZipEntry> entries = collectEntries(zipFile);
                ZipEntry contentTypesEntry = entries.get(CONTENT_TYPES);
                if (contentTypesEntry == null) return Kind.UNKNOWN;

                Map<String, String> contentTypes = readContentTypes(
                        zipFile,
                        contentTypesEntry
                );
                String rootTarget = readRootTarget(
                        zipFile,
                        entries.get(ROOT_RELATIONSHIPS)
                );
                Family markerFamily = markerFamily(entries.keySet(), contentTypes);
                if (rootTarget == null || !entries.containsKey(rootTarget)) {
                    if (markerFamily != Family.UNKNOWN) {
                        throw corrupted(
                                markerFamily,
                                "OOXML root relationship is missing or invalid",
                                null
                        );
                    }
                    return Kind.UNKNOWN;
                }

                String mainContentType = contentTypes.get(rootTarget);
                if ("xl/workbook.xml".equals(rootTarget)) {
                    if (XLSM_MAIN.equalsIgnoreCase(mainContentType)) return Kind.XLSM;
                    if (XLSX_MAIN.equalsIgnoreCase(mainContentType)) return Kind.XLSX;
                    throw corrupted(
                            Family.SPREADSHEET,
                            "Spreadsheet main content type is invalid",
                            null
                    );
                }
                if (rootTarget.startsWith("word/")
                        && DOCX_MAIN.equalsIgnoreCase(mainContentType)) {
                    return Kind.DOCX;
                }
                if (rootTarget.startsWith("word/") && mainContentType != null) {
                    String lower = mainContentType.toLowerCase(Locale.ROOT);
                    if (lower.contains("wordprocessingml")
                            && lower.contains("macroenabled")) {
                        return Kind.DOCX;
                    }
                }
                if ("ppt/presentation.xml".equals(rootTarget)
                        && PPTX_MAIN.equalsIgnoreCase(mainContentType)) {
                    return Kind.PPTX;
                }
                if (markerFamily != Family.UNKNOWN) {
                    throw corrupted(
                            markerFamily,
                            "OOXML main part declarations conflict",
                            null
                    );
                }
                return Kind.UNKNOWN;
            }
        } catch (DetectionException exception) {
            throw exception;
        } catch (ZipException exception) {
            throw corrupted(Family.UNKNOWN, "Invalid ZIP package", exception);
        } catch (IOException | XmlPullParserException | RuntimeException exception) {
            throw corrupted(Family.UNKNOWN, "Unable to inspect OOXML package", exception);
        }
    }

    private static Map<String, ZipEntry> collectEntries(ZipFile zipFile)
            throws DetectionException {
        Map<String, ZipEntry> entries = new HashMap<>();
        Set<String> caseFoldedPaths = new HashSet<>();
        int count = 0;
        long totalSize = 0L;
        Enumeration<? extends ZipEntry> enumeration = zipFile.entries();
        while (enumeration.hasMoreElements()) {
            ZipEntry entry = enumeration.nextElement();
            count++;
            if (count > DocumentLimits.MAX_DOCX_ZIP_ENTRIES) {
                throw tooLarge(Family.UNKNOWN, "ZIP entry count limit exceeded");
            }
            String name = normalizeEntryName(entry.getName(), entry.isDirectory());
            if (name == null
                    || entries.put(name, entry) != null
                    || !caseFoldedPaths.add(name.toLowerCase(Locale.ROOT))) {
                throw corrupted(Family.UNKNOWN, "Unsafe or duplicate ZIP entry path", null);
            }
            if (entry.isDirectory()) continue;
            long size = entry.getSize();
            long compressedSize = entry.getCompressedSize();
            if (size < 0L || compressedSize < 0L) {
                throw corrupted(Family.UNKNOWN, "ZIP entry size is unavailable", null);
            }
            if (size > DocumentLimits.MAX_DOCX_ENTRY_BYTES) {
                throw tooLarge(Family.UNKNOWN, "ZIP entry size limit exceeded");
            }
            if (size > Long.MAX_VALUE - totalSize) {
                throw tooLarge(Family.UNKNOWN, "ZIP size total overflow");
            }
            totalSize += size;
            if (totalSize > DocumentLimits.MAX_DOCX_UNCOMPRESSED_BYTES) {
                throw tooLarge(Family.UNKNOWN, "ZIP uncompressed size limit exceeded");
            }
            if (size > 1_024L && (compressedSize == 0L
                    || size > compressedSize * DocumentLimits.MAX_DOCX_COMPRESSION_RATIO)) {
                throw tooLarge(Family.UNKNOWN, "ZIP compression ratio limit exceeded");
            }
        }
        return entries;
    }

    private static Map<String, String> readContentTypes(ZipFile zipFile, ZipEntry entry)
            throws IOException, XmlPullParserException, DetectionException {
        Map<String, String> overrides = new HashMap<>();
        try (InputStream inputStream = zipFile.getInputStream(entry)) {
            XmlPullParser parser = newParser(inputStream);
            XmlBudget budget = new XmlBudget();
            int event;
            while ((event = next(parser, budget)) != XmlPullParser.END_DOCUMENT) {
                if (event != XmlPullParser.START_TAG
                        || !"Override".equals(parser.getName())) continue;
                String partName = attribute(parser, "PartName");
                String contentType = attribute(parser, "ContentType");
                String normalized = normalizeRelationshipTarget(partName);
                if (normalized == null || contentType == null
                        || overrides.put(normalized, contentType) != null) {
                    throw corrupted(
                            Family.UNKNOWN,
                            "Content type declaration is invalid",
                            null
                    );
                }
            }
        }
        return overrides;
    }

    private static String readRootTarget(ZipFile zipFile, ZipEntry entry)
            throws IOException, XmlPullParserException, DetectionException {
        if (entry == null) return null;
        String result = null;
        try (InputStream inputStream = zipFile.getInputStream(entry)) {
            XmlPullParser parser = newParser(inputStream);
            XmlBudget budget = new XmlBudget();
            int event;
            while ((event = next(parser, budget)) != XmlPullParser.END_DOCUMENT) {
                if (event != XmlPullParser.START_TAG
                        || !"Relationship".equals(parser.getName())) continue;
                String type = attribute(parser, "Type");
                if (type == null || !type.endsWith("/officeDocument")) continue;
                if ("External".equalsIgnoreCase(attribute(parser, "TargetMode"))) {
                    throw corrupted(
                            Family.UNKNOWN,
                            "External OOXML root relationship is not allowed",
                            null
                    );
                }
                String target = normalizeRelationshipTarget(attribute(parser, "Target"));
                if (target == null || result != null) {
                    throw corrupted(
                            Family.UNKNOWN,
                            "OOXML root relationship is ambiguous",
                            null
                    );
                }
                result = target;
            }
        }
        return result;
    }

    private static Family markerFamily(
            Set<String> entries,
            Map<String, String> contentTypes
    ) {
        String workbookType = contentTypes.get("xl/workbook.xml");
        if (entries.contains("xl/workbook.xml")
                || XLSX_MAIN.equalsIgnoreCase(workbookType)
                || XLSM_MAIN.equalsIgnoreCase(workbookType)) {
            return Family.SPREADSHEET;
        }
        String wordType = contentTypes.get("word/document.xml");
        if (entries.contains("word/document.xml") || DOCX_MAIN.equalsIgnoreCase(wordType)) {
            return Family.WORD;
        }
        String presentationType = contentTypes.get("ppt/presentation.xml");
        if (entries.contains("ppt/presentation.xml")
                || PPTX_MAIN.equalsIgnoreCase(presentationType)) {
            return Family.PRESENTATION;
        }
        return Family.UNKNOWN;
    }

    private static XmlPullParser newParser(InputStream inputStream)
            throws XmlPullParserException {
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        factory.setNamespaceAware(true);
        XmlPullParser parser = factory.newPullParser();
        try {
            parser.setFeature(PROCESS_DOCDECL, false);
        } catch (XmlPullParserException ignored) {
            // DOCDECL is rejected while consuming tokens as well.
        }
        parser.setInput(inputStream, null);
        return parser;
    }

    private static int next(XmlPullParser parser, XmlBudget budget)
            throws IOException, XmlPullParserException, DetectionException {
        int event = parser.nextToken();
        budget.events++;
        if (budget.events > DocumentLimits.MAX_XML_EVENTS
                || parser.getDepth() > DocumentLimits.MAX_XML_DEPTH
                || (event == XmlPullParser.START_TAG && parser.getAttributeCount() > 128)) {
            throw tooLarge(Family.UNKNOWN, "OOXML metadata exceeds safe limits");
        }
        if (event == XmlPullParser.DOCDECL
                || (event == XmlPullParser.ENTITY_REF
                && !isPredefinedEntity(parser.getName()))) {
            throw corrupted(Family.UNKNOWN, "Unsafe OOXML metadata", null);
        }
        return event;
    }

    private static String attribute(XmlPullParser parser, String localName) {
        for (int index = 0; index < parser.getAttributeCount(); index++) {
            if (localName.equals(parser.getAttributeName(index))) {
                return parser.getAttributeValue(index);
            }
        }
        return null;
    }

    private static boolean isPredefinedEntity(String name) {
        return "amp".equals(name) || "lt".equals(name) || "gt".equals(name)
                || "apos".equals(name) || "quot".equals(name);
    }

    private static String normalizeRelationshipTarget(String target) {
        if (target == null || target.isEmpty() || target.length() > 1_024
                || target.indexOf('\\') >= 0) return null;
        String value = target.startsWith("/") ? target.substring(1) : target;
        if (value.isEmpty() || value.contains("://")) return null;
        StringBuilder normalized = new StringBuilder();
        for (String segment : value.split("/", -1)) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)
                    || segment.indexOf(':') >= 0 || containsControlCharacter(segment)) {
                return null;
            }
            if (normalized.length() > 0) normalized.append('/');
            normalized.append(segment);
        }
        return normalized.toString();
    }

    private static String normalizeEntryName(String name, boolean directory) {
        if (name == null || name.isEmpty() || name.length() > 512
                || name.startsWith("/") || name.startsWith("\\")
                || name.indexOf('\\') >= 0 || containsControlCharacter(name)) {
            return null;
        }
        String path = directory && name.endsWith("/")
                ? name.substring(0, name.length() - 1)
                : name;
        if (path.isEmpty()) return null;
        StringBuilder normalized = new StringBuilder();
        for (String segment : path.split("/", -1)) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)
                    || segment.indexOf(':') >= 0) return null;
            if (normalized.length() > 0) normalized.append('/');
            normalized.append(segment);
        }
        if (directory) normalized.append('/');
        return normalized.toString();
    }

    private static boolean containsControlCharacter(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) return true;
        }
        return false;
    }

    private static void ensureCentralDirectoryIsSafe(File file)
            throws IOException, DetectionException {
        try (RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r")) {
            long length = randomAccessFile.length();
            if (length < 4L) {
                throw corrupted(Family.UNKNOWN, "ZIP signature is missing", null);
            }
            byte[] signature = new byte[4];
            randomAccessFile.readFully(signature);
            if (littleEndianInt(signature, 0) != 0x04034B50L) {
                throw corrupted(Family.UNKNOWN, "ZIP signature is invalid", null);
            }
            int tailLength = (int) Math.min(length, 65_557L);
            byte[] tail = new byte[tailLength];
            randomAccessFile.seek(length - tailLength);
            randomAccessFile.readFully(tail);
            int eocd = findSignatureBackward(tail, 0x06054B50);
            if (eocd < 0 || eocd + 22 > tail.length) {
                throw corrupted(Family.UNKNOWN, "ZIP central directory is missing", null);
            }
            int entriesOnDisk = littleEndianShort(tail, eocd + 8);
            int entryCount = littleEndianShort(tail, eocd + 10);
            long centralSize = littleEndianInt(tail, eocd + 12);
            long centralOffset = littleEndianInt(tail, eocd + 16);
            if (littleEndianShort(tail, eocd + 4) != 0
                    || littleEndianShort(tail, eocd + 6) != 0
                    || entriesOnDisk != entryCount) {
                throw corrupted(Family.UNKNOWN, "Multi-disk ZIP is not supported", null);
            }
            if (entryCount == 0xFFFF || centralSize == 0xFFFFFFFFL
                    || centralOffset == 0xFFFFFFFFL) {
                throw tooLarge(Family.UNKNOWN, "ZIP64 package is not supported");
            }
            if (centralOffset < 0L || centralSize < 0L
                    || centralOffset + centralSize > length) {
                throw corrupted(Family.UNKNOWN, "ZIP central directory is invalid", null);
            }
            randomAccessFile.seek(centralOffset);
            byte[] header = new byte[46];
            for (int index = 0; index < entryCount; index++) {
                randomAccessFile.readFully(header);
                if (littleEndianInt(header, 0) != 0x02014B50L) {
                    throw corrupted(
                            Family.UNKNOWN,
                            "ZIP central directory entry is invalid",
                            null
                    );
                }
                int flags = littleEndianShort(header, 8);
                if ((flags & 1) != 0) {
                    throw new DetectionException(
                            Reason.ENCRYPTED,
                            Family.UNKNOWN,
                            "Encrypted OOXML package"
                    );
                }
                int method = littleEndianShort(header, 10);
                if (method != ZipEntry.STORED && method != ZipEntry.DEFLATED) {
                    throw corrupted(
                            Family.UNKNOWN,
                            "Unsupported ZIP compression method",
                            null
                    );
                }
                long skip = (long) littleEndianShort(header, 28)
                        + littleEndianShort(header, 30)
                        + littleEndianShort(header, 32);
                long next = randomAccessFile.getFilePointer() + skip;
                if (next > centralOffset + centralSize) {
                    throw corrupted(Family.UNKNOWN, "ZIP entry exceeds its bounds", null);
                }
                long localOffset = littleEndianInt(header, 42);
                if (localOffset + 30L > length) {
                    throw corrupted(Family.UNKNOWN, "ZIP local header is invalid", null);
                }
                randomAccessFile.seek(localOffset);
                byte[] local = new byte[30];
                randomAccessFile.readFully(local);
                if (littleEndianInt(local, 0) != 0x04034B50L
                        || (littleEndianShort(local, 6) & 1) != 0
                        || littleEndianShort(local, 8) != method) {
                    throw corrupted(
                            Family.UNKNOWN,
                            "ZIP local header does not match central directory",
                            null
                    );
                }
                randomAccessFile.seek(next);
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

    private static DetectionException corrupted(
            Family family,
            String message,
            Throwable cause
    ) {
        return cause == null
                ? new DetectionException(Reason.CORRUPTED, family, message)
                : new DetectionException(Reason.CORRUPTED, family, message, cause);
    }

    private static DetectionException tooLarge(Family family, String message) {
        return new DetectionException(Reason.TOO_LARGE, family, message);
    }

    public enum Kind {
        UNKNOWN,
        XLSX,
        XLSM,
        DOCX,
        PPTX
    }

    public enum Family {
        UNKNOWN,
        SPREADSHEET,
        WORD,
        PRESENTATION
    }

    public enum Reason {
        CORRUPTED,
        TOO_LARGE,
        ENCRYPTED
    }

    public static final class DetectionException extends IOException {
        private final Reason reason;
        private final Family family;

        private DetectionException(Reason reason, Family family, String message) {
            super(message);
            this.reason = reason;
            this.family = family;
        }

        private DetectionException(
                Reason reason,
                Family family,
                String message,
                Throwable cause
        ) {
            super(message, cause);
            this.reason = reason;
            this.family = family;
        }

        public Reason getReason() {
            return reason;
        }

        public Family getFamily() {
            return family;
        }
    }

    private static final class XmlBudget {
        private int events;
    }
}
