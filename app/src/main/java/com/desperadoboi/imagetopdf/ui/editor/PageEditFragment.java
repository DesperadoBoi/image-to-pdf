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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.desperadoboi.imagetopdf.R;
import com.desperadoboi.imagetopdf.image.PreviewImageLoader;
import com.desperadoboi.imagetopdf.image.ThumbnailLoader;
import com.desperadoboi.imagetopdf.model.DocumentSessionViewModel;
import com.desperadoboi.imagetopdf.model.PageItem;
import com.google.android.material.button.MaterialButton;

import java.util.List;

public final class PageEditFragment extends Fragment {
    public static final String TAG = "PageEditFragment";
    public static final String RESULT_PAGE_EDITED = "page_edit_result_page_edited";
    public static final String RESULT_KEY_PAGE_ID = "page_id";

    private static final String ARG_PAGE_ID = "page_id";
    private static final String STATE_PAGE_ID = "state_page_id";
    private static final int PREVIEW_ZOOM_RESERVE = 2;

    private DocumentSessionViewModel sessionViewModel;
    private PreviewImageLoader previewImageLoader;
    private ThumbnailLoader thumbnailLoader;
    private DocumentSessionViewModel.PdfGenerationStateObserver generationStateObserver;

    private ZoomableImageView imageView;
    private TextView titleView;
    private TextView counterView;
    private TextView errorView;
    private ProgressBar progressBar;
    private MaterialButton backButton;
    private MaterialButton resetButton;
    private MaterialButton rotateLeftButton;
    private MaterialButton rotateRightButton;
    private MaterialButton cropButton;
    private MaterialButton documentButton;
    private MaterialButton doneButton;
    private MaterialButton cancelButton;
    private MaterialButton applyButton;
    private View normalTools;
    private View editActions;
    private RecyclerView pageStrip;
    private PageEditStripAdapter pageStripAdapter;

    private long currentPageId;
    private EditMode editMode = EditMode.NORMAL;
    private String activeLoadKey;
    private Bitmap currentBitmap;

    public static PageEditFragment newInstance(long pageId) {
        PageEditFragment fragment = new PageEditFragment();
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
        thumbnailLoader = new ThumbnailLoader(
                requireContext().getApplicationContext().getContentResolver(),
                ContextCompat.getMainExecutor(requireContext())
        );
        currentPageId = savedInstanceState != null
                ? savedInstanceState.getLong(STATE_PAGE_ID)
                : requireArguments().getLong(ARG_PAGE_ID);
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.fragment_page_edit, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bindViews(view);
        configurePageStrip();
        configureClickListeners();
        generationStateObserver = state -> updateActionAvailability();
        sessionViewModel.addPdfGenerationStateObserver(generationStateObserver);
        renderCurrentPage();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putLong(STATE_PAGE_ID, currentPageId);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onDestroyView() {
        if (generationStateObserver != null) {
            sessionViewModel.removePdfGenerationStateObserver(generationStateObserver);
            generationStateObserver = null;
        }
        activeLoadKey = null;
        releaseCurrentBitmap();
        imageView = null;
        titleView = null;
        counterView = null;
        errorView = null;
        progressBar = null;
        backButton = null;
        resetButton = null;
        rotateLeftButton = null;
        rotateRightButton = null;
        cropButton = null;
        documentButton = null;
        doneButton = null;
        cancelButton = null;
        applyButton = null;
        normalTools = null;
        editActions = null;
        pageStrip = null;
        pageStripAdapter = null;
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        previewImageLoader.shutdown();
        thumbnailLoader.shutdown();
        super.onDestroy();
    }

    public void handleBackPressed() {
        if (editMode != EditMode.NORMAL) {
            setEditMode(EditMode.NORMAL);
            return;
        }
        closeEditor();
    }

    private void bindViews(View view) {
        imageView = view.findViewById(R.id.image_page_edit);
        titleView = view.findViewById(R.id.text_page_edit_title);
        counterView = view.findViewById(R.id.text_page_edit_counter);
        errorView = view.findViewById(R.id.text_page_edit_error);
        progressBar = view.findViewById(R.id.progress_page_edit);
        backButton = view.findViewById(R.id.button_page_edit_back);
        resetButton = view.findViewById(R.id.button_page_edit_reset);
        rotateLeftButton = view.findViewById(R.id.button_page_edit_rotate_left);
        rotateRightButton = view.findViewById(R.id.button_page_edit_rotate_right);
        cropButton = view.findViewById(R.id.button_page_edit_crop);
        documentButton = view.findViewById(R.id.button_page_edit_document);
        doneButton = view.findViewById(R.id.button_page_edit_done);
        cancelButton = view.findViewById(R.id.button_page_edit_cancel);
        applyButton = view.findViewById(R.id.button_page_edit_apply);
        normalTools = view.findViewById(R.id.layout_page_edit_normal_tools);
        editActions = view.findViewById(R.id.layout_page_edit_actions);
        pageStrip = view.findViewById(R.id.recycler_page_edit_strip);
    }

    private void configurePageStrip() {
        pageStripAdapter = new PageEditStripAdapter(
                sessionViewModel.getPages(),
                thumbnailLoader,
                currentPageId,
                this::selectPage
        );
        pageStrip.setLayoutManager(new LinearLayoutManager(
                requireContext(),
                RecyclerView.HORIZONTAL,
                false
        ));
        pageStrip.setAdapter(pageStripAdapter);
    }

    private void configureClickListeners() {
        backButton.setOnClickListener(view -> handleBackPressed());
        resetButton.setOnClickListener(view -> resetCurrentMode());
        rotateLeftButton.setOnClickListener(view -> rotateCurrentPage(false));
        rotateRightButton.setOnClickListener(view -> rotateCurrentPage(true));
        cropButton.setOnClickListener(view -> setEditMode(EditMode.RECT_CROP));
        documentButton.setOnClickListener(view -> setEditMode(EditMode.DOCUMENT));
        doneButton.setOnClickListener(view -> closeEditor());
        cancelButton.setOnClickListener(view -> setEditMode(EditMode.NORMAL));
        applyButton.setOnClickListener(view -> setEditMode(EditMode.NORMAL));
    }

    private void renderCurrentPage() {
        List<PageItem> pages = sessionViewModel.getPages();
        int position = PreviewPageNavigator.findPositionById(pages, currentPageId);
        if (position == PreviewPageNavigator.POSITION_NOT_FOUND) {
            closeEditor();
            return;
        }
        PageItem page = pages.get(position);
        int pageNumber = position + 1;
        titleView.setText(resolveTitle(pageNumber));
        counterView.setText(getString(R.string.page_edit_counter, pageNumber, pages.size()));
        imageView.setContentDescription(
                getString(R.string.page_edit_image_content_description, pageNumber)
        );
        pageStripAdapter.setSelectedPageId(currentPageId);
        pageStrip.scrollToPosition(position);
        errorView.setVisibility(View.GONE);
        loadPreviewWhenMeasured(page);
        updateActionAvailability();
    }

    private CharSequence resolveTitle(int pageNumber) {
        if (editMode == EditMode.RECT_CROP) {
            return getString(R.string.page_edit_crop_title);
        }
        if (editMode == EditMode.DOCUMENT) {
            return getString(R.string.page_edit_document_title);
        }
        return getString(R.string.page_edit_title, pageNumber);
    }

    private void loadPreviewWhenMeasured(PageItem page) {
        if (imageView.getWidth() > 0 && imageView.getHeight() > 0) {
            loadPreview(page);
            return;
        }
        imageView.post(() -> {
            if (imageView != null) {
                loadPreview(page);
            }
        });
    }

    private void loadPreview(PageItem page) {
        if (imageView == null || imageView.getWidth() <= 0 || imageView.getHeight() <= 0) {
            return;
        }
        releaseCurrentBitmap();
        progressBar.setVisibility(View.VISIBLE);
        int targetWidth = imageView.getWidth() * PREVIEW_ZOOM_RESERVE;
        int targetHeight = imageView.getHeight() * PREVIEW_ZOOM_RESERVE;
        activeLoadKey = PreviewImageLoader.buildKey(page, targetWidth, targetHeight);
        previewImageLoader.load(page, targetWidth, targetHeight, new PreviewImageLoader.Callback() {
            @Override
            public void onLoaded(String key, Bitmap bitmap) {
                handlePreviewLoaded(key, bitmap);
            }

            @Override
            public void onError(String key) {
                handlePreviewError(key);
            }
        });
    }

    private void handlePreviewLoaded(String key, Bitmap bitmap) {
        if (imageView == null || !key.equals(activeLoadKey)) {
            recycle(bitmap);
            return;
        }
        progressBar.setVisibility(View.GONE);
        errorView.setVisibility(View.GONE);
        Bitmap oldBitmap = currentBitmap;
        currentBitmap = bitmap;
        imageView.setImageBitmap(bitmap);
        recycle(oldBitmap);
    }

    private void handlePreviewError(String key) {
        if (imageView == null || !key.equals(activeLoadKey)) {
            return;
        }
        progressBar.setVisibility(View.GONE);
        errorView.setVisibility(View.VISIBLE);
        releaseCurrentBitmap();
    }

    private void selectPage(long pageId) {
        if (currentPageId == pageId) {
            return;
        }
        editMode = EditMode.NORMAL;
        currentPageId = pageId;
        imageView.resetZoom();
        updateModeViews();
        renderCurrentPage();
    }

    private void rotateCurrentPage(boolean clockwise) {
        if (!sessionViewModel.canEditPages()) {
            return;
        }
        if (clockwise) {
            sessionViewModel.rotatePageRight(currentPageId);
        } else {
            sessionViewModel.rotatePageLeft(currentPageId);
        }
        publishEditResult();
        pageStripAdapter.notifyPageChanged(currentPageId);
        imageView.resetZoom();
        renderCurrentPage();
    }

    private void resetCurrentMode() {
        if (editMode != EditMode.NORMAL || !sessionViewModel.canEditPages()) {
            return;
        }
        sessionViewModel.resetPageEdits(currentPageId);
        publishEditResult();
        pageStripAdapter.notifyPageChanged(currentPageId);
        imageView.resetZoom();
        renderCurrentPage();
    }

    private void setEditMode(EditMode newMode) {
        if (newMode != EditMode.NORMAL && !sessionViewModel.canEditPages()) {
            return;
        }
        editMode = newMode;
        imageView.resetZoom();
        updateModeViews();
        renderCurrentPage();
    }

    private void updateModeViews() {
        boolean normal = editMode == EditMode.NORMAL;
        normalTools.setVisibility(normal ? View.VISIBLE : View.GONE);
        editActions.setVisibility(normal ? View.GONE : View.VISIBLE);
        pageStrip.setVisibility(normal ? View.VISIBLE : View.GONE);
        counterView.setVisibility(normal ? View.VISIBLE : View.GONE);
        imageView.setGesturesEnabled(normal);
    }

    private void updateActionAvailability() {
        if (rotateLeftButton == null) {
            return;
        }
        boolean enabled = sessionViewModel.canEditPages();
        rotateLeftButton.setEnabled(enabled);
        rotateRightButton.setEnabled(enabled);
        cropButton.setEnabled(enabled);
        documentButton.setEnabled(enabled);
        resetButton.setEnabled(enabled);
        applyButton.setEnabled(enabled);
    }

    private void publishEditResult() {
        Bundle result = new Bundle();
        result.putLong(RESULT_KEY_PAGE_ID, currentPageId);
        getParentFragmentManager().setFragmentResult(RESULT_PAGE_EDITED, result);
    }

    private void releaseCurrentBitmap() {
        if (imageView != null) {
            imageView.setImageBitmap(null);
        }
        recycle(currentBitmap);
        currentBitmap = null;
    }

    private static void recycle(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }

    private void closeEditor() {
        FragmentManager fragmentManager = getParentFragmentManager();
        if (fragmentManager.getBackStackEntryCount() > 0) {
            fragmentManager.popBackStack();
        } else {
            fragmentManager.beginTransaction().remove(this).commit();
        }
    }

    private enum EditMode {
        NORMAL,
        RECT_CROP,
        DOCUMENT
    }
}
