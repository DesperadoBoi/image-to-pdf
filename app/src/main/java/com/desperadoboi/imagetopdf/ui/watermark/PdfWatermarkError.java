package com.desperadoboi.imagetopdf.ui.watermark;

public enum PdfWatermarkError {
    NONE,
    OPEN_PDF,
    CORRUPTED_PDF,
    ENCRYPTED_PDF,
    EMPTY_PDF,
    TOO_LARGE,
    RENDER_PAGE,
    OUT_OF_MEMORY,
    CREATE_PDF,
    SAVE_PDF,
    SOURCE_UNAVAILABLE,
    CANCELLED
}
