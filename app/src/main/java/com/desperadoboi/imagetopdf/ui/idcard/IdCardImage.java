package com.desperadoboi.imagetopdf.ui.idcard;

import android.net.Uri;

import com.desperadoboi.imagetopdf.model.PageItem;
import com.desperadoboi.imagetopdf.model.PerspectiveQuad;
import com.desperadoboi.imagetopdf.ui.smartscan.ScanPage;

import java.util.Objects;

public final class IdCardImage {
    public static final PerspectiveQuad DEFAULT_CARD_QUAD = new PerspectiveQuad(
            new com.desperadoboi.imagetopdf.model.NormalizedPoint(0.06f, 0.18f),
            new com.desperadoboi.imagetopdf.model.NormalizedPoint(0.94f, 0.18f),
            new com.desperadoboi.imagetopdf.model.NormalizedPoint(0.94f, 0.82f),
            new com.desperadoboi.imagetopdf.model.NormalizedPoint(0.06f, 0.82f)
    );

    private final String uriString;
    private final String cacheFileName;
    private final int rotationDegrees;
    private final PerspectiveQuad perspectiveQuad;

    public IdCardImage(
            String uriString,
            String cacheFileName,
            int rotationDegrees,
            PerspectiveQuad perspectiveQuad
    ) {
        this.uriString = requireText(uriString, "uriString");
        this.cacheFileName = requireText(cacheFileName, "cacheFileName");
        if (rotationDegrees != 0
                && rotationDegrees != 90
                && rotationDegrees != 180
                && rotationDegrees != 270) {
            throw new IllegalArgumentException("rotation must be a right angle");
        }
        this.rotationDegrees = rotationDegrees;
        this.perspectiveQuad = Objects.requireNonNull(
                perspectiveQuad,
                "perspectiveQuad is required"
        );
    }

    public String getUriString() {
        return uriString;
    }

    public Uri getUri() {
        return Uri.parse(uriString);
    }

    public String getCacheFileName() {
        return cacheFileName;
    }

    public int getRotationDegrees() {
        return rotationDegrees;
    }

    public PerspectiveQuad getPerspectiveQuad() {
        return perspectiveQuad;
    }

    public IdCardImage rotateClockwise() {
        return new IdCardImage(
                uriString,
                cacheFileName,
                PageItem.rotateClockwise(rotationDegrees),
                perspectiveQuad.rotateClockwise()
        );
    }

    public IdCardImage withPerspectiveQuad(PerspectiveQuad quad) {
        return new IdCardImage(uriString, cacheFileName, rotationDegrees, quad);
    }

    public ScanPage toScanPage(IdCardSide side) {
        return new ScanPage(
                "id-card:" + side.name(),
                uriString,
                true,
                cacheFileName,
                rotationDegrees,
                perspectiveQuad,
                false,
                0L,
                side.ordinal()
        );
    }

    public PageItem toPageItem() {
        PageItem page = PageItem.camera(getUri(), cacheFileName);
        for (int degrees = 0; degrees < rotationDegrees; degrees += 90) {
            page = page.rotateClockwise();
        }
        return page.withPerspectiveQuad(perspectiveQuad);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
