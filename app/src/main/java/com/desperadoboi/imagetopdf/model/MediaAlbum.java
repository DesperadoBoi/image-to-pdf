package com.desperadoboi.imagetopdf.model;

import android.net.Uri;

import java.util.Objects;

public final class MediaAlbum {
    private final String bucketId;
    private final String displayName;
    private final Uri coverUri;
    private final int imageCount;

    public MediaAlbum(String bucketId, String displayName, Uri coverUri, int imageCount) {
        if (bucketId == null || bucketId.trim().isEmpty()) {
            throw new IllegalArgumentException("bucketId is required");
        }
        if (displayName == null || displayName.trim().isEmpty()) {
            throw new IllegalArgumentException("displayName is required");
        }
        if (imageCount <= 0) {
            throw new IllegalArgumentException("imageCount must be positive");
        }
        this.bucketId = bucketId.trim();
        this.displayName = displayName.trim();
        this.coverUri = Objects.requireNonNull(coverUri, "coverUri is required");
        this.imageCount = imageCount;
    }

    public String getBucketId() {
        return bucketId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Uri getCoverUri() {
        return coverUri;
    }

    public int getImageCount() {
        return imageCount;
    }
}
