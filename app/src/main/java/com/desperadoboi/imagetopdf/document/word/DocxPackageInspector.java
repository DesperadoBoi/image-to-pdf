package com.desperadoboi.imagetopdf.document.word;

import com.desperadoboi.imagetopdf.document.DocumentLimits;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

public final class DocxPackageInspector {
    public static final String CONTENT_TYPE_DOCUMENT =
            "application/vnd.openxmlformats-officedocument."
                    + "wordprocessingml.document.main+xml";
    private static final String CONTENT_TYPES = "[Content_Types].xml";
    private static final String ROOT_RELATIONSHIPS = "_rels/.rels";

    private DocxPackageInspector() {
    }

    public static Inspection inspect(File file) throws WordParseException {
        return inspect(file, null);
    }

    public static Inspection inspect(File file, AtomicBoolean cancelled)
            throws WordParseException {
        if (file == null || !file.isFile()) {
            throw corrupted("DOCX package is unavailable", null);
        }
        if (file.length() <= 0L) {
            throw corrupted("DOCX package is empty", null);
        }
        if (file.length() > DocumentLimits.MAX_DOCX_BYTES) {
            throw tooLarge("DOCX source exceeds the file limit");
        }
        try {
            ensureCentralDirectoryIsSafe(file);
            try (ZipFile zipFile = new ZipFile(file)) {
                CollectedEntries collected = collectEntries(zipFile);
                Map<String, ZipEntry> entries = collected.entries;
                if (!entries.containsKey(CONTENT_TYPES)
                        || !entries.containsKey(ROOT_RELATIONSHIPS)) {
                    return Inspection.notDocx(entries.keySet());
                }
                Map<String, String> contentTypes = readContentTypes(
                        zipFile,
                        entries.get(CONTENT_TYPES),
                        cancelled
                );
                RootRelationship root = readRootRelationship(
                        zipFile,
                        entries.get(ROOT_RELATIONSHIPS),
                        cancelled
                );
                if (root == null || !entries.containsKey(root.target)) {
                    return Inspection.notDocx(entries.keySet());
                }
                String mainContentType = contentTypes.get(root.target);
                if (mainContentType != null
                        && mainContentType.toLowerCase(Locale.ROOT)
                        .contains("macroenabled")) {
                    throw unsupported("Macro-enabled Word packages are not supported");
                }
                if (!CONTENT_TYPE_DOCUMENT.equals(mainContentType)) {
                    return Inspection.notDocx(entries.keySet());
                }
                String mainRelationships = relationshipPartName(root.target);
                ZipEntry relationshipsEntry = entries.get(mainRelationships);
                if (relationshipsEntry != null) {
                    validateDocumentRelationships(
                            zipFile,
                            relationshipsEntry,
                            root.target,
                            entries.keySet(),
                            cancelled
                    );
                }
                return new Inspection(
                        true,
                        root.target,
                        mainRelationships,
                        entries.keySet(),
                        collected.mediaCount,
                        collected.mediaBytes
                );
            }
        } catch (WordParseException exception) {
            throw exception;
        } catch (ZipException exception) {
            throw corrupted("Invalid ZIP package", exception);
        } catch (IOException | XmlPullParserException | RuntimeException exception) {
            throw corrupted("Unable to inspect DOCX package", exception);
        }
    }

    public static String normalizeRelationshipTarget(String sourcePart, String target) {
        if (target == null || target.isEmpty() || target.length() > 1_024
                || target.indexOf('\\') >= 0) {
            return null;
        }
        String lower = target.toLowerCase(Locale.ROOT);
        if (lower.contains("://") || lower.startsWith("file:")
                || lower.startsWith("data:") || lower.startsWith("content:")) {
            return null;
        }
        String base = "";
        if (!target.startsWith("/") && sourcePart != null) {
            int slash = sourcePart.lastIndexOf('/');
            base = slash < 0 ? "" : sourcePart.substring(0, slash + 1);
        }
        String combined = target.startsWith("/") ? target.substring(1) : base + target;
        ArrayDeque<String> segments = new ArrayDeque<>();
        for (String segment : combined.split("/", -1)) {
            if (segment.isEmpty() || ".".equals(segment)) continue;
            if ("..".equals(segment)) {
                return null;
            } else {
                if (segment.indexOf(':') >= 0 || containsControlCharacter(segment)) {
                    return null;
                }
                segments.addLast(segment);
            }
        }
        if (segments.isEmpty()) return null;
        return String.join("/", segments);
    }

    public static String relationshipPartName(String sourcePart) {
        int slash = sourcePart.lastIndexOf('/');
        String directory = slash < 0 ? "" : sourcePart.substring(0, slash + 1);
        String fileName = slash < 0 ? sourcePart : sourcePart.substring(slash + 1);
        return directory + "_rels/" + fileName + ".rels";
    }

    private static CollectedEntries collectEntries(ZipFile zipFile)
            throws WordParseException {
        Map<String, ZipEntry> entries = new HashMap<>();
        Set<String> caseFoldedPaths = new HashSet<>();
        long totalBytes = 0L;
        long mediaBytes = 0L;
        int mediaCount = 0;
        int count = 0;
        Enumeration<? extends ZipEntry> enumeration = zipFile.entries();
        while (enumeration.hasMoreElements()) {
            ZipEntry entry = enumeration.nextElement();
            count++;
            if (count > DocumentLimits.MAX_DOCX_ZIP_ENTRIES) {
                throw tooLarge("ZIP entry count limit exceeded");
            }
            String name = normalizeEntryName(entry.getName(), entry.isDirectory());
            if (name == null) {
                throw corrupted("Unsafe ZIP entry path", null);
            }
            if (entries.put(name, entry) != null
                    || !caseFoldedPaths.add(name.toLowerCase(Locale.ROOT))) {
                throw corrupted("Duplicate normalized ZIP entry", null);
            }
            if (entry.isDirectory()) continue;
            long size = entry.getSize();
            long compressedSize = entry.getCompressedSize();
            if (size < 0L || compressedSize < 0L) {
                throw corrupted("ZIP entry size is unavailable", null);
            }
            if (size > DocumentLimits.MAX_DOCX_ENTRY_BYTES) {
                throw tooLarge("ZIP entry size limit exceeded");
            }
            totalBytes = checkedAdd(totalBytes, size);
            if (totalBytes > DocumentLimits.MAX_DOCX_UNCOMPRESSED_BYTES) {
                throw tooLarge("ZIP uncompressed size limit exceeded");
            }
            if (size > 1_024L && (compressedSize == 0L
                    || size > compressedSize * DocumentLimits.MAX_DOCX_COMPRESSION_RATIO)) {
                throw tooLarge("ZIP compression ratio limit exceeded");
            }
            if (isMediaPart(name)) {
                mediaCount++;
                mediaBytes = checkedAdd(mediaBytes, size);
                if (mediaCount > DocumentLimits.MAX_WORD_IMAGES
                        || size > DocumentLimits.MAX_DOCX_MEDIA_ENTRY_BYTES
                        || mediaBytes > DocumentLimits.MAX_DOCX_MEDIA_BYTES) {
                    throw tooLarge("DOCX media limit exceeded");
                }
            }
            if (isForbiddenPart(name)) {
                throw unsupported("Active or embedded content is not supported");
            }
        }
        return new CollectedEntries(entries, mediaCount, mediaBytes);
    }

    private static Map<String, String> readContentTypes(
            ZipFile zipFile,
            ZipEntry entry,
            AtomicBoolean cancelled
    ) throws IOException, XmlPullParserException, WordParseException {
        Map<String, String> overrides = new HashMap<>();
        try (InputStream inputStream = zipFile.getInputStream(entry)) {
            XmlPullParser parser = WordXml.newParser(inputStream);
            WordXml.Budget budget = new WordXml.Budget(cancelled);
            int event;
            while ((event = WordXml.next(parser, budget)) != XmlPullParser.END_DOCUMENT) {
                if (event != XmlPullParser.START_TAG) continue;
                String element = parser.getName();
                if ("Override".equals(element)) {
                    String partName = WordXml.attribute(parser, "PartName");
                    String contentType = WordXml.attribute(parser, "ContentType");
                    if (partName == null || !partName.startsWith("/")
                            || contentType == null) {
                        throw corrupted("Content type override is invalid", null);
                    }
                    String normalized = normalizeRelationshipTarget(null, partName);
                    if (normalized == null
                            || overrides.put(normalized, contentType) != null) {
                        throw corrupted("Duplicate or unsafe content type override", null);
                    }
                    rejectActiveContentType(contentType);
                } else if ("Default".equals(element)) {
                    rejectActiveContentType(WordXml.attribute(parser, "ContentType"));
                }
            }
        }
        return overrides;
    }

    private static RootRelationship readRootRelationship(
            ZipFile zipFile,
            ZipEntry entry,
            AtomicBoolean cancelled
    ) throws IOException, XmlPullParserException, WordParseException {
        RootRelationship result = null;
        Set<String> ids = new HashSet<>();
        try (InputStream inputStream = zipFile.getInputStream(entry)) {
            XmlPullParser parser = WordXml.newParser(inputStream);
            WordXml.Budget budget = new WordXml.Budget(cancelled);
            int event;
            while ((event = WordXml.next(parser, budget)) != XmlPullParser.END_DOCUMENT) {
                if (event != XmlPullParser.START_TAG
                        || !"Relationship".equals(parser.getName())) {
                    continue;
                }
                String id = WordXml.attribute(parser, "Id");
                String type = WordXml.attribute(parser, "Type");
                String mode = WordXml.attribute(parser, "TargetMode");
                if (id == null || type == null || !ids.add(id)) {
                    throw corrupted("Package relationship is invalid", null);
                }
                if ("External".equalsIgnoreCase(mode)) {
                    throw unsupported("External package relationships are not supported");
                }
                if (type.endsWith("/officeDocument")) {
                    String target = normalizeRelationshipTarget(
                            null,
                            WordXml.attribute(parser, "Target")
                    );
                    if (target == null || result != null) {
                        throw corrupted("Office document relationship is invalid", null);
                    }
                    result = new RootRelationship(target);
                }
            }
        }
        return result;
    }

    private static void validateDocumentRelationships(
            ZipFile zipFile,
            ZipEntry entry,
            String sourcePart,
            Set<String> entries,
            AtomicBoolean cancelled
    ) throws IOException, XmlPullParserException, WordParseException {
        Set<String> ids = new HashSet<>();
        try (InputStream inputStream = zipFile.getInputStream(entry)) {
            XmlPullParser parser = WordXml.newParser(inputStream);
            WordXml.Budget budget = new WordXml.Budget(cancelled);
            int event;
            while ((event = WordXml.next(parser, budget)) != XmlPullParser.END_DOCUMENT) {
                if (event != XmlPullParser.START_TAG
                        || !"Relationship".equals(parser.getName())) {
                    continue;
                }
                String id = WordXml.attribute(parser, "Id");
                String type = WordXml.attribute(parser, "Type");
                String target = WordXml.attribute(parser, "Target");
                boolean external = "External".equalsIgnoreCase(
                        WordXml.attribute(parser, "TargetMode")
                );
                if (id == null || type == null || !ids.add(id)) {
                    throw corrupted("Document relationship is invalid", null);
                }
                if (isForbiddenRelationshipType(type)) {
                    throw unsupported("Active or embedded relationships are not supported");
                }
                if (external) {
                    if (!type.endsWith("/hyperlink") || !isSafeHttps(target)) {
                        throw unsupported("External document resources are not supported");
                    }
                    continue;
                }
                String normalized = normalizeRelationshipTarget(sourcePart, target);
                if (normalized == null) {
                    throw corrupted("Document relationship target is unsafe", null);
                }
                if (isRequiredInternalRelationship(type) && !entries.contains(normalized)) {
                    throw corrupted("Related DOCX part is missing", null);
                }
            }
        }
    }

    private static boolean isSafeHttps(String target) {
        if (target == null || target.length() > 2_048) return false;
        try {
            URI uri = new URI(target);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null
                    && uri.getUserInfo() == null;
        } catch (URISyntaxException exception) {
            return false;
        }
    }

    private static boolean isRequiredInternalRelationship(String type) {
        return type.endsWith("/styles")
                || type.endsWith("/numbering")
                || type.endsWith("/settings")
                || type.endsWith("/theme")
                || type.endsWith("/image")
                || type.endsWith("/header")
                || type.endsWith("/footer")
                || type.endsWith("/footnotes")
                || type.endsWith("/endnotes");
    }

    private static boolean isForbiddenRelationshipType(String type) {
        String lower = type.toLowerCase(Locale.ROOT);
        return lower.endsWith("/attachedtemplate")
                || lower.endsWith("/oleobject")
                || lower.endsWith("/package")
                || lower.endsWith("/control")
                || lower.endsWith("/vbaProject".toLowerCase(Locale.ROOT))
                || lower.contains("activex")
                || lower.contains("externalLink".toLowerCase(Locale.ROOT));
    }

    private static void rejectActiveContentType(String contentType)
            throws WordParseException {
        if (contentType == null) return;
        String lower = contentType.toLowerCase(Locale.ROOT);
        if (lower.contains("macroenabled")
                || lower.contains("vbaproject")
                || lower.contains("activex")
                || lower.contains("oleobject")) {
            throw unsupported("Active content type is not supported");
        }
    }

    private static String normalizeEntryName(String name, boolean directory) {
        if (name == null || name.isEmpty() || name.length() > 512
                || name.startsWith("/") || name.startsWith("\\")
                || name.indexOf('\\') >= 0 || containsControlCharacter(name)) {
            return null;
        }
        if (name.length() >= 2 && Character.isLetter(name.charAt(0))
                && name.charAt(1) == ':') {
            return null;
        }
        String path = directory && name.endsWith("/")
                ? name.substring(0, name.length() - 1)
                : name;
        if (path.isEmpty()) return null;
        StringBuilder normalized = new StringBuilder();
        for (String segment : path.split("/", -1)) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)
                    || segment.indexOf(':') >= 0) {
                return null;
            }
            if (normalized.length() > 0) normalized.append('/');
            normalized.append(segment);
        }
        if (directory) normalized.append('/');
        return normalized.toString();
    }

    private static boolean isMediaPart(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.startsWith("word/media/") && !lower.endsWith("/");
    }

    private static boolean isForbiddenPart(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.startsWith("word/embeddings/")
                || lower.startsWith("word/activex/")
                || lower.startsWith("word/ctrlprops/")
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
                || lower.endsWith(".jar")
                || lower.endsWith(".js")
                || lower.endsWith(".vbs")
                || lower.endsWith(".ps1");
    }

    private static boolean containsControlCharacter(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) return true;
        }
        return false;
    }

    private static long checkedAdd(long left, long right) throws WordParseException {
        if (right > Long.MAX_VALUE - left) {
            throw tooLarge("ZIP size total overflow");
        }
        return left + right;
    }

    private static void ensureCentralDirectoryIsSafe(File file)
            throws IOException, WordParseException {
        try (RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r")) {
            long length = randomAccessFile.length();
            if (length < 4L) {
                throw corrupted("ZIP signature is missing", null);
            }
            byte[] signature = new byte[4];
            randomAccessFile.readFully(signature);
            if (littleEndianInt(signature, 0) != 0x04034B50L) {
                throw corrupted("ZIP signature is invalid", null);
            }
            int tailLength = (int) Math.min(length, 65_557L);
            byte[] tail = new byte[tailLength];
            randomAccessFile.seek(length - tailLength);
            randomAccessFile.readFully(tail);
            int eocd = findSignatureBackward(tail, 0x06054B50);
            if (eocd < 0 || eocd + 22 > tail.length) {
                throw corrupted("ZIP central directory is missing", null);
            }
            int entriesOnDisk = littleEndianShort(tail, eocd + 8);
            int entryCount = littleEndianShort(tail, eocd + 10);
            long centralSize = littleEndianInt(tail, eocd + 12);
            long centralOffset = littleEndianInt(tail, eocd + 16);
            if (littleEndianShort(tail, eocd + 4) != 0
                    || littleEndianShort(tail, eocd + 6) != 0
                    || entriesOnDisk != entryCount) {
                throw unsupported("Multi-disk ZIP packages are not supported");
            }
            if (entryCount == 0xFFFF || centralSize == 0xFFFFFFFFL
                    || centralOffset == 0xFFFFFFFFL) {
                throw unsupported("ZIP64 DOCX packages are not supported");
            }
            if (entryCount > DocumentLimits.MAX_DOCX_ZIP_ENTRIES
                    || centralOffset + centralSize > length
                    || centralOffset < 0L || centralSize < 0L) {
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
                    throw encrypted("Encrypted DOCX packages are not supported");
                }
                int method = littleEndianShort(header, 10);
                if (method != ZipEntry.STORED && method != ZipEntry.DEFLATED) {
                    throw unsupported("Unsupported ZIP compression method");
                }
                long skip = (long) littleEndianShort(header, 28)
                        + littleEndianShort(header, 30)
                        + littleEndianShort(header, 32);
                long next = randomAccessFile.getFilePointer() + skip;
                if (next > centralOffset + centralSize) {
                    throw corrupted("ZIP central entry exceeds its bounds", null);
                }
                long localOffset = littleEndianInt(header, 42);
                if (localOffset + 30L > length) {
                    throw corrupted("ZIP local header is invalid", null);
                }
                randomAccessFile.seek(localOffset);
                byte[] local = new byte[30];
                randomAccessFile.readFully(local);
                if (littleEndianInt(local, 0) != 0x04034B50L
                        || (littleEndianShort(local, 6) & 1) != 0
                        || littleEndianShort(local, 8) != method) {
                    throw corrupted("ZIP local header does not match central directory", null);
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

    private static WordParseException tooLarge(String message) {
        return new WordParseException(WordParseException.Reason.TOO_LARGE, message);
    }

    private static WordParseException unsupported(String message) {
        return new WordParseException(WordParseException.Reason.UNSUPPORTED, message);
    }

    private static WordParseException encrypted(String message) {
        return new WordParseException(WordParseException.Reason.ENCRYPTED, message);
    }

    private static WordParseException corrupted(String message, Throwable cause) {
        return cause == null
                ? new WordParseException(WordParseException.Reason.CORRUPTED, message)
                : new WordParseException(
                        WordParseException.Reason.CORRUPTED,
                        message,
                        cause
                );
    }

    private static final class CollectedEntries {
        private final Map<String, ZipEntry> entries;
        private final int mediaCount;
        private final long mediaBytes;

        private CollectedEntries(
                Map<String, ZipEntry> entries,
                int mediaCount,
                long mediaBytes
        ) {
            this.entries = entries;
            this.mediaCount = mediaCount;
            this.mediaBytes = mediaBytes;
        }
    }

    private static final class RootRelationship {
        private final String target;

        private RootRelationship(String target) {
            this.target = target;
        }
    }

    public static final class Inspection {
        private final boolean docx;
        private final String mainDocumentPart;
        private final String mainRelationshipsPart;
        private final Set<String> entryNames;
        private final int mediaEntryCount;
        private final long mediaBytes;

        private Inspection(
                boolean docx,
                String mainDocumentPart,
                String mainRelationshipsPart,
                Set<String> entryNames,
                int mediaEntryCount,
                long mediaBytes
        ) {
            this.docx = docx;
            this.mainDocumentPart = mainDocumentPart;
            this.mainRelationshipsPart = mainRelationshipsPart;
            this.entryNames = Collections.unmodifiableSet(new HashSet<>(entryNames));
            this.mediaEntryCount = mediaEntryCount;
            this.mediaBytes = mediaBytes;
        }

        private static Inspection notDocx(Set<String> entryNames) {
            return new Inspection(false, "", "", entryNames, 0, 0L);
        }

        public boolean isDocx() { return docx; }
        public String getMainDocumentPart() { return mainDocumentPart; }
        public String getMainRelationshipsPart() { return mainRelationshipsPart; }
        public Set<String> getEntryNames() { return entryNames; }
        public int getMediaEntryCount() { return mediaEntryCount; }
        public long getMediaBytes() { return mediaBytes; }
    }
}
