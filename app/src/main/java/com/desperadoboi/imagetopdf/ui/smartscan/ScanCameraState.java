package com.desperadoboi.imagetopdf.ui.smartscan;

import java.util.Objects;

public final class ScanCameraState {
    private final boolean gridEnabled;
    private final boolean torchEnabled;
    private final boolean flashAvailable;

    public ScanCameraState(
            boolean gridEnabled,
            boolean torchEnabled,
            boolean flashAvailable
    ) {
        this.gridEnabled = gridEnabled;
        this.flashAvailable = flashAvailable;
        this.torchEnabled = flashAvailable && torchEnabled;
    }

    public static ScanCameraState restored(boolean gridEnabled) {
        return new ScanCameraState(gridEnabled, false, false);
    }

    public boolean isGridEnabled() {
        return gridEnabled;
    }

    public boolean isTorchEnabled() {
        return torchEnabled;
    }

    public boolean isFlashAvailable() {
        return flashAvailable;
    }

    public ScanCameraState toggleGrid() {
        return new ScanCameraState(!gridEnabled, torchEnabled, flashAvailable);
    }

    public ScanCameraState withFlashAvailable(boolean available) {
        return new ScanCameraState(gridEnabled, torchEnabled, available);
    }

    public ScanCameraState toggleTorch() {
        if (!flashAvailable) {
            return this;
        }
        return new ScanCameraState(gridEnabled, !torchEnabled, true);
    }

    public ScanCameraState onPause() {
        if (!torchEnabled) {
            return this;
        }
        return new ScanCameraState(gridEnabled, false, flashAvailable);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ScanCameraState)) {
            return false;
        }
        ScanCameraState state = (ScanCameraState) other;
        return gridEnabled == state.gridEnabled
                && torchEnabled == state.torchEnabled
                && flashAvailable == state.flashAvailable;
    }

    @Override
    public int hashCode() {
        return Objects.hash(gridEnabled, torchEnabled, flashAvailable);
    }
}
