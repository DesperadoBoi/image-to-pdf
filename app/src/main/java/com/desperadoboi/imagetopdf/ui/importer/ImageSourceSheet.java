package com.desperadoboi.imagetopdf.ui.importer;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.desperadoboi.imagetopdf.R;
import com.desperadoboi.imagetopdf.model.ImageImportMode;
import com.desperadoboi.imagetopdf.model.ImageImportSource;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public final class ImageSourceSheet extends BottomSheetDialogFragment {
    public static final String TAG = "ImageSourceSheet";
    public static final String RESULT_SOURCE_SELECTED = "image_source_sheet_result";
    public static final String RESULT_SOURCE = "image_source";
    public static final String RESULT_MODE = "image_import_mode";

    private static final String ARG_MODE = "mode";

    private ImageImportMode importMode;

    public static ImageSourceSheet newInstance(ImageImportMode importMode) {
        ImageSourceSheet sheet = new ImageSourceSheet();
        Bundle arguments = new Bundle();
        arguments.putString(ARG_MODE, importMode.name());
        sheet.setArguments(arguments);
        return sheet;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        importMode = ImageImportMode.valueOf(requireArguments().getString(ARG_MODE));
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.sheet_image_source, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        TextView titleView = view.findViewById(R.id.text_image_source_title);
        titleView.setText(importMode == ImageImportMode.NEW_DOCUMENT
                ? R.string.image_source_title_new_document
                : R.string.image_source_title_append);

        view.findViewById(R.id.row_image_source_gallery).setOnClickListener(
                clickedView -> dispatchSelection(ImageImportSource.GALLERY)
        );
        view.findViewById(R.id.row_image_source_camera).setOnClickListener(
                clickedView -> dispatchSelection(ImageImportSource.CAMERA)
        );
        view.findViewById(R.id.row_image_source_files).setOnClickListener(
                clickedView -> dispatchSelection(ImageImportSource.FILES)
        );
        view.findViewById(R.id.button_image_source_cancel).setOnClickListener(
                clickedView -> dismiss()
        );
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

    private void dispatchSelection(ImageImportSource source) {
        Bundle result = new Bundle();
        result.putString(RESULT_SOURCE, source.name());
        result.putString(RESULT_MODE, importMode.name());
        getParentFragmentManager().setFragmentResult(RESULT_SOURCE_SELECTED, result);
        dismiss();
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
