package com.desperadoboi.imagetopdf.document;

public final class DocumentLimits {
    public static final long MAX_INCOMING_BYTES = 250L * 1024L * 1024L;
    public static final long MAX_PDF_BYTES = 200L * 1024L * 1024L;
    public static final long MAX_IMAGE_BYTES = 100L * 1024L * 1024L;
    public static final long MAX_TEXT_BYTES = 25L * 1024L * 1024L;
    public static final long MAX_XLSX_BYTES = 50L * 1024L * 1024L;
    public static final long MAX_DOCX_BYTES = 50L * 1024L * 1024L;
    public static final int MAX_TEXT_PREVIEW_CHARS = 2_000_000;
    public static final int MAX_TEXT_LINES = 10_000;
    public static final int MAX_TEXT_LINE_CHARS = 8_192;
    public static final int MAX_SPREADSHEET_ROWS = 5_000;
    public static final int MAX_SPREADSHEET_COLUMNS = 100;
    public static final int MAX_CELL_CHARS = 4_096;
    public static final int MAX_ZIP_ENTRIES = 512;
    public static final long MAX_ZIP_UNCOMPRESSED_BYTES = 100L * 1024L * 1024L;
    public static final long MAX_ZIP_ENTRY_BYTES = 20L * 1024L * 1024L;
    public static final int MAX_ZIP_RATIO = 100;
    public static final long MAX_SHARED_STRINGS_BYTES = 16L * 1024L * 1024L;
    public static final int MAX_SHARED_STRINGS = 100_000;
    public static final int MAX_SHARED_STRING_CHARS = 8_000_000;
    public static final int MAX_XLSX_SHEETS = 64;
    public static final int MAX_XLSX_MERGED_RANGES = 10_000;
    public static final int MAX_XLSX_PARSED_CELLS = 1_000_000;
    public static final int MAX_XML_DEPTH = 64;
    public static final int MAX_XML_EVENTS = 2_000_000;
    public static final int MAX_DOCX_ZIP_ENTRIES = 1_024;
    public static final long MAX_DOCX_UNCOMPRESSED_BYTES = 150L * 1024L * 1024L;
    public static final long MAX_DOCX_ENTRY_BYTES = 32L * 1024L * 1024L;
    public static final long MAX_DOCX_MEDIA_ENTRY_BYTES = 16L * 1024L * 1024L;
    public static final long MAX_DOCX_MEDIA_BYTES = 80L * 1024L * 1024L;
    public static final int MAX_DOCX_COMPRESSION_RATIO = 100;
    public static final int MAX_DOCX_XML_EVENTS = 3_000_000;
    public static final int MAX_WORD_BLOCKS = 30_000;
    public static final int MAX_WORD_PARAGRAPHS = 20_000;
    public static final int MAX_WORD_RUNS = 100_000;
    public static final int MAX_WORD_TABLES = 500;
    public static final int MAX_WORD_TABLE_ROWS = 20_000;
    public static final int MAX_WORD_TABLE_CELLS = 50_000;
    public static final int MAX_WORD_IMAGES = 100;
    public static final int MAX_WORD_RUN_CHARS = 8_192;
    public static final int MAX_WORD_TOTAL_CHARS = 8_000_000;
    public static final int MAX_WORD_STYLE_DEPTH = 32;
    public static final int MAX_WORD_TABLE_DEPTH = 4;

    private DocumentLimits() {
    }

    public static boolean isAllowedKnownSize(long sizeBytes, DocumentType type) {
        if (sizeBytes < 0L || sizeBytes > MAX_INCOMING_BYTES) {
            return false;
        }
        if (type == null || type == DocumentType.UNKNOWN || type == DocumentType.XLS) {
            return true;
        }
        if (type == DocumentType.XLSX) {
            return sizeBytes <= MAX_XLSX_BYTES;
        }
        if (type == DocumentType.DOCX) {
            return sizeBytes <= MAX_DOCX_BYTES;
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
