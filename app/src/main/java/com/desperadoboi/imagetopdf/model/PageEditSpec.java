package com.desperadoboi.imagetopdf.model;

import java.util.Objects;

public final class PageEditSpec {
    public static final PageEditSpec DEFAULT = new PageEditSpec(
            CropRect.FULL,
            PerspectiveQuad.FULL
    );

    private final CropRect cropRect;
    private final PerspectiveQuad perspectiveQuad;

    public PageEditSpec(CropRect cropRect, PerspectiveQuad perspectiveQuad) {
        this.cropRect = Objects.requireNonNull(cropRect, "cropRect is required");
        this.perspectiveQuad = Objects.requireNonNull(
                perspectiveQuad,
                "perspectiveQuad is required"
        );
    }

    public CropRect getCropRect() {
        return cropRect;
    }

    public PerspectiveQuad getPerspectiveQuad() {
        return perspectiveQuad;
    }

    public boolean isDefault() {
        return equals(DEFAULT);
    }

    public PageEditSpec withCropRect(CropRect newCropRect) {
        Objects.requireNonNull(newCropRect, "cropRect is required");
        if (cropRect.equals(newCropRect)) {
            return this;
        }
        return new PageEditSpec(newCropRect, perspectiveQuad);
    }

    public PageEditSpec withPerspectiveQuad(PerspectiveQuad newPerspectiveQuad) {
        Objects.requireNonNull(newPerspectiveQuad, "perspectiveQuad is required");
        if (perspectiveQuad.equals(newPerspectiveQuad)) {
            return this;
        }
        return new PageEditSpec(CropRect.FULL, newPerspectiveQuad);
    }

    public PageEditSpec reset() {
        return DEFAULT;
    }

    public PageEditSpec rotateClockwise() {
        return new PageEditSpec(
                cropRect.rotateClockwise(),
                perspectiveQuad.rotateClockwise()
        );
    }

    public PageEditSpec rotateCounterClockwise() {
        return new PageEditSpec(
                cropRect.rotateCounterClockwise(),
                perspectiveQuad.rotateCounterClockwise()
        );
    }

    public String toKey() {
        return perspectiveQuad.toKey() + "#" + cropRect.toKey();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PageEditSpec)) {
            return false;
        }
        PageEditSpec spec = (PageEditSpec) other;
        return cropRect.equals(spec.cropRect) && perspectiveQuad.equals(spec.perspectiveQuad);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cropRect, perspectiveQuad);
    }
}
