package com.desperadoboi.imagetopdf.ui.export;

import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.desperadoboi.imagetopdf.R;
import com.desperadoboi.imagetopdf.model.DocumentSessionViewModel;
import com.desperadoboi.imagetopdf.model.MarginPreset;
import com.desperadoboi.imagetopdf.model.PageSizeMode;
import com.desperadoboi.imagetopdf.model.PdfExportDraft;
import com.desperadoboi.imagetopdf.model.PdfExportSummary;
import com.desperadoboi.imagetopdf.model.PdfFileNameFormatter;
import com.desperadoboi.imagetopdf.model.PdfOrientationMode;
import com.desperadoboi.imagetopdf.model.PdfQualityProfile;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public final class PdfExportSheet extends BottomSheetDialogFragment {
    public static final String TAG = "PdfExportSheet";
    public static final String RESULT_ACTION = "pdf_export_action";
    public static final String RESULT_ACTION_TYPE = "pdf_export_action_type";
    public static final String ACTION_CHANGE_LOCATION = "change_location";
    public static final String ACTION_CONVERT = "convert";

    private DocumentSessionViewModel sessionViewModel;
    private TextInputLayout fileNameLayout;
    private TextInputEditText fileNameEditText;
    private MaterialButtonToggleGroup qualityGroup;
    private TextView qualityDescription;
    private View additionalRow;
    private View additionalSettings;
    private ImageView additionalChevron;
    private TextView additionalSummary;
    private TextView pageSizeValue;
    private TextView orientationValue;
    private TextView marginsValue;
    private View outputLocationLayout;
    private TextView outputLocation;
    private Button convertButton;
    private PdfExportSummary.Labels summaryLabels;
    private boolean additionalExpanded;
    private boolean binding;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sessionViewModel = new ViewModelProvider(requireActivity())
                .get(DocumentSessionViewModel.class);
        if (sessionViewModel.getPdfExportDraft() == null) {
            sessionViewModel.setPdfExportDraft(PdfExportDraft.defaults("ImageToPDF"));
        }
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.sheet_pdf_export, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bindViews(view);
        summaryLabels = createSummaryLabels();
        bindDraftToViews();
        configureListeners(view);
        applyNavigationBarInsets(view);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (!(getDialog() instanceof BottomSheetDialog)) {
            return;
        }
        BottomSheetDialog dialog = (BottomSheetDialog) getDialog();
        View bottomSheet = dialog.findViewById(
                com.google.android.material.R.id.design_bottom_sheet
        );
        if (bottomSheet == null) {
            return;
        }
        bottomSheet.setBackgroundColor(Color.TRANSPARENT);
        BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        behavior.setSkipCollapsed(true);
    }

    @Override
    public void onDestroyView() {
        fileNameLayout = null;
        fileNameEditText = null;
        qualityGroup = null;
        qualityDescription = null;
        additionalRow = null;
        additionalSettings = null;
        additionalChevron = null;
        additionalSummary = null;
        pageSizeValue = null;
        orientationValue = null;
        marginsValue = null;
        outputLocationLayout = null;
        outputLocation = null;
        convertButton = null;
        summaryLabels = null;
        super.onDestroyView();
    }

    public void refreshFromDraft() {
        if (fileNameEditText != null) {
            bindDraftToViews();
        }
    }

    private void bindViews(View view) {
        fileNameLayout = view.findViewById(R.id.layout_pdf_export_file_name);
        fileNameEditText = view.findViewById(R.id.edit_pdf_export_file_name);
        qualityGroup = view.findViewById(R.id.group_pdf_export_quality);
        qualityDescription = view.findViewById(R.id.text_pdf_quality_description);
        additionalRow = view.findViewById(R.id.row_pdf_export_additional);
        additionalSettings = view.findViewById(R.id.layout_pdf_export_additional_settings);
        additionalChevron = view.findViewById(R.id.image_pdf_export_additional_chevron);
        additionalSummary = view.findViewById(R.id.text_pdf_export_summary);
        pageSizeValue = view.findViewById(R.id.text_pdf_export_page_size_value);
        orientationValue = view.findViewById(R.id.text_pdf_export_orientation_value);
        marginsValue = view.findViewById(R.id.text_pdf_export_margins_value);
        outputLocationLayout = view.findViewById(R.id.layout_pdf_export_location);
        outputLocation = view.findViewById(R.id.text_pdf_export_location);
        convertButton = view.findViewById(R.id.button_pdf_export_convert);
    }

    private void configureListeners(View view) {
        fileNameEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence value, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence value, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable value) {
                if (binding) {
                    return;
                }
                String editableName = PdfFileNameFormatter.toEditableName(value.toString());
                if (!editableName.contentEquals(value)) {
                    binding = true;
                    fileNameEditText.setText(editableName);
                    fileNameEditText.setSelection(fileNameEditText.length());
                    binding = false;
                }
                updateDraft(sessionViewModel.getPdfExportDraft().withFileName(editableName));
                renderFileNameValidity();
                renderOutputLocation();
            }
        });
        qualityGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (binding || !isChecked) {
                return;
            }
            PdfQualityProfile profile = checkedId == R.id.button_pdf_quality_compact
                    ? PdfQualityProfile.COMPACT
                    : (checkedId == R.id.button_pdf_quality_high
                            ? PdfQualityProfile.HIGH
                            : PdfQualityProfile.BALANCED);
            updateDraft(sessionViewModel.getPdfExportDraft().withQuality(profile));
            renderQualityDescription(profile);
        });
        additionalRow.setOnClickListener(clicked -> toggleAdditionalSettings());
        View pageSizeRow = view.findViewById(R.id.row_pdf_export_page_size);
        pageSizeRow.setContentDescription(
                getString(R.string.action_change_pdf_page_size_content_description)
        );
        pageSizeRow.setOnClickListener(clicked -> showPageSizeDialog());
        View orientationRow = view.findViewById(R.id.row_pdf_export_orientation);
        orientationRow.setContentDescription(
                getString(R.string.action_change_pdf_orientation_content_description)
        );
        orientationRow.setOnClickListener(clicked -> showOrientationDialog());
        View marginsRow = view.findViewById(R.id.row_pdf_export_margins);
        marginsRow.setContentDescription(
                getString(R.string.action_change_pdf_margins_content_description)
        );
        marginsRow.setOnClickListener(clicked -> showMarginsDialog());
        view.findViewById(R.id.button_pdf_export_close).setOnClickListener(clicked -> dismiss());
        view.findViewById(R.id.button_pdf_export_change_location).setOnClickListener(
                clicked -> dispatchIfValid(ACTION_CHANGE_LOCATION)
        );
        convertButton.setOnClickListener(clicked -> dispatchIfValid(ACTION_CONVERT));
    }

    private void bindDraftToViews() {
        PdfExportDraft draft = sessionViewModel.getPdfExportDraft();
        binding = true;
        String editableName = PdfFileNameFormatter.toEditableName(draft.getFileName());
        CharSequence currentText = fileNameEditText.getText();
        if (!editableName.contentEquals(currentText == null ? "" : currentText)) {
            fileNameEditText.setText(editableName);
            fileNameEditText.setSelection(fileNameEditText.length());
        }
        qualityGroup.check(draft.getQuality() == PdfQualityProfile.COMPACT
                ? R.id.button_pdf_quality_compact
                : (draft.getQuality() == PdfQualityProfile.HIGH
                        ? R.id.button_pdf_quality_high
                        : R.id.button_pdf_quality_balanced));
        binding = false;
        renderQualityDescription(draft.getQuality());
        renderAdditionalSettings(draft);
        renderOutputLocation();
        renderFileNameValidity();
    }

    private void toggleAdditionalSettings() {
        additionalExpanded = !additionalExpanded;
        additionalSettings.setVisibility(additionalExpanded ? View.VISIBLE : View.GONE);
        additionalChevron.animate()
                .rotation(additionalExpanded ? 90f : 0f)
                .setDuration(160L)
                .start();
        additionalRow.setContentDescription(getString(additionalExpanded
                ? R.string.action_collapse_pdf_additional_content_description
                : R.string.action_expand_pdf_additional_content_description));
    }

    private void showPageSizeDialog() {
        PdfExportDraft draft = sessionViewModel.getPdfExportDraft();
        CharSequence[] options = getResources().getTextArray(R.array.pdf_page_size_options);
        int checkedItem = draft.getPageSize() == PageSizeMode.IMAGE ? 0 : 1;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.pdf_page_size_label)
                .setSingleChoiceItems(options, checkedItem, (dialog, selected) -> {
                    updateDraft(draft.withPageSize(
                            selected == 0 ? PageSizeMode.IMAGE : PageSizeMode.A4
                    ));
                    renderAdditionalSettings(sessionViewModel.getPdfExportDraft());
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void showOrientationDialog() {
        PdfExportDraft draft = sessionViewModel.getPdfExportDraft();
        CharSequence[] options = getResources().getTextArray(R.array.pdf_orientation_options);
        int checkedItem = draft.getOrientation() == PdfOrientationMode.PORTRAIT
                ? 1
                : (draft.getOrientation() == PdfOrientationMode.LANDSCAPE ? 2 : 0);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.pdf_export_orientation_label)
                .setSingleChoiceItems(options, checkedItem, (dialog, selected) -> {
                    PdfOrientationMode value = selected == 1
                            ? PdfOrientationMode.PORTRAIT
                            : (selected == 2
                                    ? PdfOrientationMode.LANDSCAPE
                                    : PdfOrientationMode.AUTO);
                    updateDraft(draft.withOrientation(value));
                    renderAdditionalSettings(sessionViewModel.getPdfExportDraft());
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void showMarginsDialog() {
        PdfExportDraft draft = sessionViewModel.getPdfExportDraft();
        CharSequence[] options = getResources().getTextArray(R.array.pdf_margin_options);
        int checkedItem = draft.getMargins() == MarginPreset.NONE
                ? 0
                : (draft.getMargins() == MarginPreset.SMALL ? 1 : 2);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.pdf_margin_label)
                .setSingleChoiceItems(options, checkedItem, (dialog, selected) -> {
                    MarginPreset value = selected == 0
                            ? MarginPreset.NONE
                            : (selected == 1 ? MarginPreset.SMALL : MarginPreset.STANDARD);
                    updateDraft(draft.withMargins(value));
                    renderAdditionalSettings(sessionViewModel.getPdfExportDraft());
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void dispatchIfValid(String action) {
        PdfExportDraft draft = sessionViewModel.getPdfExportDraft().withFileName(
                fileNameEditText.getText() == null ? "" : fileNameEditText.getText().toString()
        );
        updateDraft(draft);
        try {
            draft.toRequest();
        } catch (IllegalArgumentException exception) {
            fileNameLayout.setError(getString(R.string.pdf_export_file_name_error));
            convertButton.setEnabled(false);
            return;
        }
        fileNameLayout.setError(null);
        Bundle result = new Bundle();
        result.putString(RESULT_ACTION_TYPE, action);
        getParentFragmentManager().setFragmentResult(RESULT_ACTION, result);
    }

    private void updateDraft(PdfExportDraft draft) {
        sessionViewModel.setPdfExportDraft(draft);
    }

    private void renderQualityDescription(PdfQualityProfile profile) {
        qualityDescription.setText(profile == PdfQualityProfile.COMPACT
                ? R.string.pdf_quality_compact_description
                : (profile == PdfQualityProfile.HIGH
                        ? R.string.pdf_quality_high_description
                        : R.string.pdf_quality_balanced_description));
    }

    private void renderAdditionalSettings(PdfExportDraft draft) {
        additionalSummary.setText(PdfExportSummary.from(draft).format(summaryLabels));
        pageSizeValue.setText(draft.getPageSize() == PageSizeMode.IMAGE
                ? R.string.pdf_page_size_image
                : R.string.pdf_page_size_a4);
        orientationValue.setText(draft.getOrientation() == PdfOrientationMode.PORTRAIT
                ? R.string.pdf_orientation_portrait
                : (draft.getOrientation() == PdfOrientationMode.LANDSCAPE
                        ? R.string.pdf_orientation_landscape
                        : R.string.pdf_orientation_auto));
        marginsValue.setText(draft.getMargins() == MarginPreset.NONE
                ? R.string.pdf_margin_none
                : (draft.getMargins() == MarginPreset.SMALL
                        ? R.string.pdf_margin_small
                        : R.string.pdf_margin_standard));
        additionalRow.setContentDescription(getString(additionalExpanded
                ? R.string.action_collapse_pdf_additional_content_description
                : R.string.action_expand_pdf_additional_content_description));
    }

    private void renderOutputLocation() {
        PdfExportDraft draft = sessionViewModel.getPdfExportDraft();
        if (draft.getOutputUri() == null) {
            outputLocationLayout.setVisibility(View.GONE);
            return;
        }
        String label = draft.getOutputLabel().isEmpty()
                ? getString(R.string.pdf_location_selected_folder)
                : draft.getOutputLabel();
        outputLocation.setText(getString(R.string.pdf_export_location_selected, label));
        outputLocationLayout.setVisibility(View.VISIBLE);
    }

    private void renderFileNameValidity() {
        boolean valid;
        try {
            PdfFileNameFormatter.normalizeFileName(fileNameEditText.getText() == null
                    ? ""
                    : fileNameEditText.getText().toString());
            valid = true;
        } catch (IllegalArgumentException exception) {
            valid = false;
        }
        fileNameLayout.setError(valid
                ? null
                : getString(R.string.pdf_export_file_name_error));
        convertButton.setEnabled(valid);
    }

    private PdfExportSummary.Labels createSummaryLabels() {
        return new PdfExportSummary.Labels(
                getString(R.string.pdf_page_size_image),
                getString(R.string.pdf_page_size_a4),
                getString(R.string.pdf_orientation_auto_short),
                getString(R.string.pdf_orientation_portrait),
                getString(R.string.pdf_orientation_landscape),
                getString(R.string.pdf_margin_none),
                getString(R.string.pdf_margin_small),
                getString(R.string.pdf_margin_standard_summary)
        );
    }

    private void applyNavigationBarInsets(View view) {
        int initialLeft = view.getPaddingLeft();
        int initialTop = view.getPaddingTop();
        int initialRight = view.getPaddingRight();
        int initialBottom = view.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(view, (target, windowInsets) -> {
            Insets navigationBars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.navigationBars()
            );
            target.setPadding(
                    initialLeft,
                    initialTop,
                    initialRight,
                    initialBottom + navigationBars.bottom
            );
            return windowInsets;
        });
    }
}
