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
    private final PageEditSpec editSpec;

    public PageItem(Uri imageUri) {
        this(
                NEXT_ID.getAndIncrement(),
                imageUri,
                0,
                PageSource.GALLERY,
                null,
                PageEditSpec.DEFAULT
        );
    }

    public PageItem(Uri imageUri, int manualRotationDegrees) {
        this(
                NEXT_ID.getAndIncrement(),
                imageUri,
                manualRotationDegrees,
                PageSource.GALLERY,
                null,
                PageEditSpec.DEFAULT
        );
    }

    public static PageItem camera(Uri imageUri, String capturedFileName) {
        return new PageItem(
                NEXT_ID.getAndIncrement(),
                imageUri,
                0,
                PageSource.CAMERA,
                capturedFileName,
                PageEditSpec.DEFAULT
        );
    }

    public static PageItem gallery(Uri imageUri) {
        return new PageItem(
                NEXT_ID.getAndIncrement(),
                imageUri,
                0,
                PageSource.GALLERY,
                null,
                PageEditSpec.DEFAULT
        );
    }

    public static PageItem files(Uri imageUri) {
        return new PageItem(
                NEXT_ID.getAndIncrement(),
                imageUri,
                0,
                PageSource.FILES,
                null,
                PageEditSpec.DEFAULT
        );
    }

    private PageItem(
            long id,
            Uri imageUri,
            int manualRotationDegrees,
            PageSource source,
            String capturedFileName,
            PageEditSpec editSpec
    ) {
        this.imageUri = Objects.requireNonNull(imageUri, "imageUri is required");
        validateRotation(manualRotationDegrees);
        this.source = Objects.requireNonNull(source, "source is required");
        this.capturedFileName = normalizeCapturedFileName(source, capturedFileName);
        this.id = id;
        this.manualRotationDegrees = manualRotationDegrees;
        this.editSpec = Objects.requireNonNull(editSpec, "editSpec is required");
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

    public PageEditSpec getEditSpec() {
        return editSpec;
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
                capturedFileName,
                editSpec.rotateClockwise()
        );
    }

    public PageItem rotateCounterClockwise() {
        return new PageItem(
                id,
                imageUri,
                rotateCounterClockwise(manualRotationDegrees),
                source,
                capturedFileName,
                editSpec.rotateCounterClockwise()
        );
    }

    public PageItem withCropRect(CropRect cropRect) {
        return withEditSpec(editSpec.withCropRect(cropRect));
    }

    public PageItem withPerspectiveQuad(PerspectiveQuad perspectiveQuad) {
        return withEditSpec(editSpec.withPerspectiveQuad(perspectiveQuad));
    }

    public PageItem resetCrop() {
        return withCropRect(CropRect.FULL);
    }

    public PageItem resetPerspective() {
        return withEditSpec(new PageEditSpec(CropRect.FULL, PerspectiveQuad.FULL));
    }

    public PageItem resetEdits() {
        return new PageItem(
                id,
                imageUri,
                0,
                source,
                capturedFileName,
                PageEditSpec.DEFAULT
        );
    }

    public boolean swapsDimensions() {
        return manualRotationDegrees == 90 || manualRotationDegrees == 270;
    }

    public String getThumbnailKey() {
        return id + "#" + manualRotationDegrees + "#" + editSpec.toKey();
    }

    public static int rotateClockwise(int rotationDegrees) {
        validateRotation(rotationDegrees);
        return (rotationDegrees + 90) % 360;
    }

    public static int rotateCounterClockwise(int rotationDegrees) {
        validateRotation(rotationDegrees);
        return (rotationDegrees + 270) % 360;
    }

    private PageItem withEditSpec(PageEditSpec newEditSpec) {
        if (editSpec.equals(newEditSpec)) {
            return this;
        }
        return new PageItem(
                id,
                imageUri,
                manualRotationDegrees,
                source,
                capturedFileName,
                newEditSpec
        );
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
        if (source != PageSource.CAMERA) {
            return null;
        }
        if (capturedFileName == null || capturedFileName.trim().isEmpty()) {
            throw new IllegalArgumentException("Camera page requires captured file name");
        }
        return capturedFileName.trim();
    }
}
