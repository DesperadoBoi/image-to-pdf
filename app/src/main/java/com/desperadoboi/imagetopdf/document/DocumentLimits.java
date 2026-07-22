package com.desperadoboi.imagetopdf.document;

public final class DocumentLimits {
    public static final long MAX_INCOMING_BYTES = 250L * 1024L * 1024L;
    public static final long MAX_PDF_BYTES = 200L * 1024L * 1024L;
    public static final long MAX_IMAGE_BYTES = 100L * 1024L * 1024L;
    public static final long MAX_TEXT_BYTES = 25L * 1024L * 1024L;
    public static final int MAX_TEXT_PREVIEW_CHARS = 2_000_000;
    public static final int MAX_TEXT_LINES = 10_000;
    public static final int MAX_TEXT_LINE_CHARS = 8_192;
    public static final int MAX_SPREADSHEET_ROWS = 5_000;
    public static final int MAX_SPREADSHEET_COLUMNS = 100;
    public static final int MAX_CELL_CHARS = 4_096;
    public static final int MAX_ZIP_ENTRIES = 2_000;
    public static final long MAX_ZIP_UNCOMPRESSED_BYTES = 200L * 1024L * 1024L;
    public static final int MAX_ZIP_RATIO = 100;

    private DocumentLimits() {
    }

    public static boolean isAllowedKnownSize(long sizeBytes, DocumentType type) {
        if (sizeBytes < 0L || sizeBytes > MAX_INCOMING_BYTES) {
            return false;
        }
        if (type == null || type == DocumentType.UNKNOWN || type == DocumentType.XLS
                || type == DocumentType.XLSX) {
            return true;
        }
        if (type == DocumentType.PDF) {
            return sizeBytes <= MAX_PDF_BYTES;
        }
        if (type.isImage()) {
            return sizeBytes <= MAX_IMAGE_BYTES;
        }
        return sizeBytes <= MAX_TEXT_BYTES;
    }
}
