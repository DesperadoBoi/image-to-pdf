package com.desperadoboi.imagetopdf.model;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GalleryAccessStateTest {
    @Test
    public void fullAccessReadsAndRepresentsWholeGallery() {
        assertTrue(GalleryAccessState.FULL.canReadMediaStore());
        assertTrue(GalleryAccessState.FULL.representsWholeGallery());
        assertTrue(GalleryAccessState.FULL.canUseFallbackSelection());
        assertFalse(GalleryAccessState.FULL.canRequestOrChangeAccess());
    }

    @Test
    public void partialAccessReadsButDoesNotClaimWholeGallery() {
        assertTrue(GalleryAccessState.PARTIAL.canReadMediaStore());
        assertFalse(GalleryAccessState.PARTIAL.representsWholeGallery());
        assertTrue(GalleryAccessState.PARTIAL.canRequestOrChangeAccess());
    }

    @Test
    public void deniedAccessKeepsSafeFallbacksAvailable() {
        assertFalse(GalleryAccessState.DENIED.canReadMediaStore());
        assertTrue(GalleryAccessState.DENIED.canUseFallbackSelection());
        assertTrue(GalleryAccessState.DENIED.canRequestOrChangeAccess());
    }

    @Test
    public void notRequestedAccessCanRequestAndUseFallbacks() {
        assertFalse(GalleryAccessState.NOT_REQUESTED.canReadMediaStore());
        assertTrue(GalleryAccessState.NOT_REQUESTED.canUseFallbackSelection());
        assertTrue(GalleryAccessState.NOT_REQUESTED.canRequestOrChangeAccess());
    }
}
