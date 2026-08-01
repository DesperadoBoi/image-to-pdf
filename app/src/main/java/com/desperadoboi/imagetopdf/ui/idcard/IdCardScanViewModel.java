package com.desperadoboi.imagetopdf.ui.idcard;

import android.net.Uri;
import android.os.Bundle;

import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;

import com.desperadoboi.imagetopdf.model.NormalizedPoint;
import com.desperadoboi.imagetopdf.model.PerspectiveQuad;
import com.desperadoboi.imagetopdf.pdf.CancellationToken;
import com.desperadoboi.imagetopdf.ui.smartscan.ScanPage;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;

public final class IdCardScanViewModel extends ViewModel {
    private static final String KEY_SESSION_ID = "idcard.session_id";
    private static final String KEY_FRONT = "idcard.front";
    private static final String KEY_BACK = "idcard.back";
    private static final String KEY_PRESET = "idcard.preset";
    private static final String KEY_WATERMARK_ENABLED = "idcard.watermark_enabled";
    private static final String KEY_WATERMARK_TEXT = "idcard.watermark_text";
    private static final String KEY_NEXT_OPERATION = "idcard.next_operation";

    private static final String RECORD_STATE = "state";
    private static final String RECORD_ERROR = "error";
    private static final String RECORD_CURRENT = "current";
    private static final String RECORD_PENDING = "pending";
    private static final String IMAGE_URI = "uri";
    private static final String IMAGE_FILE = "file";
    private static final String IMAGE_ROTATION = "rotation";
    private static final String IMAGE_QUAD = "quad";

    private final SavedStateHandle savedStateHandle;
    private final ArrayList<WeakReference<Observer>> observers = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private boolean awaitingDefaultWatermarkText;
    private String defaultWatermarkText = "";

    private IdCardScanSession session;
    private IdCardExportOptions exportOptions;
    private IdCardExportState exportState = IdCardExportState.idle();
    private CancellationToken cancellationToken;
    private long nextOperationId;
    private IdCardSide pendingAccessibilityFocusSide;

    public IdCardScanViewModel(SavedStateHandle savedStateHandle) {
        this.savedStateHandle = savedStateHandle;
        awaitingDefaultWatermarkText = !savedStateHandle.contains(KEY_WATERMARK_TEXT);
        session = restoreSession();
        exportOptions = restoreOptions();
        Long storedNextOperation = savedStateHandle.get(KEY_NEXT_OPERATION);
        nextOperationId = storedNextOperation == null ? 1L : Math.max(1L, storedNextOperation);
    }

    public void configureDefaultWatermarkText(String text) {
        defaultWatermarkText = text == null ? "" : text;
        if (!awaitingDefaultWatermarkText) return;
        awaitingDefaultWatermarkText = false;
        exportOptions = exportOptions.withWatermarkText(defaultWatermarkText);
        persistAndNotify();
    }

    public IdCardScanSession getSession() {
        return session;
    }

    public IdCardExportOptions getExportOptions() {
        return exportOptions;
    }

    public IdCardExportState getExportState() {
        return exportState;
    }

    public ExecutorService getExecutor() {
        return executor;
    }

    public void addObserver(Observer observer) {
        if (observer == null) return;
        observers.add(new WeakReference<>(observer));
        observer.onIdCardStateChanged(session, exportOptions, exportState);
    }

    public void removeObserver(Observer observer) {
        Iterator<WeakReference<Observer>> iterator = observers.iterator();
        while (iterator.hasNext()) {
            Observer current = iterator.next().get();
            if (current == null || current == observer) iterator.remove();
        }
    }

    public List<String> startNewSession() {
        List<String> stale = session.collectCacheFileNames();
        session = IdCardScanSession.empty(UUID.randomUUID().toString());
        exportOptions = IdCardExportOptions.defaults(defaultWatermarkText);
        exportState = IdCardExportState.idle();
        pendingAccessibilityFocusSide = null;
        cancelActiveExport();
        persistAndNotify();
        return stale;
    }

    public List<String> clearImagesAfterSuccessfulExport() {
        List<String> files = session.collectCacheFileNames();
        session = IdCardScanSession.empty(UUID.randomUUID().toString());
        exportOptions = IdCardExportOptions.defaults(defaultWatermarkText);
        pendingAccessibilityFocusSide = null;
        persistAndNotify();
        return files;
    }

    public boolean startCapture(IdCardSide side) {
        return begin(side, IdCardSideState.CAPTURING);
    }

    public boolean startGalleryImport(IdCardSide side) {
        return begin(side, IdCardSideState.PROCESSING);
    }

    private boolean begin(IdCardSide side, IdCardSideState state) {
        if (exportState.isRunning() || session.get(side).isBusy()) {
            return false;
        }
        IdCardScanSession updated = session.begin(side, state);
        if (updated == session) return false;
        session = updated;
        persistAndNotify();
        return true;
    }

    public boolean setPendingImage(IdCardSide side, Uri uri, String cacheFileName) {
        if (uri == null) return false;
        IdCardImage image = new IdCardImage(
                uri.toString(),
                cacheFileName,
                0,
                IdCardImage.DEFAULT_CARD_QUAD
        );
        IdCardScanSession updated = session.withPending(side, image);
        if (updated == session) return false;
        session = updated;
        persistAndNotify();
        return true;
    }

    public boolean openCorrection(IdCardSide side) {
        if (exportState.isRunning()) return false;
        IdCardScanSession updated = session.reviewCurrent(side);
        if (updated == session) return false;
        session = updated;
        persistAndNotify();
        return true;
    }

    public ScanPage getReviewPage(IdCardSide side) {
        IdCardImage image = session.get(side).getPendingImage();
        return image == null ? null : image.toScanPage(side);
    }

    public boolean updateReviewPage(IdCardSide side, ScanPage page) {
        IdCardImage pending = session.get(side).getPendingImage();
        if (pending == null || page == null) return false;
        IdCardImage updatedImage = new IdCardImage(
                pending.getUriString(),
                pending.getCacheFileName(),
                page.getRotationDegrees(),
                page.getPerspectiveQuad()
        );
        IdCardScanSession updated = session.updatePending(side, updatedImage);
        if (updated == session) return false;
        session = updated;
        persistAndNotify();
        return true;
    }

    public List<String> acceptReview(IdCardSide side) {
        IdCardSideRecord record = session.get(side);
        IdCardImage previous = record.getCurrentImage();
        IdCardImage pending = record.getPendingImage();
        IdCardScanSession updated = session.acceptPending(side);
        if (updated == session) return java.util.Collections.emptyList();
        session = updated;
        pendingAccessibilityFocusSide = side;
        persistAndNotify();
        if (previous != null
                && pending != null
                && !previous.getCacheFileName().equals(pending.getCacheFileName())) {
            return java.util.Collections.singletonList(previous.getCacheFileName());
        }
        return java.util.Collections.emptyList();
    }

    public IdCardSide consumePendingAccessibilityFocusSide() {
        IdCardSide side = pendingAccessibilityFocusSide;
        pendingAccessibilityFocusSide = null;
        return side;
    }

    public List<String> cancelSideOperation(IdCardSide side) {
        IdCardSideRecord record = session.get(side);
        String disposable = disposablePendingName(record);
        IdCardScanSession updated = session.cancelPending(side);
        if (updated == session) return java.util.Collections.emptyList();
        session = updated;
        persistAndNotify();
        return disposable == null
                ? java.util.Collections.emptyList()
                : java.util.Collections.singletonList(disposable);
    }

    public List<String> failSideOperation(IdCardSide side, IdCardError error) {
        IdCardSideRecord record = session.get(side);
        String disposable = disposablePendingName(record);
        session = session.fail(side, error);
        persistAndNotify();
        return disposable == null
                ? java.util.Collections.emptyList()
                : java.util.Collections.singletonList(disposable);
    }

    public List<String> deleteSide(IdCardSide side) {
        IdCardSideRecord record = session.get(side);
        ArrayList<String> names = new ArrayList<>(2);
        if (record.getCurrentImage() != null) {
            names.add(record.getCurrentImage().getCacheFileName());
        }
        String pending = disposablePendingName(record);
        if (pending != null) names.add(pending);
        session = session.delete(side);
        persistAndNotify();
        return names;
    }

    public boolean rotate(IdCardSide side) {
        IdCardScanSession updated = session.rotate(side);
        if (updated == session) return false;
        session = updated;
        persistAndNotify();
        return true;
    }

    public boolean swap() {
        IdCardScanSession updated = session.swap();
        if (updated == session) return false;
        session = updated;
        persistAndNotify();
        return true;
    }

    public List<String> recoverInterruptedOperations() {
        ArrayList<String> disposable = new ArrayList<>();
        for (IdCardSide side : IdCardSide.values()) {
            IdCardSideRecord record = session.get(side);
            if (!record.isBusy()) continue;
            String pending = disposablePendingName(record);
            if (pending != null) disposable.add(pending);
            session = session.fail(side, IdCardError.OPERATION_CANCELLED);
        }
        if (!disposable.isEmpty()
                || session.get(IdCardSide.FRONT).getState() == IdCardSideState.ERROR
                || session.get(IdCardSide.BACK).getState() == IdCardSideState.ERROR) {
            persistAndNotify();
        }
        return disposable;
    }

    public int markMissingCacheFiles(Predicate<String> exists) {
        int missing = 0;
        for (IdCardSide side : IdCardSide.values()) {
            IdCardSideRecord record = session.get(side);
            IdCardImage image = record.getCurrentImage();
            if (image != null && !exists.test(image.getCacheFileName())) {
                session = session.restoreMissingFile(side);
                missing++;
                continue;
            }
            IdCardImage pending = record.getPendingImage();
            if (pending != null && !exists.test(pending.getCacheFileName())) {
                session = session.restoreMissingFile(side);
                missing++;
            }
        }
        if (missing > 0) persistAndNotify();
        return missing;
    }

    public void setExportPreset(IdCardExportPreset preset) {
        exportOptions = exportOptions.withPreset(preset);
        persistAndNotify();
    }

    public void setWatermarkEnabled(boolean enabled) {
        exportOptions = exportOptions.withWatermarkEnabled(enabled);
        persistAndNotify();
    }

    public boolean setWatermarkText(String text) {
        try {
            exportOptions = exportOptions.withWatermarkText(text);
            persistAndNotify();
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public ExportOperation startExport() {
        if (!session.canExport()
                || session.hasBusySide()
                || !exportOptions.isValid()
                || exportState.isRunning()) {
            return null;
        }
        long operationId = nextOperationId++;
        cancellationToken = new CancellationToken();
        exportState = IdCardExportState.running(operationId, session.getReadyCount());
        persistAndNotify();
        return new ExportOperation(operationId, cancellationToken);
    }

    public void updateExportProgress(long operationId, int completed, int total) {
        exportState = exportState.withProgress(operationId, completed, total);
        notifyObservers();
    }

    public void requestCancelExport() {
        if (!exportState.isRunning()) return;
        if (cancellationToken != null) cancellationToken.cancel();
        exportState = exportState.requestCancellation();
        notifyObservers();
    }

    public void completeExportSuccess(long operationId) {
        exportState = exportState.finish(
                operationId,
                IdCardExportState.Phase.SUCCEEDED,
                IdCardError.NONE
        );
        clearCancellation(operationId);
        notifyObservers();
    }

    public void completeExportCancelled(long operationId) {
        exportState = exportState.finish(
                operationId,
                IdCardExportState.Phase.CANCELLED,
                IdCardError.OPERATION_CANCELLED
        );
        clearCancellation(operationId);
        notifyObservers();
    }

    public void completeExportError(long operationId, IdCardError error) {
        exportState = exportState.finish(operationId, IdCardExportState.Phase.ERROR, error);
        clearCancellation(operationId);
        notifyObservers();
    }

    @Override
    protected void onCleared() {
        cancelActiveExport();
        executor.shutdownNow();
        super.onCleared();
    }

    private IdCardScanSession restoreSession() {
        String id = savedStateHandle.get(KEY_SESSION_ID);
        if (id == null || id.trim().isEmpty()) id = UUID.randomUUID().toString();
        return new IdCardScanSession(
                id,
                restoreRecord(IdCardSide.FRONT, savedStateHandle.get(KEY_FRONT)),
                restoreRecord(IdCardSide.BACK, savedStateHandle.get(KEY_BACK))
        );
    }

    private IdCardExportOptions restoreOptions() {
        String presetName = savedStateHandle.get(KEY_PRESET);
        String text = savedStateHandle.get(KEY_WATERMARK_TEXT);
        Boolean enabled = savedStateHandle.get(KEY_WATERMARK_ENABLED);
        try {
            return new IdCardExportOptions(
                    presetName == null
                            ? IdCardExportPreset.EASY_TO_READ
                            : IdCardExportPreset.valueOf(presetName),
                    enabled != null && enabled,
                    text == null
                            ? defaultWatermarkText
                            : text
            );
        } catch (RuntimeException exception) {
            return IdCardExportOptions.defaults(defaultWatermarkText);
        }
    }

    private IdCardSideRecord restoreRecord(IdCardSide side, Bundle record) {
        if (record == null) return IdCardSideRecord.empty(side);
        try {
            IdCardSideState state = IdCardSideState.valueOf(record.getString(RECORD_STATE));
            IdCardError error = IdCardError.valueOf(record.getString(RECORD_ERROR));
            IdCardImage current = restoreImage(record.getBundle(RECORD_CURRENT));
            IdCardImage pending = restoreImage(record.getBundle(RECORD_PENDING));
            if (state == IdCardSideState.READY && current == null) {
                return IdCardSideRecord.empty(side).fail(IdCardError.FILE_UNAVAILABLE);
            }
            IdCardSideRecord restored = IdCardSideRecord.empty(side);
            if (current != null) restored = restored.withCurrent(current);
            if (state == IdCardSideState.CAPTURING || state == IdCardSideState.PROCESSING) {
                restored = restored.begin(state);
                if (pending != null) restored = restored.withPending(pending);
            } else if (state == IdCardSideState.ERROR) {
                restored = restored.fail(error);
            }
            return restored;
        } catch (RuntimeException exception) {
            return IdCardSideRecord.empty(side).fail(IdCardError.FILE_UNAVAILABLE);
        }
    }

    private Bundle saveRecord(IdCardSideRecord record) {
        Bundle bundle = new Bundle();
        bundle.putString(RECORD_STATE, record.getState().name());
        bundle.putString(RECORD_ERROR, record.getError().name());
        bundle.putBundle(RECORD_CURRENT, saveImage(record.getCurrentImage()));
        bundle.putBundle(RECORD_PENDING, saveImage(record.getPendingImage()));
        return bundle;
    }

    private Bundle saveImage(IdCardImage image) {
        if (image == null) return null;
        Bundle bundle = new Bundle();
        bundle.putString(IMAGE_URI, image.getUriString());
        bundle.putString(IMAGE_FILE, image.getCacheFileName());
        bundle.putInt(IMAGE_ROTATION, image.getRotationDegrees());
        PerspectiveQuad quad = image.getPerspectiveQuad();
        bundle.putFloatArray(IMAGE_QUAD, new float[]{
                quad.getTopLeft().getX(), quad.getTopLeft().getY(),
                quad.getTopRight().getX(), quad.getTopRight().getY(),
                quad.getBottomRight().getX(), quad.getBottomRight().getY(),
                quad.getBottomLeft().getX(), quad.getBottomLeft().getY()
        });
        return bundle;
    }

    private IdCardImage restoreImage(Bundle bundle) {
        if (bundle == null) return null;
        float[] values = bundle.getFloatArray(IMAGE_QUAD);
        if (values == null || values.length != 8) return null;
        return new IdCardImage(
                bundle.getString(IMAGE_URI),
                bundle.getString(IMAGE_FILE),
                bundle.getInt(IMAGE_ROTATION),
                new PerspectiveQuad(
                        new NormalizedPoint(values[0], values[1]),
                        new NormalizedPoint(values[2], values[3]),
                        new NormalizedPoint(values[4], values[5]),
                        new NormalizedPoint(values[6], values[7])
                )
        );
    }

    private void persistAndNotify() {
        savedStateHandle.set(KEY_SESSION_ID, session.getSessionId());
        savedStateHandle.set(KEY_FRONT, saveRecord(session.get(IdCardSide.FRONT)));
        savedStateHandle.set(KEY_BACK, saveRecord(session.get(IdCardSide.BACK)));
        savedStateHandle.set(KEY_PRESET, exportOptions.getPreset().name());
        savedStateHandle.set(KEY_WATERMARK_ENABLED, exportOptions.isWatermarkEnabled());
        savedStateHandle.set(KEY_WATERMARK_TEXT, exportOptions.getWatermarkText());
        savedStateHandle.set(KEY_NEXT_OPERATION, nextOperationId);
        notifyObservers();
    }

    private void notifyObservers() {
        Iterator<WeakReference<Observer>> iterator = observers.iterator();
        while (iterator.hasNext()) {
            Observer observer = iterator.next().get();
            if (observer == null) iterator.remove();
            else observer.onIdCardStateChanged(session, exportOptions, exportState);
        }
    }

    private String disposablePendingName(IdCardSideRecord record) {
        IdCardImage pending = record.getPendingImage();
        IdCardImage current = record.getCurrentImage();
        if (pending == null
                || (current != null
                && current.getCacheFileName().equals(pending.getCacheFileName()))) {
            return null;
        }
        return pending.getCacheFileName();
    }

    private void cancelActiveExport() {
        if (cancellationToken != null) cancellationToken.cancel();
        cancellationToken = null;
    }

    private void clearCancellation(long operationId) {
        if (exportState.getOperationId() == operationId) cancellationToken = null;
    }

    public interface Observer {
        void onIdCardStateChanged(
                IdCardScanSession session,
                IdCardExportOptions options,
                IdCardExportState exportState
        );
    }

    public static final class ExportOperation {
        private final long operationId;
        private final CancellationToken cancellationToken;

        private ExportOperation(long operationId, CancellationToken cancellationToken) {
            this.operationId = operationId;
            this.cancellationToken = cancellationToken;
        }

        public long getOperationId() {
            return operationId;
        }

        public CancellationToken getCancellationToken() {
            return cancellationToken;
        }
    }
}
