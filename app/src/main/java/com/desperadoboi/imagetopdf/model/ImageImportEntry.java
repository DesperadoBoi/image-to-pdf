package com.desperadoboi.imagetopdf.model;

import android.net.Uri;

import java.util.Objects;

public final class ImageImportEntry {
    private final Uri uri;
    private final ImageImportSource source;
    private final String capturedFileName;

    private ImageImportEntry(Uri uri, ImageImportSource source, String capturedFileName) {
        this.uri = Objects.requireNonNull(uri, "uri is required");
        this.source = Objects.requireNonNull(source, "source is required");
        if (source == ImageImportSource.CAMERA) {
            if (capturedFileName == null || capturedFileName.trim().isEmpty()) {
                throw new IllegalArgumentException("Camera entry requires capturedFileName");
            }
            this.capturedFileName = capturedFileName.trim();
        } else {
            this.capturedFileName = null;
        }
    }

    public static ImageImportEntry external(Uri uri, ImageImportSource source) {
        if (source == ImageImportSource.CAMERA) {
            throw new IllegalArgumentException("Use camera() for camera entries");
        }
        return new ImageImportEntry(uri, source, null);
    }

    public static ImageImportEntry camera(Uri uri, String capturedFileName) {
        return new ImageImportEntry(uri, ImageImportSource.CAMERA, capturedFileName);
    }

    public Uri getUri() {
        return uri;
    }

    public ImageImportSource getSource() {
        return source;
    }

    public String getCapturedFileName() {
        return capturedFileName;
    }
}
