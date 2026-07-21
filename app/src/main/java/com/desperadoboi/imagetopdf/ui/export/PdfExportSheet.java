package com.desperadoboi.imagetopdf.ui.export;

import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.desperadoboi.imagetopdf.R;
import com.desperadoboi.imagetopdf.model.DocumentSessionViewModel;
import com.desperadoboi.imagetopdf.model.MarginPreset;
import com.desperadoboi.imagetopdf.model.PageSizeMode;
import com.desperadoboi.imagetopdf.model.PdfExportDraft;
import com.desperadoboi.imagetopdf.model.PdfOrientationMode;
import com.desperadoboi.imagetopdf.model.PdfQualityProfile;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public final class PdfExportSheet extends BottomSheetDialogFragment {
    public static final String TAG = "PdfExportSheet";
    public static final String RESULT_ACTION = "pdf_export_action";
    public static final String RESULT_ACTION_TYPE = "pdf_export_action_type";
    public static final String ACTION_BROWSE = "browse";
    public static final String ACTION_CONVERT = "convert";

    private DocumentSessionViewModel sessionViewModel;
    private TextInputLayout fileNameLayout;
    private TextInputEditText fileNameEditText;
    private RadioGroup qualityGroup;
    private RadioGroup pageSizeGroup;
    private RadioGroup orientationGroup;
    private RadioGroup marginsGroup;
    private TextView qualityDescription;
    private TextView outputLocation;
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
        fileNameLayout = view.findViewById(R.id.layout_pdf_export_file_name);
        fileNameEditText = view.findViewById(R.id.edit_pdf_export_file_name);
        qualityGroup = view.findViewById(R.id.group_pdf_export_quality);
        pageSizeGroup = view.findViewById(R.id.group_pdf_export_page_size);
        orientationGroup = view.findViewById(R.id.group_pdf_export_orientation);
        marginsGroup = view.findViewById(R.id.group_pdf_export_margins);
        qualityDescription = view.findViewById(R.id.text_pdf_quality_description);
        outputLocation = view.findViewById(R.id.text_pdf_export_location);
        bindDraftToViews();
        configureListeners(view);
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
        pageSizeGroup = null;
        orientationGroup = null;
        marginsGroup = null;
        qualityDescription = null;
        outputLocation = null;
        super.onDestroyView();
    }

    public void refreshFromDraft() {
        if (fileNameEditText != null) {
            bindDraftToViews();
        }
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
                updateDraft(sessionViewModel.getPdfExportDraft().withFileName(value.toString()));
                fileNameLayout.setError(null);
                renderOutputLocation();
            }
        });
        qualityGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (binding) {
                return;
            }
            PdfQualityProfile profile = checkedId == R.id.radio_pdf_quality_compact
                    ? PdfQualityProfile.COMPACT
                    : (checkedId == R.id.radio_pdf_quality_high
                            ? PdfQualityProfile.HIGH
                            : PdfQualityProfile.BALANCED);
            updateDraft(sessionViewModel.getPdfExportDraft().withQuality(profile));
            renderQualityDescription(profile);
        });
        pageSizeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (!binding) {
                updateDraft(sessionViewModel.getPdfExportDraft().withPageSize(
                        checkedId == R.id.radio_pdf_page_image
                                ? PageSizeMode.IMAGE
                                : PageSizeMode.A4
                ));
            }
        });
        orientationGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (binding) {
                return;
            }
            PdfOrientationMode orientation = checkedId == R.id.radio_pdf_orientation_portrait
                    ? PdfOrientationMode.PORTRAIT
                    : (checkedId == R.id.radio_pdf_orientation_landscape
                            ? PdfOrientationMode.LANDSCAPE
                            : PdfOrientationMode.AUTO);
            updateDraft(sessionViewModel.getPdfExportDraft().withOrientation(orientation));
        });
        marginsGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (binding) {
                return;
            }
            MarginPreset margins = checkedId == R.id.radio_pdf_margin_none
                    ? MarginPreset.NONE
                    : (checkedId == R.id.radio_pdf_margin_small
                            ? MarginPreset.SMALL
                            : MarginPreset.STANDARD);
            updateDraft(sessionViewModel.getPdfExportDraft().withMargins(margins));
        });
        view.findViewById(R.id.button_pdf_export_close).setOnClickListener(clicked -> dismiss());
        view.findViewById(R.id.button_pdf_export_browse).setOnClickListener(
                clicked -> dispatchIfValid(ACTION_BROWSE)
        );
        view.findViewById(R.id.button_pdf_export_convert).setOnClickListener(
                clicked -> dispatchIfValid(ACTION_CONVERT)
        );
    }

    private void bindDraftToViews() {
        PdfExportDraft draft = sessionViewModel.getPdfExportDraft();
        binding = true;
        if (!draft.getFileName().contentEquals(fileNameEditText.getText() == null
                ? ""
                : fileNameEditText.getText())) {
            fileNameEditText.setText(draft.getFileName());
            fileNameEditText.setSelection(fileNameEditText.length());
        }
        qualityGroup.check(draft.getQuality() == PdfQualityProfile.COMPACT
                ? R.id.radio_pdf_quality_compact
                : (draft.getQuality() == PdfQualityProfile.HIGH
                        ? R.id.radio_pdf_quality_high
                        : R.id.radio_pdf_quality_balanced));
        pageSizeGroup.check(draft.getPageSize() == PageSizeMode.IMAGE
                ? R.id.radio_pdf_page_image
                : R.id.radio_pdf_page_a4);
        orientationGroup.check(draft.getOrientation() == PdfOrientationMode.PORTRAIT
                ? R.id.radio_pdf_orientation_portrait
                : (draft.getOrientation() == PdfOrientationMode.LANDSCAPE
                        ? R.id.radio_pdf_orientation_landscape
                        : R.id.radio_pdf_orientation_auto));
        marginsGroup.check(draft.getMargins() == MarginPreset.NONE
                ? R.id.radio_pdf_margin_none
                : (draft.getMargins() == MarginPreset.SMALL
                        ? R.id.radio_pdf_margin_small
                        : R.id.radio_pdf_margin_standard));
        binding = false;
        renderQualityDescription(draft.getQuality());
        renderOutputLocation();
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

    private void renderOutputLocation() {
        PdfExportDraft draft = sessionViewModel.getPdfExportDraft();
        if (draft.getOutputUri() == null) {
            outputLocation.setVisibility(View.GONE);
            return;
        }
        String label = draft.getOutputLabel().isEmpty()
                ? draft.getOutputUri().getAuthority()
                : draft.getOutputLabel();
        if (label == null || label.trim().isEmpty()) {
            label = draft.getOutputUri().toString();
        }
        outputLocation.setText(getString(R.string.pdf_export_location_selected, label));
        outputLocation.setVisibility(View.VISIBLE);
    }
}
