package com.desperadoboi.imagetopdf.document;

public enum DocumentType {
    PDF("application/pdf", true),
    XLS("application/vnd.ms-excel", false),
    XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", true),
    CSV("text/csv", true),
    TSV("text/tab-separated-values", true),
    TEXT("text/plain", true),
    JPEG("image/jpeg", true),
    PNG("image/png", true),
    WEBP("image/webp", true),
    HEIC("image/heic", true),
    UNKNOWN("", false);

    private final String mimeType;
    private final boolean viewable;

    DocumentType(String mimeType, boolean viewable) {
        this.mimeType = mimeType;
        this.viewable = viewable;
    }

    public String getMimeType() {
        return mimeType;
    }

    public boolean isViewable() {
        return viewable;
    }

    public boolean isImage() {
        return this == JPEG || this == PNG || this == WEBP || this == HEIC;
    }

    public boolean isSpreadsheetText() {
        return this == CSV || this == TSV;
    }
}
