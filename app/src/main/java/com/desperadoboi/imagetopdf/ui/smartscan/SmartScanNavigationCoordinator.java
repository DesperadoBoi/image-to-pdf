package com.desperadoboi.imagetopdf.ui.smartscan;

public final class SmartScanNavigationCoordinator {
    public Destination onPermissionGranted() {
        return Destination.CAMERA;
    }

    public Destination onCaptureSuccess() {
        return Destination.REVIEW;
    }

    public Destination onRetake() {
        return Destination.CAMERA;
    }

    public Destination onPageAdded() {
        return Destination.CAMERA;
    }

    public Destination onFinish(int pageCount) {
        return pageCount > 0 ? Destination.EDITOR : Destination.NONE;
    }

    public enum Destination {
        NONE,
        CAMERA,
        REVIEW,
        EDITOR
    }
}
