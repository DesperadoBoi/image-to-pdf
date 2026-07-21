package com.desperadoboi.imagetopdf.model;

import android.net.Uri;

import java.util.Objects;

public final class MediaImage {
    private final Uri uri;
    private final long id;
    private final String displayName;
    private final long dateAdded;
    private final long dateTaken;
    private final long size;
    private final int width;
    private final int height;
    private final String bucketId;
    private final String bucketName;

    public MediaImage(
            Uri uri,
            long id,
            String displayName,
            long dateAdded,
            long dateTaken,
            long size,
            int width,
            int height,
            String bucketId,
            String bucketName
    ) {
        this.uri = Objects.requireNonNull(uri, "uri is required");
        this.id = id;
        this.displayName = normalize(displayName);
        this.dateAdded = Math.max(0L, dateAdded);
        this.dateTaken = Math.max(0L, dateTaken);
        this.size = Math.max(0L, size);
        this.width = Math.max(0, width);
        this.height = Math.max(0, height);
        this.bucketId = normalize(bucketId);
        this.bucketName = normalize(bucketName);
    }

    public Uri getUri() {
        return uri;
    }

    public long getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public long getDateAdded() {
        return dateAdded;
    }

    public long getDateTaken() {
        return dateTaken;
    }

    public long getSize() {
        return size;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public String getBucketId() {
        return bucketId;
    }

    public String getBucketName() {
        return bucketName;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
