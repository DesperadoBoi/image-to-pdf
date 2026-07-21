package com.desperadoboi.imagetopdf.ui.smartscan;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LevelStateResolverTest {
    @Test
    public void flatDeviceIsLevel() {
        assertEquals(
                LevelState.LEVEL,
                resolveDegrees(1f, LevelState.TILTED)
        );
    }

    @Test
    public void moderateTiltIsAlmostLevel() {
        assertEquals(
                LevelState.ALMOST_LEVEL,
                resolveDegrees(7f, LevelState.TILTED)
        );
    }

    @Test
    public void largeTiltIsTilted() {
        assertEquals(
                LevelState.TILTED,
                resolveDegrees(15f, LevelState.ALMOST_LEVEL)
        );
    }

    @Test
    public void levelHysteresisPreventsRepeatedHapticUntilDeviceLeavesLevel() {
        LevelState entered = resolveDegrees(2f, LevelState.TILTED);
        LevelState retained = resolveDegrees(4f, entered);
        LevelState left = resolveDegrees(7f, retained);
        LevelState reentered = resolveDegrees(2f, left);

        assertTrue(LevelStateResolver.shouldTriggerHaptic(LevelState.TILTED, entered));
        assertEquals(LevelState.LEVEL, retained);
        assertFalse(LevelStateResolver.shouldTriggerHaptic(entered, retained));
        assertEquals(LevelState.ALMOST_LEVEL, left);
        assertTrue(LevelStateResolver.shouldTriggerHaptic(left, reentered));
    }

    @Test
    public void missingGravityIsUnavailable() {
        assertEquals(
                LevelState.UNAVAILABLE,
                LevelStateResolver.resolve(0f, 0f, 0f, LevelState.TILTED)
        );
    }

    private LevelState resolveDegrees(float degrees, LevelState previous) {
        double radians = Math.toRadians(degrees);
        float x = (float) (9.81 * Math.sin(radians));
        float z = (float) (9.81 * Math.cos(radians));
        return LevelStateResolver.resolve(x, 0f, z, previous);
    }
}
