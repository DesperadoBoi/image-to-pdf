package com.desperadoboi.imagetopdf.model;

import java.util.Objects;

public final class ImageImportRequest {
    private final ImageImportSource source;
    private final ImageImportMode mode;

    public ImageImportRequest(ImageImportSource source, ImageImportMode mode) {
        this.source = Objects.requireNonNull(source, "source is required");
        this.mode = Objects.requireNonNull(mode, "mode is required");
    }

    public ImageImportSource getSource() {
        return source;
    }

    public ImageImportMode getMode() {
        return mode;
    }
}
