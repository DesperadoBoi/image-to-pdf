package com.desperadoboi.imagetopdf.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;

public class NormalizedPointTest {
    @Test
    public void storesCoordinatesAtRangeBoundaries() {
        NormalizedPoint point = new NormalizedPoint(0f, 1f);

        assertEquals(0f, point.getX(), 0f);
        assertEquals(1f, point.getY(), 0f);
    }

    @Test
    public void rejectsInvalidCoordinates() {
        assertThrows(IllegalArgumentException.class, () -> new NormalizedPoint(-0.1f, 0f));
        assertThrows(IllegalArgumentException.class, () -> new NormalizedPoint(0f, 1.1f));
        assertThrows(IllegalArgumentException.class, () -> new NormalizedPoint(Float.NaN, 0f));
        assertThrows(
                IllegalArgumentException.class,
                () -> new NormalizedPoint(0f, Float.POSITIVE_INFINITY)
        );
    }

    @Test
    public void equalityUsesBothCoordinates() {
        assertEquals(new NormalizedPoint(0.25f, 0.75f), new NormalizedPoint(0.25f, 0.75f));
        assertEquals(
                new NormalizedPoint(0.25f, 0.75f).hashCode(),
                new NormalizedPoint(0.25f, 0.75f).hashCode()
        );
        assertNotEquals(new NormalizedPoint(0.25f, 0.75f), new NormalizedPoint(0.5f, 0.75f));
    }
}
