package com.desperadoboi.imagetopdf.ui.idcard;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.desperadoboi.imagetopdf.R;
import com.desperadoboi.imagetopdf.image.PreviewImageLoader;
import com.desperadoboi.imagetopdf.model.DocumentSessionViewModel;
import com.desperadoboi.imagetopdf.model.PageItem;
import com.desperadoboi.imagetopdf.model.PdfResult;
import com.desperadoboi.imagetopdf.pdf.IdCardPdfGenerator;
import com.desperadoboi.imagetopdf.pdf.PdfGenerationCallback;
import com.desperadoboi.imagetopdf.pdf.PdfLocationLabelResolver;
import com.desperadoboi.imagetopdf.pdf.PdfResultMetadataReader;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;

public final class IdCardScanFragment extends Fragment {
    public static final String TAG = "IdCardScanFragment";

    private static final String PDF_MIME_TYPE = "application/pdf";
    private static final String STATE_PICKER_SIDE = "id_card.picker_side";
    private static final String STATE_AWAITING_SAVE = "id_card.awaiting_save";

    private final EnumMap<IdCardSide, SideViews> sideViews = new EnumMap<>(IdCardSide.class);
    private final EnumMap<IdCardSide, Bitmap> previewBitmaps = new EnumMap<>(IdCardSide.class);
    private final EnumMap<IdCardSide, String> previewKeys = new EnumMap<>(IdCardSide.class);
    private IdCardScanViewModel viewModel;
    private DocumentSessionViewModel documentSessionViewModel;
    private NavigationCallback navigationCallback;
    private IdCardCacheStorage cacheStorage;
    private PreviewImageLoader previewLoader;
    private Executor mainExecutor;
    private ActivityResultLauncher<PickVisualMediaRequest> pickerLauncher;
    private ActivityResultLauncher<String> createDocumentLauncher;
    private IdCardScanViewModel.Observer observer;

    private IdCardSide pendingPickerSide;
    private boolean awaitingSaveLocation;
    private boolean renderingOptions;
    private boolean perspectiveNavigationPending;
    private MaterialButton swapButton;
    private MaterialButton exportButton;
    private MaterialButton cancelExportButton;
    private ProgressBar exportProgress;
    private TextView exportStatus;
    private RadioButton easyPreset;
    private RadioButton actualPreset;
    private MaterialSwitch watermarkSwitch;
    private TextInputLayout watermarkLayout;
    private TextInputEditText watermarkInput;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (!(context instanceof NavigationCallback)) {
            throw new IllegalStateException("Host activity must implement NavigationCallback");
        }
        navigationCallback = (NavigationCallback) context;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(IdCardScanViewModel.class);
        documentSessionViewModel = new ViewModelProvider(requireActivity())
                .get(DocumentSessionViewModel.class);
        cacheStorage = new IdCardCacheStorage(requireContext());
        mainExecutor = ContextCompat.getMainExecutor(requireContext());
        previewLoader = new PreviewImageLoader(
                requireContext().getApplicationContext().getContentResolver(),
                mainExecutor
        );
        if (savedInstanceState != null) {
            String sideName = savedInstanceState.getString(STATE_PICKER_SIDE);
            if (sideName != null) pendingPickerSide = IdCardSide.valueOf(sideName);
            awaitingSaveLocation = savedInstanceState.getBoolean(STATE_AWAITING_SAVE, false);
        }
        pickerLauncher = registerForActivityResult(
                new ActivityResultContracts.PickVisualMedia(),
                this::handlePickerResult
        );
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
        return inflater.inflate(R.layout.fragment_id_card_scan, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bindViews(view);
        configureSide(IdCardSide.FRONT, view.findViewById(R.id.card_id_front));
        configureSide(IdCardSide.BACK, view.findViewById(R.id.card_id_back));
        configureOptions(view);
        view.findViewById(R.id.button_id_card_back).setOnClickListener(ignored -> handleBackPressed());
        swapButton.setOnClickListener(ignored -> viewModel.swap());
        exportButton.setOnClickListener(ignored -> requestExport());
        cancelExportButton.setOnClickListener(ignored -> viewModel.requestCancelExport());
        int missing = viewModel.markMissingCacheFiles(cacheStorage::existsAndHasContent);
        if (missing > 0) {
            exportStatus.setText(R.string.id_card_error_file_unavailable);
            exportStatus.setVisibility(View.VISIBLE);
        }
        observer = this::render;
        viewModel.addObserver(observer);
        if (savedInstanceState != null) {
            view.postDelayed(this::recoverOrphanedMainOperation, 5000L);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        perspectiveNavigationPending = false;
        openPendingReviewIfNeeded();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        if (pendingPickerSide != null) outState.putString(STATE_PICKER_SIDE, pendingPickerSide.name());
        outState.putBoolean(STATE_AWAITING_SAVE, awaitingSaveLocation);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onDestroyView() {
        if (observer != null) {
            viewModel.removeObserver(observer);
            observer = null;
        }
        releasePreviews();
        sideViews.clear();
        swapButton = null;
        exportButton = null;
        cancelExportButton = null;
        exportProgress = null;
        exportStatus = null;
        easyPreset = null;
        actualPreset = null;
        watermarkSwitch = null;
        watermarkLayout = null;
        watermarkInput = null;
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        previewLoader.shutdown();
        super.onDestroy();
    }

    @Override
    public void onDetach() {
        navigationCallback = null;
        super.onDetach();
    }

    public void handleBackPressed() {
        if (viewModel.getExportState().isRunning() || awaitingSaveLocation) return;
        if (viewModel.getSession().collectCacheFileNames().isEmpty()) {
            closeSession();
            return;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.id_card_exit_title)
                .setMessage(R.string.id_card_exit_message)
                .setNegativeButton(R.string.id_card_exit_keep, null)
                .setPositiveButton(
                        R.string.id_card_exit_confirm,
                        (dialog, which) -> closeSession()
                )
                .show();
    }

    private void bindViews(View view) {
        swapButton = view.findViewById(R.id.button_id_card_swap);
        exportButton = view.findViewById(R.id.button_id_card_export);
        cancelExportButton = view.findViewById(R.id.button_id_card_cancel_export);
        exportProgress = view.findViewById(R.id.progress_id_card_export);
        exportStatus = view.findViewById(R.id.text_id_card_export_status);
        easyPreset = view.findViewById(R.id.radio_id_card_easy);
        actualPreset = view.findViewById(R.id.radio_id_card_actual);
        watermarkSwitch = view.findViewById(R.id.switch_id_card_watermark);
        watermarkLayout = view.findViewById(R.id.input_layout_id_card_watermark);
        watermarkInput = view.findViewById(R.id.input_id_card_watermark);
    }

    private void configureSide(IdCardSide side, View root) {
        SideViews views = new SideViews(root);
        sideViews.put(side, views);
        views.title.setText(side == IdCardSide.FRONT ? R.string.id_card_front : R.string.id_card_back);
        views.camera.setOnClickListener(ignored -> openCamera(side));
        views.gallery.setOnClickListener(ignored -> openGallery(side));
        views.replace.setOnClickListener(ignored -> openGallery(side));
        views.retake.setOnClickListener(ignored -> openCamera(side));
        views.rotate.setOnClickListener(ignored -> viewModel.rotate(side));
        views.correct.setOnClickListener(ignored -> openCorrection(side));
        views.delete.setOnClickListener(ignored -> deleteSide(side));
    }

    private void configureOptions(View view) {
        RadioGroup presetGroup = view.findViewById(R.id.radio_group_id_card_preset);
        presetGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (renderingOptions) return;
            viewModel.setExportPreset(checkedId == R.id.radio_id_card_actual
                    ? IdCardExportPreset.ACTUAL_SIZE
                    : IdCardExportPreset.EASY_TO_READ);
        });
        watermarkSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!renderingOptions) viewModel.setWatermarkEnabled(checked);
        });
        watermarkInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable editable) {
                if (renderingOptions) return;
                viewModel.setWatermarkText(editable == null ? "" : editable.toString());
                watermarkLayout.setError(editable == null || editable.toString().trim().isEmpty()
                        ? getString(R.string.id_card_watermark_empty_error)
                        : null);
            }
        });
    }

    private void openCamera(IdCardSide side) {
        if (!viewModel.startCapture(side) || navigationCallback == null) return;
        navigationCallback.onIdCardCameraRequested(side);
    }

    private void openGallery(IdCardSide side) {
        if (!viewModel.startGalleryImport(side)) return;
        pendingPickerSide = side;
        pickerLauncher.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build());
    }

    private void handlePickerResult(Uri uri) {
        IdCardSide side = pendingPickerSide;
        pendingPickerSide = null;
        if (side == null) return;
        if (uri == null) {
            deleteFiles(viewModel.cancelSideOperation(side));
            return;
        }
        viewModel.getExecutor().execute(() -> {
            IdCardCacheStorage.CacheImage copied = null;
            IdCardError error = IdCardError.NONE;
            try {
                copied = cacheStorage.copyFrom(uri);
            } catch (IOException | RuntimeException exception) {
                error = IdCardError.OPEN_IMAGE;
            } catch (OutOfMemoryError outOfMemoryError) {
                error = IdCardError.OUT_OF_MEMORY;
            }
            IdCardCacheStorage.CacheImage result = copied;
            IdCardError resultError = error;
            mainExecutor.execute(() -> {
                if (result == null) {
                    deleteFiles(viewModel.failSideOperation(side, resultError));
                    return;
                }
                if (!viewModel.setPendingImage(side, result.getUri(), result.getFileName())) {
                    cacheStorage.delete(result.getFileName());
                    return;
                }
                navigateToPerspective(side);
            });
        });
    }

    private void openCorrection(IdCardSide side) {
        if (!viewModel.openCorrection(side) || navigationCallback == null) return;
        navigateToPerspective(side);
    }

    private void openPendingReviewIfNeeded() {
        if (!isResumed() || perspectiveNavigationPending || navigationCallback == null) return;
        for (IdCardSide side : IdCardSide.values()) {
            if (viewModel.getReviewPage(side) != null) {
                navigateToPerspective(side);
                return;
            }
        }
    }

    private void navigateToPerspective(IdCardSide side) {
        if (!isAdded() || !isResumed() || perspectiveNavigationPending
                || navigationCallback == null) return;
        perspectiveNavigationPending = true;
        navigationCallback.onIdCardPerspectiveRequested(side);
    }

    private void recoverOrphanedMainOperation() {
        if (!isResumed() || pendingPickerSide != null) return;
        for (IdCardSide side : IdCardSide.values()) {
            IdCardSideRecord record = viewModel.getSession().get(side);
            if (record.isBusy() && record.getPendingImage() == null) {
                deleteFiles(viewModel.failSideOperation(
                        side,
                        IdCardError.OPERATION_CANCELLED
                ));
            }
        }
    }

    private void deleteSide(IdCardSide side) {
        deleteFiles(viewModel.deleteSide(side));
        SideViews views = sideViews.get(side);
        if (views != null) views.camera.post(views.camera::requestFocus);
    }

    private void render(
            IdCardScanSession session,
            IdCardExportOptions options,
            IdCardExportState state
    ) {
        if (exportButton == null) return;
        renderSide(session.get(IdCardSide.FRONT));
        renderSide(session.get(IdCardSide.BACK));
        focusCompletedSide(viewModel.consumePendingAccessibilityFocusSide(), session);
        swapButton.setVisibility(session.getReadyCount() == 2 ? View.VISIBLE : View.GONE);
        boolean controlsEnabled = !state.isRunning() && !awaitingSaveLocation;
        swapButton.setEnabled(controlsEnabled);
        exportButton.setEnabled(controlsEnabled && session.canExport() && options.isValid());
        renderOptions(options, controlsEnabled);
        renderExportState(state);
    }

    private void renderSide(IdCardSideRecord record) {
        SideViews views = sideViews.get(record.getSide());
        if (views == null) return;
        IdCardSideState state = record.getState();
        views.state.setText(stateText(state));
        views.root.setContentDescription(getString(
                R.string.id_card_slot_state_description,
                getString(record.getSide() == IdCardSide.FRONT
                        ? R.string.id_card_front : R.string.id_card_back),
                getString(stateText(state))
        ));
        boolean busy = record.isBusy();
        boolean ready = record.isReady();
        boolean error = state == IdCardSideState.ERROR;
        views.camera.setText(error
                ? R.string.id_card_action_retry
                : R.string.id_card_action_take_photo);
        views.progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        views.placeholder.setVisibility(ready || busy || error ? View.GONE : View.VISIBLE);
        views.error.setVisibility(error ? View.VISIBLE : View.GONE);
        if (error) views.error.setText(errorText(record.getError()));
        views.emptyActions.setVisibility(ready || busy ? View.GONE : View.VISIBLE);
        views.readyActions.setVisibility(ready || (error && record.getCurrentImage() != null)
                ? View.VISIBLE : View.GONE);
        views.replace.setVisibility(ready ? View.VISIBLE : View.GONE);
        views.retake.setVisibility(ready ? View.VISIBLE : View.GONE);
        views.rotate.setVisibility(ready ? View.VISIBLE : View.GONE);
        views.correct.setVisibility(ready ? View.VISIBLE : View.GONE);
        views.delete.setVisibility(ready || record.getCurrentImage() != null
                ? View.VISIBLE : View.GONE);
        setSideActionsEnabled(views, !busy && !viewModel.getExportState().isRunning(), ready);
        if (ready) loadPreview(record.getSide(), record.getCurrentImage(), views);
        else clearPreview(record.getSide(), views);

    }

    private void focusCompletedSide(IdCardSide side, IdCardScanSession session) {
        if (side == null) return;
        SideViews views = sideViews.get(side);
        if (views == null || !session.get(side).isReady()) return;
        views.root.post(() -> {
            if (sideViews.get(side) != views) return;
            views.root.requestFocus();
            views.root.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);
            if (side == IdCardSide.FRONT
                    && session.get(IdCardSide.BACK).getState() == IdCardSideState.EMPTY) {
                views.root.announceForAccessibility(getString(R.string.id_card_add_back_hint));
                if (exportStatus != null) {
                    exportStatus.setText(R.string.id_card_add_back_hint);
                    exportStatus.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    private void setSideActionsEnabled(SideViews views, boolean enabled, boolean ready) {
        views.camera.setEnabled(enabled);
        views.gallery.setEnabled(enabled);
        views.replace.setEnabled(enabled);
        views.retake.setEnabled(enabled);
        views.rotate.setEnabled(enabled && ready);
        views.correct.setEnabled(enabled && ready);
        views.delete.setEnabled(enabled);
    }

    private void loadPreview(IdCardSide side, IdCardImage image, SideViews views) {
        if (views.preview.getWidth() <= 0 || views.preview.getHeight() <= 0) {
            views.preview.post(() -> {
                IdCardSideRecord current = viewModel.getSession().get(side);
                if (current.isReady()) loadPreview(side, current.getCurrentImage(), views);
            });
            return;
        }
        PageItem page = image.toPageItem();
        String key = PreviewImageLoader.buildKey(
                page,
                views.preview.getWidth() * 2,
                views.preview.getHeight() * 2
        );
        if (key.equals(previewKeys.get(side)) && previewBitmaps.get(side) != null) {
            views.preview.setVisibility(View.VISIBLE);
            return;
        }
        previewKeys.put(side, key);
        views.progress.setVisibility(View.VISIBLE);
        previewLoader.load(
                page,
                views.preview.getWidth() * 2,
                views.preview.getHeight() * 2,
                new PreviewImageLoader.Callback() {
                    @Override
                    public void onLoaded(String loadedKey, Bitmap bitmap) {
                        if (!loadedKey.equals(previewKeys.get(side))
                                || sideViews.get(side) != views) {
                            recycle(bitmap);
                            return;
                        }
                        recycle(previewBitmaps.put(side, bitmap));
                        views.preview.setImageBitmap(bitmap);
                        views.preview.setVisibility(View.VISIBLE);
                        views.progress.setVisibility(View.GONE);
                    }

                    @Override
                    public void onError(String loadedKey) {
                        if (!loadedKey.equals(previewKeys.get(side))
                                || sideViews.get(side) != views) return;
                        viewModel.failSideOperation(side, IdCardError.OPEN_IMAGE);
                    }
                }
        );
    }

    private void clearPreview(IdCardSide side, SideViews views) {
        previewKeys.remove(side);
        recycle(previewBitmaps.remove(side));
        views.preview.setImageDrawable(null);
        views.preview.setVisibility(View.GONE);
    }

    private void renderOptions(IdCardExportOptions options, boolean enabled) {
        renderingOptions = true;
        easyPreset.setChecked(options.getPreset() == IdCardExportPreset.EASY_TO_READ);
        actualPreset.setChecked(options.getPreset() == IdCardExportPreset.ACTUAL_SIZE);
        watermarkSwitch.setChecked(options.isWatermarkEnabled());
        String currentText = watermarkInput.getText() == null
                ? "" : watermarkInput.getText().toString();
        if (!currentText.equals(options.getWatermarkText())) {
            watermarkInput.setText(options.getWatermarkText());
            watermarkInput.setSelection(options.getWatermarkText().length());
        }
        watermarkLayout.setVisibility(options.isWatermarkEnabled() ? View.VISIBLE : View.GONE);
        watermarkLayout.setError(options.isValid()
                ? null : getString(R.string.id_card_watermark_empty_error));
        easyPreset.setEnabled(enabled);
        actualPreset.setEnabled(enabled);
        watermarkSwitch.setEnabled(enabled);
        watermarkInput.setEnabled(enabled);
        renderingOptions = false;
    }

    private void renderExportState(IdCardExportState state) {
        boolean running = state.isRunning();
        exportProgress.setVisibility(running ? View.VISIBLE : View.GONE);
        cancelExportButton.setVisibility(running ? View.VISIBLE : View.GONE);
        if (running) {
            exportProgress.setMax(Math.max(1, state.getTotalSteps()));
            exportProgress.setProgress(state.getCompletedSteps());
            exportStatus.setText(getString(
                    R.string.id_card_export_progress,
                    state.getCompletedSteps(),
                    state.getTotalSteps()
            ));
            exportStatus.setVisibility(View.VISIBLE);
        } else if (state.getPhase() == IdCardExportState.Phase.ERROR
                || state.getPhase() == IdCardExportState.Phase.CANCELLED) {
            exportStatus.setText(errorText(state.getError()));
            exportStatus.setVisibility(View.VISIBLE);
        } else if (state.getPhase() == IdCardExportState.Phase.IDLE) {
            exportStatus.setVisibility(View.GONE);
        } else if (state.getPhase() == IdCardExportState.Phase.SUCCEEDED) {
            exportStatus.setVisibility(View.GONE);
        }
    }

    private void requestExport() {
        if (!viewModel.getSession().canExport() || awaitingSaveLocation) return;
        if (viewModel.getSession().getReadyCount() == 1) {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.id_card_one_side_title)
                    .setMessage(R.string.id_card_one_side_message)
                    .setNegativeButton(R.string.action_cancel, null)
                    .setPositiveButton(
                            R.string.id_card_one_side_confirm,
                            (dialog, which) -> launchCreateDocument()
                    )
                    .show();
            return;
        }
        launchCreateDocument();
    }

    private void launchCreateDocument() {
        awaitingSaveLocation = true;
        render(viewModel.getSession(), viewModel.getExportOptions(), viewModel.getExportState());
        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        createDocumentLauncher.launch(getString(R.string.id_card_file_name_template, date));
    }

    private void handleCreateDocumentResult(Uri outputUri) {
        awaitingSaveLocation = false;
        if (outputUri == null) {
            render(viewModel.getSession(), viewModel.getExportOptions(), viewModel.getExportState());
            exportStatus.setText(R.string.id_card_error_cancelled);
            exportStatus.setVisibility(View.VISIBLE);
            return;
        }
        startGeneration(outputUri);
    }

    private void startGeneration(Uri outputUri) {
        List<IdCardPdfGenerator.SideImage> images = new ArrayList<>(2);
        for (IdCardSide side : IdCardSide.values()) {
            IdCardSideRecord record = viewModel.getSession().get(side);
            if (record.isReady()) images.add(new IdCardPdfGenerator.SideImage(
                    side,
                    record.getCurrentImage()
            ));
        }
        IdCardScanViewModel.ExportOperation operation = viewModel.startExport();
        if (operation == null) return;
        Context applicationContext = requireContext().getApplicationContext();
        IdCardPdfGenerator generator = new IdCardPdfGenerator(applicationContext);
        PdfResultMetadataReader metadataReader = new PdfResultMetadataReader(
                applicationContext
        );
        long operationId = operation.getOperationId();
        String fallbackName = getString(
                R.string.id_card_file_name_template,
                new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date())
        );
        generator.generate(
                images,
                viewModel.getExportOptions(),
                outputUri,
                operation.getCancellationToken(),
                viewModel.getExecutor(),
                mainExecutor,
                new PdfGenerationCallback() {
                    @Override
                    public void onProgress(int completedPages, int totalPages) {
                        viewModel.updateExportProgress(operationId, completedPages, totalPages);
                    }

                    @Override
                    public void onSuccess(Uri savedUri, long sizeBytes) {
                        PdfResult initial = new PdfResult(
                                savedUri,
                                fallbackName,
                                sizeBytes,
                                1,
                                System.currentTimeMillis(),
                                PdfLocationLabelResolver.resolveLabel(
                                        applicationContext,
                                        savedUri
                                )
                        );
                        viewModel.getExecutor().execute(() -> {
                            PdfResult result = metadataReader.read(initial);
                            mainExecutor.execute(() -> completeSuccess(operationId, result));
                        });
                    }

                    @Override
                    public void onCancelled() {
                        viewModel.completeExportCancelled(operationId);
                    }

                    @Override
                    public void onError(Exception exception) {
                        viewModel.completeExportError(
                                operationId,
                                exception.getCause() instanceof OutOfMemoryError
                                        ? IdCardError.OUT_OF_MEMORY
                                        : exception instanceof IdCardPdfGenerator.PdfOutputException
                                        ? IdCardError.SAVE_PDF
                                        : IdCardError.CREATE_PDF
                        );
                    }
                }
        );
    }

    private void completeSuccess(long operationId, PdfResult result) {
        viewModel.completeExportSuccess(operationId);
        deleteFiles(viewModel.clearImagesAfterSuccessfulExport());
        documentSessionViewModel.publishExternalPdfResult(result);
        if (navigationCallback != null) navigationCallback.onIdCardPdfResultRequested();
    }

    private void closeSession() {
        deleteFiles(viewModel.startNewSession());
        if (navigationCallback != null) navigationCallback.onIdCardScanCancelled();
    }

    private int stateText(IdCardSideState state) {
        switch (state) {
            case CAPTURING: return R.string.id_card_state_capturing;
            case PROCESSING: return R.string.id_card_state_processing;
            case READY: return R.string.id_card_state_ready;
            case ERROR: return R.string.id_card_state_error;
            case EMPTY:
            default: return R.string.id_card_state_empty;
        }
    }

    private int errorText(IdCardError error) {
        switch (error) {
            case OPEN_IMAGE: return R.string.id_card_error_open_image;
            case PROCESS_IMAGE: return R.string.id_card_error_process_image;
            case CARD_NOT_FOUND: return R.string.id_card_error_card_not_found;
            case FILE_UNAVAILABLE: return R.string.id_card_error_file_unavailable;
            case OUT_OF_MEMORY: return R.string.id_card_error_out_of_memory;
            case CAMERA_PERMISSION_DENIED: return R.string.id_card_error_camera_permission;
            case OPERATION_CANCELLED: return R.string.id_card_error_cancelled;
            case CREATE_PDF: return R.string.id_card_error_create_pdf;
            case SAVE_PDF: return R.string.id_card_error_save_pdf;
            case NONE:
            default: return R.string.id_card_error_generic;
        }
    }

    private void deleteFiles(List<String> names) {
        for (String name : names) cacheStorage.delete(name);
    }

    private void releasePreviews() {
        for (Bitmap bitmap : previewBitmaps.values()) recycle(bitmap);
        previewBitmaps.clear();
        previewKeys.clear();
    }

    private static void recycle(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
    }

    public interface NavigationCallback {
        void onIdCardCameraRequested(IdCardSide side);
        void onIdCardPerspectiveRequested(IdCardSide side);
        void onIdCardScanCancelled();
        void onIdCardPdfResultRequested();
    }

    private static final class SideViews {
        private final View root;
        private final TextView title;
        private final TextView state;
        private final ImageView preview;
        private final ImageView placeholder;
        private final ProgressBar progress;
        private final TextView error;
        private final View emptyActions;
        private final View readyActions;
        private final MaterialButton camera;
        private final MaterialButton gallery;
        private final MaterialButton replace;
        private final MaterialButton retake;
        private final MaterialButton rotate;
        private final MaterialButton correct;
        private final MaterialButton delete;

        private SideViews(View root) {
            this.root = root;
            title = root.findViewById(R.id.text_id_card_side_title);
            state = root.findViewById(R.id.text_id_card_side_state);
            preview = root.findViewById(R.id.image_id_card_preview);
            placeholder = root.findViewById(R.id.image_id_card_placeholder);
            progress = root.findViewById(R.id.progress_id_card_side);
            error = root.findViewById(R.id.text_id_card_side_error);
            emptyActions = root.findViewById(R.id.layout_id_card_empty_actions);
            readyActions = root.findViewById(R.id.layout_id_card_ready_actions);
            camera = root.findViewById(R.id.button_id_card_camera);
            gallery = root.findViewById(R.id.button_id_card_gallery);
            replace = root.findViewById(R.id.button_id_card_replace);
            retake = root.findViewById(R.id.button_id_card_retake);
            rotate = root.findViewById(R.id.button_id_card_rotate);
            correct = root.findViewById(R.id.button_id_card_correct);
            delete = root.findViewById(R.id.button_id_card_delete);
        }
    }
}
