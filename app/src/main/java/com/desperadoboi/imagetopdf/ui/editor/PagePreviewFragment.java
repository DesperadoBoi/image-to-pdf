package com.desperadoboi.imagetopdf.ui.editor;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.desperadoboi.imagetopdf.R;
import com.desperadoboi.imagetopdf.image.PreviewImageLoader;
import com.desperadoboi.imagetopdf.model.DocumentSessionViewModel;
import com.desperadoboi.imagetopdf.model.PageItem;
import com.google.android.material.button.MaterialButton;

import java.util.List;

public final class PagePreviewFragment extends Fragment {
    public static final String TAG = "PagePreviewFragment";
    public static final String RESULT_PAGE_ROTATED = "page_preview_result_page_rotated";
    public static final String RESULT_KEY_PAGE_ID = "page_id";

    private static final String ARG_PAGE_ID = "page_id";
    private static final String STATE_PAGE_ID = "state_page_id";
    private static final int PREVIEW_ZOOM_RESERVE = 2;

    private DocumentSessionViewModel sessionViewModel;
    private PreviewImageLoader previewImageLoader;
    private DocumentSessionViewModel.PdfGenerationStateObserver pdfGenerationStateObserver;

    private ZoomableImageView previewImageView;
    private TextView titleTextView;
    private TextView errorTextView;
    private ProgressBar progressBar;
    private MaterialButton closeButton;
    private MaterialButton previousButton;
    private MaterialButton rotateButton;
    private MaterialButton nextButton;

    private long currentPageId;
    private String activeLoadKey;
    private Bitmap currentBitmap;

    public static PagePreviewFragment newInstance(long pageId) {
        PagePreviewFragment fragment = new PagePreviewFragment();
        Bundle arguments = new Bundle();
        arguments.putLong(ARG_PAGE_ID, pageId);
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sessionViewModel = new ViewModelProvider(requireActivity())
                .get(DocumentSessionViewModel.class);
        previewImageLoader = new PreviewImageLoader(
                requireContext().getApplicationContext().getContentResolver(),
                ContextCompat.getMainExecutor(requireContext())
        );
        if (savedInstanceState != null) {
            currentPageId = savedInstanceState.getLong(STATE_PAGE_ID);
        } else {
            Bundle arguments = requireArguments();
            currentPageId = arguments.getLong(ARG_PAGE_ID);
        }
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.fragment_page_preview, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bindViews(view);
        configureClickListeners();
        pdfGenerationStateObserver = generationState ->
                rotateButton.setEnabled(sessionViewModel.canEditPages());
        sessionViewModel.addPdfGenerationStateObserver(pdfGenerationStateObserver);
        renderCurrentPage();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putLong(STATE_PAGE_ID, currentPageId);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onDestroyView() {
        if (sessionViewModel != null && pdfGenerationStateObserver != null) {
            sessionViewModel.removePdfGenerationStateObserver(pdfGenerationStateObserver);
            pdfGenerationStateObserver = null;
        }
        activeLoadKey = null;
        releaseCurrentBitmap();
        previewImageView = null;
        titleTextView = null;
        errorTextView = null;
        progressBar = null;
        closeButton = null;
        previousButton = null;
        rotateButton = null;
        nextButton = null;
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        if (previewImageLoader != null) {
            previewImageLoader.shutdown();
        }
        super.onDestroy();
    }

    private void bindViews(View view) {
        previewImageView = view.findViewById(R.id.image_page_preview);
        titleTextView = view.findViewById(R.id.text_page_preview_title);
        errorTextView = view.findViewById(R.id.text_page_preview_error);
        progressBar = view.findViewById(R.id.progress_page_preview);
        closeButton = view.findViewById(R.id.button_close_page_preview);
        previousButton = view.findViewById(R.id.button_previous_page);
        rotateButton = view.findViewById(R.id.button_rotate_preview_page);
        nextButton = view.findViewById(R.id.button_next_page);
    }

    private void configureClickListeners() {
        closeButton.setOnClickListener(view -> closePreview());
        previousButton.setOnClickListener(view -> moveToPreviousPage());
        nextButton.setOnClickListener(view -> moveToNextPage());
        rotateButton.setOnClickListener(view -> rotateCurrentPage());
    }

    private void renderCurrentPage() {
        List<PageItem> pages = sessionViewModel.getPages();
        int position = PreviewPageNavigator.findPositionById(pages, currentPageId);
        if (position == PreviewPageNavigator.POSITION_NOT_FOUND) {
            closePreview();
            return;
        }

        PageItem pageItem = pages.get(position);
        int pageNumber = position + 1;
        int pageCount = pages.size();
        titleTextView.setText(getString(R.string.page_preview_title, pageNumber, pageCount));
        previewImageView.setContentDescription(
                getString(R.string.page_preview_image_content_description, pageNumber)
        );
        previousButton.setEnabled(PreviewPageNavigator.hasPrevious(pages, currentPageId));
        nextButton.setEnabled(PreviewPageNavigator.hasNext(pages, currentPageId));
        rotateButton.setEnabled(sessionViewModel.canEditPages());
        errorTextView.setVisibility(View.GONE);
        loadPreviewImageWhenMeasured(pageItem);
    }

    private void loadPreviewImageWhenMeasured(PageItem pageItem) {
        if (previewImageView.getWidth() > 0 && previewImageView.getHeight() > 0) {
            loadPreviewImage(pageItem);
            return;
        }
        previewImageView.post(() -> {
            if (previewImageView == null) {
                return;
            }
            loadPreviewImage(pageItem);
        });
    }

    private void loadPreviewImage(PageItem pageItem) {
        if (previewImageView == null
                || previewImageView.getWidth() <= 0
                || previewImageView.getHeight() <= 0) {
            return;
        }

        releaseCurrentBitmap();
        progressBar.setVisibility(View.VISIBLE);
        int targetWidth = previewImageView.getWidth() * PREVIEW_ZOOM_RESERVE;
        int targetHeight = previewImageView.getHeight() * PREVIEW_ZOOM_RESERVE;
        activeLoadKey = PreviewImageLoader.buildKey(pageItem, targetWidth, targetHeight);
        previewImageLoader.load(
                pageItem,
                targetWidth,
                targetHeight,
                new PreviewImageLoader.Callback() {
                    @Override
                    public void onLoaded(String key, Bitmap bitmap) {
                        handlePreviewLoaded(key, bitmap);
                    }

                    @Override
                    public void onError(String key) {
                        handlePreviewError(key);
                    }
                }
        );
    }

    private void handlePreviewLoaded(String key, Bitmap bitmap) {
        if (previewImageView == null || !key.equals(activeLoadKey)) {
            recycle(bitmap);
            return;
        }
        progressBar.setVisibility(View.GONE);
        errorTextView.setVisibility(View.GONE);
        setCurrentBitmap(bitmap);
    }

    private void handlePreviewError(String key) {
        if (previewImageView == null || !key.equals(activeLoadKey)) {
            return;
        }
        progressBar.setVisibility(View.GONE);
        errorTextView.setVisibility(View.VISIBLE);
        releaseCurrentBitmap();
    }

    private void moveToPreviousPage() {
        List<PageItem> pages = sessionViewModel.getPages();
        if (!PreviewPageNavigator.hasPrevious(pages, currentPageId)) {
            return;
        }
        currentPageId = PreviewPageNavigator.previousPageId(pages, currentPageId);
        previewImageView.resetZoom();
        renderCurrentPage();
    }

    private void moveToNextPage() {
        List<PageItem> pages = sessionViewModel.getPages();
        if (!PreviewPageNavigator.hasNext(pages, currentPageId)) {
            return;
        }
        currentPageId = PreviewPageNavigator.nextPageId(pages, currentPageId);
        previewImageView.resetZoom();
        renderCurrentPage();
    }

    private void rotateCurrentPage() {
        if (!sessionViewModel.canEditPages()) {
            return;
        }
        int position = PreviewPageNavigator.findPositionById(
                sessionViewModel.getPages(),
                currentPageId
        );
        if (position == PreviewPageNavigator.POSITION_NOT_FOUND) {
            closePreview();
            return;
        }
        PageItem rotatedPage = sessionViewModel.rotatePage(position);
        publishRotationResult(rotatedPage.getId());
        previewImageView.resetZoom();
        renderCurrentPage();
    }

    private void publishRotationResult(long pageId) {
        Bundle result = new Bundle();
        result.putLong(RESULT_KEY_PAGE_ID, pageId);
        getParentFragmentManager().setFragmentResult(RESULT_PAGE_ROTATED, result);
    }

    private void setCurrentBitmap(Bitmap bitmap) {
        Bitmap oldBitmap = currentBitmap;
        currentBitmap = bitmap;
        previewImageView.setImageBitmap(bitmap);
        recycle(oldBitmap);
    }

    private void releaseCurrentBitmap() {
        if (previewImageView != null) {
            previewImageView.setImageBitmap(null);
        }
        recycle(currentBitmap);
        currentBitmap = null;
    }

    private void recycle(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }

    private void closePreview() {
        FragmentManager fragmentManager = getParentFragmentManager();
        if (fragmentManager.getBackStackEntryCount() > 0) {
            fragmentManager.popBackStack();
            return;
        }
        fragmentManager.beginTransaction().remove(this).commit();
    }
}
