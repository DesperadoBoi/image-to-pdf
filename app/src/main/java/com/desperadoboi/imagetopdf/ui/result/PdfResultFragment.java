package com.desperadoboi.imagetopdf.ui.result;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
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
import com.desperadoboi.imagetopdf.model.PdfResult;
import com.desperadoboi.imagetopdf.pdf.PdfPreviewLoader;
import com.desperadoboi.imagetopdf.pdf.PdfResultMetadataReader;
import com.desperadoboi.imagetopdf.util.FileSizeFormatter;
import com.google.android.material.button.MaterialButton;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;

public final class PdfResultFragment extends Fragment {
    public static final String TAG = "PdfResultFragment";
    private static final String PDF_MIME_TYPE = "application/pdf";

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
    private TextView pagesText;
    private TextView sizeText;
    private TextView locationText;
    private MaterialButton shareButton;
    private MaterialButton openButton;
    private MaterialButton editPagesButton;
    private MaterialButton newDocumentButton;

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
        metadataReader = new PdfResultMetadataReader(applicationContext.getContentResolver());
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
        PdfResult result = sessionViewModel.getLastPdfResult();
        renderResult(result);
        if (result != null) {
            loadMetadata(result, viewGeneration);
            loadPreview(result);
        }
    }

    @Override
    public void onDestroyView() {
        viewGeneration++;
        previewLoader.clear();
        if (previewImage != null) {
            previewImage.setImageDrawable(null);
        }
        recyclePreviewBitmap();
        previewImage = null;
        previewProgress = null;
        pageBadge = null;
        fileNameText = null;
        pagesText = null;
        sizeText = null;
        locationText = null;
        shareButton = null;
        openButton = null;
        editPagesButton = null;
        newDocumentButton = null;
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
        pagesText = view.findViewById(R.id.text_pdf_result_pages);
        sizeText = view.findViewById(R.id.text_pdf_result_size);
        locationText = view.findViewById(R.id.text_pdf_result_location);
        shareButton = view.findViewById(R.id.button_pdf_result_share);
        openButton = view.findViewById(R.id.button_pdf_result_open);
        editPagesButton = view.findViewById(R.id.button_pdf_result_edit_pages);
        newDocumentButton = view.findViewById(R.id.button_pdf_result_new_document);
    }

    private void configureActions(View view) {
        ImageButton closeButton = view.findViewById(R.id.button_pdf_result_close);
        closeButton.setOnClickListener(clicked -> handleBackPressed());
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
                previewImage.setImageBitmap(bitmap);
                previewProgress.setVisibility(View.GONE);
            }

            @Override
            public void onError(Exception exception) {
                if (previewImage == null) {
                    return;
                }
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
            pagesText.setVisibility(View.GONE);
            sizeText.setVisibility(View.GONE);
            locationText.setVisibility(View.GONE);
            previewProgress.setVisibility(View.GONE);
            return;
        }

        fileNameText.setText(result.getDisplayName().isEmpty()
                ? getString(R.string.pdf_result_unknown_name)
                : result.getDisplayName());
        pageBadge.setText(String.valueOf(result.getPageCount()));
        pageBadge.setContentDescription(getResources().getQuantityString(
                R.plurals.pdf_result_pages_count,
                result.getPageCount(),
                result.getPageCount()
        ));
        pageBadge.setVisibility(View.VISIBLE);
        pagesText.setText(getResources().getQuantityString(
                R.plurals.pdf_result_pages_count,
                result.getPageCount(),
                result.getPageCount()
        ));
        pagesText.setVisibility(View.VISIBLE);
        sizeText.setText(result.hasKnownSize()
                ? getString(
                        R.string.pdf_result_size,
                        FileSizeFormatter.format(result.getSizeBytes(), Locale.getDefault())
                )
                : getString(R.string.pdf_result_size_unknown));
        sizeText.setVisibility(View.VISIBLE);
        String location = result.getLocationLabel();
        if (location.isEmpty()) {
            location = getString(R.string.pdf_result_location_unknown);
        }
        locationText.setText(getString(R.string.pdf_result_location, location));
        locationText.setVisibility(View.VISIBLE);
    }

    private void sharePdf() {
        PdfResult result = sessionViewModel.getLastPdfResult();
        if (result == null) {
            return;
        }
        Intent sendIntent = new Intent(Intent.ACTION_SEND);
        sendIntent.setType(PDF_MIME_TYPE);
        sendIntent.putExtra(Intent.EXTRA_STREAM, result.getUri());
        grantReadPermission(sendIntent, result);
        Intent chooser = Intent.createChooser(
                sendIntent,
                getString(R.string.pdf_share_chooser_title)
        );
        chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        chooser.setClipData(sendIntent.getClipData());
        try {
            startActivity(chooser);
        } catch (ActivityNotFoundException | SecurityException exception) {
            showToast(R.string.status_pdf_share_error);
        }
    }

    private void openPdf() {
        PdfResult result = sessionViewModel.getLastPdfResult();
        if (result == null) {
            return;
        }
        Intent viewIntent = new Intent(Intent.ACTION_VIEW);
        viewIntent.setDataAndType(result.getUri(), PDF_MIME_TYPE);
        grantReadPermission(viewIntent, result);
        try {
            startActivity(viewIntent);
        } catch (ActivityNotFoundException exception) {
            showToast(R.string.status_pdf_open_app_not_found);
        } catch (SecurityException exception) {
            showToast(R.string.status_pdf_open_error);
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
