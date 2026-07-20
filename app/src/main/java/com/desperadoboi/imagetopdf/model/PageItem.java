package com.desperadoboi.imagetopdf.model;

import android.net.Uri;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

public final class PageItem {
    private static final AtomicLong NEXT_ID = new AtomicLong(1L);

    private final long id;
    private final Uri imageUri;
    private final int manualRotationDegrees;

    public PageItem(Uri imageUri) {
        this(NEXT_ID.getAndIncrement(), imageUri, 0);
    }

    public PageItem(Uri imageUri, int manualRotationDegrees) {
        this(NEXT_ID.getAndIncrement(), imageUri, manualRotationDegrees);
    }

    private PageItem(long id, Uri imageUri, int manualRotationDegrees) {
        this.imageUri = Objects.requireNonNull(imageUri, "imageUri is required");
        validateRotation(manualRotationDegrees);
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

    public PageItem rotateClockwise() {
        return new PageItem(id, imageUri, rotateClockwise(manualRotationDegrees));
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
}
