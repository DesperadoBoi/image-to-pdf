package com.desperadoboi.imagetopdf.model;

import java.util.Objects;

public final class PdfOptions {
    private final PageSizeMode pageSizeMode;
    private final ImagePlacementMode imagePlacementMode;
    private final MarginPreset marginPreset;
    private final PdfQualityProfile qualityProfile;
    private final PdfOrientationMode orientationMode;

    public PdfOptions(
            PageSizeMode pageSizeMode,
            ImagePlacementMode imagePlacementMode,
            MarginPreset marginPreset
    ) {
        this(
                pageSizeMode,
                imagePlacementMode,
                marginPreset,
                PdfQualityProfile.BALANCED,
                PdfOrientationMode.AUTO
        );
    }

    public PdfOptions(
            PageSizeMode pageSizeMode,
            ImagePlacementMode imagePlacementMode,
            MarginPreset marginPreset,
            PdfQualityProfile qualityProfile,
            PdfOrientationMode orientationMode
    ) {
        this.pageSizeMode = Objects.requireNonNull(pageSizeMode, "pageSizeMode is required");
        this.imagePlacementMode = Objects.requireNonNull(
                imagePlacementMode,
                "imagePlacementMode is required"
        );
        this.marginPreset = Objects.requireNonNull(marginPreset, "marginPreset is required");
        this.qualityProfile = Objects.requireNonNull(
                qualityProfile,
                "qualityProfile is required"
        );
        this.orientationMode = Objects.requireNonNull(
                orientationMode,
                "orientationMode is required"
        );
    }

    public static PdfOptions defaults() {
        return new PdfOptions(
                PageSizeMode.A4,
                ImagePlacementMode.FIT,
                MarginPreset.STANDARD
        );
    }

    public PageSizeMode getPageSizeMode() {
        return pageSizeMode;
    }

    public ImagePlacementMode getImagePlacementMode() {
        return imagePlacementMode;
    }

    public MarginPreset getMarginPreset() {
        return marginPreset;
    }

    public PdfQualityProfile getQualityProfile() {
        return qualityProfile;
    }

    public PdfOrientationMode getOrientationMode() {
        return orientationMode;
    }
}
