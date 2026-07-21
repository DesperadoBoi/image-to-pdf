package com.desperadoboi.imagetopdf.ui.smartscan;

import android.net.Uri;
import android.os.Bundle;

import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;

import com.desperadoboi.imagetopdf.model.PageItem;
import com.desperadoboi.imagetopdf.model.PerspectiveQuad;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

public final class ScanSessionViewModel extends ViewModel {
    private static final String KEY_SESSION_ID = "scan.session_id";
    private static final String KEY_PAGES = "scan.pages";
    private static final String KEY_PENDING = "scan.pending";
    private static final String KEY_CAPTURE_IN_PROGRESS = "scan.capture_in_progress";
    private static final String KEY_FINISHED = "scan.finished";
    private static final String KEY_NEXT_SEQUENCE = "scan.next_sequence";
    private static final String KEY_GRID_ENABLED = "scan.grid_enabled";
    private static final String KEY_PERMISSION_REQUESTED = "scan.permission_requested";
    private static final String KEY_PERMISSION_DENIAL_COUNT = "scan.permission_denial_count";

    private static final String PAGE_ID = "id";
    private static final String PAGE_URI = "uri";
    private static final String PAGE_APP_OWNED = "app_owned";
    private static final String PAGE_FILE_NAME = "file_name";
    private static final String PAGE_ROTATION = "rotation";
    private static final String PAGE_QUAD = "quad";
    private static final String PAGE_ORIGINAL = "original";
    private static final String PAGE_CREATED_AT = "created_at";
    private static final String PAGE_ORDER = "order";

    private final SavedStateHandle savedStateHandle;
    private final ArrayList<WeakReference<Observer>> observers = new ArrayList<>();

    private ScanSessionState state;
    private ScanCameraState cameraState;
    private long nextSequence;
    private boolean permissionRequested;
    private int permissionDenialCount;

    public ScanSessionViewModel(SavedStateHandle savedStateHandle) {
        this.savedStateHandle = savedStateHandle;
        state = restoreState();
        nextSequence = valueOrDefault(savedStateHandle.get(KEY_NEXT_SEQUENCE), 0L);
        boolean gridEnabled = valueOrDefault(
                savedStateHandle.get(KEY_GRID_ENABLED),
                false
        );
        cameraState = ScanCameraState.restored(gridEnabled);
        permissionRequested = valueOrDefault(
                savedStateHandle.get(KEY_PERMISSION_REQUESTED),
                false
        );
        Integer storedDenialCount = savedStateHandle.get(KEY_PERMISSION_DENIAL_COUNT);
        permissionDenialCount = valueOrDefault(storedDenialCount, 0);
    }

    public ScanSessionState getState() {
        return state;
    }

    public ScanCameraState getCameraState() {
        return cameraState;
    }

    public void addObserver(Observer observer) {
        if (observer == null) {
            return;
        }
        observers.add(new WeakReference<>(observer));
        observer.onScanSessionChanged(state, cameraState);
    }

    public void removeObserver(Observer observer) {
        Iterator<WeakReference<Observer>> iterator = observers.iterator();
        while (iterator.hasNext()) {
            Observer existing = iterator.next().get();
            if (existing == null || existing == observer) {
                iterator.remove();
            }
        }
    }

    public List<String> startNewSession() {
        List<String> staleFiles = ScanSessionReducer.collectAppOwnedFileNames(state);
        state = ScanSessionState.empty(newSessionId());
        nextSequence = 0L;
        cameraState = cameraState.onPause().withFlashAvailable(false);
        persistAndNotify();
        return staleFiles;
    }

    public ScanPage startCapture(Uri uri, String fileName, long createdAt) {
        if (state.isCaptureInProgress() || state.getPendingCapture() != null) {
            return null;
        }
        ScanPage page = new ScanPage(
                nextPageId(),
                uri.toString(),
                true,
                fileName,
                0,
                ScanPage.DEFAULT_DOCUMENT_QUAD,
                false,
                createdAt,
                state.getPageCount()
        );
        ScanSessionState updated = ScanSessionReducer.captureStarted(state, page);
        if (updated == state) {
            return null;
        }
        state = updated;
        persistAndNotify();
        return page;
    }

    public boolean captureSucceeded(String captureId) {
        ScanSessionState updated = ScanSessionReducer.captureSucceeded(state, captureId);
        if (updated == state) {
            return false;
        }
        state = updated;
        persistAndNotify();
        return true;
    }

    public ScanPage captureFailed(String captureId) {
        ScanPage pending = state.getPendingCapture();
        ScanSessionState updated = ScanSessionReducer.captureFailed(state, captureId);
        if (updated == state) {
            return null;
        }
        state = updated;
        persistAndNotify();
        return pending;
    }

    public ScanPage recoverInterruptedCapture() {
        if (!state.isCaptureInProgress() || state.getPendingCapture() == null) {
            return null;
        }
        return captureFailed(state.getPendingCapture().getId());
    }

    public ScanPage selectGalleryImage(Uri uri, long createdAt) {
        if (uri == null || state.isCaptureInProgress() || state.getPendingCapture() != null) {
            return null;
        }
        ScanPage page = new ScanPage(
                nextPageId(),
                uri.toString(),
                false,
                null,
                0,
                ScanPage.DEFAULT_DOCUMENT_QUAD,
                false,
                createdAt,
                state.getPageCount()
        );
        ScanSessionState updated = ScanSessionReducer.gallerySelected(state, page);
        if (updated == state) {
            return null;
        }
        state = updated;
        persistAndNotify();
        return page;
    }

    public boolean updatePendingPage(ScanPage page) {
        ScanSessionState updated = ScanSessionReducer.updatePending(state, page);
        if (updated == state) {
            return false;
        }
        state = updated;
        persistAndNotify();
        return true;
    }

    public ScanPage retakePendingPage() {
        ScanPage pending = state.getPendingCapture();
        ScanSessionState updated = ScanSessionReducer.retake(state);
        if (updated == state) {
            return null;
        }
        state = updated;
        persistAndNotify();
        return pending;
    }

    public boolean addPendingPage() {
        ScanSessionState updated = ScanSessionReducer.addPendingPage(state);
        if (updated == state) {
            return false;
        }
        state = updated;
        persistAndNotify();
        return true;
    }

    public List<PageItem> finishPages() {
        ScanSessionState updated = ScanSessionReducer.finish(state);
        if (updated == state) {
            return java.util.Collections.emptyList();
        }
        state = updated;
        ArrayList<PageItem> pages = new ArrayList<>(state.getPageCount());
        for (ScanPage page : state.getPages()) {
            pages.add(page.toPageItem());
        }
        persistAndNotify();
        return pages;
    }

    public void completeTransfer() {
        state = ScanSessionState.empty(newSessionId());
        nextSequence = 0L;
        cameraState = cameraState.onPause().withFlashAvailable(false);
        persistAndNotify();
    }

    public List<String> cancelSession() {
        List<String> files = ScanSessionReducer.collectAppOwnedFileNames(state);
        state = ScanSessionState.empty(newSessionId());
        nextSequence = 0L;
        cameraState = cameraState.onPause().withFlashAvailable(false);
        persistAndNotify();
        return files;
    }

    public int removeMissingAppOwnedPages(Predicate<String> fileExists) {
        ArrayList<ScanPage> kept = new ArrayList<>();
        int removed = 0;
        for (ScanPage page : state.getPages()) {
            if (page.isAppOwned() && !fileExists.test(page.getCapturedFileName())) {
                removed++;
            } else {
                kept.add(page.withOrder(kept.size()));
            }
        }
        ScanPage pending = state.getPendingCapture();
        if (pending != null
                && pending.isAppOwned()
                && !fileExists.test(pending.getCapturedFileName())) {
            pending = null;
            removed++;
        }
        if (removed > 0) {
            state = new ScanSessionState(
                    state.getSessionId(),
                    kept,
                    pending,
                    pending != null && state.isCaptureInProgress(),
                    false
            );
            persistAndNotify();
        }
        return removed;
    }

    public boolean wasPermissionRequested() {
        return permissionRequested;
    }

    public void markPermissionRequested() {
        if (permissionRequested) {
            return;
        }
        permissionRequested = true;
        savedStateHandle.set(KEY_PERMISSION_REQUESTED, true);
    }

    public void recordPermissionResult(boolean granted) {
        if (granted) {
            permissionDenialCount = 0;
        } else {
            permissionDenialCount++;
        }
        savedStateHandle.set(KEY_PERMISSION_DENIAL_COUNT, permissionDenialCount);
    }

    public int getPermissionDenialCount() {
        return permissionDenialCount;
    }

    public void toggleGrid() {
        cameraState = cameraState.toggleGrid();
        savedStateHandle.set(KEY_GRID_ENABLED, cameraState.isGridEnabled());
        notifyObservers();
    }

    public void setFlashAvailable(boolean available) {
        cameraState = cameraState.withFlashAvailable(available);
        notifyObservers();
    }

    public boolean toggleTorch() {
        ScanCameraState updated = cameraState.toggleTorch();
        if (updated == cameraState) {
            return false;
        }
        cameraState = updated;
        notifyObservers();
        return true;
    }

    public void resetTorchOnPause() {
        ScanCameraState updated = cameraState.onPause();
        if (updated == cameraState) {
            return;
        }
        cameraState = updated;
        notifyObservers();
    }

    private ScanSessionState restoreState() {
        String sessionId = savedStateHandle.get(KEY_SESSION_ID);
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return ScanSessionState.empty(newSessionId());
        }
        ArrayList<ScanPage> pages = new ArrayList<>();
        ArrayList<Bundle> pageRecords = savedStateHandle.get(KEY_PAGES);
        if (pageRecords != null) {
            for (Bundle record : pageRecords) {
                ScanPage page = restorePage(record);
                if (page != null) {
                    pages.add(page.withOrder(pages.size()));
                }
            }
        }
        ScanPage pending = restorePage(savedStateHandle.get(KEY_PENDING));
        boolean captureInProgress = valueOrDefault(
                savedStateHandle.get(KEY_CAPTURE_IN_PROGRESS),
                false
        );
        boolean finished = valueOrDefault(savedStateHandle.get(KEY_FINISHED), false);
        return new ScanSessionState(
                sessionId,
                pages,
                pending,
                captureInProgress && pending != null,
                finished
        );
    }

    private void persistAndNotify() {
        savedStateHandle.set(KEY_SESSION_ID, state.getSessionId());
        ArrayList<Bundle> pageRecords = new ArrayList<>();
        for (ScanPage page : state.getPages()) {
            pageRecords.add(savePage(page));
        }
        savedStateHandle.set(KEY_PAGES, pageRecords);
        savedStateHandle.set(
                KEY_PENDING,
                state.getPendingCapture() == null ? null : savePage(state.getPendingCapture())
        );
        savedStateHandle.set(KEY_CAPTURE_IN_PROGRESS, state.isCaptureInProgress());
        savedStateHandle.set(KEY_FINISHED, state.isFinished());
        savedStateHandle.set(KEY_NEXT_SEQUENCE, nextSequence);
        savedStateHandle.set(KEY_GRID_ENABLED, cameraState.isGridEnabled());
        notifyObservers();
    }

    private Bundle savePage(ScanPage page) {
        Bundle record = new Bundle();
        record.putString(PAGE_ID, page.getId());
        record.putString(PAGE_URI, page.getSourceUriString());
        record.putBoolean(PAGE_APP_OWNED, page.isAppOwned());
        record.putString(PAGE_FILE_NAME, page.getCapturedFileName());
        record.putInt(PAGE_ROTATION, page.getRotationDegrees());
        record.putFloatArray(PAGE_QUAD, saveQuad(page.getPerspectiveQuad()));
        record.putBoolean(PAGE_ORIGINAL, page.isOriginal());
        record.putLong(PAGE_CREATED_AT, page.getCreatedAt());
        record.putInt(PAGE_ORDER, page.getOrder());
        return record;
    }

    private ScanPage restorePage(Bundle record) {
        if (record == null) {
            return null;
        }
        try {
            String id = record.getString(PAGE_ID);
            String uri = record.getString(PAGE_URI);
            boolean appOwned = record.getBoolean(PAGE_APP_OWNED);
            String fileName = record.getString(PAGE_FILE_NAME);
            int rotation = record.getInt(PAGE_ROTATION);
            PerspectiveQuad quad = restoreQuad(record.getFloatArray(PAGE_QUAD));
            return new ScanPage(
                    id,
                    uri,
                    appOwned,
                    fileName,
                    rotation,
                    quad,
                    record.getBoolean(PAGE_ORIGINAL),
                    record.getLong(PAGE_CREATED_AT),
                    record.getInt(PAGE_ORDER)
            );
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private float[] saveQuad(PerspectiveQuad quad) {
        return new float[]{
                quad.getTopLeft().getX(), quad.getTopLeft().getY(),
                quad.getTopRight().getX(), quad.getTopRight().getY(),
                quad.getBottomRight().getX(), quad.getBottomRight().getY(),
                quad.getBottomLeft().getX(), quad.getBottomLeft().getY()
        };
    }

    private PerspectiveQuad restoreQuad(float[] values) {
        if (values == null || values.length != 8) {
            return ScanPage.DEFAULT_DOCUMENT_QUAD;
        }
        return new PerspectiveQuad(
                new com.desperadoboi.imagetopdf.model.NormalizedPoint(values[0], values[1]),
                new com.desperadoboi.imagetopdf.model.NormalizedPoint(values[2], values[3]),
                new com.desperadoboi.imagetopdf.model.NormalizedPoint(values[4], values[5]),
                new com.desperadoboi.imagetopdf.model.NormalizedPoint(values[6], values[7])
        );
    }

    private String nextPageId() {
        String pageId = state.getSessionId() + ":" + nextSequence;
        nextSequence++;
        return pageId;
    }

    private String newSessionId() {
        return UUID.randomUUID().toString();
    }

    private void notifyObservers() {
        Iterator<WeakReference<Observer>> iterator = observers.iterator();
        while (iterator.hasNext()) {
            Observer observer = iterator.next().get();
            if (observer == null) {
                iterator.remove();
            } else {
                observer.onScanSessionChanged(state, cameraState);
            }
        }
    }

    private static boolean valueOrDefault(Boolean value, boolean fallback) {
        return value == null ? fallback : value;
    }

    private static long valueOrDefault(Long value, long fallback) {
        return value == null ? fallback : value;
    }

    private static int valueOrDefault(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    public interface Observer {
        void onScanSessionChanged(ScanSessionState state, ScanCameraState cameraState);
    }
}
