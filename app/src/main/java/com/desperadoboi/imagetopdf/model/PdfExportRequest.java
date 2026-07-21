package com.desperadoboi.imagetopdf.model;

import android.net.Uri;

import java.util.Locale;
import java.util.Objects;

public final class PdfExportRequest {
    private static final String PDF_EXTENSION = ".pdf";

    private final String fileName;
    private final PdfQualityProfile quality;
    private final PageSizeMode pageSize;
    private final PdfOrientationMode orientation;
    private final MarginPreset margins;
    private final Uri outputUri;

    public PdfExportRequest(
            String fileName,
            PdfQualityProfile quality,
            PageSizeMode pageSize,
            PdfOrientationMode orientation,
            MarginPreset margins,
            Uri outputUri
    ) {
        this.fileName = normalizeFileName(fileName);
        this.quality = Objects.requireNonNull(quality, "quality is required");
        this.pageSize = Objects.requireNonNull(pageSize, "pageSize is required");
        this.orientation = Objects.requireNonNull(orientation, "orientation is required");
        this.margins = Objects.requireNonNull(margins, "margins is required");
        this.outputUri = outputUri;
    }

    public String getFileName() {
        return fileName;
    }

    public PdfQualityProfile getQuality() {
        return quality;
    }

    public PageSizeMode getPageSize() {
        return pageSize;
    }

    public PdfOrientationMode getOrientation() {
        return orientation;
    }

    public MarginPreset getMargins() {
        return margins;
    }

    public Uri getOutputUri() {
        return outputUri;
    }

    public PdfOptions toPdfOptions() {
        return new PdfOptions(
                pageSize,
                ImagePlacementMode.FIT,
                margins,
                quality,
                orientation
        );
    }

    public PdfExportRequest withOutputUri(Uri uri) {
        return new PdfExportRequest(fileName, quality, pageSize, orientation, margins, uri);
    }

    public static String normalizeFileName(String value) {
        if (value == null) {
            throw new IllegalArgumentException("fileName is required");
        }
        String normalized = value.trim();
        while (normalized.toLowerCase(Locale.ROOT).endsWith(PDF_EXTENSION)) {
            normalized = normalized.substring(0, normalized.length() - PDF_EXTENSION.length())
                    .trim();
        }
        normalized = normalized.replaceAll("[\\p{Cntrl}\\\\/:*?\"<>|]", "_");
        normalized = normalized.replaceAll("^[.\\s]+|[.\\s]+$", "");
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("fileName must not be empty");
        }
        return normalized + PDF_EXTENSION;
    }
}
