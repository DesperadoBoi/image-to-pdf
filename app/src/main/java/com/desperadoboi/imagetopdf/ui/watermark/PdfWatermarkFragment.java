package com.desperadoboi.imagetopdf.ui.watermark;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.desperadoboi.imagetopdf.R;
import com.desperadoboi.imagetopdf.document.TemporaryDocumentStore;
import com.desperadoboi.imagetopdf.document.pdf.PdfDocumentRenderer;
import com.desperadoboi.imagetopdf.model.DocumentSessionViewModel;
import com.desperadoboi.imagetopdf.model.PdfResult;
import com.desperadoboi.imagetopdf.pdf.PdfLocationLabelResolver;
import com.desperadoboi.imagetopdf.pdf.PdfResultMetadataReader;
import com.desperadoboi.imagetopdf.pdf.PdfWatermarkGenerator;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.slider.Slider;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

public final class PdfWatermarkFragment extends Fragment {
    public static final String TAG = "PdfWatermarkFragment";
    private static final String PDF_MIME_TYPE = "application/pdf";

    private NavigationCallback navigationCallback;
    private PdfWatermarkViewModel viewModel;
    private DocumentSessionViewModel documentSessionViewModel;
    private TemporaryDocumentStore documentStore;
    private PdfWatermarkGenerator generator;
    private Executor mainExecutor;
    private ActivityResultLauncher<String[]> openDocumentLauncher;
    private ActivityResultLauncher<String> createDocumentLauncher;
    private final PdfWatermarkViewModel.Observer stateObserver = this::renderState;

    private View root;
    private NestedScrollView scroll;
    private View sourceState;
    private View controlsRoot;
    private ImageButton replaceButton;
    private ProgressBar sourceProgress;
    private TextView sourceTitle;
    private TextView sourceMessage;
    private MaterialButton chooseSourceButton;
    private TextView fileName;
    private TextView filePages;
    private MaterialCardView previewCard;
    private ImageView previewImage;
    private WatermarkOverlayView previewOverlay;
    private ProgressBar previewProgress;
    private TextView previewError;
    private MaterialButton retryPreviewButton;
    private ImageButton previousButton;
    private ImageButton nextButton;
    private TextView pageCounter;
    private TextInputLayout textLayout;
    private TextInputEditText textInput;
    private ChipGroup presetGroup;
    private Chip copyChip;
    private Chip draftChip;
    private Chip confidentialChip;
    private MaterialButtonToggleGroup styleGroup;
    private Slider opacitySlider;
    private Slider sizeSlider;
    private Slider rotationSlider;
    private TextView opacityLabel;
    private TextView sizeLabel;
    private TextView rotationLabel;
    private ChipGroup colorGroup;
    private View positionSection;
    private final Map<WatermarkPosition, MaterialButton> positionButtons =
            new EnumMap<>(WatermarkPosition.class);
    private ChipGroup pagesGroup;
    private TextInputLayout rangeLayout;
    private TextInputEditText rangeInput;
    private TextView exportStatus;
    private ProgressBar exportProgress;
    private MaterialButton cancelExportButton;
    private MaterialButton createButton;

    private PdfDocumentRenderer renderer;
    private String rendererSource;
    private boolean rendererReady;
    private boolean bindingState;
    private boolean autoPickerRequested;
    private int scrollBasePaddingBottom;
    private int renderedPage = -1;
    private int renderedPageWidth;
    private int renderedPageHeight;

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
        viewModel = new ViewModelProvider(requireActivity()).get(PdfWatermarkViewModel.class);
        documentSessionViewModel = new ViewModelProvider(requireActivity())
                .get(DocumentSessionViewModel.class);
        documentStore = new TemporaryDocumentStore(requireContext());
        generator = new PdfWatermarkGenerator(requireContext());
        mainExecutor = ContextCompat.getMainExecutor(requireContext());
        openDocumentLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                this::handleSourcePickerResult
        );
        createDocumentLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument(PDF_MIME_TYPE),
                this::handleSaveLocationResult
        );
        viewModel.configureLocalizedDefault(getString(R.string.pdf_watermark_default_text));
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.fragment_pdf_watermark, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        root = view;
        bindViews(view);
        configureControls();
        viewModel.addObserver(stateObserver);
        if (viewModel.getSourceStatus() == PdfWatermarkViewModel.SourceStatus.EMPTY
                && !autoPickerRequested) {
            autoPickerRequested = true;
            view.post(this::launchSourcePicker);
        }
    }

    @Override
    public void onDestroyView() {
        viewModel.removeObserver(stateObserver);
        closeRenderer();
        root = null;
        super.onDestroyView();
    }

    @Override
    public void onDetach() {
        navigationCallback = null;
        super.onDetach();
    }

    public void handleBackPressed() {
        if (viewModel.getExportState().isBusy()) {
            cancelExport();
            return;
        }
        closeSession();
    }

    private void bindViews(View view) {
        scroll = view.findViewById(R.id.scroll_pdf_watermark);
        scrollBasePaddingBottom = scroll.getPaddingBottom();
        sourceState = view.findViewById(R.id.state_pdf_watermark_source);
        controlsRoot = view.findViewById(R.id.content_pdf_watermark_controls);
        replaceButton = view.findViewById(R.id.button_pdf_watermark_replace);
        sourceProgress = view.findViewById(R.id.progress_pdf_watermark_source);
        sourceTitle = view.findViewById(R.id.text_pdf_watermark_source_title);
        sourceMessage = view.findViewById(R.id.text_pdf_watermark_source_message);
        chooseSourceButton = view.findViewById(R.id.button_pdf_watermark_choose_source);
        fileName = view.findViewById(R.id.text_pdf_watermark_file_name);
        filePages = view.findViewById(R.id.text_pdf_watermark_file_pages);
        previewCard = view.findViewById(R.id.card_pdf_watermark_preview);
        previewImage = view.findViewById(R.id.image_pdf_watermark_preview);
        previewOverlay = view.findViewById(R.id.overlay_pdf_watermark_preview);
        previewProgress = view.findViewById(R.id.progress_pdf_watermark_preview);
        previewError = view.findViewById(R.id.text_pdf_watermark_preview_error);
        retryPreviewButton = view.findViewById(R.id.button_pdf_watermark_retry_preview);
        previousButton = view.findViewById(R.id.button_pdf_watermark_previous);
        nextButton = view.findViewById(R.id.button_pdf_watermark_next);
        pageCounter = view.findViewById(R.id.text_pdf_watermark_page_counter);
        textLayout = view.findViewById(R.id.input_layout_pdf_watermark_text);
        textInput = view.findViewById(R.id.input_pdf_watermark_text);
        presetGroup = view.findViewById(R.id.group_pdf_watermark_presets);
        copyChip = view.findViewById(R.id.chip_pdf_watermark_copy);
        draftChip = view.findViewById(R.id.chip_pdf_watermark_draft);
        confidentialChip = view.findViewById(R.id.chip_pdf_watermark_confidential);
        styleGroup = view.findViewById(R.id.group_pdf_watermark_style);
        opacitySlider = view.findViewById(R.id.slider_pdf_watermark_opacity);
        sizeSlider = view.findViewById(R.id.slider_pdf_watermark_size);
        rotationSlider = view.findViewById(R.id.slider_pdf_watermark_rotation);
        opacityLabel = view.findViewById(R.id.text_pdf_watermark_opacity);
        sizeLabel = view.findViewById(R.id.text_pdf_watermark_size);
        rotationLabel = view.findViewById(R.id.text_pdf_watermark_rotation);
        colorGroup = view.findViewById(R.id.group_pdf_watermark_colors);
        positionSection = view.findViewById(R.id.section_pdf_watermark_position);
        positionButtons.put(WatermarkPosition.TOP_LEFT,
                view.findViewById(R.id.button_position_top_left));
        positionButtons.put(WatermarkPosition.TOP_CENTER,
                view.findViewById(R.id.button_position_top_center));
        positionButtons.put(WatermarkPosition.TOP_RIGHT,
                view.findViewById(R.id.button_position_top_right));
        positionButtons.put(WatermarkPosition.CENTER_LEFT,
                view.findViewById(R.id.button_position_center_left));
        positionButtons.put(WatermarkPosition.CENTER,
                view.findViewById(R.id.button_position_center));
        positionButtons.put(WatermarkPosition.CENTER_RIGHT,
                view.findViewById(R.id.button_position_center_right));
        positionButtons.put(WatermarkPosition.BOTTOM_LEFT,
                view.findViewById(R.id.button_position_bottom_left));
        positionButtons.put(WatermarkPosition.BOTTOM_CENTER,
                view.findViewById(R.id.button_position_bottom_center));
        positionButtons.put(WatermarkPosition.BOTTOM_RIGHT,
                view.findViewById(R.id.button_position_bottom_right));
        pagesGroup = view.findViewById(R.id.group_pdf_watermark_pages);
        rangeLayout = view.findViewById(R.id.input_layout_pdf_watermark_range);
        rangeInput = view.findViewById(R.id.input_pdf_watermark_range);
        exportStatus = view.findViewById(R.id.text_pdf_watermark_export_status);
        exportProgress = view.findViewById(R.id.progress_pdf_watermark_export);
        cancelExportButton = view.findViewById(R.id.button_pdf_watermark_cancel_export);
        createButton = view.findViewById(R.id.button_pdf_watermark_create);
    }

    private void configureControls() {
        root.findViewById(R.id.button_pdf_watermark_back).setOnClickListener(
                ignored -> handleBackPressed()
        );
        replaceButton.setOnClickListener(ignored -> launchSourcePicker());
        chooseSourceButton.setOnClickListener(ignored -> launchSourcePicker());
        previousButton.setOnClickListener(
                ignored -> viewModel.setCurrentPage(viewModel.getCurrentPage() - 1)
        );
        nextButton.setOnClickListener(
                ignored -> viewModel.setCurrentPage(viewModel.getCurrentPage() + 1)
        );
        retryPreviewButton.setOnClickListener(ignored -> renderPreview());
        textInput.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                if (!bindingState) viewModel.setText(editable == null ? "" : editable.toString());
            }
        });
        textInput.setOnFocusChangeListener((ignored, hasFocus) -> {
            if (hasFocus) requestFocusedFieldVisibility(textInput);
        });
        configurePreset(copyChip);
        configurePreset(draftChip);
        configurePreset(confidentialChip);
        styleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!bindingState && isChecked) {
                viewModel.setStyle(checkedId == R.id.button_pdf_watermark_tiled
                        ? WatermarkStyle.TILED : WatermarkStyle.SINGLE);
            }
        });
        opacitySlider.addOnChangeListener((slider, value, fromUser) -> {
            if (!bindingState) viewModel.setOpacity(value / 100f);
        });
        sizeSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (!bindingState) viewModel.setSizePoints(value);
        });
        rotationSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (!bindingState) viewModel.setRotationDegrees(value);
        });
        opacitySlider.setLabelFormatter(value -> Math.round(value) + "%");
        sizeSlider.setLabelFormatter(value -> Integer.toString(Math.round(value)));
        rotationSlider.setLabelFormatter(value -> String.format(
                Locale.getDefault(), "%+d°", Math.round(value)
        ));
        configureColorChip(R.id.chip_pdf_watermark_gray, WatermarkColor.GRAY);
        configureColorChip(R.id.chip_pdf_watermark_red, WatermarkColor.RED);
        configureColorChip(R.id.chip_pdf_watermark_blue, WatermarkColor.BLUE);
        for (Map.Entry<WatermarkPosition, MaterialButton> entry : positionButtons.entrySet()) {
            WatermarkPosition position = entry.getKey();
            entry.getValue().setOnClickListener(ignored -> viewModel.setPosition(position));
        }
        pagesGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (bindingState || checkedIds.isEmpty()) return;
            int checkedId = checkedIds.get(0);
            if (checkedId == R.id.chip_pdf_watermark_current_page) {
                viewModel.setPageSelection(WatermarkPageSelection.CURRENT);
            } else if (checkedId == R.id.chip_pdf_watermark_range) {
                viewModel.setPageSelection(WatermarkPageSelection.RANGE);
            } else {
                viewModel.setPageSelection(WatermarkPageSelection.ALL);
            }
        });
        rangeInput.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                if (!bindingState) {
                    viewModel.setRangeInput(editable == null ? "" : editable.toString());
                }
            }
        });
        rangeInput.setOnFocusChangeListener((ignored, hasFocus) -> {
            if (hasFocus) {
                requestFocusedFieldVisibility(rangeInput);
                return;
            }
            if (bindingState) return;
            PageRangeParser.Result result = viewModel.getRangeResult();
            if (result.isValid() && !result.normalized().equals(viewModel.getRangeInput())) {
                viewModel.setRangeInput(result.normalized());
            }
        });
        ViewCompat.setOnApplyWindowInsetsListener(scroll, (view, insets) -> {
            boolean imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime());
            int imeBottom = imeVisible
                    ? insets.getInsets(WindowInsetsCompat.Type.ime()).bottom : 0;
            int desiredBottomPadding = scrollBasePaddingBottom + imeBottom;
            if (scroll.getPaddingBottom() != desiredBottomPadding) {
                scroll.setPadding(
                        scroll.getPaddingLeft(),
                        scroll.getPaddingTop(),
                        scroll.getPaddingRight(),
                        desiredBottomPadding
                );
            }
            if (imeVisible && root != null) {
                View focused = root.findFocus();
                if (focused == textInput || focused == rangeInput) {
                    focused.post(() -> requestFocusedFieldVisibility(focused));
                }
            }
            return insets;
        });
        cancelExportButton.setOnClickListener(ignored -> cancelExport());
        createButton.setOnClickListener(ignored -> startExport());
    }

    private void requestFocusedFieldVisibility(View field) {
        if (root == null || !field.hasFocus()) return;
        scroll.post(() -> {
            if (root == null || !field.hasFocus()) return;
            int margin = getResources().getDimensionPixelSize(R.dimen.content_spacing_medium);
            int[] scrollLocation = new int[2];
            int[] fieldLocation = new int[2];
            int[] rootLocation = new int[2];
            scroll.getLocationOnScreen(scrollLocation);
            field.getLocationOnScreen(fieldLocation);
            scroll.getRootView().getLocationOnScreen(rootLocation);

            int visibleTop = scrollLocation[1] + scroll.getPaddingTop();
            int visibleBottom = rootLocation[1] + scroll.getRootView().getHeight();
            WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(scroll);
            if (insets != null && insets.isVisible(WindowInsetsCompat.Type.ime())) {
                visibleBottom -= insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            }

            int scrollDelta = 0;
            int fieldBottom = fieldLocation[1] + field.getHeight();
            if (fieldBottom + margin > visibleBottom) {
                scrollDelta = fieldBottom + margin - visibleBottom;
            } else if (fieldLocation[1] - margin < visibleTop) {
                scrollDelta = fieldLocation[1] - margin - visibleTop;
            }
            if (scrollDelta != 0) scroll.smoothScrollBy(0, scrollDelta);
        });
    }

    private void configurePreset(Chip chip) {
        chip.setOnClickListener(ignored -> viewModel.setText(chip.getText().toString()));
    }

    private void configureColorChip(int id, WatermarkColor color) {
        Chip chip = root.findViewById(id);
        chip.setChipIconTint(ColorStateList.valueOf(color.getArgb()));
        chip.setOnClickListener(ignored -> viewModel.setColor(color));
    }

    private void renderState() {
        if (root == null) return;
        PdfWatermarkViewModel.SourceStatus sourceStatus = viewModel.getSourceStatus();
        boolean ready = sourceStatus == PdfWatermarkViewModel.SourceStatus.READY;
        boolean loading = sourceStatus == PdfWatermarkViewModel.SourceStatus.LOADING;
        scroll.setVisibility(ready ? View.VISIBLE : View.GONE);
        sourceState.setVisibility(ready ? View.GONE : View.VISIBLE);
        replaceButton.setVisibility(ready ? View.VISIBLE : View.GONE);
        sourceProgress.setVisibility(loading ? View.VISIBLE : View.GONE);
        sourceTitle.setVisibility(loading ? View.GONE : View.VISIBLE);
        sourceMessage.setVisibility(loading ? View.GONE : View.VISIBLE);
        chooseSourceButton.setVisibility(loading ? View.GONE : View.VISIBLE);
        if (!ready) renderSourceState(sourceStatus);
        if (ready) {
            renderReadyState();
            ensureRenderer();
        }
        if (viewModel.consumeSaveLocationRequest()) launchSaveLocationPicker();
        if (viewModel.consumeResultNavigation() && navigationCallback != null) {
            navigationCallback.onPdfWatermarkResultRequested();
        }
    }

    private void renderSourceState(PdfWatermarkViewModel.SourceStatus status) {
        if (status == PdfWatermarkViewModel.SourceStatus.LOADING) return;
        if (status == PdfWatermarkViewModel.SourceStatus.ERROR) {
            sourceTitle.setText(R.string.pdf_watermark_source_prompt_title);
            sourceMessage.setText(errorText(viewModel.getSourceError()));
            chooseSourceButton.setText(R.string.pdf_watermark_retry);
        } else {
            sourceTitle.setText(R.string.pdf_watermark_source_prompt_title);
            sourceMessage.setText(R.string.pdf_watermark_source_prompt_message);
            chooseSourceButton.setText(R.string.pdf_watermark_choose_pdf);
        }
    }

    private void renderReadyState() {
        PdfWatermarkOptions options = viewModel.getOptions();
        PdfWatermarkExportState exportState = viewModel.getExportState();
        int currentPage = viewModel.getCurrentPage();
        int pageCount = viewModel.getPageCount();
        bindingState = true;
        fileName.setText(viewModel.getSourceDisplayName());
        filePages.setText(getResources().getQuantityString(
                R.plurals.pdf_watermark_page_count, pageCount, pageCount
        ));
        pageCounter.setText(getString(
                R.string.pdf_watermark_page_counter, currentPage + 1, pageCount
        ));
        previousButton.setContentDescription(getString(
                R.string.pdf_watermark_previous_page_content_description,
                Math.max(1, currentPage)
        ));
        nextButton.setContentDescription(getString(
                R.string.pdf_watermark_next_page_content_description,
                Math.min(pageCount, currentPage + 2)
        ));
        setTextIfDifferent(textInput, options.getText());
        textLayout.setError(options.hasValidText()
                ? null : getString(R.string.pdf_watermark_text_empty_error));
        selectMatchingPreset(options.getText());
        styleGroup.check(options.getStyle() == WatermarkStyle.TILED
                ? R.id.button_pdf_watermark_tiled : R.id.button_pdf_watermark_single);
        setSliderValue(opacitySlider, options.getOpacity() * 100f);
        setSliderValue(sizeSlider, options.getSizePoints());
        setSliderValue(rotationSlider, options.getRotationDegrees());
        int opacity = Math.round(options.getOpacity() * 100f);
        int size = Math.round(options.getSizePoints());
        int rotation = Math.round(options.getRotationDegrees());
        opacityLabel.setText(getString(R.string.pdf_watermark_opacity_value, opacity));
        sizeLabel.setText(getString(R.string.pdf_watermark_size_value, size));
        rotationLabel.setText(getString(R.string.pdf_watermark_rotation_value, rotation));
        opacitySlider.setContentDescription(opacityLabel.getText());
        sizeSlider.setContentDescription(sizeLabel.getText());
        rotationSlider.setContentDescription(rotationLabel.getText());
        colorGroup.check(colorChipId(options.getColor()));
        renderPosition(options);
        pagesGroup.check(pageSelectionChipId(viewModel.getPageSelection()));
        boolean rangeVisible = viewModel.getPageSelection() == WatermarkPageSelection.RANGE;
        rangeLayout.setVisibility(rangeVisible ? View.VISIBLE : View.GONE);
        setTextIfDifferent(rangeInput, viewModel.getRangeInput());
        PageRangeParser.Result rangeResult = viewModel.getRangeResult();
        rangeLayout.setError(rangeVisible && !rangeResult.isValid()
                ? getString(R.string.pdf_watermark_range_error) : null);
        renderExport(exportState);
        bindingState = false;

        boolean busy = exportState.isBusy();
        setEnabledRecursively(controlsRoot, !busy);
        replaceButton.setEnabled(!busy);
        cancelExportButton.setEnabled(busy && !exportState.isCancellationRequested());
        previousButton.setEnabled(!busy && currentPage > 0);
        nextButton.setEnabled(!busy && currentPage + 1 < pageCount);
        createButton.setEnabled(viewModel.canCreatePdf());
        previewOverlay.setOptions(shouldShowWatermarkOnCurrentPage()
                ? options : null);
        updatePreviewContentDescription();
    }

    private void renderPosition(PdfWatermarkOptions options) {
        boolean visible = options.getStyle() == WatermarkStyle.SINGLE;
        positionSection.setVisibility(visible ? View.VISIBLE : View.GONE);
        for (Map.Entry<WatermarkPosition, MaterialButton> entry : positionButtons.entrySet()) {
            boolean selected = entry.getKey() == options.getPosition();
            entry.getValue().setChecked(selected);
            ViewCompat.setStateDescription(entry.getValue(), getString(selected
                    ? R.string.pdf_watermark_selected_state
                    : R.string.pdf_watermark_not_selected_state));
        }
    }

    private void renderExport(PdfWatermarkExportState state) {
        boolean busy = state.isBusy();
        boolean showStatus = busy
                || state.getPhase() == PdfWatermarkExportState.Phase.ERROR
                || state.getPhase() == PdfWatermarkExportState.Phase.CANCELLED;
        exportStatus.setVisibility(showStatus ? View.VISIBLE : View.GONE);
        exportProgress.setVisibility(busy ? View.VISIBLE : View.GONE);
        cancelExportButton.setVisibility(busy ? View.VISIBLE : View.GONE);
        exportProgress.setMax(Math.max(1, state.getTotalPages()));
        exportProgress.setProgress(state.getCompletedPages());
        switch (state.getPhase()) {
            case GENERATING:
                exportStatus.setText(getString(
                        R.string.pdf_watermark_export_generating,
                        state.getCompletedPages(), state.getTotalPages()
                ));
                break;
            case AWAITING_DESTINATION:
                exportStatus.setText(R.string.pdf_watermark_export_waiting);
                break;
            case SAVING:
                exportStatus.setText(R.string.pdf_watermark_export_saving);
                exportProgress.setIndeterminate(true);
                break;
            case ERROR:
            case CANCELLED:
                exportStatus.setText(errorText(state.getError()));
                break;
            default:
                break;
        }
        if (state.getPhase() != PdfWatermarkExportState.Phase.SAVING) {
            exportProgress.setIndeterminate(false);
        }
        int targetCount = viewModel.getTargetPages().size();
        createButton.setText(getResources().getQuantityString(
                R.plurals.pdf_watermark_create_page_count,
                Math.max(1, targetCount), Math.max(0, targetCount)
        ));
    }

    private void selectMatchingPreset(String text) {
        int id = View.NO_ID;
        if (copyChip.getText().toString().equals(text)) id = copyChip.getId();
        else if (draftChip.getText().toString().equals(text)) id = draftChip.getId();
        else if (confidentialChip.getText().toString().equals(text)) {
            id = confidentialChip.getId();
        }
        if (id == View.NO_ID) presetGroup.clearCheck();
        else presetGroup.check(id);
    }

    private void ensureRenderer() {
        String source = viewModel.getSourceCacheFileName();
        if (source == null) {
            viewModel.markSourceUnavailable();
            return;
        }
        if (renderer != null && source.equals(rendererSource)) {
            if (rendererReady && renderedPage != viewModel.getCurrentPage()) renderPreview();
            return;
        }
        closeRenderer();
        File sourceFile = documentStore.resolveOwnedFile(source);
        if (sourceFile == null) {
            viewModel.markSourceUnavailable();
            return;
        }
        rendererSource = source;
        renderer = new PdfDocumentRenderer(mainExecutor);
        PdfDocumentRenderer openingRenderer = renderer;
        renderer.open(sourceFile, new PdfDocumentRenderer.OpenCallback() {
            @Override
            public void onOpened(int pageCount) {
                if (renderer != openingRenderer || !source.equals(rendererSource)) return;
                if (pageCount != viewModel.getPageCount()) {
                    viewModel.markSourceUnavailable();
                    return;
                }
                rendererReady = true;
                renderPreview();
            }

            @Override
            public void onError(Exception exception) {
                if (renderer == openingRenderer) {
                    viewModel.markPreviewError(mapThrowable(exception, false));
                }
            }
        });
    }

    private void renderPreview() {
        if (!rendererReady || renderer == null || root == null) return;
        int width = previewCard.getWidth();
        int height = previewCard.getHeight();
        if (width <= 0 || height <= 0) {
            previewCard.post(this::renderPreview);
            return;
        }
        int requestedPage = viewModel.getCurrentPage();
        renderedPage = -1;
        renderedPageWidth = 0;
        renderedPageHeight = 0;
        previewImage.setImageDrawable(null);
        previewOverlay.setPageGeometry(0, 0);
        previewProgress.setVisibility(View.VISIBLE);
        previewError.setVisibility(View.GONE);
        retryPreviewButton.setVisibility(View.GONE);
        PdfDocumentRenderer activeRenderer = renderer;
        activeRenderer.renderPageDetailed(
                requestedPage,
                width,
                height,
                new PdfDocumentRenderer.DetailedRenderCallback() {
                    @Override
                    public void onRendered(PdfDocumentRenderer.RenderedPage page) {
                        if (root == null || renderer != activeRenderer
                                || viewModel.getCurrentPage() != page.getPageIndex()) return;
                        renderedPage = page.getPageIndex();
                        renderedPageWidth = page.getPageWidthPoints();
                        renderedPageHeight = page.getPageHeightPoints();
                        Bitmap bitmap = page.getBitmap();
                        previewImage.setImageBitmap(bitmap);
                        previewOverlay.setPageGeometry(renderedPageWidth, renderedPageHeight);
                        previewOverlay.setOptions(shouldShowWatermarkOnCurrentPage()
                                ? viewModel.getOptions() : null);
                        previewProgress.setVisibility(View.GONE);
                        previewError.setVisibility(View.GONE);
                        retryPreviewButton.setVisibility(View.GONE);
                        updatePreviewContentDescription();
                        viewModel.clearPreviewError();
                    }

                    @Override
                    public void onError(Exception exception) {
                        if (root == null || renderer != activeRenderer
                                || viewModel.getCurrentPage() != requestedPage) return;
                        previewProgress.setVisibility(View.GONE);
                        previewError.setVisibility(View.VISIBLE);
                        retryPreviewButton.setVisibility(View.VISIBLE);
                        viewModel.markPreviewError(mapThrowable(exception, false));
                    }
                }
        );
    }

    private boolean shouldShowWatermarkOnCurrentPage() {
        Set<Integer> targets = viewModel.getTargetPages();
        return targets.contains(viewModel.getCurrentPage());
    }

    private void updatePreviewContentDescription() {
        if (renderedPage < 0 || previewCard == null) return;
        previewCard.setContentDescription(getString(
                shouldShowWatermarkOnCurrentPage()
                        ? R.string.pdf_watermark_preview_content_description
                        : R.string.pdf_watermark_preview_without_content_description,
                renderedPage + 1,
                viewModel.getPageCount()
        ));
    }

    private void launchSourcePicker() {
        if (!isAdded() || viewModel.getExportState().isBusy()) return;
        openDocumentLauncher.launch(new String[]{PDF_MIME_TYPE});
    }

    private void handleSourcePickerResult(Uri uri) {
        if (uri == null) {
            viewModel.handlePickerCancelled();
            return;
        }
        try {
            requireContext().getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            );
        } catch (SecurityException | UnsupportedOperationException ignored) {
            // The picker grant remains sufficient while the PDF is copied into app cache.
        }
        if (!viewModel.beginSourceLoad()) return;
        Context applicationContext = requireContext().getApplicationContext();
        PdfWatermarkSourceLoader loader = new PdfWatermarkSourceLoader(
                applicationContext, documentStore
        );
        viewModel.getExecutor().execute(() -> {
            try {
                PdfWatermarkSourceLoader.Result result = loader.load(
                        uri,
                        null,
                        applicationContext.getString(R.string.pdf_watermark_file_fallback),
                        viewModel.getSourceCancellation()
                );
                mainExecutor.execute(() -> {
                    String staleFile = viewModel.completeSourceLoad(result);
                    if (!result.getCacheFileName().equals(
                            viewModel.getSourceCacheFileName())) {
                        documentStore.delete(result.getCacheFileName());
                        return;
                    }
                    if (staleFile != null) documentStore.delete(staleFile);
                });
            } catch (PdfWatermarkSourceLoader.SourceLoadException exception) {
                mainExecutor.execute(() -> viewModel.completeSourceLoadError(exception.getError()));
            } catch (RuntimeException exception) {
                mainExecutor.execute(() -> viewModel.completeSourceLoadError(
                        mapThrowable(exception, true)
                ));
            }
        });
    }

    private void startExport() {
        PdfWatermarkViewModel.ExportOperation operation = viewModel.startExport();
        if (operation == null) return;
        File source = documentStore.resolveOwnedFile(operation.getSourceCacheFileName());
        if (source == null) {
            viewModel.completeExportError(
                    operation.getOperationId(), PdfWatermarkError.SOURCE_UNAVAILABLE
            );
            return;
        }
        generator.generateToTemporaryFile(
                source,
                operation.getPageCount(),
                operation.getOptions(),
                operation.getSelectedPages(),
                operation.getCancellationToken(),
                viewModel.getExecutor(),
                mainExecutor,
                new ViewModelGenerationCallback(viewModel, operation.getOperationId())
        );
    }

    private void launchSaveLocationPicker() {
        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        createDocumentLauncher.launch(PdfWatermarkFileName.suggest(
                viewModel.getSourceDisplayName(), date
        ));
    }

    private void handleSaveLocationResult(Uri outputUri) {
        if (outputUri == null) {
            generator.deleteTemporaryFile(viewModel.handleSaveLocationCancelled());
            return;
        }
        PdfWatermarkViewModel.SaveOperation operation = viewModel.startSaving();
        if (operation == null) return;
        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        String fallbackName = PdfWatermarkFileName.suggest(
                viewModel.getSourceDisplayName(), date
        );
        generator.saveTemporaryFile(
                operation.getTemporaryFileName(),
                outputUri,
                operation.getCancellationToken(),
                viewModel.getExecutor(),
                mainExecutor,
                new ViewModelSaveCallback(
                        viewModel,
                        documentSessionViewModel,
                        new PdfResultMetadataReader(requireContext()),
                        mainExecutor,
                        requireContext().getApplicationContext(),
                        operation.getOperationId(),
                        viewModel.getPageCount(),
                        fallbackName
                )
        );
    }

    private void cancelExport() {
        String temporary = viewModel.requestCancelExport();
        if (temporary != null) generator.deleteTemporaryFile(temporary);
    }

    private void closeSession() {
        PdfWatermarkViewModel.CleanupFiles files = viewModel.clearSession();
        documentStore.delete(files.getSourceCacheFileName());
        generator.deleteTemporaryFile(files.getTemporaryOutputFileName());
        if (navigationCallback != null) navigationCallback.onPdfWatermarkClosed();
    }

    private void closeRenderer() {
        if (previewImage != null) previewImage.setImageDrawable(null);
        if (previewOverlay != null) previewOverlay.setPageGeometry(0, 0);
        if (renderer != null) renderer.close();
        renderer = null;
        rendererSource = null;
        rendererReady = false;
        renderedPage = -1;
        renderedPageWidth = 0;
        renderedPageHeight = 0;
    }

    private int errorText(PdfWatermarkError error) {
        if (error == null) return R.string.pdf_watermark_error_open;
        switch (error) {
            case CORRUPTED_PDF: return R.string.pdf_watermark_error_corrupted;
            case ENCRYPTED_PDF: return R.string.pdf_watermark_error_encrypted;
            case EMPTY_PDF: return R.string.pdf_watermark_error_empty;
            case TOO_LARGE: return R.string.pdf_watermark_error_too_large;
            case RENDER_PAGE: return R.string.pdf_watermark_error_render;
            case OUT_OF_MEMORY: return R.string.pdf_watermark_error_memory;
            case CREATE_PDF: return R.string.pdf_watermark_error_create;
            case SAVE_PDF: return R.string.pdf_watermark_error_save;
            case SOURCE_UNAVAILABLE: return R.string.pdf_watermark_error_source_unavailable;
            case CANCELLED: return R.string.pdf_watermark_error_cancelled;
            case OPEN_PDF:
            case NONE:
            default: return R.string.pdf_watermark_error_open;
        }
    }

    private static PdfWatermarkError mapThrowable(Throwable throwable, boolean opening) {
        if (throwable instanceof PdfWatermarkGenerator.GenerationException) {
            switch (((PdfWatermarkGenerator.GenerationException) throwable).getReason()) {
                case SOURCE_UNAVAILABLE: return PdfWatermarkError.SOURCE_UNAVAILABLE;
                case TOO_LARGE: return PdfWatermarkError.TOO_LARGE;
                case OUT_OF_MEMORY: return PdfWatermarkError.OUT_OF_MEMORY;
                case SAVE_PDF: return PdfWatermarkError.SAVE_PDF;
                case CREATE_PDF:
                default: return PdfWatermarkError.CREATE_PDF;
            }
        }
        Throwable cause = throwable;
        while (cause != null) {
            if (cause instanceof OutOfMemoryError) return PdfWatermarkError.OUT_OF_MEMORY;
            cause = cause.getCause();
        }
        return opening ? PdfWatermarkError.OPEN_PDF : PdfWatermarkError.RENDER_PAGE;
    }

    private int colorChipId(WatermarkColor color) {
        switch (color) {
            case RED: return R.id.chip_pdf_watermark_red;
            case BLUE: return R.id.chip_pdf_watermark_blue;
            case GRAY:
            default: return R.id.chip_pdf_watermark_gray;
        }
    }

    private int pageSelectionChipId(WatermarkPageSelection selection) {
        switch (selection) {
            case CURRENT: return R.id.chip_pdf_watermark_current_page;
            case RANGE: return R.id.chip_pdf_watermark_range;
            case ALL:
            default: return R.id.chip_pdf_watermark_all_pages;
        }
    }

    private static void setTextIfDifferent(TextView view, String value) {
        String safe = value == null ? "" : value;
        if (!view.getText().toString().equals(safe)) view.setText(safe);
    }

    private static void setSliderValue(Slider slider, float value) {
        if (Math.abs(slider.getValue() - value) > 0.001f) slider.setValue(value);
    }

    private static void setEnabledRecursively(View view, boolean enabled) {
        view.setEnabled(enabled);
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int index = 0; index < group.getChildCount(); index++) {
            setEnabledRecursively(group.getChildAt(index), enabled);
        }
    }

    private abstract static class SimpleTextWatcher implements TextWatcher {
        @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) { }
        @Override public void onTextChanged(CharSequence text, int start, int before, int count) { }
    }

    private static final class ViewModelGenerationCallback
            implements PdfWatermarkGenerator.GenerationCallback {
        private final PdfWatermarkViewModel viewModel;
        private final long operationId;

        private ViewModelGenerationCallback(PdfWatermarkViewModel viewModel, long operationId) {
            this.viewModel = viewModel;
            this.operationId = operationId;
        }

        @Override
        public void onProgress(int completedPages, int totalPages) {
            viewModel.updateExportProgress(operationId, completedPages, totalPages);
        }

        @Override
        public void onGenerated(String temporaryFileName, long sizeBytes) {
            viewModel.completeGeneration(operationId, temporaryFileName);
        }

        @Override
        public void onCancelled() {
            viewModel.completeExportCancelled(operationId);
        }

        @Override
        public void onError(Exception exception) {
            viewModel.completeExportError(operationId, mapThrowable(exception, false));
        }
    }

    private static final class ViewModelSaveCallback
            implements PdfWatermarkGenerator.SaveCallback {
        private final PdfWatermarkViewModel viewModel;
        private final DocumentSessionViewModel documentSessionViewModel;
        private final PdfResultMetadataReader metadataReader;
        private final Executor mainExecutor;
        private final Context applicationContext;
        private final long operationId;
        private final int pageCount;
        private final String fallbackName;

        private ViewModelSaveCallback(
                PdfWatermarkViewModel viewModel,
                DocumentSessionViewModel documentSessionViewModel,
                PdfResultMetadataReader metadataReader,
                Executor mainExecutor,
                Context applicationContext,
                long operationId,
                int pageCount,
                String fallbackName
        ) {
            this.viewModel = viewModel;
            this.documentSessionViewModel = documentSessionViewModel;
            this.metadataReader = metadataReader;
            this.mainExecutor = mainExecutor;
            this.applicationContext = applicationContext;
            this.operationId = operationId;
            this.pageCount = pageCount;
            this.fallbackName = fallbackName;
        }

        @Override
        public void onSaved(Uri outputUri, long sizeBytes) {
            if (!viewModel.isCurrentOperation(operationId)) return;
            PdfResult initial = new PdfResult(
                    outputUri,
                    fallbackName,
                    sizeBytes,
                    pageCount,
                    System.currentTimeMillis(),
                    PdfLocationLabelResolver.resolveLabel(applicationContext, outputUri)
            );
            viewModel.getExecutor().execute(() -> {
                PdfResult result = metadataReader.read(initial);
                mainExecutor.execute(() -> completeSuccess(result));
            });
        }

        private void completeSuccess(PdfResult result) {
            if (!viewModel.isCurrentOperation(operationId)) return;
            documentSessionViewModel.publishExternalPdfResult(result);
            viewModel.completeExportSuccess(operationId);
        }

        @Override
        public void onCancelled() {
            viewModel.completeExportCancelled(operationId);
        }

        @Override
        public void onError(Exception exception) {
            viewModel.completeExportError(operationId, mapThrowable(exception, false));
        }
    }

    public interface NavigationCallback {
        void onPdfWatermarkClosed();
        void onPdfWatermarkResultRequested();
    }
}
