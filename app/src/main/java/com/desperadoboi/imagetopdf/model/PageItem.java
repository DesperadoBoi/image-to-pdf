package com.desperadoboi.imagetopdf.model;

import android.net.Uri;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

public final class PageItem {
    private static final AtomicLong NEXT_ID = new AtomicLong(1L);

    private final long id;
    private final Uri imageUri;
    private final int manualRotationDegrees;
    private final PageSource source;
    private final String capturedFileName;

    public PageItem(Uri imageUri) {
        this(NEXT_ID.getAndIncrement(), imageUri, 0, PageSource.GALLERY, null);
    }

    public PageItem(Uri imageUri, int manualRotationDegrees) {
        this(NEXT_ID.getAndIncrement(), imageUri, manualRotationDegrees, PageSource.GALLERY, null);
    }

    public static PageItem camera(Uri imageUri, String capturedFileName) {
        return new PageItem(
                NEXT_ID.getAndIncrement(),
                imageUri,
                0,
                PageSource.CAMERA,
                capturedFileName
        );
    }

    private PageItem(
            long id,
            Uri imageUri,
            int manualRotationDegrees,
            PageSource source,
            String capturedFileName
    ) {
        this.imageUri = Objects.requireNonNull(imageUri, "imageUri is required");
        validateRotation(manualRotationDegrees);
        this.source = Objects.requireNonNull(source, "source is required");
        this.capturedFileName = normalizeCapturedFileName(source, capturedFileName);
        this.id = id;
        this.manualRotationDegrees = manualRotationDegrees;
    }

    public long getId() {
        return id;
    }

    public Uri getImageUri() {
        return imageUri;
    }

    public int getManualRotationDegrees() {
        return manualRotationDegrees;
    }

    public PageSource getSource() {
        return source;
    }

    public String getCapturedFileName() {
        return capturedFileName;
    }

    public boolean isAppOwnedCapture() {
        return source == PageSource.CAMERA;
    }

    public PageItem rotateClockwise() {
        return new PageItem(
                id,
                imageUri,
                rotateClockwise(manualRotationDegrees),
                source,
                capturedFileName
        );
    }

    public boolean swapsDimensions() {
        return manualRotationDegrees == 90 || manualRotationDegrees == 270;
    }

    public String getThumbnailKey() {
        return id + "#" + manualRotationDegrees;
    }

    public static int rotateClockwise(int rotationDegrees) {
        validateRotation(rotationDegrees);
        return (rotationDegrees + 90) % 360;
    }

    private static void validateRotation(int rotationDegrees) {
        if (rotationDegrees != 0
                && rotationDegrees != 90
                && rotationDegrees != 180
                && rotationDegrees != 270) {
            throw new IllegalArgumentException("Rotation must be 0, 90, 180, or 270 degrees");
        }
    }

    private static String normalizeCapturedFileName(PageSource source, String capturedFileName) {
        if (source == PageSource.GALLERY) {
            return null;
        }
        if (capturedFileName == null || capturedFileName.trim().isEmpty()) {
            throw new IllegalArgumentException("Camera page requires captured file name");
        }
        return capturedFileName.trim();
    }
}
