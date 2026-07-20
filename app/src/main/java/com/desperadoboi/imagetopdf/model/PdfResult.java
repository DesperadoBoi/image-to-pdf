package com.desperadoboi.imagetopdf.model;

import android.net.Uri;

import java.util.Objects;

public final class PdfResult {
    public static final long UNKNOWN_SIZE_BYTES = -1L;

    private final Uri uri;
    private final String displayName;
    private final long sizeBytes;
    private final int pageCount;

    public PdfResult(Uri uri, String displayName, long sizeBytes, int pageCount) {
        this.uri = Objects.requireNonNull(uri, "uri is required");
        this.displayName = displayName == null ? "" : displayName.trim();
        if (sizeBytes < 0L && sizeBytes != UNKNOWN_SIZE_BYTES) {
            throw new IllegalArgumentException("sizeBytes must be non-negative or unknown");
        }
        if (pageCount < 0) {
            throw new IllegalArgumentException("pageCount must be non-negative");
        }
        this.sizeBytes = sizeBytes;
        this.pageCount = pageCount;
    }

    public Uri getUri() {
        return uri;
    }

    public String getDisplayName() {
        return displayName;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public boolean hasKnownSize() {
        return sizeBytes != UNKNOWN_SIZE_BYTES;
    }

    public int getPageCount() {
        return pageCount;
    }
}
