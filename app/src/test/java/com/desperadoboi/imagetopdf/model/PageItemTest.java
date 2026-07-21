package com.desperadoboi.imagetopdf.model;

import android.net.Uri;
import android.net.FakeUri;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class PageItemTest {
    private static final Uri TEST_URI = FakeUri.create("content://test/image");

    @Test
    public void initialManualRotationIsZero() {
        PageItem pageItem = new PageItem(TEST_URI);

        assertEquals(0, pageItem.getManualRotationDegrees());
        assertSame(PageEditSpec.DEFAULT, pageItem.getEditSpec());
    }

    @Test
    public void galleryPageUsesGallerySourceByDefault() {
        PageItem pageItem = new PageItem(TEST_URI);

        assertEquals(PageSource.GALLERY, pageItem.getSource());
        assertFalse(pageItem.isAppOwnedCapture());
    }

    @Test
    public void cameraPageUsesCameraSource() {
        PageItem pageItem = PageItem.camera(TEST_URI, "capture_test.jpg");

        assertEquals(PageSource.CAMERA, pageItem.getSource());
        assertTrue(pageItem.isAppOwnedCapture());
        assertEquals("capture_test.jpg", pageItem.getCapturedFileName());
    }

    @Test
    public void filesPageUsesFilesSource() {
        PageItem pageItem = PageItem.files(TEST_URI);

        assertEquals(PageSource.FILES, pageItem.getSource());
        assertFalse(pageItem.isAppOwnedCapture());
    }

    @Test
    public void clockwiseRotationCyclesThroughSupportedAngles() {
        assertEquals(90, PageItem.rotateClockwise(0));
        assertEquals(180, PageItem.rotateClockwise(90));
        assertEquals(270, PageItem.rotateClockwise(180));
        assertEquals(0, PageItem.rotateClockwise(270));
    }

    @Test
    public void rotateClockwiseReturnsNewPageItem() {
        PageItem pageItem = new PageItem(TEST_URI, 270);
        PageItem rotatedPageItem = pageItem.rotateClockwise();

        assertSame(TEST_URI, rotatedPageItem.getImageUri());
        assertEquals(pageItem.getId(), rotatedPageItem.getId());
        assertEquals(0, rotatedPageItem.getManualRotationDegrees());
    }

    @Test
    public void sourceIsPreservedAfterRotation() {
        PageItem pageItem = PageItem.camera(TEST_URI, "capture_test.jpg");

        PageItem rotatedPageItem = pageItem.rotateClockwise();

        assertEquals(PageSource.CAMERA, rotatedPageItem.getSource());
        assertEquals("capture_test.jpg", rotatedPageItem.getCapturedFileName());
    }

    @Test
    public void filesSourceIsPreservedAfterRotationAndCrop() {
        PageItem pageItem = PageItem.files(TEST_URI);

        PageItem editedPageItem = pageItem
                .rotateClockwise()
                .withCropRect(new CropRect(0.1f, 0.1f, 0.9f, 0.9f));

        assertEquals(PageSource.FILES, editedPageItem.getSource());
        assertEquals(pageItem.getId(), editedPageItem.getId());
    }

    @Test
    public void stableIdDoesNotChangeAfterRotation() {
        PageItem pageItem = new PageItem(TEST_URI);

        PageItem rotatedPageItem = pageItem.rotateClockwise();

        assertEquals(pageItem.getId(), rotatedPageItem.getId());
    }

    @Test
    public void sameUriPagesHaveDifferentStableIds() {
        PageItem firstPage = new PageItem(TEST_URI);
        PageItem secondPage = new PageItem(TEST_URI);

        assertNotEquals(firstPage.getId(), secondPage.getId());
    }

    @Test
    public void cameraPagesHaveDifferentStableIds() {
        PageItem firstPage = PageItem.camera(TEST_URI, "capture_first.jpg");
        PageItem secondPage = PageItem.camera(TEST_URI, "capture_second.jpg");

        assertNotEquals(firstPage.getId(), secondPage.getId());
    }

    @Test
    public void invalidRotationIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new PageItem(TEST_URI, 45));
        assertThrows(IllegalArgumentException.class, () -> PageItem.rotateClockwise(45));
    }

    @Test
    public void rotationPreservesEditsAndTransformsTheirCoordinates() {
        PageItem pageItem = new PageItem(TEST_URI).withCropRect(
                new CropRect(0.1f, 0.2f, 0.7f, 0.9f)
        );

        PageItem rotated = pageItem.rotateClockwise();

        assertEquals(pageItem.getId(), rotated.getId());
        assertSame(TEST_URI, rotated.getImageUri());
        assertEquals(new CropRect(0.1f, 0.1f, 0.8f, 0.7f), rotated.getEditSpec().getCropRect());
    }

    @Test
    public void thumbnailKeyChangesForRotationCropAndPerspective() {
        PageItem page = new PageItem(TEST_URI);
        PageItem rotated = page.rotateClockwise();
        PageItem cropped = page.withCropRect(new CropRect(0.1f, 0.1f, 0.9f, 0.9f));
        PageItem perspective = page.withPerspectiveQuad(new PerspectiveQuad(
                new NormalizedPoint(0.1f, 0.1f),
                new NormalizedPoint(0.9f, 0.1f),
                new NormalizedPoint(0.8f, 0.9f),
                new NormalizedPoint(0.2f, 0.9f)
        ));

        assertNotEquals(page.getThumbnailKey(), rotated.getThumbnailKey());
        assertNotEquals(page.getThumbnailKey(), cropped.getThumbnailKey());
        assertNotEquals(page.getThumbnailKey(), perspective.getThumbnailKey());
        assertEquals(page.getId(), perspective.getId());
    }

    @Test
    public void uriIsStoredWithoutChanges() {
        PageItem pageItem = new PageItem(TEST_URI, 90);

        assertSame(TEST_URI, pageItem.getImageUri());
    }

    @Test
    public void swapsDimensionsOnlyForQuarterTurns() {
        assertFalse(new PageItem(TEST_URI, 0).swapsDimensions());
        assertTrue(new PageItem(TEST_URI, 90).swapsDimensions());
        assertFalse(new PageItem(TEST_URI, 180).swapsDimensions());
        assertTrue(new PageItem(TEST_URI, 270).swapsDimensions());
    }
}
