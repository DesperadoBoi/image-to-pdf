package com.desperadoboi.imagetopdf.model;

import android.net.Uri;

import java.util.Objects;

public final class PdfResult {
    private final Uri uri;
    private final String displayName;
    private final long sizeBytes;
    private final boolean sizeKnown;
    private final int pageCount;
    private final long timestamp;
    private final String locationLabel;

    public PdfResult(Uri uri, String displayName, long sizeBytes, int pageCount) {
        this(uri, displayName, sizeBytes, true, pageCount, 0L, "");
    }

    public PdfResult(
            Uri uri,
            String displayName,
            long sizeBytes,
            int pageCount,
            long timestamp,
            String locationLabel
    ) {
        this(uri, displayName, sizeBytes, true, pageCount, timestamp, locationLabel);
    }

    private PdfResult(
            Uri uri,
            String displayName,
            long sizeBytes,
            boolean sizeKnown,
            int pageCount,
            long timestamp,
            String locationLabel
    ) {
        this.uri = Objects.requireNonNull(uri, "uri is required");
        this.displayName = displayName == null ? "" : displayName.trim();
        if (sizeBytes < 0L) {
            throw new IllegalArgumentException("sizeBytes must be non-negative");
        }
        if (pageCount < 0) {
            throw new IllegalArgumentException("pageCount must be non-negative");
        }
        if (timestamp < 0L) {
            throw new IllegalArgumentException("timestamp must be non-negative");
        }
        this.sizeBytes = sizeBytes;
        this.sizeKnown = sizeKnown;
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
                0L,
                false,
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
        return sizeKnown;
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
        return new PdfResult(
                uri,
                newDisplayName,
                newSizeBytes,
                true,
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
                sizeKnown,
                pageCount,
                timestamp,
                newLocationLabel
        );
    }
}
