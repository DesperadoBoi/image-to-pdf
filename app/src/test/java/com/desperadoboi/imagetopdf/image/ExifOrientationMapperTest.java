package com.desperadoboi.imagetopdf.image;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

public class ExifOrientationMapperTest {
    @Test
    public void undefinedAndNormalUseIdentityTransform() {
        assertTransform(0, 0, false, false);
        assertTransform(1, 0, false, false);
    }

    @Test
    public void rotationsAreMappedCorrectly() {
        assertTransform(6, 90, false, true);
        assertTransform(3, 180, false, false);
        assertTransform(8, 270, false, true);
    }

    @Test
    public void mirroredOrientationsAreMappedCorrectly() {
        assertTransform(2, 0, true, false);
        assertTransform(4, 180, true, false);
        assertTransform(5, 90, true, true);
        assertTransform(7, 270, true, true);
    }

    @Test
    public void unknownOrientationFallsBackToNormal() {
        assertTransform(99, 0, false, false);
    }

    @Test
    public void transformRejectsUnsupportedRotation() {
        assertThrows(IllegalArgumentException.class, () -> new ImageTransform(45, false));
    }

    private void assertTransform(
            int orientation,
            int expectedRotation,
            boolean expectedFlipHorizontally,
            boolean expectedDimensionsSwapped
    ) {
        ImageTransform transform = ExifOrientationMapper.map(orientation);

        assertEquals(expectedRotation, transform.getRotationDegrees());
        if (expectedFlipHorizontally) {
            assertTrue(transform.shouldFlipHorizontally());
        } else {
            assertFalse(transform.shouldFlipHorizontally());
        }
        if (expectedDimensionsSwapped) {
            assertTrue(transform.swapsDimensions());
        } else {
            assertFalse(transform.swapsDimensions());
        }
    }
}
