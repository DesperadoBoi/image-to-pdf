package com.desperadoboi.imagetopdf.ui.smartscan;

import android.net.Uri;

import com.desperadoboi.imagetopdf.model.NormalizedPoint;
import com.desperadoboi.imagetopdf.model.PageItem;
import com.desperadoboi.imagetopdf.model.PerspectiveQuad;

import java.util.Objects;

public final class ScanPage {
    public static final PerspectiveQuad DEFAULT_DOCUMENT_QUAD = new PerspectiveQuad(
            new NormalizedPoint(0.06f, 0.06f),
            new NormalizedPoint(0.94f, 0.06f),
            new NormalizedPoint(0.94f, 0.94f),
            new NormalizedPoint(0.06f, 0.94f)
    );

    private final String id;
    private final String sourceUri;
    private final boolean appOwned;
    private final String capturedFileName;
    private final int rotationDegrees;
    private final PerspectiveQuad perspectiveQuad;
    private final boolean original;
    private final long createdAt;
    private final int order;

    public ScanPage(
            String id,
            String sourceUri,
            boolean appOwned,
            String capturedFileName,
            int rotationDegrees,
            PerspectiveQuad perspectiveQuad,
            boolean original,
            long createdAt,
            int order
    ) {
        this.id = requireText(id, "id");
        this.sourceUri = requireText(sourceUri, "sourceUri");
        this.appOwned = appOwned;
        this.capturedFileName = normalizeFileName(appOwned, capturedFileName);
        validateRotation(rotationDegrees);
        this.rotationDegrees = rotationDegrees;
        this.perspectiveQuad = Objects.requireNonNull(
                perspectiveQuad,
                "perspectiveQuad is required"
        );
        this.original = original;
        this.createdAt = createdAt;
        this.order = order;
    }

    public String getId() {
        return id;
    }

    public String getSourceUriString() {
        return sourceUri;
    }

    public Uri getSourceUri() {
        return Uri.parse(sourceUri);
    }

    public boolean isAppOwned() {
        return appOwned;
    }

    public String getCapturedFileName() {
        return capturedFileName;
    }

    public int getRotationDegrees() {
        return rotationDegrees;
    }

    public PerspectiveQuad getPerspectiveQuad() {
        return perspectiveQuad;
    }

    public boolean isOriginal() {
        return original;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public int getOrder() {
        return order;
    }

    public ScanPage rotateClockwise() {
        return copy(
                PageItem.rotateClockwise(rotationDegrees),
                perspectiveQuad.rotateClockwise(),
                original,
                order
        );
    }

    public ScanPage withPerspectiveQuad(PerspectiveQuad quad) {
        return copy(rotationDegrees, Objects.requireNonNull(quad), false, order);
    }

    public ScanPage withDefaultCrop() {
        return copy(rotationDegrees, DEFAULT_DOCUMENT_QUAD, false, order);
    }

    public ScanPage withOriginal() {
        return copy(rotationDegrees, PerspectiveQuad.FULL, true, order);
    }

    public ScanPage withOrder(int newOrder) {
        return copy(rotationDegrees, perspectiveQuad, original, newOrder);
    }

    public PageItem toPageItem() {
        PageItem page = appOwned
                ? PageItem.camera(getSourceUri(), capturedFileName)
                : PageItem.gallery(getSourceUri());
        for (int degrees = 0; degrees < rotationDegrees; degrees += 90) {
            page = page.rotateClockwise();
        }
        if (!original) {
            page = page.withPerspectiveQuad(perspectiveQuad);
        }
        return page;
    }

    private ScanPage copy(
            int newRotation,
            PerspectiveQuad newQuad,
            boolean newOriginal,
            int newOrder
    ) {
        return new ScanPage(
                id,
                sourceUri,
                appOwned,
                capturedFileName,
                newRotation,
                newQuad,
                newOriginal,
                createdAt,
                newOrder
        );
    }

    private static String requireText(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }

    private static String normalizeFileName(boolean appOwned, String fileName) {
        if (!appOwned) {
            return null;
        }
        return requireText(fileName, "capturedFileName");
    }

    private static void validateRotation(int degrees) {
        if (degrees != 0 && degrees != 90 && degrees != 180 && degrees != 270) {
            throw new IllegalArgumentException("rotation must be a right angle");
        }
    }
}
