package com.desperadoboi.imagetopdf.ui.smartscan;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ScanCameraStateTest {
    @Test
    public void gridPreferenceIsRestoredAndToggleable() {
        ScanCameraState restored = ScanCameraState.restored(true);

        assertTrue(restored.isGridEnabled());
        assertFalse(restored.toggleGrid().isGridEnabled());
    }

    @Test
    public void unavailableTorchCannotBeEnabled() {
        ScanCameraState state = ScanCameraState.restored(false);

        assertSame(state, state.toggleTorch());
        assertFalse(state.isTorchEnabled());
    }

    @Test
    public void torchIsResetOnPause() {
        ScanCameraState enabled = ScanCameraState.restored(false)
                .withFlashAvailable(true)
                .toggleTorch();

        assertTrue(enabled.isTorchEnabled());
        assertFalse(enabled.onPause().isTorchEnabled());
        assertTrue(enabled.onPause().isFlashAvailable());
    }
}
