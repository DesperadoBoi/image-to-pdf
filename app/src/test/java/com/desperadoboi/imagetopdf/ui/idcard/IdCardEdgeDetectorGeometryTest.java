package com.desperadoboi.imagetopdf.ui.idcard;

import com.desperadoboi.imagetopdf.model.PerspectiveQuad;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public final class IdCardEdgeDetectorGeometryTest {
    @Test public void portraitFallbackMatchesIdOneAspectInSourcePixels() {
        assertSourceAspect(3000, 4000);
    }

    @Test public void landscapeFallbackMatchesIdOneAspectInSourcePixels() {
        assertSourceAspect(4000, 3000);
    }

    @Test public void invalidBoundsUseSafeDefaultQuad() {
        assertSame(IdCardImage.DEFAULT_CARD_QUAD, IdCardEdgeDetector.defaultQuadFor(0, 100));
    }

    private static void assertSourceAspect(int imageWidth, int imageHeight) {
        PerspectiveQuad quad = IdCardEdgeDetector.defaultQuadFor(imageWidth, imageHeight);
        float normalizedWidth = quad.getTopRight().getX() - quad.getTopLeft().getX();
        float normalizedHeight = quad.getBottomLeft().getY() - quad.getTopLeft().getY();
        float sourceAspect = normalizedWidth * imageWidth / (normalizedHeight * imageHeight);

        assertEquals(85.60f / 53.98f, sourceAspect, 0.002f);
    }
}
