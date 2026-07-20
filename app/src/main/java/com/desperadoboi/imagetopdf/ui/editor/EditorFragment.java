package com.desperadoboi.imagetopdf.ui.editor;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
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
import com.desperadoboi.imagetopdf.model.ImageImportRequest;
import com.desperadoboi.imagetopdf.model.ImageImportResult;
import com.desperadoboi.imagetopdf.model.ImageImportSource;
import com.desperadoboi.imagetopdf.model.PageItem;
import com.desperadoboi.imagetopdf.model.PdfGenerationState;
import com.desperadoboi.imagetopdf.model.PdfOptions;
import com.desperadoboi.imagetopdf.model.PdfResult;
import com.desperadoboi.imagetopdf.pdf.PdfGenerationCallback;
import com.desperadoboi.imagetopdf.pdf.PdfGenerator;
import com.desperadoboi.imagetopdf.ui.importer.ImageImportCoordinator;
import com.desperadoboi.imagetopdf.ui.importer.ImagePickerLauncher;
import com.desperadoboi.imagetopdf.ui.importer.ImageSourceSheet;
import com.desperadoboi.imagetopdf.util.FileSizeFormatter;
import com.google.android.material.button.MaterialButton;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class EditorFragment extends Fragment {
    public static final String TAG = "EditorFragment";

    private static final String PDF_MIME_TYPE = "application/pdf";
    private static final float DRAG_ACTIVE_ALPHA = 0.85f;

    private DocumentSessionViewModel sessionViewModel;
    private NavigationCallback navigationCallback;
    private ActivityResultLauncher<PickVisualMediaRequest> addImagesLauncher;
    private ActivityResultLauncher<Uri> cameraLauncher;
    private ActivityResultLauncher<String[]> filesLauncher;
    private ActivityResultLauncher<String> createDocumentLauncher;
    private ImagePickerLauncher imagePickerLauncher;

    private TextView selectedImagesTextView;
    private TextView reorderHintTextView;
    private TextView operationStatusTextView;
    private View pdfResultLayout;
    private TextView pdfResultNameTextView;
    private TextView pdfResultDetailsTextView;
    private Button openPdfButton;
    private Button sharePdfButton;
    private Button newDocumentButton;
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
    private boolean fragmentDestroyed;

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
        addImagesLauncher = registerForActivityResult(
                new ActivityResultContracts.PickMultipleVisualMedia(),
                this::handleAdditionalImages
        );
        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicture(),
                this::handleCameraResult
        );
        filesLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenMultipleDocuments(),
                this::handleAdditionalFiles
        );
        createDocumentLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument(PDF_MIME_TYPE),
                this::handleCreateDocumentResult
        );
        imagePickerLauncher = new ImageImportCoordinator()
                .register(ImageImportSource.GALLERY, request -> launchAddImagesPicker())
                .register(ImageImportSource.CAMERA, request -> launchCamera())
                .register(ImageImportSource.FILES, request -> filesLauncher.launch(
                        new String[]{"image/*"}
                ));
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
        configureImageSourceResultListener();
        configurePageEditResultListener();
        configurePageList();
        configureClickListeners();
        pdfGenerationStateObserver = generationState -> {
            refreshSuccessfulPdfMetadata(generationState);
            updateUiState();
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
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        fragmentDestroyed = true;
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
        pdfResultNameTextView = view.findViewById(R.id.text_pdf_result_name);
        pdfResultDetailsTextView = view.findViewById(R.id.text_pdf_result_details);
        openPdfButton = view.findViewById(R.id.button_open_pdf);
        sharePdfButton = view.findViewById(R.id.button_share_pdf);
        newDocumentButton = view.findViewById(R.id.button_new_document);
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
                    public void onDragStart(RecyclerView.ViewHolder viewHolder) {
                        startPageDrag(viewHolder);
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
        addImagesButton.setOnClickListener(v -> showImageSourceSheet());
        createPdfButton.setOnClickListener(v -> launchCreateDocument());
        openPdfButton.setOnClickListener(v -> openLastPdf());
        sharePdfButton.setOnClickListener(v -> shareLastPdf());
        newDocumentButton.setOnClickListener(v -> createNewDocument());
        cancelGenerationButton.setOnClickListener(v -> cancelPdfGeneration());
    }

    private void showImageSourceSheet() {
        if (!sessionViewModel.canEditPages()) {
            return;
        }
        if (getParentFragmentManager().findFragmentByTag(ImageSourceSheet.TAG) != null) {
            return;
        }
        ImageSourceSheet.newInstance(ImageImportMode.APPEND_TO_DOCUMENT)
                .show(getParentFragmentManager(), ImageSourceSheet.TAG);
    }

    private void configureImageSourceResultListener() {
        getParentFragmentManager().setFragmentResultListener(
                ImageSourceSheet.RESULT_SOURCE_SELECTED,
                getViewLifecycleOwner(),
                (requestKey, result) -> imagePickerLauncher.launch(new ImageImportRequest(
                        ImageImportSource.valueOf(result.getString(ImageSourceSheet.RESULT_SOURCE)),
                        ImageImportMode.valueOf(result.getString(ImageSourceSheet.RESULT_MODE))
                ))
        );
    }

    private void launchAddImagesPicker() {
        if (!sessionViewModel.canEditPages()) {
            return;
        }
        PickVisualMediaRequest request = new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build();
        addImagesLauncher.launch(request);
    }

    private void handleAdditionalImages(List<Uri> imageUris) {
        handleImportedUris(
                ImageImportSource.GALLERY,
                ImageImportMode.APPEND_TO_DOCUMENT,
                imageUris
        );
    }

    private void handleAdditionalFiles(List<Uri> imageUris) {
        if (imageUris == null || imageUris.isEmpty()) {
            return;
        }
        for (Uri imageUri : imageUris) {
            takePersistableReadPermission(imageUri);
        }
        handleImportedUris(
                ImageImportSource.FILES,
                ImageImportMode.APPEND_TO_DOCUMENT,
                imageUris
        );
    }

    private void launchCreateDocument() {
        if (!sessionViewModel.hasPages() || !sessionViewModel.canEditPages()) {
            return;
        }
        sessionViewModel.setAwaitingSaveLocation(true);
        sessionViewModel.setTransientStatusMessage(null);
        sessionViewModel.setPendingSuggestedFileName(buildSuggestedFileName());
        updateUiState();
        createDocumentLauncher.launch(sessionViewModel.getPendingSuggestedFileName());
    }

    private void handleCreateDocumentResult(Uri outputUri) {
        sessionViewModel.setAwaitingSaveLocation(false);
        if (outputUri == null) {
            sessionViewModel.setPendingSuggestedFileName(null);
            sessionViewModel.setTransientStatusMessage(
                    getString(R.string.status_save_location_cancelled)
            );
            updateUiState();
            return;
        }
        startPdfGeneration(outputUri);
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

    private void launchCamera() {
        if (!sessionViewModel.canEditPages()) {
            return;
        }

        CapturedImageStorage.CapturedImage capturedImage;
        try {
            capturedImage = capturedImageStorage.createCapturedImage();
        } catch (IOException | RuntimeException exception) {
            showToast(getString(R.string.status_camera_destination_error));
            return;
        }

        sessionViewModel.setPendingCapturedImage(
                capturedImage.getUri(),
                capturedImage.getFileName(),
                DocumentSessionViewModel.CaptureTarget.EDITOR
        );
        try {
            cameraLauncher.launch(capturedImage.getUri());
        } catch (ActivityNotFoundException exception) {
            cleanupPendingCapture();
            showToast(getString(R.string.status_camera_app_not_found));
        } catch (RuntimeException exception) {
            cleanupPendingCapture();
            showToast(getString(R.string.status_camera_destination_error));
        }
    }

    private void handleCameraResult(Boolean isSuccessful) {
        DocumentSessionViewModel.PendingCapturedImage pendingCapturedImage =
                sessionViewModel.getPendingCapturedImage();
        if (pendingCapturedImage == null
                || pendingCapturedImage.getTarget() != DocumentSessionViewModel.CaptureTarget.EDITOR) {
            return;
        }

        pendingCapturedImage = sessionViewModel.clearPendingCapturedImage();
        if (!Boolean.TRUE.equals(isSuccessful)) {
            capturedImageStorage.delete(pendingCapturedImage.getCapturedFileName());
            return;
        }
        if (!capturedImageStorage.existsAndHasContent(pendingCapturedImage.getCapturedFileName())) {
            capturedImageStorage.delete(pendingCapturedImage.getCapturedFileName());
            showToast(getString(R.string.status_camera_empty_file));
            return;
        }

        ImageImportResult result = sessionViewModel.importCameraImage(
                new ImageImportRequest(
                        ImageImportSource.CAMERA,
                        ImageImportMode.APPEND_TO_DOCUMENT
                ),
                pendingCapturedImage.getUri(),
                pendingCapturedImage.getCapturedFileName()
        );
        finishAppendImport(result);
    }

    private void handleImportedUris(
            ImageImportSource source,
            ImageImportMode mode,
            List<Uri> imageUris
    ) {
        ImageImportResult result = sessionViewModel.importImages(
                new ImageImportRequest(source, mode),
                imageUris
        );
        finishAppendImport(result);
    }

    private void finishAppendImport(ImageImportResult result) {
        if (!result.hasChanges()) {
            return;
        }
        pageAdapter.notifyItemRangeInserted(
                result.getFirstInsertedPosition(),
                result.getInsertedCount()
        );
        pagesRecyclerView.scrollToPosition(result.getFirstInsertedPosition());
        updateUiState();
    }

    private void takePersistableReadPermission(Uri imageUri) {
        try {
            requireContext().getContentResolver().takePersistableUriPermission(
                    imageUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            );
        } catch (SecurityException exception) {
            // Some document providers only grant access for the current session.
        }
    }

    private void startPageDrag(RecyclerView.ViewHolder viewHolder) {
        if (sessionViewModel.canEditPages()) {
            pageTouchHelper.startDrag(viewHolder);
        }
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

    private void startPdfGeneration(Uri outputUri) {
        List<PageItem> pageSnapshot = sessionViewModel.getPagesSnapshot();
        PdfOptions pdfOptionsSnapshot = PdfOptions.defaults();
        String fallbackDisplayName = sessionViewModel.getPendingSuggestedFileName();
        int pageCount = pageSnapshot.size();
        DocumentSessionViewModel.GenerationOperation generationOperation =
                sessionViewModel.startGeneration(pageCount);
        if (generationOperation == null) {
            updateUiState();
            return;
        }
        updateUiState();

        PdfGenerator pdfGenerator = new PdfGenerator(
                requireContext().getApplicationContext().getContentResolver()
        );
        long operationId = generationOperation.getOperationId();
        DocumentSessionViewModel viewModel = sessionViewModel;
        pdfGenerator.generate(
                pageSnapshot,
                pdfOptionsSnapshot,
                outputUri,
                generationOperation.getCancellationToken(),
                sessionViewModel.getPdfExecutor(),
                ContextCompat.getMainExecutor(requireContext().getApplicationContext()),
                new ViewModelPdfGenerationCallback(
                        viewModel,
                        operationId,
                        fallbackDisplayName,
                        pageCount
                )
        );
    }

    private void cancelPdfGeneration() {
        sessionViewModel.requestCancelGeneration();
        updateUiState();
    }

    private void openLastPdf() {
        PdfResult lastPdfResult = sessionViewModel.getLastPdfResult();
        if (lastPdfResult == null || sessionViewModel.isGenerationInProgress()) {
            return;
        }
        if (!canReadPdfResult(lastPdfResult, R.string.status_pdf_open_error)) {
            return;
        }

        Intent viewIntent = new Intent(Intent.ACTION_VIEW);
        viewIntent.setDataAndType(lastPdfResult.getUri(), PDF_MIME_TYPE);
        grantReadPermission(viewIntent, lastPdfResult);
        try {
            startActivity(viewIntent);
        } catch (ActivityNotFoundException exception) {
            showToast(getString(R.string.status_pdf_open_app_not_found));
        } catch (SecurityException exception) {
            showToast(getString(R.string.status_pdf_open_error));
        }
    }

    private void shareLastPdf() {
        PdfResult lastPdfResult = sessionViewModel.getLastPdfResult();
        if (lastPdfResult == null || sessionViewModel.isGenerationInProgress()) {
            return;
        }
        if (!canReadPdfResult(lastPdfResult, R.string.status_pdf_share_error)) {
            return;
        }

        Intent sendIntent = new Intent(Intent.ACTION_SEND);
        sendIntent.setType(PDF_MIME_TYPE);
        sendIntent.putExtra(Intent.EXTRA_STREAM, lastPdfResult.getUri());
        grantReadPermission(sendIntent, lastPdfResult);

        Intent chooserIntent = Intent.createChooser(
                sendIntent,
                getString(R.string.pdf_share_chooser_title)
        );
        chooserIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        chooserIntent.setClipData(sendIntent.getClipData());
        try {
            startActivity(chooserIntent);
        } catch (ActivityNotFoundException | SecurityException exception) {
            showToast(getString(R.string.status_pdf_share_error));
        }
    }

    private boolean canReadPdfResult(PdfResult result, int errorStringResId) {
        try (InputStream inputStream = requireContext()
                .getContentResolver()
                .openInputStream(result.getUri())) {
            if (inputStream == null) {
                showToast(getString(errorStringResId));
                return false;
            }
            return true;
        } catch (IOException | SecurityException exception) {
            showToast(getString(errorStringResId));
            return false;
        }
    }

    private void grantReadPermission(Intent intent, PdfResult result) {
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setClipData(ClipData.newUri(
                requireContext().getContentResolver(),
                result.getDisplayName(),
                result.getUri()
        ));
    }

    private void createNewDocument() {
        if (!sessionViewModel.canEditPages()) {
            return;
        }
        deleteCapturedFiles(sessionViewModel.clearForNewDocument());
        if (navigationCallback != null) {
            navigationCallback.onReturnHomeRequested();
        }
    }

    private PdfResult buildPdfResult(Uri savedUri, String fallbackDisplayName, int pageCount) {
        String displayName = normalizeFallbackDisplayName(fallbackDisplayName);
        long sizeBytes = PdfResult.UNKNOWN_SIZE_BYTES;

        PdfMetadata metadata = queryPdfMetadata(savedUri);
        if (metadata.displayName != null && !metadata.displayName.trim().isEmpty()) {
            displayName = metadata.displayName.trim();
        }
        if (metadata.sizeBytes != PdfResult.UNKNOWN_SIZE_BYTES) {
            sizeBytes = metadata.sizeBytes;
        }

        return new PdfResult(savedUri, displayName, sizeBytes, pageCount);
    }

    private PdfMetadata queryPdfMetadata(Uri savedUri) {
        try (Cursor cursor = requireContext().getContentResolver().query(
                savedUri,
                new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE},
                null,
                null,
                null
        )) {
            if (cursor == null || !cursor.moveToFirst()) {
                return PdfMetadata.unknown();
            }

            String displayName = null;
            long sizeBytes = PdfResult.UNKNOWN_SIZE_BYTES;
            int displayNameColumnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            if (displayNameColumnIndex >= 0 && !cursor.isNull(displayNameColumnIndex)) {
                displayName = cursor.getString(displayNameColumnIndex);
            }

            int sizeColumnIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
            if (sizeColumnIndex >= 0 && !cursor.isNull(sizeColumnIndex)) {
                long cursorSize = cursor.getLong(sizeColumnIndex);
                if (cursorSize >= 0L) {
                    sizeBytes = cursorSize;
                }
            }
            return new PdfMetadata(displayName, sizeBytes);
        } catch (RuntimeException exception) {
            return PdfMetadata.unknown();
        }
    }

    private String normalizeFallbackDisplayName(String fallbackDisplayName) {
        if (fallbackDisplayName == null || fallbackDisplayName.trim().isEmpty()) {
            return getString(R.string.pdf_result_unknown_name);
        }
        return fallbackDisplayName.trim();
    }

    private String buildSuggestedFileName() {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
        String timestamp = formatter.format(new Date());
        return getString(R.string.pdf_file_name_template, timestamp);
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
        PdfResult lastPdfResult = sessionViewModel.getLastPdfResult();
        if (lastPdfResult == null) {
            pdfResultLayout.setVisibility(View.GONE);
            openPdfButton.setEnabled(false);
            sharePdfButton.setEnabled(false);
            newDocumentButton.setEnabled(false);
            return;
        }

        pdfResultLayout.setVisibility(View.VISIBLE);
        pdfResultNameTextView.setText(buildPdfResultNameText(lastPdfResult));
        pdfResultDetailsTextView.setText(buildPdfResultDetailsText(lastPdfResult));
        openPdfButton.setEnabled(controlsEnabled);
        sharePdfButton.setEnabled(controlsEnabled);
        newDocumentButton.setEnabled(controlsEnabled);
    }

    private String buildPdfResultNameText(PdfResult pdfResult) {
        if (pdfResult.getDisplayName().isEmpty()) {
            return getString(R.string.pdf_result_unknown_name);
        }
        return pdfResult.getDisplayName();
    }

    private String buildPdfResultDetailsText(PdfResult pdfResult) {
        String pagesText = getResources().getQuantityString(
                R.plurals.pdf_result_pages_count,
                pdfResult.getPageCount(),
                pdfResult.getPageCount()
        );
        if (!pdfResult.hasKnownSize()) {
            return pagesText;
        }
        String sizeText = FileSizeFormatter.format(pdfResult.getSizeBytes(), Locale.getDefault());
        return getString(R.string.pdf_result_details_with_size, pagesText, sizeText);
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
        if (generationState.isSucceeded()) {
            return getString(R.string.status_pdf_created);
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

    private void refreshSuccessfulPdfMetadata(PdfGenerationState generationState) {
        if (!generationState.isSucceeded()) {
            return;
        }
        PdfResult lastPdfResult = sessionViewModel.getLastPdfResult();
        if (lastPdfResult == null || lastPdfResult.hasKnownSize()) {
            return;
        }
        sessionViewModel.setLastPdfResult(
                buildPdfResult(
                        lastPdfResult.getUri(),
                        lastPdfResult.getDisplayName(),
                        lastPdfResult.getPageCount()
                )
        );
    }

    private void showToast(String message) {
        if (isUiSafe()) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
        }
    }

    private void cleanupPendingCapture() {
        DocumentSessionViewModel.PendingCapturedImage pendingCapturedImage =
                sessionViewModel.clearPendingCapturedImage();
        if (pendingCapturedImage != null) {
            capturedImageStorage.delete(pendingCapturedImage.getCapturedFileName());
        }
    }

    private void deleteCapturedFileIfNeeded(PageItem pageItem) {
        if (pageItem != null && pageItem.isAppOwnedCapture()) {
            capturedImageStorage.delete(pageItem.getCapturedFileName());
        }
    }

    private void deleteCapturedFiles(List<String> capturedFileNames) {
        for (String capturedFileName : capturedFileNames) {
            capturedImageStorage.delete(capturedFileName);
        }
    }

    private boolean isUiSafe() {
        return !fragmentDestroyed && isAdded();
    }

    public interface NavigationCallback {
        void onReturnHomeRequested();

        void onPageEditRequested(long pageId);
    }

    private static final class PdfMetadata {
        private final String displayName;
        private final long sizeBytes;

        private PdfMetadata(String displayName, long sizeBytes) {
            this.displayName = displayName;
            this.sizeBytes = sizeBytes;
        }

        private static PdfMetadata unknown() {
            return new PdfMetadata(null, PdfResult.UNKNOWN_SIZE_BYTES);
        }
    }

    private static final class ViewModelPdfGenerationCallback implements PdfGenerationCallback {
        private final DocumentSessionViewModel sessionViewModel;
        private final long operationId;
        private final String fallbackDisplayName;
        private final int pageCount;

        private ViewModelPdfGenerationCallback(
                DocumentSessionViewModel sessionViewModel,
                long operationId,
                String fallbackDisplayName,
                int pageCount
        ) {
            this.sessionViewModel = sessionViewModel;
            this.operationId = operationId;
            this.fallbackDisplayName = fallbackDisplayName;
            this.pageCount = pageCount;
        }

        @Override
        public void onProgress(int completedPages, int totalPages) {
            sessionViewModel.updateGenerationProgress(operationId, completedPages, totalPages);
        }

        @Override
        public void onSuccess(Uri savedUri) {
            sessionViewModel.completeGenerationSuccess(
                    operationId,
                    savedUri,
                    fallbackDisplayName,
                    pageCount
            );
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
            viewHolder.itemView.setElevation(0f);
            viewHolder.itemView.setActivated(false);
            viewHolder.itemView.setPressed(false);
        }
    }
}
