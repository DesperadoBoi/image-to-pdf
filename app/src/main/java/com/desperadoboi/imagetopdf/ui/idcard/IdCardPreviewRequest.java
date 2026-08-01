package com.desperadoboi.imagetopdf.ui.idcard;

import android.net.Uri;

import com.desperadoboi.imagetopdf.image.PageProcessingMode;
import com.desperadoboi.imagetopdf.model.CropRect;
import com.desperadoboi.imagetopdf.model.NormalizedPoint;
import com.desperadoboi.imagetopdf.model.PageEditSpec;
import com.desperadoboi.imagetopdf.model.PerspectiveQuad;

import java.util.Objects;

public final class IdCardPreviewRequest {
    private final String uriString;
    private final int rotationDegrees;
    private final PageEditSpec editSpec;
    private final int targetWidth;
    private final int targetHeight;
    private final String key;

    public IdCardPreviewRequest(
            IdCardSide side,
            IdCardImage image,
            int targetWidth,
            int targetHeight
    ) {
        Objects.requireNonNull(side, "side is required");
        Objects.requireNonNull(image, "image is required");
        if (targetWidth <= 0 || targetHeight <= 0) {
            throw new IllegalArgumentException("Preview target must be positive");
        }
        uriString = image.getUriString();
        rotationDegrees = image.getRotationDegrees();
        editSpec = new PageEditSpec(CropRect.FULL, image.getPerspectiveQuad());
        this.targetWidth = targetWidth;
        this.targetHeight = targetHeight;
        key = buildKey(side, image, targetWidth, targetHeight);
    }

    public Uri getImageUri() {
        return Uri.parse(uriString);
    }

    public int getRotationDegrees() {
        return rotationDegrees;
    }

    public PageEditSpec getEditSpec() {
        return editSpec;
    }

    public int getTargetWidth() {
        return targetWidth;
    }

    public int getTargetHeight() {
        return targetHeight;
    }

    public String getKey() {
        return key;
    }

    private static String buildKey(
            IdCardSide side,
            IdCardImage image,
            int targetWidth,
            int targetHeight
    ) {
        return "id-card-preview:"
                + side.name()
                + ":" + image.getCacheFileName()
                + ":" + image.getUriString()
                + ":" + image.getRotationDegrees()
                + ":" + quadToken(image.getPerspectiveQuad())
                + "@" + targetWidth + "x" + targetHeight
                + "@" + PageProcessingMode.FINAL.name();
    }

    private static String quadToken(PerspectiveQuad quad) {
        return pointToken(quad.getTopLeft())
                + ":" + pointToken(quad.getTopRight())
                + ":" + pointToken(quad.getBottomRight())
                + ":" + pointToken(quad.getBottomLeft());
    }

    private static String pointToken(NormalizedPoint point) {
        return Integer.toHexString(Float.floatToIntBits(point.getX()))
                + ","
                + Integer.toHexString(Float.floatToIntBits(point.getY()));
    }
}
