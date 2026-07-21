package com.desperadoboi.imagetopdf.model;

import android.net.Uri;

import java.util.Objects;

public final class PdfExportDraft {
    private final String fileName;
    private final PdfQualityProfile quality;
    private final PageSizeMode pageSize;
    private final PdfOrientationMode orientation;
    private final MarginPreset margins;
    private final Uri outputUri;
    private final String outputLabel;

    public PdfExportDraft(
            String fileName,
            PdfQualityProfile quality,
            PageSizeMode pageSize,
            PdfOrientationMode orientation,
            MarginPreset margins,
            Uri outputUri,
            String outputLabel
    ) {
        this.fileName = fileName == null ? "" : fileName;
        this.quality = Objects.requireNonNull(quality, "quality is required");
        this.pageSize = Objects.requireNonNull(pageSize, "pageSize is required");
        this.orientation = Objects.requireNonNull(orientation, "orientation is required");
        this.margins = Objects.requireNonNull(margins, "margins is required");
        this.outputUri = outputUri;
        this.outputLabel = outputLabel == null ? "" : outputLabel.trim();
    }

    public static PdfExportDraft defaults(String suggestedFileName) {
        return new PdfExportDraft(
                suggestedFileName,
                PdfQualityProfile.BALANCED,
                PageSizeMode.A4,
                PdfOrientationMode.AUTO,
                MarginPreset.STANDARD,
                null,
                null
        );
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

    public String getOutputLabel() {
        return outputLabel;
    }

    public PdfExportDraft withFileName(String newFileName) {
        if (fileName.equals(newFileName)) {
            return this;
        }
        return new PdfExportDraft(
                newFileName,
                quality,
                pageSize,
                orientation,
                margins,
                null,
                null
        );
    }

    public PdfExportDraft withQuality(PdfQualityProfile newQuality) {
        return new PdfExportDraft(
                fileName,
                newQuality,
                pageSize,
                orientation,
                margins,
                outputUri,
                outputLabel
        );
    }

    public PdfExportDraft withPageSize(PageSizeMode newPageSize) {
        return new PdfExportDraft(
                fileName,
                quality,
                newPageSize,
                orientation,
                margins,
                outputUri,
                outputLabel
        );
    }

    public PdfExportDraft withOrientation(PdfOrientationMode newOrientation) {
        return new PdfExportDraft(
                fileName,
                quality,
                pageSize,
                newOrientation,
                margins,
                outputUri,
                outputLabel
        );
    }

    public PdfExportDraft withMargins(MarginPreset newMargins) {
        return new PdfExportDraft(
                fileName,
                quality,
                pageSize,
                orientation,
                newMargins,
                outputUri,
                outputLabel
        );
    }

    public PdfExportDraft withOutput(Uri newOutputUri, String newOutputLabel) {
        return new PdfExportDraft(
                fileName,
                quality,
                pageSize,
                orientation,
                margins,
                newOutputUri,
                newOutputLabel
        );
    }

    public PdfExportRequest toRequest() {
        return new PdfExportRequest(
                fileName,
                quality,
                pageSize,
                orientation,
                margins,
                outputUri
        );
    }
}
