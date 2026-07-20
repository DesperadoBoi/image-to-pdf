package com.desperadoboi.imagetopdf.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class CropRectTest {
    @Test
    public void fullCoversNormalizedImage() {
        assertEquals(0f, CropRect.FULL.getLeft(), 0f);
        assertEquals(0f, CropRect.FULL.getTop(), 0f);
        assertEquals(1f, CropRect.FULL.getRight(), 0f);
        assertEquals(1f, CropRect.FULL.getBottom(), 0f);
        assertTrue(CropRect.FULL.isFull());
    }

    @Test
    public void reportsWidthHeightAndEquality() {
        CropRect rect = new CropRect(0.1f, 0.2f, 0.8f, 0.9f);
        CropRect equalRect = new CropRect(0.1f, 0.2f, 0.8f, 0.9f);

        assertEquals(0.7f, rect.getWidth(), 0.0001f);
        assertEquals(0.7f, rect.getHeight(), 0.0001f);
        assertFalse(rect.isFull());
        assertEquals(rect, equalRect);
        assertEquals(rect.hashCode(), equalRect.hashCode());
        assertNotEquals(rect, CropRect.FULL);
    }

    @Test
    public void rejectsInvalidBoundsAndOrder() {
        assertThrows(IllegalArgumentException.class, () -> new CropRect(-0.1f, 0f, 1f, 1f));
        assertThrows(IllegalArgumentException.class, () -> new CropRect(0f, 0f, 1.1f, 1f));
        assertThrows(IllegalArgumentException.class, () -> new CropRect(0.5f, 0f, 0.5f, 1f));
        assertThrows(IllegalArgumentException.class, () -> new CropRect(0f, 0.8f, 1f, 0.2f));
        assertThrows(IllegalArgumentException.class, () -> new CropRect(0f, 0f, Float.NaN, 1f));
    }

    @Test
    public void fourClockwiseRotationsRestoreCrop() {
        CropRect original = new CropRect(0.1f, 0.2f, 0.7f, 0.9f);
        CropRect rotated = original;
        for (int index = 0; index < 4; index++) {
            rotated = rotated.rotateClockwise();
        }

        assertEquals(original, rotated);
    }

    @Test
    public void rotationNinetyOneEightyAndTwoSeventyUsesRectifiedCoordinates() {
        CropRect original = new CropRect(0.1f, 0.2f, 0.7f, 0.9f);

        CropRect ninety = original.rotateClockwise();
        CropRect oneEighty = ninety.rotateClockwise();
        CropRect twoSeventy = oneEighty.rotateClockwise();

        assertEquals(new CropRect(0.1f, 0.1f, 0.8f, 0.7f), ninety);
        assertEquals(new CropRect(0.3f, 0.1f, 0.9f, 0.8f), oneEighty);
        assertEquals(new CropRect(0.2f, 0.3f, 0.9f, 0.9f), twoSeventy);
    }
}
