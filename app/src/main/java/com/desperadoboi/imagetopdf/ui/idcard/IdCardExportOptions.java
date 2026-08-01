package com.desperadoboi.imagetopdf.ui.idcard;

import java.util.Objects;

public final class IdCardExportOptions {
    public static final int MAX_WATERMARK_LENGTH = 40;

    private final IdCardExportPreset preset;
    private final boolean watermarkEnabled;
    private final String watermarkText;

    public IdCardExportOptions(
            IdCardExportPreset preset,
            boolean watermarkEnabled,
            String watermarkText
    ) {
        this.preset = Objects.requireNonNull(preset, "preset is required");
        this.watermarkEnabled = watermarkEnabled;
        String value = watermarkText == null ? "" : watermarkText;
        if (value.length() > MAX_WATERMARK_LENGTH) {
            throw new IllegalArgumentException("watermark is too long");
        }
        this.watermarkText = value;
    }

    public static IdCardExportOptions defaults(String defaultWatermarkText) {
        return new IdCardExportOptions(
                IdCardExportPreset.EASY_TO_READ,
                false,
                defaultWatermarkText
        );
    }

    public IdCardExportPreset getPreset() {
        return preset;
    }

    public boolean isWatermarkEnabled() {
        return watermarkEnabled;
    }

    public String getWatermarkText() {
        return watermarkText;
    }

    public boolean isValid() {
        return !watermarkEnabled || !watermarkText.trim().isEmpty();
    }

    public IdCardExportOptions withPreset(IdCardExportPreset newPreset) {
        if (preset == newPreset) {
            return this;
        }
        return new IdCardExportOptions(newPreset, watermarkEnabled, watermarkText);
    }

    public IdCardExportOptions withWatermarkEnabled(boolean enabled) {
        if (watermarkEnabled == enabled) {
            return this;
        }
        return new IdCardExportOptions(preset, enabled, watermarkText);
    }

    public IdCardExportOptions withWatermarkText(String text) {
        String value = text == null ? "" : text;
        if (watermarkText.equals(value)) {
            return this;
        }
        return new IdCardExportOptions(preset, watermarkEnabled, value);
    }
}
