package com.desperadoboi.imagetopdf.ui.smartscan;

import android.content.Context;
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
import androidx.lifecycle.ViewModelProvider;

import com.desperadoboi.imagetopdf.R;
import com.desperadoboi.imagetopdf.image.CapturedImageStorage;
import com.desperadoboi.imagetopdf.image.PageProcessingMode;
import com.desperadoboi.imagetopdf.image.PreviewImageLoader;
import com.desperadoboi.imagetopdf.model.PageItem;
import com.desperadoboi.imagetopdf.model.PerspectiveQuad;
import com.desperadoboi.imagetopdf.ui.editor.DocumentPerspectiveOverlayView;
import com.desperadoboi.imagetopdf.ui.editor.ZoomableImageView;
import com.desperadoboi.imagetopdf.ui.idcard.IdCardCacheStorage;
import com.desperadoboi.imagetopdf.ui.idcard.IdCardEdgeDetector;
import com.desperadoboi.imagetopdf.ui.idcard.IdCardError;
import com.desperadoboi.imagetopdf.ui.idcard.IdCardScanViewModel;
import com.desperadoboi.imagetopdf.ui.idcard.IdCardSide;
import com.google.android.material.button.MaterialButton;

public final class ScanReviewFragment extends Fragment {
    public static final String TAG = "ScanReviewFragment";
    public static final String TAG_ID_CARD = "IdCardPerspectiveReviewFragment";

    private static final String ARG_ID_CARD_SIDE = "id_card_side";
    private static final String STATE_EDGE_SUGGESTED = "id_card_edge_suggested";

    private static final int PREVIEW_ZOOM_RESERVE = 2;

    private ScanSessionViewModel sessionViewModel;
    private IdCardScanViewModel idCardViewModel;
    private IdCardSide idCardSide;
    private boolean idCardMode;
    private NavigationCallback navigationCallback;
    private PreviewImageLoader previewImageLoader;
    private CapturedImageStorage capturedImageStorage;
    private IdCardCacheStorage idCardCacheStorage;

    private ZoomableImageView imageView;
    private DocumentPerspectiveOverlayView overlayView;
    private TextView titleView;
    private TextView errorView;
    private ProgressBar progressBar;
    private MaterialButton addButton;
    private MaterialButton autoButton;
    private MaterialButton originalButton;
    private TextView hintView;

    private Bitmap currentBitmap;
    private String activeLoadKey;
    private boolean previewReady;
    private boolean edgeSuggestionAttempted;

    public static ScanReviewFragment newIdCardInstance(IdCardSide side) {
        Bundle arguments = new Bundle();
        arguments.putString(ARG_ID_CARD_SIDE, side.name());
        ScanReviewFragment fragment = new ScanReviewFragment();
        fragment.setArguments(arguments);
        return fragment;
    }

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
        Bundle arguments = getArguments();
        String sideName = arguments == null ? null : arguments.getString(ARG_ID_CARD_SIDE);
        if (sideName != null) {
            idCardSide = IdCardSide.valueOf(sideName);
            idCardMode = true;
            idCardViewModel = new ViewModelProvider(requireActivity())
                    .get(IdCardScanViewModel.class);
        }
        if (savedInstanceState != null) {
            edgeSuggestionAttempted = savedInstanceState.getBoolean(
                    STATE_EDGE_SUGGESTED,
                    false
            );
        }
        sessionViewModel = new ViewModelProvider(requireActivity())
                .get(ScanSessionViewModel.class);
        previewImageLoader = new PreviewImageLoader(
                requireContext().getApplicationContext().getContentResolver(),
                ContextCompat.getMainExecutor(requireContext())
        );
        capturedImageStorage = new CapturedImageStorage(requireContext());
        idCardCacheStorage = new IdCardCacheStorage(requireContext());
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.fragment_scan_review, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bindViews(view);
        configureActions(view);
        if (idCardMode) {
            configureIdCardPresentation(view);
        }
        ScanPage page = currentPage();
        if (page == null) {
            closeReview(false);
            return;
        }
        renderPage(page);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        saveOverlayState();
        if (idCardMode) outState.putBoolean(STATE_EDGE_SUGGESTED, edgeSuggestionAttempted);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onDestroyView() {
        activeLoadKey = null;
        releaseCurrentBitmap();
        imageView = null;
        overlayView = null;
        titleView = null;
        errorView = null;
        progressBar = null;
        addButton = null;
        autoButton = null;
        originalButton = null;
        hintView = null;
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        previewImageLoader.shutdown();
        super.onDestroy();
    }

    @Override
    public void onDetach() {
        navigationCallback = null;
        super.onDetach();
    }

    public void handleBackPressed() {
        discardPendingAndClose();
    }

    private void bindViews(View view) {
        imageView = view.findViewById(R.id.image_scan_review);
        overlayView = view.findViewById(R.id.overlay_scan_review);
        titleView = view.findViewById(R.id.text_scan_review_title);
        errorView = view.findViewById(R.id.text_scan_review_error);
        progressBar = view.findViewById(R.id.progress_scan_review);
        addButton = view.findViewById(R.id.button_scan_review_add);
        autoButton = view.findViewById(R.id.button_scan_review_auto);
        originalButton = view.findViewById(R.id.button_scan_review_original);
        hintView = view.findViewById(R.id.text_scan_review_hint);
        imageView.setGesturesEnabled(false);
        overlayView.setEdgeHandlesEnabled(false);
    }

    private void configureIdCardPresentation(View view) {
        titleView.setText(idCardSide == IdCardSide.FRONT
                ? R.string.id_card_correct_front_title
                : R.string.id_card_correct_back_title);
        autoButton.setText(R.string.id_card_find_edges);
        originalButton.setVisibility(View.GONE);
        addButton.setText(R.string.id_card_apply_correction);
        view.findViewById(R.id.button_scan_review_retake).setContentDescription(
                getString(R.string.id_card_action_retry)
        );
        view.findViewById(R.id.button_scan_review_back).setContentDescription(
                getString(R.string.id_card_review_back_description)
        );
        view.findViewById(R.id.button_scan_review_delete).setContentDescription(
                getString(R.string.id_card_review_delete_description)
        );
    }

    private void configureActions(View view) {
        ImageButton backButton = view.findViewById(R.id.button_scan_review_back);
        ImageButton deleteButton = view.findViewById(R.id.button_scan_review_delete);
        backButton.setOnClickListener(ignored -> handleBackPressed());
        deleteButton.setOnClickListener(ignored -> discardPendingAndClose());
        view.findViewById(R.id.button_scan_review_retake).setOnClickListener(
                ignored -> discardPendingAndClose()
        );
        view.findViewById(R.id.button_scan_review_rotate).setOnClickListener(
                ignored -> rotatePage()
        );
        autoButton.setOnClickListener(ignored -> resetDefaultCrop());
        originalButton.setOnClickListener(ignored -> useOriginal());
        addButton.setOnClickListener(ignored -> addPage());
    }

    private void renderPage(ScanPage page) {
        int pageNumber = sessionViewModel.getState().getPageCount() + 1;
        if (idCardMode) {
            titleView.setText(idCardSide == IdCardSide.FRONT
                    ? R.string.id_card_correct_front_title
                    : R.string.id_card_correct_back_title);
            imageView.setContentDescription(idCardSide == IdCardSide.FRONT
                    ? getString(R.string.id_card_front_preview_description)
                    : getString(R.string.id_card_back_preview_description));
        } else {
            titleView.setText(getString(R.string.scan_review_title, pageNumber));
            imageView.setContentDescription(
                    getString(R.string.scan_review_image_content_description, pageNumber)
            );
        }
        overlayView.setPerspectiveQuad(page.getPerspectiveQuad());
        overlayView.setVisibility(page.isOriginal() ? View.GONE : View.VISIBLE);
        autoButton.setSelected(!page.isOriginal());
        originalButton.setSelected(page.isOriginal());
        loadPreviewWhenMeasured(page);
    }

    private void loadPreviewWhenMeasured(ScanPage page) {
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

    private void loadPreview(ScanPage scanPage) {
        if (imageView == null || imageView.getWidth() <= 0 || imageView.getHeight() <= 0) {
            return;
        }
        previewReady = false;
        addButton.setEnabled(false);
        errorView.setVisibility(View.GONE);
        hintView.setVisibility(View.GONE);
        progressBar.setVisibility(View.VISIBLE);
        overlayView.clearImageContentRect();
        releaseCurrentBitmap();
        PageItem pageItem;
        try {
            pageItem = scanPage.toPageItem();
        } catch (RuntimeException exception) {
            showLoadError();
            return;
        }
        int targetWidth = imageView.getWidth() * PREVIEW_ZOOM_RESERVE;
        int targetHeight = imageView.getHeight() * PREVIEW_ZOOM_RESERVE;
        activeLoadKey = PreviewImageLoader.buildKey(
                pageItem,
                targetWidth,
                targetHeight,
                PageProcessingMode.ORIENTED_ONLY
        );
        previewImageLoader.load(
                pageItem,
                targetWidth,
                targetHeight,
                PageProcessingMode.ORIENTED_ONLY,
                new PreviewImageLoader.Callback() {
                    @Override
                    public void onLoaded(String key, Bitmap bitmap) {
                        handlePreviewLoaded(key, bitmap);
                    }

                    @Override
                    public void onError(String key) {
                        if (key.equals(activeLoadKey)) {
                            showLoadError();
                            if (idCardMode) {
                                deleteIdCardFiles(idCardViewModel.failSideOperation(
                                        idCardSide,
                                        IdCardError.OPEN_IMAGE
                                ));
                                imageView.post(() -> closeReview(false));
                            }
                        }
                    }
                }
        );
    }

    private void handlePreviewLoaded(String key, Bitmap bitmap) {
        if (imageView == null || !key.equals(activeLoadKey)) {
            recycle(bitmap);
            return;
        }
        Bitmap previous = currentBitmap;
        currentBitmap = bitmap;
        imageView.setImageBitmap(bitmap);
        recycle(previous);
        progressBar.setVisibility(View.GONE);
        errorView.setVisibility(View.GONE);
        previewReady = true;
        addButton.setEnabled(true);
        updateOverlayContentRect();
        if (idCardMode && !edgeSuggestionAttempted) {
            edgeSuggestionAttempted = true;
            suggestCardEdges();
        }
    }

    private void updateOverlayContentRect() {
        imageView.post(() -> {
            if (imageView == null || overlayView == null) {
                return;
            }
            RectF contentRect = new RectF();
            if (imageView.getImageContentRect(contentRect)) {
                overlayView.setImageContentRect(contentRect);
            } else {
                overlayView.clearImageContentRect();
            }
        });
    }

    private void rotatePage() {
        ScanPage page = saveOverlayState();
        if (page == null) {
            return;
        }
        ScanPage rotated = page.rotateClockwise();
        if (updatePendingPage(rotated)) {
            imageView.resetZoom();
            edgeSuggestionAttempted = false;
            renderPage(rotated);
        }
    }

    private void resetDefaultCrop() {
        ScanPage page = currentPage();
        if (page == null) {
            return;
        }
        if (idCardMode) {
            suggestCardEdges();
            return;
        }
        page = page.withDefaultCrop();
        if (updatePendingPage(page)) {
            overlayView.setPerspectiveQuad(page.getPerspectiveQuad());
            overlayView.setVisibility(View.VISIBLE);
            updateOverlayContentRect();
            autoButton.setSelected(true);
            originalButton.setSelected(false);
        }
    }

    private void useOriginal() {
        if (idCardMode) {
            return;
        }
        ScanPage page = currentPage();
        if (page == null) {
            return;
        }
        page = page.withOriginal();
        if (updatePendingPage(page)) {
            overlayView.setPerspectiveQuad(page.getPerspectiveQuad());
            overlayView.setVisibility(View.GONE);
            autoButton.setSelected(false);
            originalButton.setSelected(true);
        }
    }

    private void addPage() {
        if (!previewReady) {
            return;
        }
        if (saveOverlayState() == null) {
            return;
        }
        if (idCardMode) {
            deleteIdCardFiles(idCardViewModel.acceptReview(idCardSide));
            closeReview(true);
            return;
        }
        if (!sessionViewModel.addPendingPage()) {
            return;
        }
        closeReview(true);
    }

    private ScanPage saveOverlayState() {
        ScanPage page = currentPage();
        if (page == null || page.isOriginal() || overlayView == null) {
            return page;
        }
        ScanPage updated = page.withPerspectiveQuad(overlayView.getPerspectiveQuad());
        updatePendingPage(updated);
        return updated;
    }

    private void discardPendingAndClose() {
        if (idCardMode) {
            deleteIdCardFiles(idCardViewModel.cancelSideOperation(idCardSide));
            closeReview(false);
            return;
        }
        ScanPage page = sessionViewModel.retakePendingPage();
        if (page != null && page.isAppOwned()) {
            capturedImageStorage.delete(page.getCapturedFileName());
        }
        closeReview(false);
    }

    private ScanPage currentPage() {
        return idCardMode
                ? idCardViewModel.getReviewPage(idCardSide)
                : sessionViewModel.getState().getCurrentReviewPage();
    }

    private void suggestCardEdges() {
        if (!idCardMode || currentBitmap == null || currentBitmap.isRecycled()) {
            return;
        }
        PerspectiveQuad detected = IdCardEdgeDetector.detect(currentBitmap);
        ScanPage page = currentPage();
        if (page == null) return;
        if (detected == null) {
            PerspectiveQuad fallback = IdCardEdgeDetector.defaultQuadFor(
                    currentBitmap.getWidth(),
                    currentBitmap.getHeight()
            );
            hintView.setText(R.string.id_card_edges_manual_hint);
            hintView.setVisibility(View.VISIBLE);
            overlayView.setPerspectiveQuad(fallback);
            updatePendingPage(page.withPerspectiveQuad(fallback));
            return;
        }
        hintView.setVisibility(View.GONE);
        overlayView.setPerspectiveQuad(detected);
        updatePendingPage(page.withPerspectiveQuad(detected));
    }

    private boolean updatePendingPage(ScanPage page) {
        return idCardMode
                ? idCardViewModel.updateReviewPage(idCardSide, page)
                : sessionViewModel.updatePendingPage(page);
    }

    private void closeReview(boolean accepted) {
        if (navigationCallback == null) return;
        if (idCardMode) {
            navigationCallback.onIdCardReviewClosed(idCardSide, accepted);
        } else {
            navigationCallback.onScanReviewClosed();
        }
    }

    private void showLoadError() {
        if (progressBar == null) {
            return;
        }
        progressBar.setVisibility(View.GONE);
        errorView.setVisibility(View.VISIBLE);
        previewReady = false;
        addButton.setEnabled(false);
        releaseCurrentBitmap();
    }

    private void releaseCurrentBitmap() {
        if (imageView != null) {
            imageView.setImageBitmap(null);
        }
        recycle(currentBitmap);
        currentBitmap = null;
    }

    private void deleteIdCardFiles(java.util.List<String> fileNames) {
        for (String fileName : fileNames) {
            idCardCacheStorage.delete(fileName);
        }
    }

    private static void recycle(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }

    public interface NavigationCallback {
        void onScanReviewClosed();

        void onIdCardReviewClosed(IdCardSide side, boolean accepted);
    }
}
