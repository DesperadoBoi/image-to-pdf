package com.desperadoboi.imagetopdf.image;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class BitmapSampleSizeCalculatorTest {
    @Test
    public void choosesLargestPowerOfTwoThatMeetsBothTargets() {
        assertEquals(4, BitmapSampleSizeCalculator.calculate(4000, 3000, 1000, 750));
        assertEquals(2, BitmapSampleSizeCalculator.calculate(4000, 3000, 1001, 750));
        assertEquals(2, BitmapSampleSizeCalculator.calculate(4000, 3000, 1000, 751));
    }

    @Test
    public void smallSourceIsNotUpscaled() {
        assertEquals(1, BitmapSampleSizeCalculator.calculate(400, 300, 1000, 1000));
    }

    @Test
    public void invalidDimensionsAreRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> BitmapSampleSizeCalculator.calculate(0, 300, 100, 100)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> BitmapSampleSizeCalculator.calculate(400, 300, -1, 100)
        );
    }
}
