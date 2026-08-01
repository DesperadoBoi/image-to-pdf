package com.desperadoboi.imagetopdf.ui.idcard;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class IdCardScanSessionTest {
    @Test public void emptySessionHasTwoExplicitEmptySides() {
        IdCardScanSession session = IdCardScanSession.empty("session");

        assertEquals(IdCardSide.FRONT, session.get(IdCardSide.FRONT).getSide());
        assertEquals(IdCardSide.BACK, session.get(IdCardSide.BACK).getSide());
        assertEquals(IdCardSideState.EMPTY, session.get(IdCardSide.FRONT).getState());
        assertEquals(IdCardSideState.EMPTY, session.get(IdCardSide.BACK).getState());
        assertFalse(session.canExport());
    }

    @Test public void frontReadyAllowsOneSideExport() {
        IdCardScanSession session = ready(
                IdCardScanSession.empty("session"),
                IdCardSide.FRONT,
                "front.jpg"
        );

        assertTrue(session.get(IdCardSide.FRONT).isReady());
        assertFalse(session.get(IdCardSide.BACK).isReady());
        assertTrue(session.canExport());
        assertEquals(1, session.getReadyCount());
    }

    @Test public void backReadyAllowsOneSideExport() {
        IdCardScanSession session = ready(
                IdCardScanSession.empty("session"),
                IdCardSide.BACK,
                "back.jpg"
        );

        assertFalse(session.get(IdCardSide.FRONT).isReady());
        assertTrue(session.get(IdCardSide.BACK).isReady());
        assertTrue(session.canExport());
    }

    @Test public void bothSidesReadyKeepFrontThenBackExportOrder() {
        IdCardScanSession session = bothReady();

        assertEquals(2, session.getReadyCount());
        assertEquals(
                Arrays.asList("front.jpg", "back.jpg"),
                session.collectCacheFileNames()
        );
        assertEquals("front.jpg", session.getReadyImages().get(0).getCacheFileName());
        assertEquals("back.jpg", session.getReadyImages().get(1).getCacheFileName());
    }

    @Test public void errorOnFrontDoesNotChangeReadyBack() {
        IdCardScanSession session = bothReady().fail(
                IdCardSide.FRONT,
                IdCardError.PROCESS_IMAGE
        );

        assertEquals(IdCardSideState.ERROR, session.get(IdCardSide.FRONT).getState());
        assertEquals(IdCardError.PROCESS_IMAGE, session.get(IdCardSide.FRONT).getError());
        assertTrue(session.get(IdCardSide.BACK).isReady());
        assertTrue(session.canExport());
        assertEquals(1, session.getReadyCount());
    }

    @Test public void deletingOneSideDoesNotDeleteTheOther() {
        IdCardScanSession session = bothReady().delete(IdCardSide.FRONT);

        assertEquals(IdCardSideState.EMPTY, session.get(IdCardSide.FRONT).getState());
        assertTrue(session.get(IdCardSide.BACK).isReady());
        assertEquals("back.jpg", session.get(IdCardSide.BACK).getCurrentImage().getCacheFileName());
    }

    @Test public void swapChangesImagesButNotSideIdentity() {
        IdCardScanSession swapped = bothReady().swap();

        assertEquals(IdCardSide.FRONT, swapped.get(IdCardSide.FRONT).getSide());
        assertEquals(IdCardSide.BACK, swapped.get(IdCardSide.BACK).getSide());
        assertEquals("back.jpg", swapped.get(IdCardSide.FRONT).getCurrentImage().getCacheFileName());
        assertEquals("front.jpg", swapped.get(IdCardSide.BACK).getCurrentImage().getCacheFileName());
    }

    @Test public void swapIsIgnoredUntilBothSidesAreReady() {
        IdCardScanSession frontOnly = ready(
                IdCardScanSession.empty("session"),
                IdCardSide.FRONT,
                "front.jpg"
        );

        assertSame(frontOnly, frontOnly.swap());
    }

    @Test public void rotationIsKeptOnTheTargetSideOnly() {
        IdCardScanSession rotated = bothReady().rotate(IdCardSide.FRONT);

        assertEquals(90, rotated.get(IdCardSide.FRONT).getCurrentImage().getRotationDegrees());
        assertEquals(0, rotated.get(IdCardSide.BACK).getCurrentImage().getRotationDegrees());
    }

    @Test public void missingCacheFileBecomesRecoverableSideError() {
        IdCardScanSession missing = bothReady().restoreMissingFile(IdCardSide.BACK);

        assertEquals(IdCardSideState.ERROR, missing.get(IdCardSide.BACK).getState());
        assertEquals(IdCardError.FILE_UNAVAILABLE, missing.get(IdCardSide.BACK).getError());
        assertTrue(missing.get(IdCardSide.FRONT).isReady());
    }

    @Test public void secondOperationOnSameBusySideIsBlocked() {
        IdCardScanSession capturing = IdCardScanSession.empty("session").begin(
                IdCardSide.FRONT,
                IdCardSideState.CAPTURING
        );

        assertSame(
                capturing,
                capturing.begin(IdCardSide.FRONT, IdCardSideState.PROCESSING)
        );
        assertTrue(capturing.hasBusySide());
    }

    @Test public void cancellingReplacementRestoresExistingReadyImage() {
        IdCardScanSession original = ready(
                IdCardScanSession.empty("session"),
                IdCardSide.FRONT,
                "front.jpg"
        );
        IdCardScanSession replacing = original
                .begin(IdCardSide.FRONT, IdCardSideState.PROCESSING)
                .withPending(IdCardSide.FRONT, image("replacement.jpg"));

        IdCardScanSession cancelled = replacing.cancelPending(IdCardSide.FRONT);

        assertTrue(cancelled.get(IdCardSide.FRONT).isReady());
        assertEquals(
                "front.jpg",
                cancelled.get(IdCardSide.FRONT).getCurrentImage().getCacheFileName()
        );
    }

    private static IdCardScanSession bothReady() {
        IdCardScanSession session = ready(
                IdCardScanSession.empty("session"),
                IdCardSide.FRONT,
                "front.jpg"
        );
        return ready(session, IdCardSide.BACK, "back.jpg");
    }

    private static IdCardScanSession ready(
            IdCardScanSession session,
            IdCardSide side,
            String fileName
    ) {
        return session
                .begin(side, IdCardSideState.PROCESSING)
                .withPending(side, image(fileName))
                .acceptPending(side);
    }

    private static IdCardImage image(String fileName) {
        return new IdCardImage(
                "content://test/" + fileName,
                fileName,
                0,
                IdCardImage.DEFAULT_CARD_QUAD
        );
    }
}
