package com.desperadoboi.imagetopdf.ui.result;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.desperadoboi.imagetopdf.R;
import com.desperadoboi.imagetopdf.image.CapturedImageStorage;
import com.desperadoboi.imagetopdf.model.DocumentSessionViewModel;
import com.desperadoboi.imagetopdf.model.PdfFileNameFormatter;
import com.desperadoboi.imagetopdf.model.PdfResult;
import com.desperadoboi.imagetopdf.model.PdfSuccessEvent;
import com.desperadoboi.imagetopdf.pdf.PdfPreviewLoader;
import com.desperadoboi.imagetopdf.pdf.PdfResultMetadataReader;
import com.desperadoboi.imagetopdf.util.PageCountFormatter;
import com.desperadoboi.imagetopdf.util.PdfResultSizeFormatter;
import com.google.android.material.button.MaterialButton;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;

public final class PdfResultFragment extends Fragment {
    public static final String TAG = "PdfResultFragment";

    private DocumentSessionViewModel sessionViewModel;
    private NavigationCallback navigationCallback;
    private CapturedImageStorage capturedImageStorage;
    private PdfPreviewLoader previewLoader;
    private PdfResultMetadataReader metadataReader;
    private Executor mainExecutor;

    private ImageView previewImage;
    private ProgressBar previewProgress;
    private TextView pageBadge;
    private TextView fileNameText;
    private TextView summaryText;
    private TextView locationText;
    private MaterialButton shareButton;
    private MaterialButton openButton;
    private MaterialButton editPagesButton;
    private MaterialButton newDocumentButton;
    private PdfSuccessBanner successBanner;
    private PageCountFormatter.Labels pageCountLabels;

    private Bitmap previewBitmap;
    private long viewGeneration;

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
        sessionViewModel = new ViewModelProvider(requireActivity())
                .get(DocumentSessionViewModel.class);
        Context applicationContext = requireContext().getApplicationContext();
        capturedImageStorage = new CapturedImageStorage(applicationContext);
        metadataReader = new PdfResultMetadataReader(applicationContext);
        mainExecutor = ContextCompat.getMainExecutor(applicationContext);
        previewLoader = new PdfPreviewLoader(
                applicationContext.getContentResolver(),
                mainExecutor
        );
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.fragment_pdf_result, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewGeneration++;
        bindViews(view);
        configureActions(view);
        pageCountLabels = new PageCountFormatter.Labels(
                getString(R.string.pdf_page_word_one),
                getString(R.string.pdf_page_word_few),
                getString(R.string.pdf_page_word_many)
        );
        PdfResult result = sessionViewModel.getLastPdfResult();
        renderResult(result);
        if (result != null) {
            showPendingSuccess(result);
            loadMetadata(result, viewGeneration);
            loadPreview(result);
        }
    }

    @Override
    public void onDestroyView() {
        viewGeneration++;
        if (successBanner != null) {
            successBanner.hideImmediately();
        }
        previewLoader.clear();
        if (previewImage != null) {
            previewImage.setImageDrawable(null);
        }
        recyclePreviewBitmap();
        previewImage = null;
        previewProgress = null;
        pageBadge = null;
        fileNameText = null;
        summaryText = null;
        locationText = null;
        shareButton = null;
        openButton = null;
        editPagesButton = null;
        newDocumentButton = null;
        successBanner = null;
        pageCountLabels = null;
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
        if (navigationCallback != null) {
            navigationCallback.onPdfResultClosed();
        }
    }

    private void bindViews(View view) {
        previewImage = view.findViewById(R.id.image_pdf_result_preview);
        previewProgress = view.findViewById(R.id.progress_pdf_result_preview);
        pageBadge = view.findViewById(R.id.text_pdf_result_page_badge);
        fileNameText = view.findViewById(R.id.text_pdf_result_file_name);
        summaryText = view.findViewById(R.id.text_pdf_result_summary);
        locationText = view.findViewById(R.id.text_pdf_result_location);
        shareButton = view.findViewById(R.id.button_pdf_result_share);
        openButton = view.findViewById(R.id.button_pdf_result_open);
        editPagesButton = view.findViewById(R.id.button_pdf_result_edit_pages);
        newDocumentButton = view.findViewById(R.id.button_pdf_result_new_document);
        successBanner = view.findViewById(R.id.banner_pdf_success);
    }

    private void configureActions(View view) {
        ImageButton backButton = view.findViewById(R.id.button_pdf_result_back);
        backButton.setOnClickListener(clicked -> handleBackPressed());
        shareButton.setOnClickListener(clicked -> sharePdf());
        openButton.setOnClickListener(clicked -> openPdf());
        editPagesButton.setOnClickListener(clicked -> editPages());
        newDocumentButton.setOnClickListener(clicked -> createNewDocument());
    }

    private void loadMetadata(PdfResult initialResult, long requestedViewGeneration) {
        sessionViewModel.getPdfExecutor().execute(() -> {
            PdfResult updatedResult = metadataReader.read(initialResult);
            mainExecutor.execute(() -> {
                if (!isAdded()
                        || previewImage == null
                        || viewGeneration != requestedViewGeneration
                        || sessionViewModel.getLastPdfResult() != initialResult) {
                    return;
                }
                sessionViewModel.setLastPdfResult(updatedResult);
                renderResult(updatedResult);
            });
        });
    }

    private void loadPreview(PdfResult result) {
        int maxWidth = getResources().getDimensionPixelSize(R.dimen.pdf_result_preview_max_width);
        int maxHeight = getResources().getDimensionPixelSize(R.dimen.pdf_result_preview_max_height);
        previewProgress.setVisibility(View.VISIBLE);
        previewLoader.load(result.getUri(), maxWidth, maxHeight, new PdfPreviewLoader.Callback() {
            @Override
            public void onLoaded(Bitmap bitmap) {
                if (previewImage == null) {
                    recycle(bitmap);
                    return;
                }
                recyclePreviewBitmap();
                previewBitmap = bitmap;
                previewImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
                previewImage.setImageBitmap(bitmap);
                previewProgress.setVisibility(View.GONE);
            }

            @Override
            public void onError(Exception exception) {
                if (previewImage == null) {
                    return;
                }
                previewImage.setScaleType(ImageView.ScaleType.CENTER);
                previewImage.setImageResource(R.drawable.ic_action_create_pdf_24);
                previewProgress.setVisibility(View.GONE);
            }
        });
    }

    private void renderResult(PdfResult result) {
        boolean available = result != null;
        shareButton.setEnabled(available);
        openButton.setEnabled(available);
        editPagesButton.setEnabled(sessionViewModel.hasPages());
        newDocumentButton.setEnabled(sessionViewModel.canEditPages());
        if (!available) {
            fileNameText.setText(R.string.pdf_result_unavailable);
            pageBadge.setVisibility(View.GONE);
            summaryText.setVisibility(View.GONE);
            locationText.setVisibility(View.GONE);
            previewProgress.setVisibility(View.GONE);
            return;
        }

        String displayName = result.getDisplayName().isEmpty()
                ? getString(R.string.pdf_result_unknown_name)
                : result.getDisplayName();
        fileNameText.setText(result.getDisplayName().isEmpty()
                ? displayName
                : PdfFileNameFormatter.toDisplayTitle(displayName));
        fileNameText.setContentDescription(displayName);
        pageBadge.setText(getString(R.string.pdf_result_page_badge, result.getPageCount()));
        pageBadge.setContentDescription(formatPageCount(result.getPageCount()));
        pageBadge.setVisibility(View.VISIBLE);
        String pages = formatPageCount(result.getPageCount());
        String size = PdfResultSizeFormatter.format(
                result,
                Locale.getDefault(),
                getString(R.string.pdf_result_size_unknown)
        );
        summaryText.setText(getString(R.string.pdf_result_summary, pages, size));
        summaryText.setVisibility(View.VISIBLE);
        String location = result.getLocationLabel();
        if (location.isEmpty()
                || location.equals(getString(R.string.pdf_location_selected_folder))) {
            locationText.setText(R.string.pdf_result_location_unknown);
        } else {
            locationText.setText(getString(R.string.pdf_result_location, location));
        }
        locationText.setVisibility(View.VISIBLE);
    }

    private void showPendingSuccess(PdfResult result) {
        PdfSuccessEvent event = sessionViewModel.consumePendingPdfSuccessEvent(result);
        if (event == null || successBanner == null) {
            return;
        }
        successBanner.showResult(buildSummary(event.getResult()));
    }

    private String buildSummary(PdfResult result) {
        String size = PdfResultSizeFormatter.format(
                result,
                Locale.getDefault(),
                getString(R.string.pdf_result_size_unknown)
        );
        return getString(
                R.string.pdf_result_summary,
                formatPageCount(result.getPageCount()),
                size
        );
    }

    private String formatPageCount(int pageCount) {
        return PageCountFormatter.format(pageCount, pageCountLabels);
    }

    private void sharePdf() {
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
            showToast(R.string.status_pdf_share_error);
        }
    }

    private void openPdf() {
        PdfResult result = sessionViewModel.getLastPdfResult();
        if (result == null) {
            return;
        }
        try {
            startActivity(PdfIntentFactory.createOpenIntent(
                    requireContext().getContentResolver(),
                    result
            ));
        } catch (ActivityNotFoundException exception) {
            showToast(R.string.status_pdf_open_app_not_found);
        } catch (SecurityException exception) {
            showToast(R.string.status_pdf_open_error);
        }
    }

    private void editPages() {
        if (sessionViewModel.hasPages() && navigationCallback != null) {
            navigationCallback.onEditPdfPagesRequested();
        }
    }

    private void createNewDocument() {
        if (!sessionViewModel.canEditPages()) {
            return;
        }
        List<String> capturedFileNames = sessionViewModel.clearForNewDocument();
        for (String capturedFileName : capturedFileNames) {
            capturedImageStorage.delete(capturedFileName);
        }
        if (navigationCallback != null) {
            navigationCallback.onNewPdfDocumentRequested();
        }
    }

    private void recyclePreviewBitmap() {
        recycle(previewBitmap);
        previewBitmap = null;
    }

    private void recycle(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }

    private void showToast(int stringResId) {
        if (isAdded()) {
            Toast.makeText(requireContext(), stringResId, Toast.LENGTH_SHORT).show();
        }
    }

    public interface NavigationCallback {
        void onPdfResultClosed();

        void onEditPdfPagesRequested();

        void onNewPdfDocumentRequested();
    }
}
