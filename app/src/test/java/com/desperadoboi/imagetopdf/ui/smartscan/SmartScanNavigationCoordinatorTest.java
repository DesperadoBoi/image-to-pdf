package com.desperadoboi.imagetopdf.ui.smartscan;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SmartScanNavigationCoordinatorTest {
    private final SmartScanNavigationCoordinator coordinator =
            new SmartScanNavigationCoordinator();

    @Test
    public void permissionGrantedOpensCamera() {
        assertEquals(
                SmartScanNavigationCoordinator.Destination.CAMERA,
                coordinator.onPermissionGranted()
        );
    }

    @Test
    public void captureSuccessOpensReview() {
        assertEquals(
                SmartScanNavigationCoordinator.Destination.REVIEW,
                coordinator.onCaptureSuccess()
        );
    }

    @Test
    public void retakeAndAddPageReturnToCamera() {
        assertEquals(
                SmartScanNavigationCoordinator.Destination.CAMERA,
                coordinator.onRetake()
        );
        assertEquals(
                SmartScanNavigationCoordinator.Destination.CAMERA,
                coordinator.onPageAdded()
        );
    }

    @Test
    public void finishRequiresAtLeastOnePage() {
        assertEquals(
                SmartScanNavigationCoordinator.Destination.NONE,
                coordinator.onFinish(0)
        );
        assertEquals(
                SmartScanNavigationCoordinator.Destination.EDITOR,
                coordinator.onFinish(2)
        );
    }
}
