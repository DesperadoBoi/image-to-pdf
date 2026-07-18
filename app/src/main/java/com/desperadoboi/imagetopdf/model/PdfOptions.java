package com.desperadoboi.imagetopdf.model;

import java.util.Objects;

public final class PdfOptions {
    private final PageSizeMode pageSizeMode;
    private final ImagePlacementMode imagePlacementMode;
    private final MarginPreset marginPreset;

    public PdfOptions(
            PageSizeMode pageSizeMode,
            ImagePlacementMode imagePlacementMode,
            MarginPreset marginPreset
    ) {
        this.pageSizeMode = Objects.requireNonNull(pageSizeMode, "pageSizeMode is required");
        this.imagePlacementMode = Objects.requireNonNull(
                imagePlacementMode,
                "imagePlacementMode is required"
        );
        this.marginPreset = Objects.requireNonNull(marginPreset, "marginPreset is required");
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
}
