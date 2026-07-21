package com.desperadoboi.imagetopdf.model;

import android.net.Uri;

import java.util.Objects;

public final class PdfResult {
    public static final long UNKNOWN_SIZE = -1L;

    private final Uri uri;
    private final String displayName;
    private final long sizeBytes;
    private final int pageCount;
    private final long timestamp;
    private final String locationLabel;

    public PdfResult(Uri uri, String displayName, long sizeBytes, int pageCount) {
        this(uri, displayName, sizeBytes, pageCount, 0L, "");
    }

    public PdfResult(
            Uri uri,
            String displayName,
            long sizeBytes,
            int pageCount,
            long timestamp,
            String locationLabel
    ) {
        this.uri = Objects.requireNonNull(uri, "uri is required");
        this.displayName = displayName == null ? "" : displayName.trim();
        if (sizeBytes < UNKNOWN_SIZE) {
            throw new IllegalArgumentException("sizeBytes must be known or UNKNOWN_SIZE");
        }
        if (pageCount < 0) {
            throw new IllegalArgumentException("pageCount must be non-negative");
        }
        if (timestamp < 0L) {
            throw new IllegalArgumentException("timestamp must be non-negative");
        }
        this.sizeBytes = sizeBytes;
        this.pageCount = pageCount;
        this.timestamp = timestamp;
        this.locationLabel = locationLabel == null ? "" : locationLabel.trim();
    }

    public static PdfResult pendingMetadata(
            Uri uri,
            String displayName,
            int pageCount,
            long timestamp,
            String locationLabel
    ) {
        return new PdfResult(
                uri,
                displayName,
                UNKNOWN_SIZE,
                pageCount,
                timestamp,
                locationLabel
        );
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
        return sizeBytes >= 0L;
    }

    public int getPageCount() {
        return pageCount;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getLocationLabel() {
        return locationLabel;
    }

    public PdfResult withKnownMetadata(
            String newDisplayName,
            long newSizeBytes,
            String newLocationLabel
    ) {
        if (newSizeBytes < 0L) {
            throw new IllegalArgumentException("newSizeBytes must be non-negative");
        }
        return new PdfResult(
                uri,
                newDisplayName,
                newSizeBytes,
                pageCount,
                timestamp,
                newLocationLabel
        );
    }

    public PdfResult withDisplayNameAndLocation(
            String newDisplayName,
            String newLocationLabel
    ) {
        return new PdfResult(
                uri,
                newDisplayName,
                sizeBytes,
                pageCount,
                timestamp,
                newLocationLabel
        );
    }
}
