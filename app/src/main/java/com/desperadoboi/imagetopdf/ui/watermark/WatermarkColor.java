package com.desperadoboi.imagetopdf.ui.watermark;

public enum WatermarkColor {
    GRAY(0xFF4B5563),
    RED(0xFFB4232C),
    BLUE(0xFF2457A7);

    private final int argb;

    WatermarkColor(int argb) {
        this.argb = argb;
    }

    public int getArgb() {
        return argb;
    }
}
