package com.desperadoboi.imagetopdf.ui.editor.geometry;

import com.desperadoboi.imagetopdf.model.NormalizedPoint;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class NormalizedCoordinateMapperTest {
    private static final float DELTA = 0.0001f;

    @Test
    public void mapsViewCoordinatesToNormalizedCoordinates() {
        NormalizedCoordinateMapper mapper = new NormalizedCoordinateMapper(20f, 40f, 220f, 440f);

        NormalizedPoint point = mapper.toNormalized(70f, 140f);

        assertEquals(0.25f, point.getX(), DELTA);
        assertEquals(0.25f, point.getY(), DELTA);
    }

    @Test
    public void mapsNormalizedCoordinatesToViewCoordinates() {
        NormalizedCoordinateMapper mapper = new NormalizedCoordinateMapper(20f, 40f, 220f, 440f);

        NormalizedCoordinateMapper.ViewPoint point = mapper.toView(
                new NormalizedPoint(0.75f, 0.5f)
        );

        assertEquals(170f, point.getX(), DELTA);
        assertEquals(240f, point.getY(), DELTA);
    }

    @Test
    public void viewCoordinatesAreClampedToImageBounds() {
        NormalizedCoordinateMapper mapper = new NormalizedCoordinateMapper(20f, 40f, 220f, 440f);

        assertEquals(new NormalizedPoint(0f, 1f), mapper.toNormalized(-100f, 900f));
    }

    @Test
    public void invalidMappingBoundsAreRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new NormalizedCoordinateMapper(0f, 0f, 0f, 100f)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new NormalizedCoordinateMapper(0f, 0f, Float.NaN, 100f)
        );
    }
}
