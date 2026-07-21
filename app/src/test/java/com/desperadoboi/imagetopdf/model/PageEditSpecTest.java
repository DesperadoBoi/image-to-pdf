package com.desperadoboi.imagetopdf.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class PageEditSpecTest {
    @Test
    public void defaultUsesFullGeometry() {
        assertSame(CropRect.FULL, PageEditSpec.DEFAULT.getCropRect());
        assertSame(PerspectiveQuad.FULL, PageEditSpec.DEFAULT.getPerspectiveQuad());
        assertTrue(PageEditSpec.DEFAULT.isDefault());
    }

    @Test
    public void immutableCropUpdatePreservesQuad() {
        CropRect crop = new CropRect(0.1f, 0.2f, 0.8f, 0.9f);

        PageEditSpec updated = PageEditSpec.DEFAULT.withCropRect(crop);

        assertSame(crop, updated.getCropRect());
        assertSame(PerspectiveQuad.FULL, updated.getPerspectiveQuad());
        assertSame(CropRect.FULL, PageEditSpec.DEFAULT.getCropRect());
    }

    @Test
    public void perspectiveUpdateResetsExistingCrop() {
        PageEditSpec cropped = PageEditSpec.DEFAULT.withCropRect(
                new CropRect(0.1f, 0.2f, 0.8f, 0.9f)
        );
        PerspectiveQuad quad = new PerspectiveQuad(
                new NormalizedPoint(0.1f, 0.1f),
                new NormalizedPoint(0.9f, 0.1f),
                new NormalizedPoint(0.8f, 0.9f),
                new NormalizedPoint(0.2f, 0.9f)
        );

        PageEditSpec updated = cropped.withPerspectiveQuad(quad);

        assertSame(CropRect.FULL, updated.getCropRect());
        assertSame(quad, updated.getPerspectiveQuad());
    }

    @Test
    public void resetAndEqualityUseBothTransforms() {
        CropRect crop = new CropRect(0.1f, 0.1f, 0.9f, 0.9f);
        PageEditSpec first = PageEditSpec.DEFAULT.withCropRect(crop);
        PageEditSpec second = PageEditSpec.DEFAULT.withCropRect(crop);

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(first, PageEditSpec.DEFAULT);
        assertSame(PageEditSpec.DEFAULT, first.reset());
    }

    @Test
    public void fourRotationsRestoreSpec() {
        PageEditSpec original = new PageEditSpec(
                new CropRect(0.1f, 0.2f, 0.7f, 0.9f),
                new PerspectiveQuad(
                        new NormalizedPoint(0.2f, 0.1f),
                        new NormalizedPoint(0.8f, 0.15f),
                        new NormalizedPoint(0.95f, 0.9f),
                        new NormalizedPoint(0.05f, 0.85f)
                )
        );
        PageEditSpec rotated = original;
        for (int index = 0; index < 4; index++) {
            rotated = rotated.rotateClockwise();
        }

        assertEquals(original, rotated);
    }
}
