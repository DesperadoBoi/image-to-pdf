package com.desperadoboi.imagetopdf.ui.smartscan;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class ScanSessionReducer {
    private ScanSessionReducer() {
    }

    public static ScanSessionState captureStarted(ScanSessionState state, ScanPage pending) {
        requireState(state);
        Objects.requireNonNull(pending, "pending page is required");
        if (state.isCaptureInProgress() || state.getPendingCapture() != null || state.isFinished()) {
            return state;
        }
        return copy(state, state.getPages(), pending, true, false);
    }

    public static ScanSessionState captureSucceeded(ScanSessionState state, String captureId) {
        requireState(state);
        ScanPage pending = state.getPendingCapture();
        if (!state.isCaptureInProgress()
                || pending == null
                || !pending.getId().equals(captureId)) {
            return state;
        }
        return copy(state, state.getPages(), pending, false, false);
    }

    public static ScanSessionState captureFailed(ScanSessionState state, String captureId) {
        requireState(state);
        ScanPage pending = state.getPendingCapture();
        if (pending == null || !pending.getId().equals(captureId)) {
            return state;
        }
        return copy(state, state.getPages(), null, false, false);
    }

    public static ScanSessionState gallerySelected(ScanSessionState state, ScanPage pending) {
        requireState(state);
        Objects.requireNonNull(pending, "pending page is required");
        if (state.isCaptureInProgress() || state.getPendingCapture() != null || state.isFinished()) {
            return state;
        }
        return copy(state, state.getPages(), pending, false, false);
    }

    public static ScanSessionState updatePending(ScanSessionState state, ScanPage updatedPage) {
        requireState(state);
        Objects.requireNonNull(updatedPage, "updated page is required");
        ScanPage pending = state.getPendingCapture();
        if (pending == null || !pending.getId().equals(updatedPage.getId())) {
            return state;
        }
        return copy(
                state,
                state.getPages(),
                updatedPage,
                state.isCaptureInProgress(),
                state.isFinished()
        );
    }

    public static ScanSessionState retake(ScanSessionState state) {
        requireState(state);
        if (state.getPendingCapture() == null) {
            return state;
        }
        return copy(state, state.getPages(), null, false, false);
    }

    public static ScanSessionState addPendingPage(ScanSessionState state) {
        requireState(state);
        ScanPage pending = state.getCurrentReviewPage();
        if (pending == null || state.isFinished()) {
            return state;
        }
        for (ScanPage page : state.getPages()) {
            if (page.getId().equals(pending.getId())) {
                return copy(state, state.getPages(), null, false, false);
            }
        }
        ArrayList<ScanPage> pages = new ArrayList<>(state.getPages());
        pages.add(pending.withOrder(pages.size()));
        return copy(state, pages, null, false, false);
    }

    public static ScanSessionState deletePage(ScanSessionState state, String pageId) {
        requireState(state);
        ArrayList<ScanPage> pages = new ArrayList<>();
        boolean removed = false;
        for (ScanPage page : state.getPages()) {
            if (page.getId().equals(pageId)) {
                removed = true;
            } else {
                pages.add(page.withOrder(pages.size()));
            }
        }
        return removed
                ? copy(state, pages, state.getPendingCapture(), state.isCaptureInProgress(), false)
                : state;
    }

    public static ScanSessionState finish(ScanSessionState state) {
        requireState(state);
        if (!state.hasPages() || state.getPendingCapture() != null || state.isFinished()) {
            return state;
        }
        return copy(state, state.getPages(), null, false, true);
    }

    public static List<String> collectAppOwnedFileNames(ScanSessionState state) {
        requireState(state);
        Set<String> names = new LinkedHashSet<>();
        for (ScanPage page : state.getPages()) {
            addOwnedFileName(names, page);
        }
        addOwnedFileName(names, state.getPendingCapture());
        return new ArrayList<>(names);
    }

    static ScanSessionState copy(
            ScanSessionState state,
            List<ScanPage> pages,
            ScanPage pending,
            boolean captureInProgress,
            boolean finished
    ) {
        return new ScanSessionState(
                state.getSessionId(),
                pages,
                pending,
                captureInProgress,
                finished
        );
    }

    private static void addOwnedFileName(Set<String> names, ScanPage page) {
        if (page != null && page.isAppOwned()) {
            names.add(page.getCapturedFileName());
        }
    }

    private static void requireState(ScanSessionState state) {
        Objects.requireNonNull(state, "state is required");
    }
}
