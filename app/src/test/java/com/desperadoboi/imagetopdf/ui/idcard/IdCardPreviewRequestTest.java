package com.desperadoboi.imagetopdf.ui.idcard;

import com.desperadoboi.imagetopdf.model.NormalizedPoint;
import com.desperadoboi.imagetopdf.model.PerspectiveQuad;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public final class IdCardPreviewRequestTest {
    private static final int WIDTH = 600;
    private static final int HEIGHT = 400;

    @Test public void repeatedReadyBindKeepsTheSameKey() {
        IdCardImage image = image("front-a.jpg");

        String first = request(IdCardSide.FRONT, image).getKey();
        String second = request(IdCardSide.FRONT, image).getKey();

        assertTrue(first.equals(second));
    }

    @Test public void frontAndBackHaveDifferentKeysForTheSameImage() {
        IdCardImage image = image("same.jpg");

        assertNotEquals(
                request(IdCardSide.FRONT, image).getKey(),
                request(IdCardSide.BACK, image).getKey()
        );
    }

    @Test public void rotatePerspectiveAndReplaceChangeTheKey() {
        IdCardImage original = image("front-a.jpg");
        IdCardImage corrected = original.withPerspectiveQuad(new PerspectiveQuad(
                new NormalizedPoint(0.1f, 0.2f),
                new NormalizedPoint(0.9f, 0.2f),
                new NormalizedPoint(0.88f, 0.8f),
                new NormalizedPoint(0.12f, 0.8f)
        ));

        String originalKey = request(IdCardSide.FRONT, original).getKey();
        assertNotEquals(originalKey, request(IdCardSide.FRONT, original.rotateClockwise()).getKey());
        assertNotEquals(originalKey, request(IdCardSide.FRONT, corrected).getKey());
        assertNotEquals(originalKey, request(IdCardSide.FRONT, image("front-b.jpg")).getKey());
    }

    @Test public void restorationFromSavedValuesDoesNotChangeIdentity() {
        IdCardImage before = image("front-a.jpg").rotateClockwise();
        IdCardImage restored = new IdCardImage(
                before.getUriString(),
                before.getCacheFileName(),
                before.getRotationDegrees(),
                before.getPerspectiveQuad()
        );

        assertTrue(request(IdCardSide.FRONT, before).getKey().equals(
                request(IdCardSide.FRONT, restored).getKey()
        ));
    }

    @Test public void staleCallbackIsIgnoredAndCurrentCallbackIsAccepted() {
        IdCardPreviewRequestTracker tracker = new IdCardPreviewRequestTracker();
        String oldKey = request(IdCardSide.FRONT, image("front-a.jpg")).getKey();
        String newKey = request(IdCardSide.FRONT, image("front-b.jpg")).getKey();

        assertTrue(tracker.start(IdCardSide.FRONT, oldKey));
        assertTrue(tracker.start(IdCardSide.FRONT, newKey));
        assertFalse(tracker.isCurrent(IdCardSide.FRONT, oldKey));
        assertTrue(tracker.isCurrent(IdCardSide.FRONT, newKey));
    }

    @Test public void swapDoesNotMixFrontAndBackCallbacks() {
        IdCardPreviewRequestTracker tracker = new IdCardPreviewRequestTracker();
        IdCardImage front = image("front.jpg");
        IdCardImage back = image("back.jpg");
        String oldFront = request(IdCardSide.FRONT, front).getKey();
        String oldBack = request(IdCardSide.BACK, back).getKey();
        tracker.start(IdCardSide.FRONT, oldFront);
        tracker.start(IdCardSide.BACK, oldBack);

        String swappedFront = request(IdCardSide.FRONT, back).getKey();
        String swappedBack = request(IdCardSide.BACK, front).getKey();
        tracker.start(IdCardSide.FRONT, swappedFront);
        tracker.start(IdCardSide.BACK, swappedBack);

        assertFalse(tracker.isCurrent(IdCardSide.FRONT, oldFront));
        assertFalse(tracker.isCurrent(IdCardSide.BACK, oldBack));
        assertTrue(tracker.isCurrent(IdCardSide.FRONT, swappedFront));
        assertTrue(tracker.isCurrent(IdCardSide.BACK, swappedBack));
    }

    @Test public void repeatedStartIsDeduplicatedAndDeleteClearsState() {
        IdCardPreviewRequestTracker tracker = new IdCardPreviewRequestTracker();
        String key = request(IdCardSide.FRONT, image("front.jpg")).getKey();

        assertTrue(tracker.start(IdCardSide.FRONT, key));
        assertFalse(tracker.start(IdCardSide.FRONT, key));
        tracker.clear(IdCardSide.FRONT);
        assertFalse(tracker.isCurrent(IdCardSide.FRONT, key));
    }

    private static IdCardPreviewRequest request(IdCardSide side, IdCardImage image) {
        return new IdCardPreviewRequest(side, image, WIDTH, HEIGHT);
    }

    private static IdCardImage image(String fileName) {
        return new IdCardImage(
                "content://id-card-cache/" + fileName,
                fileName,
                0,
                IdCardImage.DEFAULT_CARD_QUAD
        );
    }
}
