package com.desperadoboi.imagetopdf.ui.editor;

import android.graphics.Bitmap;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
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
import com.desperadoboi.imagetopdf.image.PageProcessingMode;
import com.desperadoboi.imagetopdf.image.PreviewImageLoader;
import com.desperadoboi.imagetopdf.image.ThumbnailLoader;
import com.desperadoboi.imagetopdf.model.CropRect;
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
    private RectCropOverlayView rectCropOverlay;
    private TextView titleView;
    private TextView counterView;
    private TextView errorView;
    private ProgressBar progressBar;
    private MaterialButton backButton;
    private MaterialButton resetButton;
    private ImageButton rotateLeftButton;
    private ImageButton rotateRightButton;
    private MaterialButton cropButton;
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
        rectCropOverlay = null;
        titleView = null;
        counterView = null;
        errorView = null;
        progressBar = null;
        backButton = null;
        resetButton = null;
        rotateLeftButton = null;
        rotateRightButton = null;
        cropButton = null;
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
        rectCropOverlay = view.findViewById(R.id.overlay_rect_crop);
        titleView = view.findViewById(R.id.text_page_edit_title);
        counterView = view.findViewById(R.id.text_page_edit_counter);
        errorView = view.findViewById(R.id.text_page_edit_error);
        progressBar = view.findViewById(R.id.progress_page_edit);
        backButton = view.findViewById(R.id.button_page_edit_back);
        resetButton = view.findViewById(R.id.button_page_edit_reset);
        rotateLeftButton = view.findViewById(R.id.button_page_edit_rotate_left);
        rotateRightButton = view.findViewById(R.id.button_page_edit_rotate_right);
        cropButton = view.findViewById(R.id.button_page_edit_crop);
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
        doneButton.setOnClickListener(view -> closeEditor());
        cancelButton.setOnClickListener(view -> setEditMode(EditMode.NORMAL));
        applyButton.setOnClickListener(view -> applyCurrentMode());
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
        rectCropOverlay.clearImageContentRect();
        progressBar.setVisibility(View.VISIBLE);
        int targetWidth = imageView.getWidth() * PREVIEW_ZOOM_RESERVE;
        int targetHeight = imageView.getHeight() * PREVIEW_ZOOM_RESERVE;
        PageProcessingMode processingMode = resolveProcessingMode();
        activeLoadKey = PreviewImageLoader.buildKey(
                page,
                targetWidth,
                targetHeight,
                processingMode
        );
        previewImageLoader.load(
                page,
                targetWidth,
                targetHeight,
                processingMode,
                new PreviewImageLoader.Callback() {
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
        updateOverlayContentRect();
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
        if (!sessionViewModel.canEditPages()) {
            return;
        }
        if (editMode == EditMode.RECT_CROP) {
            rectCropOverlay.setCropRect(CropRect.FULL);
            return;
        }
        if (editMode != EditMode.NORMAL) {
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
        if (newMode == EditMode.RECT_CROP) {
            PageItem page = findCurrentPage();
            if (page != null) {
                rectCropOverlay.setCropRect(page.getEditSpec().getCropRect());
            }
        }
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
        rotateLeftButton.setVisibility(normal ? View.VISIBLE : View.GONE);
        rotateRightButton.setVisibility(normal ? View.VISIBLE : View.GONE);
        imageView.setGesturesEnabled(normal);
        rectCropOverlay.setVisibility(
                editMode == EditMode.RECT_CROP ? View.VISIBLE : View.GONE
        );
        backButton.setContentDescription(getString(normal
                ? R.string.action_page_edit_back_content_description
                : R.string.action_crop_cancel_content_description));
        resetButton.setContentDescription(getString(normal
                ? R.string.action_page_edit_reset_content_description
                : R.string.action_crop_reset_content_description));
    }

    private void updateActionAvailability() {
        if (rotateLeftButton == null) {
            return;
        }
        boolean enabled = sessionViewModel.canEditPages();
        rotateLeftButton.setEnabled(enabled);
        rotateRightButton.setEnabled(enabled);
        cropButton.setEnabled(enabled);
        resetButton.setEnabled(enabled);
        applyButton.setEnabled(enabled);
    }

    private void publishEditResult() {
        Bundle result = new Bundle();
        result.putLong(RESULT_KEY_PAGE_ID, currentPageId);
        getParentFragmentManager().setFragmentResult(RESULT_PAGE_EDITED, result);
    }

    private void applyCurrentMode() {
        if (!sessionViewModel.canEditPages()) {
            return;
        }
        if (editMode == EditMode.RECT_CROP) {
            sessionViewModel.updatePageCrop(currentPageId, rectCropOverlay.getCropRect());
            publishEditResult();
            pageStripAdapter.notifyPageChanged(currentPageId);
        }
        setEditMode(EditMode.NORMAL);
    }

    private PageItem findCurrentPage() {
        int position = PreviewPageNavigator.findPositionById(
                sessionViewModel.getPages(),
                currentPageId
        );
        if (position == PreviewPageNavigator.POSITION_NOT_FOUND) {
            return null;
        }
        return sessionViewModel.getPages().get(position);
    }

    private void updateOverlayContentRect() {
        imageView.post(() -> {
            if (imageView == null || rectCropOverlay == null) {
                return;
            }
            RectF contentRect = new RectF();
            if (imageView.getImageContentRect(contentRect)) {
                rectCropOverlay.setImageContentRect(contentRect);
            } else {
                rectCropOverlay.clearImageContentRect();
            }
        });
    }

    private PageProcessingMode resolveProcessingMode() {
        if (editMode == EditMode.RECT_CROP) {
            return PageProcessingMode.BEFORE_CROP;
        }
        return PageProcessingMode.FINAL;
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
        RECT_CROP
    }
}
