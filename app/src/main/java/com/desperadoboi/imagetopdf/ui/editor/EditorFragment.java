package com.desperadoboi.imagetopdf.ui.editor;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
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
import com.desperadoboi.imagetopdf.image.PreviewImageLoader;
import com.desperadoboi.imagetopdf.model.DocumentSessionViewModel;
import com.desperadoboi.imagetopdf.model.ImageImportMode;
import com.desperadoboi.imagetopdf.model.PageItem;
import com.desperadoboi.imagetopdf.model.PdfGenerationState;
import com.desperadoboi.imagetopdf.model.PdfExportDraft;
import com.desperadoboi.imagetopdf.model.PdfExportRequest;
import com.desperadoboi.imagetopdf.model.PdfOptions;
import com.desperadoboi.imagetopdf.model.PdfResult;
import com.desperadoboi.imagetopdf.model.PdfResultNavigationCoordinator;
import com.desperadoboi.imagetopdf.pdf.PdfGenerationCallback;
import com.desperadoboi.imagetopdf.pdf.PdfGenerator;
import com.desperadoboi.imagetopdf.pdf.PdfLocationLabelResolver;
import com.desperadoboi.imagetopdf.pdf.PdfResultMetadataReader;
import com.desperadoboi.imagetopdf.ui.export.PdfExportSheet;
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
    private static final String STATE_SELECTED_PAGE_ID = "selected_page_id";

    private DocumentSessionViewModel sessionViewModel;
    private NavigationCallback navigationCallback;
    private ActivityResultLauncher<String> createDocumentLauncher;

    private TextView selectedImagesTextView;
    private TextView operationStatusTextView;
    private ProgressBar progressBar;
    private TextView generationProgressTextView;
    private Button cancelGenerationButton;
    private RecyclerView pagesRecyclerView;
    private ImageView selectedPageImageView;
    private TextView selectedPageCounterView;
    private ProgressBar previewProgressBar;
    private ImageButton rotateSelectedPageButton;
    private ImageButton deleteSelectedPageButton;
    private ImageButton backButton;
    private MaterialButton addImagesButton;
    private Button createPdfButton;

    private ThumbnailLoader thumbnailLoader;
    private PreviewImageLoader previewImageLoader;
    private CapturedImageStorage capturedImageStorage;
    private EditorPageStripAdapter pageAdapter;
    private ItemTouchHelper pageTouchHelper;
    private DocumentSessionViewModel.PdfGenerationStateObserver pdfGenerationStateObserver;
    private long selectedPageId = SelectedPageResolver.NO_PAGE_ID;
    private String activePreviewKey;
    private Bitmap currentPreviewBitmap;

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
        previewImageLoader = new PreviewImageLoader(requireContext().getApplicationContext().getContentResolver(), ContextCompat.getMainExecutor(requireContext()));
        if (savedInstanceState != null) selectedPageId = savedInstanceState.getLong(STATE_SELECTED_PAGE_ID, SelectedPageResolver.NO_PAGE_ID);
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
            handlePdfResultNavigation(generationState);
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
        activePreviewKey = null;
        releaseCurrentPreviewBitmap();
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        if (thumbnailLoader != null) {
            thumbnailLoader.shutdown();
        }
        if (previewImageLoader != null) previewImageLoader.shutdown();
        super.onDestroy();
    }

    @Override
    public void onDetach() {
        navigationCallback = null;
        super.onDetach();
    }

    private void bindViews(View view) {
        selectedImagesTextView = view.findViewById(R.id.text_selection_status);
        operationStatusTextView = view.findViewById(R.id.text_operation_status);
        progressBar = view.findViewById(R.id.progress_generation);
        generationProgressTextView = view.findViewById(R.id.text_generation_progress);
        cancelGenerationButton = view.findViewById(R.id.button_cancel_generation);
        pagesRecyclerView = view.findViewById(R.id.recycler_pages);
        selectedPageImageView = view.findViewById(R.id.image_selected_page);
        selectedPageCounterView = view.findViewById(R.id.text_selected_page_counter);
        previewProgressBar = view.findViewById(R.id.progress_editor_preview);
        rotateSelectedPageButton = view.findViewById(R.id.button_rotate_selected_page);
        deleteSelectedPageButton = view.findViewById(R.id.button_delete_selected_page);
        backButton = view.findViewById(R.id.button_back);
        addImagesButton = view.findViewById(R.id.button_add_images);
        createPdfButton = view.findViewById(R.id.button_create_pdf);
    }

    private void configurePageList() {
        pageAdapter = new EditorPageStripAdapter(
                sessionViewModel.getPages(),
                thumbnailLoader,
                selectedPageId,
                getResources().getBoolean(R.bool.editor_page_strip_show_add_item),
                new EditorPageStripAdapter.Listener() {
                    @Override public void onPageSelected(long pageId) { selectPage(pageId); }
                    @Override public void onAddRequested() { openImagePicker(); }
                    @Override public boolean onPageDragStart(RecyclerView.ViewHolder viewHolder) {
                        if (!sessionViewModel.canEditPages()) return false;
                        pageTouchHelper.startDrag(viewHolder);
                        return true;
                    }
                }
        );
        boolean verticalStrip = getResources().getBoolean(R.bool.editor_page_strip_vertical);
        pagesRecyclerView.setLayoutManager(new LinearLayoutManager(
                requireContext(),
                verticalStrip ? RecyclerView.VERTICAL : RecyclerView.HORIZONTAL,
                false
        ));
        pagesRecyclerView.setAdapter(pageAdapter);
        pageTouchHelper = new ItemTouchHelper(new PageMoveCallback(verticalStrip));
        pageTouchHelper.attachToRecyclerView(pagesRecyclerView);
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
        cancelGenerationButton.setOnClickListener(v -> cancelPdfGeneration());
        rotateSelectedPageButton.setOnClickListener(v -> rotateSelectedPage());
        deleteSelectedPageButton.setOnClickListener(v -> deleteSelectedPage());
        selectedPageImageView.setOnClickListener(v -> openPagePreview(
                PreviewPageNavigator.findPositionById(
                        sessionViewModel.getPages(),
                        selectedPageId
                )
        ));
    }

    @Override public void onSaveInstanceState(@NonNull Bundle outState) { outState.putLong(STATE_SELECTED_PAGE_ID, selectedPageId); super.onSaveInstanceState(outState); }

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
        pageAdapter.notifyPageChanged(pageId);
        if (selectedPageId == pageId) renderSelectedPage();
        updateUiState();
    }

    private void selectPage(long pageId) {
        if (!sessionViewModel.canEditPages() || PreviewPageNavigator.findPositionById(sessionViewModel.getPages(), pageId) == PreviewPageNavigator.POSITION_NOT_FOUND) return;
        selectedPageId = pageId;
        pageAdapter.setSelectedPageId(pageId);
        renderSelectedPage();
    }

    private void rotateSelectedPage() {
        int position = PreviewPageNavigator.findPositionById(sessionViewModel.getPages(), selectedPageId);
        if (!sessionViewModel.canEditPages()
                || position < 0
                || position >= sessionViewModel.getPageCount()) {
            return;
        }
        sessionViewModel.rotatePage(position);
        pageAdapter.notifyPageChanged(selectedPageId);
        renderSelectedPage();
        updateUiState();
    }

    private void deleteSelectedPage() {
        int position = PreviewPageNavigator.findPositionById(sessionViewModel.getPages(), selectedPageId);
        if (!sessionViewModel.canEditPages()
                || position < 0
                || position >= sessionViewModel.getPageCount()) {
            return;
        }
        PageItem removedPage = sessionViewModel.deletePage(position);
        deleteCapturedFileIfNeeded(removedPage);
        java.util.ArrayList<Long> ids = new java.util.ArrayList<>();
        for (PageItem page : sessionViewModel.getPages()) ids.add(page.getId());
        selectedPageId = SelectedPageResolver.selectAfterDeletion(ids, position);
        pageAdapter.notifyDataSetChanged();
        pageAdapter.setSelectedPageId(selectedPageId);
        renderSelectedPage();
        updateUiState();
    }

    private boolean movePage(int fromPosition, int toPosition) {
        int pageCount = sessionViewModel.getPageCount();
        if (!sessionViewModel.canEditPages()
                || fromPosition < 0 || fromPosition >= pageCount
                || toPosition < 0 || toPosition >= pageCount) {
            return false;
        }
        if (!sessionViewModel.movePage(fromPosition, toPosition)) {
            return false;
        }
        pageAdapter.notifyItemMoved(fromPosition, toPosition);
        int firstChangedPosition = Math.min(fromPosition, toPosition);
        pageAdapter.notifyItemRangeChanged(
                firstChangedPosition,
                Math.abs(fromPosition - toPosition) + 1
        );
        renderSelectedPage();
        return true;
    }

    private void renderSelectedPage() {
        int position = PreviewPageNavigator.findPositionById(sessionViewModel.getPages(), selectedPageId);
        if (position == PreviewPageNavigator.POSITION_NOT_FOUND) { activePreviewKey = null; releaseCurrentPreviewBitmap(); selectedPageImageView.setImageDrawable(null); selectedPageCounterView.setVisibility(View.GONE); previewProgressBar.setVisibility(View.GONE); return; }
        PageItem page = sessionViewModel.getPages().get(position);
        selectedPageCounterView.setVisibility(View.VISIBLE);
        selectedPageCounterView.setText(getString(R.string.editor_selected_page_counter, position + 1, sessionViewModel.getPageCount()));
        if (selectedPageImageView.getWidth() == 0 || selectedPageImageView.getHeight() == 0) { selectedPageImageView.post(this::renderSelectedPage); return; }
        String key = PreviewImageLoader.buildKey(page, selectedPageImageView.getWidth(), selectedPageImageView.getHeight());
        if (key.equals(activePreviewKey)) return;
        activePreviewKey = key; releaseCurrentPreviewBitmap(); selectedPageImageView.setImageDrawable(null); previewProgressBar.setVisibility(View.VISIBLE);
        previewImageLoader.load(page, selectedPageImageView.getWidth(), selectedPageImageView.getHeight(), new PreviewImageLoader.Callback() {
            @Override public void onLoaded(String loadedKey, Bitmap bitmap) { if (!loadedKey.equals(activePreviewKey) || selectedPageImageView == null) { recycle(bitmap); return; } releaseCurrentPreviewBitmap(); currentPreviewBitmap = bitmap; selectedPageImageView.setImageBitmap(bitmap); previewProgressBar.setVisibility(View.GONE); }
            @Override public void onError(String loadedKey) { if (loadedKey.equals(activePreviewKey) && previewProgressBar != null) previewProgressBar.setVisibility(View.GONE); }
        });
    }
    private void releaseCurrentPreviewBitmap() { recycle(currentPreviewBitmap); currentPreviewBitmap = null; }
    private static void recycle(Bitmap bitmap) { if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle(); }

    private final class PageMoveCallback extends ItemTouchHelper.SimpleCallback {
        PageMoveCallback(boolean verticalStrip) {
            super(verticalStrip ? ItemTouchHelper.UP | ItemTouchHelper.DOWN
                    : ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT, 0);
        }

        @Override public boolean isLongPressDragEnabled() { return false; }
        @Override public boolean isItemViewSwipeEnabled() { return false; }

        @Override public int getMovementFlags(@NonNull RecyclerView recyclerView,
                @NonNull RecyclerView.ViewHolder viewHolder) {
            if (!sessionViewModel.canEditPages()
                    || viewHolder.getBindingAdapterPosition() == RecyclerView.NO_POSITION
                    || viewHolder.getBindingAdapterPosition() >= sessionViewModel.getPageCount()) {
                return 0;
            }
            return super.getMovementFlags(recyclerView, viewHolder);
        }

        @Override public boolean canDropOver(@NonNull RecyclerView recyclerView,
                @NonNull RecyclerView.ViewHolder current, @NonNull RecyclerView.ViewHolder target) {
            return target.getBindingAdapterPosition() != RecyclerView.NO_POSITION
                    && target.getBindingAdapterPosition() < sessionViewModel.getPageCount();
        }

        @Override public boolean onMove(@NonNull RecyclerView recyclerView,
                @NonNull RecyclerView.ViewHolder viewHolder,
                @NonNull RecyclerView.ViewHolder target) {
            return movePage(viewHolder.getBindingAdapterPosition(), target.getBindingAdapterPosition());
        }

        @Override public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) { }

        @Override public void onSelectedChanged(@Nullable RecyclerView.ViewHolder viewHolder,
                int actionState) {
            super.onSelectedChanged(viewHolder, actionState);
            if (viewHolder != null && actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                viewHolder.itemView.setAlpha(0.86f);
                viewHolder.itemView.setElevation(getResources().getDimension(R.dimen.page_drag_elevation));
            }
        }

        @Override public void clearView(@NonNull RecyclerView recyclerView,
                @NonNull RecyclerView.ViewHolder viewHolder) {
            super.clearView(recyclerView, viewHolder);
            viewHolder.itemView.setAlpha(1f);
            viewHolder.itemView.setElevation(0f);
            pageAdapter.onDragFinished(viewHolder);
        }
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
        createPdfButton.setText(getResources().getQuantityString(R.plurals.create_pdf_page_count, sessionViewModel.getPageCount(), sessionViewModel.getPageCount()));
        updateGenerationProgressState(generationState);
        pagesRecyclerView.setVisibility(hasPages ? View.VISIBLE : View.GONE);
        pageAdapter.setActionsEnabled(controlsEnabled);
        selectedImagesTextView.setText(getResources().getQuantityString(R.plurals.editor_page_count, sessionViewModel.getPageCount(), sessionViewModel.getPageCount()));
        if (hasPages && PreviewPageNavigator.findPositionById(sessionViewModel.getPages(), selectedPageId) == PreviewPageNavigator.POSITION_NOT_FOUND) {
            selectedPageId = sessionViewModel.getPages().get(0).getId();
            pageAdapter.setSelectedPageId(selectedPageId);
        }
        rotateSelectedPageButton.setEnabled(controlsEnabled && hasPages);
        deleteSelectedPageButton.setEnabled(controlsEnabled && hasPages);
        renderSelectedPage();
        String operationStatus = buildOperationStatusText();
        operationStatusTextView.setText(operationStatus);
        operationStatusTextView.setVisibility(
                operationStatus.isEmpty() ? View.GONE : View.VISIBLE
        );
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

    private void handlePdfResultNavigation(PdfGenerationState generationState) {
        if (navigationCallback == null
                || sessionViewModel.resolvePdfResultNavigation(generationState)
                        != PdfResultNavigationCoordinator.Decision.NAVIGATE_TO_RESULT) {
            return;
        }
        navigationCallback.onPdfResultRequested();
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

}
