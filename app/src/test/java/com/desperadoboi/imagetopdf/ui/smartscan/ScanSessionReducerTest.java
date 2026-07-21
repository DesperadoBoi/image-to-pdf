package com.desperadoboi.imagetopdf.ui.smartscan;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ScanSessionReducerTest {
    @Test
    public void captureStartAndSuccessOpenOnePendingReview() {
        ScanSessionState initial = ScanSessionState.empty("session");
        ScanPage pending = cameraPage("capture-1", "capture_1.jpg", 0);

        ScanSessionState capturing = ScanSessionReducer.captureStarted(initial, pending);
        ScanSessionState succeeded = ScanSessionReducer.captureSucceeded(
                capturing,
                pending.getId()
        );

        assertTrue(capturing.isCaptureInProgress());
        assertNull(capturing.getCurrentReviewPage());
        assertFalse(succeeded.isCaptureInProgress());
        assertSame(pending, succeeded.getCurrentReviewPage());
    }

    @Test
    public void duplicateCaptureSuccessIsIgnored() {
        ScanPage pending = cameraPage("capture-1", "capture_1.jpg", 0);
        ScanSessionState capturing = ScanSessionReducer.captureStarted(
                ScanSessionState.empty("session"),
                pending
        );
        ScanSessionState succeeded = ScanSessionReducer.captureSucceeded(
                capturing,
                pending.getId()
        );

        assertSame(
                succeeded,
                ScanSessionReducer.captureSucceeded(succeeded, pending.getId())
        );
    }

    @Test
    public void retakeRemovesPendingWithoutAddingPage() {
        ScanPage pending = cameraPage("capture-1", "capture_1.jpg", 0);
        ScanSessionState state = successfulCapture(
                ScanSessionState.empty("session"),
                pending
        );

        ScanSessionState retaken = ScanSessionReducer.retake(state);

        assertNull(retaken.getPendingCapture());
        assertEquals(0, retaken.getPageCount());
    }

    @Test
    public void addDeleteAndFinishPreserveCaptureOrder() {
        ScanSessionState state = ScanSessionState.empty("session");
        state = addCapture(state, cameraPage("first", "capture_first.jpg", 8));
        state = addCapture(state, cameraPage("second", "capture_second.jpg", 3));

        assertEquals(Arrays.asList("first", "second"), ids(state.getPages()));
        assertEquals(0, state.getPages().get(0).getOrder());
        assertEquals(1, state.getPages().get(1).getOrder());

        ScanSessionState deleted = ScanSessionReducer.deletePage(state, "first");
        ScanSessionState finished = ScanSessionReducer.finish(deleted);

        assertEquals(Arrays.asList("second"), ids(finished.getPages()));
        assertEquals(0, finished.getPages().get(0).getOrder());
        assertTrue(finished.isFinished());
    }

    @Test
    public void finishWithPendingOrNoPagesIsIgnored() {
        ScanSessionState empty = ScanSessionState.empty("session");
        ScanPage pending = cameraPage("pending", "capture_pending.jpg", 0);
        ScanSessionState reviewing = successfulCapture(empty, pending);

        assertSame(empty, ScanSessionReducer.finish(empty));
        assertSame(reviewing, ScanSessionReducer.finish(reviewing));
    }

    @Test
    public void cancelCollectsOnlyAppOwnedFilesAndGalleryStaysExternal() {
        ScanSessionState state = ScanSessionState.empty("session");
        state = addCapture(state, cameraPage("camera", "capture_camera.jpg", 0));
        ScanPage gallery = new ScanPage(
                "gallery",
                "content://gallery/page",
                false,
                null,
                0,
                ScanPage.DEFAULT_DOCUMENT_QUAD,
                false,
                2L,
                1
        );
        state = ScanSessionReducer.gallerySelected(state, gallery);
        state = ScanSessionReducer.addPendingPage(state);

        assertFalse(state.getPages().get(1).isAppOwned());
        assertEquals(
                Arrays.asList("capture_camera.jpg"),
                ScanSessionReducer.collectAppOwnedFileNames(state)
        );
    }

    private ScanSessionState addCapture(ScanSessionState state, ScanPage page) {
        return ScanSessionReducer.addPendingPage(successfulCapture(state, page));
    }

    private ScanSessionState successfulCapture(ScanSessionState state, ScanPage page) {
        return ScanSessionReducer.captureSucceeded(
                ScanSessionReducer.captureStarted(state, page),
                page.getId()
        );
    }

    private ScanPage cameraPage(String id, String fileName, int order) {
        return new ScanPage(
                id,
                "content://camera/" + id,
                true,
                fileName,
                0,
                ScanPage.DEFAULT_DOCUMENT_QUAD,
                false,
                order + 1L,
                order
        );
    }

    private List<String> ids(List<ScanPage> pages) {
        java.util.ArrayList<String> ids = new java.util.ArrayList<>();
        for (ScanPage page : pages) {
            ids.add(page.getId());
        }
        return ids;
    }
}
