package com.desperadoboi.imagetopdf.document;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;

public final class DocumentTypeResolver {
    private static final byte[] PDF = {'%', 'P', 'D', 'F'};
    private static final byte[] XLS = {
            (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
            (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1
    };
    private static final byte[] PNG = {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A
    };

    public DocumentType resolve(
            String mimeType,
            byte[] prefix,
            Set<String> zipEntryNames,
            String displayName
    ) {
        byte[] safePrefix = prefix == null ? new byte[0] : prefix;
        Set<String> safeZipEntries = zipEntryNames == null
                ? Collections.emptySet()
                : zipEntryNames;
        DocumentType signatureType = fromSignature(safePrefix, safeZipEntries);
        if (signatureType != DocumentType.UNKNOWN) {
            return signatureType;
        }
        if (isZip(safePrefix)) {
            return DocumentType.UNKNOWN;
        }

        DocumentType mimeTypeResult = fromMimeType(mimeType);
        if (mimeTypeResult != DocumentType.UNKNOWN) {
            if (isClearlyBinary(safePrefix) && isTextType(mimeTypeResult)) {
                return DocumentType.UNKNOWN;
            }
            return mimeTypeResult;
        }

        DocumentType extensionType = fromExtension(displayName);
        if (extensionType != DocumentType.UNKNOWN) {
            if (isClearlyBinary(safePrefix) && isTextType(extensionType)) {
                return DocumentType.UNKNOWN;
            }
            return extensionType;
        }
        return isLikelyUtfText(safePrefix) ? DocumentType.TEXT : DocumentType.UNKNOWN;
    }

    public DocumentType fromMimeType(String mimeType) {
        if (mimeType == null) {
            return DocumentType.UNKNOWN;
        }
        switch (mimeType.trim().toLowerCase(Locale.ROOT)) {
            case "application/pdf":
                return DocumentType.PDF;
            case "application/vnd.ms-excel":
                return DocumentType.XLS;
            case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet":
                return DocumentType.XLSX;
            case "text/csv":
            case "application/csv":
                return DocumentType.CSV;
            case "text/tab-separated-values":
                return DocumentType.TSV;
            case "text/plain":
                return DocumentType.TEXT;
            case "image/jpeg":
                return DocumentType.JPEG;
            case "image/png":
                return DocumentType.PNG;
            case "image/webp":
                return DocumentType.WEBP;
            case "image/heic":
            case "image/heif":
                return DocumentType.HEIC;
            default:
                return DocumentType.UNKNOWN;
        }
    }

    private DocumentType fromSignature(byte[] bytes, Set<String> zipEntries) {
        if (startsWith(bytes, PDF)) {
            return DocumentType.PDF;
        }
        if (startsWith(bytes, XLS)) {
            return DocumentType.XLS;
        }
        if (startsWith(bytes, PNG)) {
            return DocumentType.PNG;
        }
        if (bytes.length >= 3
                && unsigned(bytes[0]) == 0xFF
                && unsigned(bytes[1]) == 0xD8
                && unsigned(bytes[2]) == 0xFF) {
            return DocumentType.JPEG;
        }
        if (bytes.length >= 12
                && asciiEquals(bytes, 0, "RIFF")
                && asciiEquals(bytes, 8, "WEBP")) {
            return DocumentType.WEBP;
        }
        if (isHeif(bytes)) {
            return DocumentType.HEIC;
        }
        if (isZip(bytes)
                && zipEntries.contains("[Content_Types].xml")
                && zipEntries.contains("xl/workbook.xml")) {
            return DocumentType.XLSX;
        }
        return DocumentType.UNKNOWN;
    }

    private DocumentType fromExtension(String displayName) {
        String name = SafeDisplayName.sanitize(displayName).toLowerCase(Locale.ROOT);
        if (name.endsWith(".pdf")) return DocumentType.PDF;
        if (name.endsWith(".xls")) return DocumentType.XLS;
        if (name.endsWith(".xlsx")) return DocumentType.XLSX;
        if (name.endsWith(".csv")) return DocumentType.CSV;
        if (name.endsWith(".tsv")) return DocumentType.TSV;
        if (name.endsWith(".txt")) return DocumentType.TEXT;
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return DocumentType.JPEG;
        if (name.endsWith(".png")) return DocumentType.PNG;
        if (name.endsWith(".webp")) return DocumentType.WEBP;
        if (name.endsWith(".heic") || name.endsWith(".heif")) return DocumentType.HEIC;
        return DocumentType.UNKNOWN;
    }

    private boolean isHeif(byte[] bytes) {
        if (bytes.length < 12 || !asciiEquals(bytes, 4, "ftyp")) {
            return false;
        }
        String brand = new String(bytes, 8, 4, StandardCharsets.US_ASCII);
        return "heic".equals(brand) || "heix".equals(brand) || "hevc".equals(brand)
                || "hevx".equals(brand) || "mif1".equals(brand) || "msf1".equals(brand);
    }

    private boolean isZip(byte[] bytes) {
        return bytes.length >= 4
                && bytes[0] == 'P'
                && bytes[1] == 'K'
                && (bytes[2] == 3 || bytes[2] == 5 || bytes[2] == 7)
                && (bytes[3] == 4 || bytes[3] == 6 || bytes[3] == 8);
    }

    private boolean isClearlyBinary(byte[] bytes) {
        if (bytes.length == 0) {
            return false;
        }
        int controls = 0;
        for (byte value : bytes) {
            int unsigned = unsigned(value);
            if (unsigned == 0) {
                return true;
            }
            if (unsigned < 0x20 && unsigned != '\n' && unsigned != '\r' && unsigned != '\t') {
                controls++;
            }
        }
        return controls > Math.max(2, bytes.length / 10);
    }

    private boolean isLikelyUtfText(byte[] bytes) {
        if (bytes.length == 0) {
            return true;
        }
        int offset = bytes.length >= 3
                && unsigned(bytes[0]) == 0xEF
                && unsigned(bytes[1]) == 0xBB
                && unsigned(bytes[2]) == 0xBF ? 3 : 0;
        for (int index = offset; index < bytes.length; index++) {
            if (bytes[index] == 0) {
                return false;
            }
        }
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, offset, bytes.length - offset));
            return true;
        } catch (CharacterCodingException exception) {
            return false;
        }
    }

    private boolean isTextType(DocumentType type) {
        return type == DocumentType.TEXT || type == DocumentType.CSV || type == DocumentType.TSV;
    }

    private boolean startsWith(byte[] bytes, byte[] expected) {
        if (bytes.length < expected.length) {
            return false;
        }
        for (int index = 0; index < expected.length; index++) {
            if (bytes[index] != expected[index]) {
                return false;
            }
        }
        return true;
    }

    private boolean asciiEquals(byte[] bytes, int offset, String expected) {
        if (bytes.length < offset + expected.length()) {
            return false;
        }
        for (int index = 0; index < expected.length(); index++) {
            if (bytes[offset + index] != (byte) expected.charAt(index)) {
                return false;
            }
        }
        return true;
    }

    private int unsigned(byte value) {
        return value & 0xFF;
    }
}
