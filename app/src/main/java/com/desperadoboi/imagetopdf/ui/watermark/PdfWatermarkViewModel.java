package com.desperadoboi.imagetopdf.ui.watermark;

import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;

import com.desperadoboi.imagetopdf.pdf.CancellationToken;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PdfWatermarkViewModel extends ViewModel {
    private static final String PREFIX = "pdf_watermark.";
    private static final String KEY_SOURCE_FILE = PREFIX + "source_file";
    private static final String KEY_SOURCE_NAME = PREFIX + "source_name";
    private static final String KEY_PAGE_COUNT = PREFIX + "page_count";
    private static final String KEY_CURRENT_PAGE = PREFIX + "current_page";
    private static final String KEY_SOURCE_STATUS = PREFIX + "source_status";
    private static final String KEY_SOURCE_ERROR = PREFIX + "source_error";
    private static final String KEY_TEXT = PREFIX + "text";
    private static final String KEY_STYLE = PREFIX + "style";
    private static final String KEY_POSITION = PREFIX + "position";
    private static final String KEY_OPACITY = PREFIX + "opacity";
    private static final String KEY_SIZE = PREFIX + "size";
    private static final String KEY_ROTATION = PREFIX + "rotation";
    private static final String KEY_COLOR = PREFIX + "color";
    private static final String KEY_PAGE_SELECTION = PREFIX + "page_selection";
    private static final String KEY_RANGE = PREFIX + "range";
    private static final String KEY_NEXT_OPERATION = PREFIX + "next_operation";
    private static final String KEY_EXPORT_PHASE = PREFIX + "export_phase";
    private static final String KEY_EXPORT_OPERATION = PREFIX + "export_operation";
    private static final String KEY_EXPORT_TOTAL = PREFIX + "export_total";
    private static final String KEY_EXPORT_ERROR = PREFIX + "export_error";
    private static final String KEY_TEMP_OUTPUT = PREFIX + "temp_output";

    public enum SourceStatus {
        EMPTY,
        LOADING,
        READY,
        ERROR
    }

    private final SavedStateHandle savedStateHandle;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ArrayList<WeakReference<Observer>> observers = new ArrayList<>();
    private final PageRangeParser rangeParser = new PageRangeParser();
    private final AtomicBoolean sourceCancellation = new AtomicBoolean(false);

    private boolean awaitingLocalizedDefault;
    private String localizedDefault = "";
    private SourceStatus sourceStatus;
    private PdfWatermarkError sourceError;
    private String sourceCacheFileName;
    private String sourceDisplayName;
    private int pageCount;
    private int currentPage;
    private PdfWatermarkOptions options;
    private WatermarkPageSelection pageSelection;
    private String rangeInput;
    private PdfWatermarkExportState exportState;
    private CancellationToken exportCancellation;
    private long nextOperationId;
    private boolean saveLocationRequestPending;
    private boolean resultNavigationPending;

    public PdfWatermarkViewModel(SavedStateHandle savedStateHandle) {
        this.savedStateHandle = savedStateHandle;
        awaitingLocalizedDefault = !savedStateHandle.contains(KEY_TEXT);
        restoreSource();
        options = restoreOptions();
        pageSelection = enumValue(
                WatermarkPageSelection.class,
                savedStateHandle.get(KEY_PAGE_SELECTION),
                WatermarkPageSelection.ALL
        );
        String savedRange = savedStateHandle.get(KEY_RANGE);
        rangeInput = savedRange == null ? "" : savedRange;
        Long savedNextOperation = savedStateHandle.get(KEY_NEXT_OPERATION);
        nextOperationId = savedNextOperation == null ? 1L : Math.max(1L, savedNextOperation);
        exportState = restoreExportState();
    }

    public void configureLocalizedDefault(String value) {
        localizedDefault = value == null ? "" : value;
        if (!awaitingLocalizedDefault) return;
        awaitingLocalizedDefault = false;
        options = options.withText(localizedDefault);
        persistAndNotify();
    }

    public ExecutorService getExecutor() { return executor; }
    public SourceStatus getSourceStatus() { return sourceStatus; }
    public PdfWatermarkError getSourceError() { return sourceError; }
    public String getSourceCacheFileName() { return sourceCacheFileName; }
    public String getSourceDisplayName() { return sourceDisplayName; }
    public int getPageCount() { return pageCount; }
    public int getCurrentPage() { return currentPage; }
    public PdfWatermarkOptions getOptions() { return options; }
    public WatermarkPageSelection getPageSelection() { return pageSelection; }
    public String getRangeInput() { return rangeInput; }
    public PdfWatermarkExportState getExportState() { return exportState; }
    public AtomicBoolean getSourceCancellation() { return sourceCancellation; }

    public void addObserver(Observer observer) {
        if (observer == null) return;
        observers.add(new WeakReference<>(observer));
        observer.onStateChanged();
    }

    public void removeObserver(Observer observer) {
        Iterator<WeakReference<Observer>> iterator = observers.iterator();
        while (iterator.hasNext()) {
            Observer current = iterator.next().get();
            if (current == null || current == observer) iterator.remove();
        }
    }

    public boolean beginSourceLoad() {
        if (exportState.isBusy()) return false;
        sourceCancellation.set(false);
        sourceStatus = SourceStatus.LOADING;
        sourceError = PdfWatermarkError.NONE;
        persistAndNotify();
        return true;
    }

    public String completeSourceLoad(PdfWatermarkSourceLoader.Result result) {
        if (sourceStatus != SourceStatus.LOADING || result == null) return null;
        String stale = sourceCacheFileName;
        sourceCacheFileName = result.getCacheFileName();
        sourceDisplayName = result.getDisplayName();
        pageCount = result.getPageCount();
        currentPage = 0;
        sourceStatus = SourceStatus.READY;
        sourceError = PdfWatermarkError.NONE;
        pageSelection = WatermarkPageSelection.ALL;
        rangeInput = "";
        resetExportTerminalState();
        persistAndNotify();
        return sourceCacheFileName.equals(stale) ? null : stale;
    }

    public void completeSourceLoadError(PdfWatermarkError error) {
        if (sourceStatus != SourceStatus.LOADING) return;
        sourceStatus = SourceStatus.ERROR;
        sourceError = error == null ? PdfWatermarkError.OPEN_PDF : error;
        persistAndNotify();
    }

    public void handlePickerCancelled() {
        if (sourceStatus == SourceStatus.READY) return;
        if (sourceCacheFileName != null && pageCount > 0) {
            sourceStatus = SourceStatus.READY;
            sourceError = PdfWatermarkError.NONE;
        } else {
            sourceStatus = SourceStatus.ERROR;
            sourceError = PdfWatermarkError.CANCELLED;
        }
        persistAndNotify();
    }

    public void markSourceUnavailable() {
        sourceStatus = SourceStatus.ERROR;
        sourceError = PdfWatermarkError.SOURCE_UNAVAILABLE;
        persistAndNotify();
    }

    public void markPreviewError(PdfWatermarkError error) {
        if (sourceStatus != SourceStatus.READY) return;
        sourceError = error == null ? PdfWatermarkError.RENDER_PAGE : error;
        notifyObservers();
    }

    public void clearPreviewError() {
        if (sourceStatus == SourceStatus.READY && sourceError != PdfWatermarkError.NONE) {
            sourceError = PdfWatermarkError.NONE;
            notifyObservers();
        }
    }

    public void setCurrentPage(int value) {
        int clamped = pageCount <= 0 ? 0 : Math.max(0, Math.min(value, pageCount - 1));
        if (currentPage == clamped) return;
        currentPage = clamped;
        persistAndNotify();
    }

    public boolean setText(String value) {
        try {
            options = options.withText(value);
            persistAndNotify();
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public void setStyle(WatermarkStyle value) {
        options = options.withStyle(value);
        persistAndNotify();
    }

    public void setPosition(WatermarkPosition value) {
        options = options.withPosition(value);
        persistAndNotify();
    }

    public void setOpacity(float value) {
        options = options.withOpacity(value);
        persistAndNotify();
    }

    public void setSizePoints(float value) {
        options = options.withSizePoints(value);
        persistAndNotify();
    }

    public void setRotationDegrees(float value) {
        options = options.withRotationDegrees(value);
        persistAndNotify();
    }

    public void setColor(WatermarkColor value) {
        options = options.withColor(value);
        persistAndNotify();
    }

    public void setPageSelection(WatermarkPageSelection value) {
        pageSelection = value == null ? WatermarkPageSelection.ALL : value;
        persistAndNotify();
    }

    public void setRangeInput(String value) {
        rangeInput = value == null ? "" : value;
        persistAndNotify();
    }

    public PageRangeParser.Result getRangeResult() {
        return rangeParser.parse(rangeInput, pageCount);
    }

    public Set<Integer> getTargetPages() {
        return PdfWatermarkPageTargets.resolve(
                pageSelection,
                currentPage,
                pageCount,
                pageSelection == WatermarkPageSelection.RANGE ? getRangeResult() : null
        );
    }

    public boolean canCreatePdf() {
        return sourceStatus == SourceStatus.READY
                && sourceError == PdfWatermarkError.NONE
                && options.hasValidText()
                && !getTargetPages().isEmpty()
                && !exportState.isBusy();
    }

    public ExportOperation startExport() {
        if (!canCreatePdf()) return null;
        long operationId = nextOperationId++;
        exportCancellation = new CancellationToken();
        exportState = PdfWatermarkExportState.generating(operationId, pageCount);
        resultNavigationPending = false;
        persistAndNotify();
        return new ExportOperation(
                operationId,
                sourceCacheFileName,
                pageCount,
                options,
                getTargetPages(),
                exportCancellation
        );
    }

    public boolean isCurrentOperation(long operationId) {
        return exportState.isBusy() && exportState.getOperationId() == operationId;
    }

    public void updateExportProgress(long operationId, int completed, int total) {
        exportState = exportState.withProgress(operationId, completed, total);
        persistAndNotify();
    }

    public void completeGeneration(long operationId, String temporaryFileName) {
        PdfWatermarkExportState updated = exportState.awaitingDestination(
                operationId,
                temporaryFileName
        );
        if (updated == exportState) return;
        exportState = updated;
        exportCancellation = null;
        saveLocationRequestPending = true;
        persistAndNotify();
    }

    public boolean consumeSaveLocationRequest() {
        if (!saveLocationRequestPending
                || exportState.getPhase()
                != PdfWatermarkExportState.Phase.AWAITING_DESTINATION) {
            return false;
        }
        saveLocationRequestPending = false;
        return true;
    }

    public SaveOperation startSaving() {
        if (exportState.getPhase()
                != PdfWatermarkExportState.Phase.AWAITING_DESTINATION) {
            return null;
        }
        long operationId = exportState.getOperationId();
        String temporaryFileName = exportState.getTemporaryFileName();
        exportState = exportState.saving(operationId);
        exportCancellation = new CancellationToken();
        persistAndNotify();
        return new SaveOperation(operationId, temporaryFileName, exportCancellation);
    }

    public String handleSaveLocationCancelled() {
        if (exportState.getPhase()
                != PdfWatermarkExportState.Phase.AWAITING_DESTINATION) {
            return null;
        }
        String temporaryFile = exportState.getTemporaryFileName();
        long operationId = exportState.getOperationId();
        exportState = exportState.finish(
                operationId,
                PdfWatermarkExportState.Phase.CANCELLED,
                PdfWatermarkError.CANCELLED
        );
        persistAndNotify();
        return temporaryFile;
    }

    public String requestCancelExport() {
        if (!exportState.isBusy()) return null;
        if (exportState.getPhase()
                == PdfWatermarkExportState.Phase.AWAITING_DESTINATION) {
            return handleSaveLocationCancelled();
        }
        if (exportCancellation != null) exportCancellation.cancel();
        exportState = exportState.requestCancellation();
        persistAndNotify();
        return null;
    }

    public void completeExportCancelled(long operationId) {
        exportState = exportState.finish(
                operationId,
                PdfWatermarkExportState.Phase.CANCELLED,
                PdfWatermarkError.CANCELLED
        );
        clearCancellation(operationId);
        persistAndNotify();
    }

    public void completeExportError(long operationId, PdfWatermarkError error) {
        exportState = exportState.finish(
                operationId,
                PdfWatermarkExportState.Phase.ERROR,
                error == null ? PdfWatermarkError.CREATE_PDF : error
        );
        clearCancellation(operationId);
        persistAndNotify();
    }

    public void completeExportSuccess(long operationId) {
        PdfWatermarkExportState updated = exportState.finish(
                operationId,
                PdfWatermarkExportState.Phase.SUCCEEDED,
                PdfWatermarkError.NONE
        );
        if (updated == exportState) return;
        exportState = updated;
        clearCancellation(operationId);
        resultNavigationPending = true;
        persistAndNotify();
    }

    public boolean consumeResultNavigation() {
        if (!resultNavigationPending) return false;
        resultNavigationPending = false;
        return true;
    }

    public CleanupFiles clearSession() {
        sourceCancellation.set(true);
        if (exportCancellation != null) exportCancellation.cancel();
        CleanupFiles files = new CleanupFiles(
                sourceCacheFileName,
                exportState.getTemporaryFileName()
        );
        sourceCacheFileName = null;
        sourceDisplayName = "";
        pageCount = 0;
        currentPage = 0;
        sourceStatus = SourceStatus.EMPTY;
        sourceError = PdfWatermarkError.NONE;
        options = PdfWatermarkOptions.defaults(localizedDefault);
        pageSelection = WatermarkPageSelection.ALL;
        rangeInput = "";
        exportState = PdfWatermarkExportState.idle();
        exportCancellation = null;
        saveLocationRequestPending = false;
        resultNavigationPending = false;
        persistAndNotify();
        return files;
    }

    @Override
    protected void onCleared() {
        sourceCancellation.set(true);
        if (exportCancellation != null) exportCancellation.cancel();
        executor.shutdownNow();
        super.onCleared();
    }

    private void restoreSource() {
        sourceCacheFileName = savedStateHandle.get(KEY_SOURCE_FILE);
        String storedName = savedStateHandle.get(KEY_SOURCE_NAME);
        sourceDisplayName = storedName == null ? "" : storedName;
        Integer storedCount = savedStateHandle.get(KEY_PAGE_COUNT);
        pageCount = storedCount == null ? 0 : Math.max(0, storedCount);
        Integer storedCurrent = savedStateHandle.get(KEY_CURRENT_PAGE);
        currentPage = storedCurrent == null || pageCount == 0
                ? 0
                : Math.max(0, Math.min(storedCurrent, pageCount - 1));
        SourceStatus restoredStatus = enumValue(
                SourceStatus.class,
                savedStateHandle.get(KEY_SOURCE_STATUS),
                sourceCacheFileName != null && pageCount > 0
                        ? SourceStatus.READY
                        : SourceStatus.EMPTY
        );
        if (restoredStatus == SourceStatus.LOADING) restoredStatus = SourceStatus.ERROR;
        sourceStatus = restoredStatus;
        sourceError = enumValue(
                PdfWatermarkError.class,
                savedStateHandle.get(KEY_SOURCE_ERROR),
                restoredStatus == SourceStatus.ERROR
                        ? PdfWatermarkError.CANCELLED
                        : PdfWatermarkError.NONE
        );
    }

    private PdfWatermarkOptions restoreOptions() {
        String text = savedStateHandle.get(KEY_TEXT);
        Float opacity = savedStateHandle.get(KEY_OPACITY);
        Float size = savedStateHandle.get(KEY_SIZE);
        Float rotation = savedStateHandle.get(KEY_ROTATION);
        try {
            return new PdfWatermarkOptions(
                    text == null ? "" : text,
                    enumValue(
                            WatermarkStyle.class,
                            savedStateHandle.get(KEY_STYLE),
                            WatermarkStyle.SINGLE
                    ),
                    enumValue(
                            WatermarkPosition.class,
                            savedStateHandle.get(KEY_POSITION),
                            WatermarkPosition.CENTER
                    ),
                    opacity == null ? PdfWatermarkOptions.DEFAULT_OPACITY : opacity,
                    size == null ? PdfWatermarkOptions.DEFAULT_SIZE_POINTS : size,
                    rotation == null
                            ? PdfWatermarkOptions.DEFAULT_ROTATION_DEGREES
                            : rotation,
                    enumValue(
                            WatermarkColor.class,
                            savedStateHandle.get(KEY_COLOR),
                            WatermarkColor.GRAY
                    )
            );
        } catch (RuntimeException exception) {
            return PdfWatermarkOptions.defaults("");
        }
    }

    private PdfWatermarkExportState restoreExportState() {
        PdfWatermarkExportState.Phase phase = enumValue(
                PdfWatermarkExportState.Phase.class,
                savedStateHandle.get(KEY_EXPORT_PHASE),
                PdfWatermarkExportState.Phase.IDLE
        );
        Long operation = savedStateHandle.get(KEY_EXPORT_OPERATION);
        Integer total = savedStateHandle.get(KEY_EXPORT_TOTAL);
        long operationId = operation == null ? 0L : operation;
        int totalPages = total == null ? pageCount : Math.max(0, total);
        if (phase == PdfWatermarkExportState.Phase.AWAITING_DESTINATION) {
            String temporary = savedStateHandle.get(KEY_TEMP_OUTPUT);
            if (temporary != null && totalPages > 0) {
                saveLocationRequestPending = true;
                return PdfWatermarkExportState.restoreAwaiting(
                        operationId,
                        totalPages,
                        temporary
                );
            }
            return PdfWatermarkExportState.interrupted(operationId, totalPages);
        }
        if (phase == PdfWatermarkExportState.Phase.GENERATING
                || phase == PdfWatermarkExportState.Phase.SAVING) {
            return PdfWatermarkExportState.interrupted(operationId, totalPages);
        }
        if (phase == PdfWatermarkExportState.Phase.CANCELLED
                || phase == PdfWatermarkExportState.Phase.ERROR
                || phase == PdfWatermarkExportState.Phase.SUCCEEDED) {
            return PdfWatermarkExportState.terminal(
                    phase,
                    operationId,
                    totalPages,
                    enumValue(
                            PdfWatermarkError.class,
                            savedStateHandle.get(KEY_EXPORT_ERROR),
                            phase == PdfWatermarkExportState.Phase.CANCELLED
                                    ? PdfWatermarkError.CANCELLED
                                    : PdfWatermarkError.NONE
                    )
            );
        }
        return PdfWatermarkExportState.idle();
    }

    private void resetExportTerminalState() {
        if (!exportState.isBusy()) exportState = PdfWatermarkExportState.idle();
    }

    private void clearCancellation(long operationId) {
        if (exportState.getOperationId() == operationId) exportCancellation = null;
    }

    private void persistAndNotify() {
        savedStateHandle.set(KEY_SOURCE_FILE, sourceCacheFileName);
        savedStateHandle.set(KEY_SOURCE_NAME, sourceDisplayName);
        savedStateHandle.set(KEY_PAGE_COUNT, pageCount);
        savedStateHandle.set(KEY_CURRENT_PAGE, currentPage);
        savedStateHandle.set(KEY_SOURCE_STATUS, sourceStatus.name());
        savedStateHandle.set(KEY_SOURCE_ERROR, sourceError.name());
        savedStateHandle.set(KEY_TEXT, options.getText());
        savedStateHandle.set(KEY_STYLE, options.getStyle().name());
        savedStateHandle.set(KEY_POSITION, options.getPosition().name());
        savedStateHandle.set(KEY_OPACITY, options.getOpacity());
        savedStateHandle.set(KEY_SIZE, options.getSizePoints());
        savedStateHandle.set(KEY_ROTATION, options.getRotationDegrees());
        savedStateHandle.set(KEY_COLOR, options.getColor().name());
        savedStateHandle.set(KEY_PAGE_SELECTION, pageSelection.name());
        savedStateHandle.set(KEY_RANGE, rangeInput);
        savedStateHandle.set(KEY_NEXT_OPERATION, nextOperationId);
        savedStateHandle.set(KEY_EXPORT_PHASE, exportState.getPhase().name());
        savedStateHandle.set(KEY_EXPORT_OPERATION, exportState.getOperationId());
        savedStateHandle.set(KEY_EXPORT_TOTAL, exportState.getTotalPages());
        savedStateHandle.set(KEY_EXPORT_ERROR, exportState.getError().name());
        savedStateHandle.set(KEY_TEMP_OUTPUT, exportState.getTemporaryFileName());
        notifyObservers();
    }

    private void notifyObservers() {
        Iterator<WeakReference<Observer>> iterator = observers.iterator();
        while (iterator.hasNext()) {
            Observer observer = iterator.next().get();
            if (observer == null) iterator.remove();
            else observer.onStateChanged();
        }
    }

    private static <T extends Enum<T>> T enumValue(
            Class<T> type,
            String value,
            T fallback
    ) {
        if (value == null) return fallback;
        try {
            return Enum.valueOf(type, value);
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    public interface Observer {
        void onStateChanged();
    }

    public static final class ExportOperation {
        private final long operationId;
        private final String sourceCacheFileName;
        private final int pageCount;
        private final PdfWatermarkOptions options;
        private final Set<Integer> selectedPages;
        private final CancellationToken cancellationToken;

        private ExportOperation(
                long operationId,
                String sourceCacheFileName,
                int pageCount,
                PdfWatermarkOptions options,
                Set<Integer> selectedPages,
                CancellationToken cancellationToken
        ) {
            this.operationId = operationId;
            this.sourceCacheFileName = sourceCacheFileName;
            this.pageCount = pageCount;
            this.options = options;
            this.selectedPages = Collections.unmodifiableSet(selectedPages);
            this.cancellationToken = cancellationToken;
        }

        public long getOperationId() { return operationId; }
        public String getSourceCacheFileName() { return sourceCacheFileName; }
        public int getPageCount() { return pageCount; }
        public PdfWatermarkOptions getOptions() { return options; }
        public Set<Integer> getSelectedPages() { return selectedPages; }
        public CancellationToken getCancellationToken() { return cancellationToken; }
    }

    public static final class SaveOperation {
        private final long operationId;
        private final String temporaryFileName;
        private final CancellationToken cancellationToken;

        private SaveOperation(
                long operationId,
                String temporaryFileName,
                CancellationToken cancellationToken
        ) {
            this.operationId = operationId;
            this.temporaryFileName = temporaryFileName;
            this.cancellationToken = cancellationToken;
        }

        public long getOperationId() { return operationId; }
        public String getTemporaryFileName() { return temporaryFileName; }
        public CancellationToken getCancellationToken() { return cancellationToken; }
    }

    public static final class CleanupFiles {
        private final String sourceCacheFileName;
        private final String temporaryOutputFileName;

        private CleanupFiles(String sourceCacheFileName, String temporaryOutputFileName) {
            this.sourceCacheFileName = sourceCacheFileName;
            this.temporaryOutputFileName = temporaryOutputFileName;
        }

        public String getSourceCacheFileName() { return sourceCacheFileName; }
        public String getTemporaryOutputFileName() { return temporaryOutputFileName; }
    }
}
