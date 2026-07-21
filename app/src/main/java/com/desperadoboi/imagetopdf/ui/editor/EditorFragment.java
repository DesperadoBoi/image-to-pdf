package com.desperadoboi.imagetopdf.ui.editor;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.desperadoboi.imagetopdf.R;
import com.desperadoboi.imagetopdf.image.CapturedImageStorage;
import com.desperadoboi.imagetopdf.image.ThumbnailLoader;
import com.desperadoboi.imagetopdf.model.DocumentSessionViewModel;
import com.desperadoboi.imagetopdf.model.ImageImportMode;
import com.desperadoboi.imagetopdf.model.PageItem;
import com.desperadoboi.imagetopdf.model.PdfGenerationState;
import com.desperadoboi.imagetopdf.model.PdfExportDraft;
import com.desperadoboi.imagetopdf.model.PdfExportRequest;
import com.desperadoboi.imagetopdf.model.PdfFileNameFormatter;
import com.desperadoboi.imagetopdf.model.PdfOptions;
import com.desperadoboi.imagetopdf.model.PdfResult;
import com.desperadoboi.imagetopdf.model.PdfSuccessEvent;
import com.desperadoboi.imagetopdf.pdf.PdfGenerationCallback;
import com.desperadoboi.imagetopdf.pdf.PdfGenerator;
import com.desperadoboi.imagetopdf.pdf.PdfLocationLabelResolver;
import com.desperadoboi.imagetopdf.pdf.PdfResultMetadataReader;
import com.desperadoboi.imagetopdf.ui.export.PdfExportSheet;
import com.desperadoboi.imagetopdf.ui.result.PdfIntentFactory;
import com.desperadoboi.imagetopdf.util.PdfResultSizeFormatter;
import com.google.android.material.button.MaterialButton;

import java.io.InterruptedIOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;

public final class EditorFragment extends Fragment {
    public static final String TAG = "EditorFragment";

    private static final String PDF_MIME_TYPE = "application/pdf";
    private static final float DRAG_ACTIVE_ALPHA = 0.94f;
    private static final float DRAG_ACTIVE_SCALE = 1.015f;

    private DocumentSessionViewModel sessionViewModel;
    private NavigationCallback navigationCallback;
    private ActivityResultLauncher<String> createDocumentLauncher;

    private TextView selectedImagesTextView;
    private TextView reorderHintTextView;
    private TextView operationStatusTextView;
    private View pdfResultLayout;
    private TextView latestPdfNameTextView;
    private TextView latestPdfSummaryTextView;
    private Button shareLatestPdfButton;
    private Button openPdfResultButton;
    private PdfSuccessBanner pdfSuccessBanner;
    private ProgressBar progressBar;
    private TextView generationProgressTextView;
    private Button cancelGenerationButton;
    private RecyclerView pagesRecyclerView;
    private ImageButton backButton;
    private MaterialButton addImagesButton;
    private Button createPdfButton;

    private ThumbnailLoader thumbnailLoader;
    private CapturedImageStorage capturedImageStorage;
    private PageAdapter pageAdapter;
    private ItemTouchHelper pageTouchHelper;
    private DocumentSessionViewModel.PdfGenerationStateObserver pdfGenerationStateObserver;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof NavigationCallback) {
            navigationCallback = (NavigationCallback) context;
            return;
        }
        throw new IllegalStateException("Host activity must implement NavigationCallback");
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sessionViewModel = new ViewModelProvider(requireActivity())
                .get(DocumentSessionViewModel.class);
        thumbnailLoader = new ThumbnailLoader(
                requireContext().getApplicationContext().getContentResolver(),
                ContextCompat.getMainExecutor(requireContext())
        );
        capturedImageStorage = new CapturedImageStorage(requireContext());
        createDocumentLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument(PDF_MIME_TYPE),
                this::handleCreateDocumentResult
        );
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.fragment_editor, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bindViews(view);
        configurePdfExportResultListener();
        configurePageEditResultListener();
        configurePageList();
        configureClickListeners();
        pdfGenerationStateObserver = generationState -> {
            updateUiState();
            showPendingPdfSuccess();
        };
        sessionViewModel.addPdfGenerationStateObserver(pdfGenerationStateObserver);
        updateUiState();
    }

    @Override
    public void onDestroyView() {
        if (sessionViewModel != null && pdfGenerationStateObserver != null) {
            sessionViewModel.removePdfGenerationStateObserver(pdfGenerationStateObserver);
            pdfGenerationStateObserver = null;
        }
        if (pdfSuccessBanner != null) {
            pdfSuccessBanner.hideImmediately();
        }
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        if (thumbnailLoader != null) {
            thumbnailLoader.shutdown();
        }
        super.onDestroy();
    }

    @Override
    public void onDetach() {
        navigationCallback = null;
        super.onDetach();
    }

    private void bindViews(View view) {
        selectedImagesTextView = view.findViewById(R.id.text_selection_status);
        reorderHintTextView = view.findViewById(R.id.text_reorder_hint);
        operationStatusTextView = view.findViewById(R.id.text_operation_status);
        pdfResultLayout = view.findViewById(R.id.layout_pdf_result);
        latestPdfNameTextView = view.findViewById(R.id.text_latest_pdf_name);
        latestPdfSummaryTextView = view.findViewById(R.id.text_latest_pdf_summary);
        shareLatestPdfButton = view.findViewById(R.id.button_share_latest_pdf);
        openPdfResultButton = view.findViewById(R.id.button_open_pdf_result);
        pdfSuccessBanner = view.findViewById(R.id.banner_pdf_success);
        progressBar = view.findViewById(R.id.progress_generation);
        generationProgressTextView = view.findViewById(R.id.text_generation_progress);
        cancelGenerationButton = view.findViewById(R.id.button_cancel_generation);
        pagesRecyclerView = view.findViewById(R.id.recycler_pages);
        backButton = view.findViewById(R.id.button_back);
        addImagesButton = view.findViewById(R.id.button_add_images);
        createPdfButton = view.findViewById(R.id.button_create_pdf);
    }

    private void configurePageList() {
        pageAdapter = new PageAdapter(
                sessionViewModel.getPages(),
                thumbnailLoader,
                new PageAdapter.PageActionCallback() {
                    @Override
                    public void onRotate(int position) {
                        rotatePage(position);
                    }

                    @Override
                    public void onDelete(int position) {
                        deletePage(position);
                    }

                    @Override
                    public void onPreview(int position) {
                        openPagePreview(position);
                    }

                    @Override
                    public boolean onDragStart(RecyclerView.ViewHolder viewHolder) {
                        return startPageDrag(viewHolder);
                    }

                    @Override
                    public boolean onMoveUp(int position) {
                        return movePage(position, position - 1);
                    }

                    @Override
                    public boolean onMoveDown(int position) {
                        return movePage(position, position + 1);
                    }
                }
        );
        pagesRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        pagesRecyclerView.setAdapter(pageAdapter);
        pageTouchHelper = new ItemTouchHelper(new PageMoveCallback());
        pageTouchHelper.attachToRecyclerView(pagesRecyclerView);
        int scrollPosition = sessionViewModel.consumePendingEditorScrollPosition();
        if (scrollPosition >= 0 && scrollPosition < sessionViewModel.getPageCount()) {
            pagesRecyclerView.scrollToPosition(scrollPosition);
        }
    }

    private void configurePageEditResultListener() {
        getParentFragmentManager().setFragmentResultListener(
                PageEditFragment.RESULT_PAGE_EDITED,
                getViewLifecycleOwner(),
                (requestKey, result) ->
                        notifyPageEdited(result.getLong(PageEditFragment.RESULT_KEY_PAGE_ID))
        );
    }

    private void configureClickListeners() {
        backButton.setOnClickListener(v -> {
            if (navigationCallback != null) {
                navigationCallback.onReturnHomeRequested();
            }
        });
        addImagesButton.setOnClickListener(v -> openImagePicker());
        createPdfButton.setOnClickListener(v -> showPdfExportSheet());
        shareLatestPdfButton.setOnClickListener(v -> shareLatestPdf());
        openPdfResultButton.setOnClickListener(v -> openPdfResult());
        cancelGenerationButton.setOnClickListener(v -> cancelPdfGeneration());
    }

    private void openImagePicker() {
        if (!sessionViewModel.canEditPages()) {
            return;
        }
        if (navigationCallback != null) {
            navigationCallback.onImagePickerRequested(ImageImportMode.APPEND_TO_DOCUMENT);
        }
    }

    private void showPdfExportSheet() {
        if (!sessionViewModel.hasPages() || !sessionViewModel.canEditPages()) {
            return;
        }
        if (sessionViewModel.getPdfExportDraft() == null) {
            sessionViewModel.setPdfExportDraft(PdfExportDraft.defaults(
                    buildSuggestedFileName()
            ));
        }
        if (getParentFragmentManager().findFragmentByTag(PdfExportSheet.TAG) != null) {
            return;
        }
        new PdfExportSheet().show(getParentFragmentManager(), PdfExportSheet.TAG);
    }

    private void configurePdfExportResultListener() {
        getParentFragmentManager().setFragmentResultListener(
                PdfExportSheet.RESULT_ACTION,
                getViewLifecycleOwner(),
                (requestKey, result) -> handlePdfExportAction(
                        result.getString(PdfExportSheet.RESULT_ACTION_TYPE)
                )
        );
    }

    private void handlePdfExportAction(String action) {
        PdfExportDraft draft = sessionViewModel.getPdfExportDraft();
        if (draft == null) {
            return;
        }
        PdfExportRequest request;
        try {
            request = draft.toRequest();
        } catch (IllegalArgumentException exception) {
            return;
        }
        if (PdfExportSheet.ACTION_CHANGE_LOCATION.equals(action)) {
            launchCreateDocument(
                    request,
                    DocumentSessionViewModel.PdfOutputSelectionMode.CHANGE_LOCATION
            );
            return;
        }
        if (!PdfExportSheet.ACTION_CONVERT.equals(action)) {
            return;
        }
        if (request.getOutputUri() != null) {
            startPdfGeneration(request);
        } else {
            launchCreateDocument(
                    request,
                    DocumentSessionViewModel.PdfOutputSelectionMode.CONVERT_AFTER_SELECTION
            );
        }
    }

    private void launchCreateDocument(
            PdfExportRequest request,
            DocumentSessionViewModel.PdfOutputSelectionMode selectionMode
    ) {
        sessionViewModel.setAwaitingSaveLocation(true);
        sessionViewModel.setTransientStatusMessage(null);
        sessionViewModel.setPendingPdfOutputSelectionMode(selectionMode);
        updateUiState();
        createDocumentLauncher.launch(request.getFileName());
    }

    private void handleCreateDocumentResult(Uri outputUri) {
        sessionViewModel.setAwaitingSaveLocation(false);
        DocumentSessionViewModel.PdfOutputSelectionMode selectionMode =
                sessionViewModel.getPendingPdfOutputSelectionMode();
        sessionViewModel.setPendingPdfOutputSelectionMode(null);
        if (outputUri == null) {
            updateUiState();
            return;
        }
        PdfExportDraft draft = sessionViewModel.getPdfExportDraft();
        if (draft == null) {
            return;
        }
        String outputLabel = PdfLocationLabelResolver.resolveLabel(
                requireContext().getApplicationContext(),
                outputUri
        );
        draft = draft.withOutput(outputUri, outputLabel);
        sessionViewModel.setPdfExportDraft(draft);
        if (selectionMode == DocumentSessionViewModel.PdfOutputSelectionMode.CONVERT_AFTER_SELECTION) {
            startPdfGeneration(draft.toRequest());
            return;
        }
        PdfExportSheet sheet = (PdfExportSheet) getParentFragmentManager()
                .findFragmentByTag(PdfExportSheet.TAG);
        if (sheet != null) {
            sheet.refreshFromDraft();
        }
        updateUiState();
    }

    private void openPagePreview(int position) {
        if (!sessionViewModel.canEditPages()
                || position < 0
                || position >= sessionViewModel.getPageCount()
                || navigationCallback == null) {
            return;
        }
        navigationCallback.onPageEditRequested(sessionViewModel.getPages().get(position).getId());
    }

    private void notifyPageEdited(long pageId) {
        int position = PreviewPageNavigator.findPositionById(sessionViewModel.getPages(), pageId);
        if (position == PreviewPageNavigator.POSITION_NOT_FOUND) {
            return;
        }
        pageAdapter.notifyItemChanged(position, PageAdapter.PAYLOAD_IMAGE_EDITS);
        updateUiState();
    }

    private void rotatePage(int position) {
        if (!sessionViewModel.canEditPages()
                || position < 0
                || position >= sessionViewModel.getPageCount()) {
            return;
        }
        sessionViewModel.rotatePage(position);
        pageAdapter.notifyItemChanged(position, PageAdapter.PAYLOAD_IMAGE_EDITS);
        updateUiState();
    }

    private void deletePage(int position) {
        if (!sessionViewModel.canEditPages()
                || position < 0
                || position >= sessionViewModel.getPageCount()) {
            return;
        }
        int oldPageCount = sessionViewModel.getPageCount();
        PageItem removedPage = sessionViewModel.deletePage(position);
        pageAdapter.notifyItemRemoved(position);
        deleteCapturedFileIfNeeded(removedPage);
        int changedPageCount = oldPageCount - position - 1;
        if (changedPageCount > 0) {
            pageAdapter.notifyItemRangeChanged(
                    position,
                    changedPageCount,
                    PageAdapter.PAYLOAD_PAGE_NUMBER
            );
        }
        updateUiState();
    }

    private boolean startPageDrag(RecyclerView.ViewHolder viewHolder) {
        if (sessionViewModel.canEditPages()) {
            pageTouchHelper.startDrag(viewHolder);
            return true;
        }
        return false;
    }

    private boolean movePage(int fromPosition, int toPosition) {
        if (!sessionViewModel.canEditPages()) {
            return false;
        }
        boolean moved = sessionViewModel.movePage(fromPosition, toPosition);
        if (!moved) {
            return false;
        }
        pageAdapter.notifyItemMoved(fromPosition, toPosition);
        notifyPageNumbersChanged(fromPosition, toPosition);
        updateUiState();
        return true;
    }

    private void notifyPageNumbersChanged(int fromPosition, int toPosition) {
        int firstChangedPosition = Math.min(fromPosition, toPosition);
        int changedItemCount = Math.abs(fromPosition - toPosition) + 1;
        pageAdapter.notifyItemRangeChanged(
                firstChangedPosition,
                changedItemCount,
                PageAdapter.PAYLOAD_PAGE_NUMBER
        );
    }

    private void startPdfGeneration(PdfExportRequest exportRequest) {
        Uri outputUri = exportRequest.getOutputUri();
        if (outputUri == null) {
            return;
        }
        List<PageItem> pageSnapshot = sessionViewModel.getPagesSnapshot();
        PdfOptions pdfOptionsSnapshot = exportRequest.toPdfOptions();
        String fallbackDisplayName = exportRequest.getFileName();
        sessionViewModel.setPendingSuggestedFileName(fallbackDisplayName);
        int pageCount = pageSnapshot.size();
        DocumentSessionViewModel.GenerationOperation generationOperation =
                sessionViewModel.startGeneration(pageCount);
        if (generationOperation == null) {
            updateUiState();
            return;
        }
        PdfExportSheet sheet = (PdfExportSheet) getParentFragmentManager()
                .findFragmentByTag(PdfExportSheet.TAG);
        if (sheet != null) {
            sheet.dismissAllowingStateLoss();
        }
        updateUiState();

        Context applicationContext = requireContext().getApplicationContext();
        PdfGenerator pdfGenerator = new PdfGenerator(applicationContext.getContentResolver());
        PdfResultMetadataReader metadataReader = new PdfResultMetadataReader(applicationContext);
        Executor mainExecutor = ContextCompat.getMainExecutor(applicationContext);
        long operationId = generationOperation.getOperationId();
        DocumentSessionViewModel viewModel = sessionViewModel;
        pdfGenerator.generate(
                pageSnapshot,
                pdfOptionsSnapshot,
                outputUri,
                generationOperation.getCancellationToken(),
                sessionViewModel.getPdfExecutor(),
                mainExecutor,
                new ViewModelPdfGenerationCallback(
                        viewModel,
                        operationId,
                        fallbackDisplayName,
                        pageCount,
                        metadataReader,
                        mainExecutor
                )
        );
    }

    private void cancelPdfGeneration() {
        sessionViewModel.requestCancelGeneration();
        updateUiState();
    }

    private void openPdfResult() {
        if (sessionViewModel.getLastPdfResult() != null && navigationCallback != null) {
            navigationCallback.onPdfResultRequested();
        }
    }

    private String buildSuggestedFileName() {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
        String timestamp = formatter.format(new Date());
        return getString(R.string.pdf_file_name_base_template, timestamp);
    }

    private String mapErrorMessage(Exception exception) {
        if (exception instanceof SecurityException) {
            return getString(R.string.status_pdf_creation_permission_error);
        }
        if (exception instanceof IllegalArgumentException) {
            return getString(R.string.status_pdf_creation_invalid_input);
        }
        if (exception instanceof InterruptedIOException) {
            return getString(R.string.status_pdf_creation_interrupted);
        }
        return getString(R.string.status_pdf_creation_error);
    }

    private void updateUiState() {
        boolean controlsEnabled = sessionViewModel.canEditPages();
        boolean hasPages = sessionViewModel.hasPages();
        PdfGenerationState generationState = sessionViewModel.getPdfGenerationState();

        backButton.setEnabled(controlsEnabled);
        addImagesButton.setEnabled(controlsEnabled);
        createPdfButton.setEnabled(controlsEnabled && hasPages);
        updateGenerationProgressState(generationState);
        pagesRecyclerView.setVisibility(hasPages ? View.VISIBLE : View.GONE);
        pageAdapter.setActionsEnabled(controlsEnabled);
        selectedImagesTextView.setText(buildSelectionStatusText());
        reorderHintTextView.setVisibility(sessionViewModel.getPageCount() >= 2 ? View.VISIBLE : View.GONE);
        String operationStatus = buildOperationStatusText();
        operationStatusTextView.setText(operationStatus);
        operationStatusTextView.setVisibility(
                operationStatus.isEmpty() ? View.GONE : View.VISIBLE
        );
        updatePdfResultState(controlsEnabled);
    }

    private void updateGenerationProgressState(PdfGenerationState generationState) {
        if (!generationState.isRunning()) {
            progressBar.setVisibility(View.GONE);
            generationProgressTextView.setVisibility(View.GONE);
            cancelGenerationButton.setVisibility(View.GONE);
            return;
        }

        int totalPages = generationState.getTotalPages();
        int completedPages = generationState.getCompletedPages();
        progressBar.setIndeterminate(false);
        progressBar.setMax(totalPages);
        progressBar.setProgress(completedPages);
        progressBar.setVisibility(View.VISIBLE);
        generationProgressTextView.setText(
                getString(R.string.status_pdf_generation_progress, completedPages, totalPages)
        );
        generationProgressTextView.setVisibility(View.VISIBLE);
        cancelGenerationButton.setVisibility(View.VISIBLE);
    }

    private void updatePdfResultState(boolean controlsEnabled) {
        PdfResult result = sessionViewModel.getLastPdfResult();
        boolean hasResult = result != null;
        pdfResultLayout.setVisibility(hasResult ? View.VISIBLE : View.GONE);
        shareLatestPdfButton.setEnabled(hasResult && controlsEnabled);
        openPdfResultButton.setEnabled(hasResult && controlsEnabled);
        if (!hasResult) {
            return;
        }
        String displayName = result.getDisplayName().isEmpty()
                ? getString(R.string.pdf_result_unknown_name)
                : result.getDisplayName();
        latestPdfNameTextView.setText(result.getDisplayName().isEmpty()
                ? displayName
                : PdfFileNameFormatter.toDisplayTitle(displayName));
        latestPdfNameTextView.setContentDescription(displayName);
        latestPdfSummaryTextView.setText(buildPdfResultSummary(result, true));
    }

    private String buildSelectionStatusText() {
        return getResources().getQuantityString(
                R.plurals.selected_images_count,
                sessionViewModel.getPageCount(),
                sessionViewModel.getPageCount()
        );
    }

    private String buildOperationStatusText() {
        PdfGenerationState generationState = sessionViewModel.getPdfGenerationState();
        if (generationState.isRunning()) {
            return "";
        }
        if (generationState.isCancelled()) {
            return getString(R.string.status_pdf_generation_cancelled);
        }
        if (generationState.isError()) {
            return mapErrorMessage(generationState.getError());
        }
        String transientStatusMessage = sessionViewModel.getTransientStatusMessage();
        if (transientStatusMessage == null || transientStatusMessage.isEmpty()) {
            return "";
        }
        return transientStatusMessage;
    }

    private void showPendingPdfSuccess() {
        PdfSuccessEvent event = sessionViewModel.consumePendingPdfSuccessEvent();
        if (event == null || pdfSuccessBanner == null) {
            return;
        }
        pdfSuccessBanner.showResult(
                buildPdfResultSummary(event.getResult(), false),
                clicked -> openPdfResult()
        );
    }

    private String buildPdfResultSummary(PdfResult result, boolean compactPageCount) {
        int pageCount = result.getPageCount();
        String pages = getResources().getQuantityString(
                compactPageCount
                        ? R.plurals.pdf_latest_result_pages_count
                        : R.plurals.pdf_result_pages_count,
                pageCount,
                pageCount
        );
        String size = PdfResultSizeFormatter.format(
                result,
                Locale.getDefault(),
                getString(R.string.pdf_result_size_unknown)
        );
        return getString(R.string.pdf_result_summary, pages, size);
    }

    private void shareLatestPdf() {
        PdfResult result = sessionViewModel.getLastPdfResult();
        if (result == null) {
            return;
        }
        try {
            startActivity(PdfIntentFactory.createShareIntent(
                    requireContext().getContentResolver(),
                    result,
                    getString(R.string.pdf_share_chooser_title)
            ));
        } catch (ActivityNotFoundException | SecurityException exception) {
            sessionViewModel.setTransientStatusMessage(
                    getString(R.string.status_pdf_share_error)
            );
            updateUiState();
        }
    }

    private void deleteCapturedFileIfNeeded(PageItem pageItem) {
        if (pageItem != null && pageItem.isAppOwnedCapture()) {
            capturedImageStorage.delete(pageItem.getCapturedFileName());
        }
    }

    public interface NavigationCallback {
        void onReturnHomeRequested();

        void onPageEditRequested(long pageId);

        void onImagePickerRequested(ImageImportMode mode);

        void onPdfResultRequested();
    }

    private static final class ViewModelPdfGenerationCallback implements PdfGenerationCallback {
        private final DocumentSessionViewModel sessionViewModel;
        private final long operationId;
        private final String fallbackDisplayName;
        private final int pageCount;
        private final PdfResultMetadataReader metadataReader;
        private final Executor mainExecutor;

        private ViewModelPdfGenerationCallback(
                DocumentSessionViewModel sessionViewModel,
                long operationId,
                String fallbackDisplayName,
                int pageCount,
                PdfResultMetadataReader metadataReader,
                Executor mainExecutor
        ) {
            this.sessionViewModel = sessionViewModel;
            this.operationId = operationId;
            this.fallbackDisplayName = fallbackDisplayName;
            this.pageCount = pageCount;
            this.metadataReader = metadataReader;
            this.mainExecutor = mainExecutor;
        }

        @Override
        public void onProgress(int completedPages, int totalPages) {
            sessionViewModel.updateGenerationProgress(operationId, completedPages, totalPages);
        }

        @Override
        public void onSuccess(Uri savedUri, long sizeBytes) {
            PdfResult generatedResult = new PdfResult(
                    savedUri,
                    fallbackDisplayName,
                    sizeBytes,
                    pageCount,
                    System.currentTimeMillis(),
                    ""
            );
            sessionViewModel.getPdfExecutor().execute(() -> {
                PdfResult resolvedResult = metadataReader.read(generatedResult);
                mainExecutor.execute(() -> sessionViewModel.completeGenerationSuccess(
                        operationId,
                        resolvedResult
                ));
            });
        }

        @Override
        public void onCancelled() {
            sessionViewModel.completeGenerationCancelled(operationId);
        }

        @Override
        public void onError(Exception exception) {
            sessionViewModel.completeGenerationError(operationId, exception);
        }
    }

    private final class PageMoveCallback extends ItemTouchHelper.SimpleCallback {
        PageMoveCallback() {
            super(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
        }

        @Override
        public boolean isLongPressDragEnabled() {
            return false;
        }

        @Override
        public boolean isItemViewSwipeEnabled() {
            return false;
        }

        @Override
        public float getMoveThreshold(@NonNull RecyclerView.ViewHolder viewHolder) {
            return 0.15f;
        }

        @Override
        public boolean onMove(
                @NonNull RecyclerView recyclerView,
                @NonNull RecyclerView.ViewHolder viewHolder,
                @NonNull RecyclerView.ViewHolder target
        ) {
            int fromPosition = viewHolder.getBindingAdapterPosition();
            int toPosition = target.getBindingAdapterPosition();
            if (fromPosition == RecyclerView.NO_POSITION || toPosition == RecyclerView.NO_POSITION) {
                return false;
            }
            return movePage(fromPosition, toPosition);
        }

        @Override
        public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
        }

        @Override
        public void onSelectedChanged(@Nullable RecyclerView.ViewHolder viewHolder, int actionState) {
            super.onSelectedChanged(viewHolder, actionState);
            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
                viewHolder.itemView.setAlpha(DRAG_ACTIVE_ALPHA);
                viewHolder.itemView.setScaleX(DRAG_ACTIVE_SCALE);
                viewHolder.itemView.setScaleY(DRAG_ACTIVE_SCALE);
                viewHolder.itemView.setElevation(
                        getResources().getDimensionPixelSize(R.dimen.page_drag_elevation)
                );
                viewHolder.itemView.setActivated(true);
                viewHolder.itemView.setPressed(true);
            }
        }

        @Override
        public void clearView(
                @NonNull RecyclerView recyclerView,
                @NonNull RecyclerView.ViewHolder viewHolder
        ) {
            super.clearView(recyclerView, viewHolder);
            viewHolder.itemView.setAlpha(1f);
            viewHolder.itemView.setScaleX(1f);
            viewHolder.itemView.setScaleY(1f);
            viewHolder.itemView.setElevation(0f);
            viewHolder.itemView.setActivated(false);
            viewHolder.itemView.setPressed(false);
            pageAdapter.onDragFinished(viewHolder);
        }
    }
}
