package com.desperadoboi.imagetopdf;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.desperadoboi.imagetopdf.image.ThumbnailLoader;
import com.desperadoboi.imagetopdf.model.ImagePlacementMode;
import com.desperadoboi.imagetopdf.model.MarginPreset;
import com.desperadoboi.imagetopdf.model.PageSizeMode;
import com.desperadoboi.imagetopdf.model.PageItem;
import com.desperadoboi.imagetopdf.model.PageOrderManager;
import com.desperadoboi.imagetopdf.model.PdfOptions;
import com.desperadoboi.imagetopdf.model.PdfResult;
import com.desperadoboi.imagetopdf.pdf.PdfGenerationCallback;
import com.desperadoboi.imagetopdf.pdf.PdfGenerator;
import com.desperadoboi.imagetopdf.ui.PageAdapter;
import com.desperadoboi.imagetopdf.util.FileSizeFormatter;
import com.google.android.material.button.MaterialButton;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final String PDF_MIME_TYPE = "application/pdf";
    private static final String STATE_PDF_RESULT_URI = "pdf_result_uri";
    private static final String STATE_PDF_RESULT_DISPLAY_NAME = "pdf_result_display_name";
    private static final String STATE_PDF_RESULT_SIZE_BYTES = "pdf_result_size_bytes";
    private static final String STATE_PDF_RESULT_PAGE_COUNT = "pdf_result_page_count";

    private MaterialButton selectImagesButton;
    private MaterialButton createPdfButton;
    private TextView selectionStatusTextView;
    private TextView pageDragHintTextView;
    private TextView operationStatusTextView;
    private View pdfResultLayout;
    private TextView pdfResultNameTextView;
    private TextView pdfResultDetailsTextView;
    private Button openPdfButton;
    private Button sharePdfButton;
    private Button newDocumentButton;
    private ProgressBar progressBar;
    private RecyclerView pagesRecyclerView;
    private Spinner pageSizeSpinner;
    private Spinner imagePlacementSpinner;
    private Spinner marginSpinner;

    private ActivityResultLauncher<PickVisualMediaRequest> photoPickerLauncher;
    private ActivityResultLauncher<String> createDocumentLauncher;

    private final List<PageItem> pageItems = new ArrayList<>();
    private PdfOptions pdfOptions = PdfOptions.defaults();
    private PdfResult lastPdfResult;

    private ExecutorService pdfExecutor;
    private PdfGenerator pdfGenerator;
    private ThumbnailLoader thumbnailLoader;
    private PageAdapter pageAdapter;
    private ItemTouchHelper pageTouchHelper;

    private boolean awaitingSaveLocation;
    private boolean generationInProgress;
    private boolean activityDestroyed;
    private String transientStatusMessage;
    private String pendingSuggestedFileName;
    private int pagesVersion;
    private int submittedPagesVersion = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        pdfExecutor = Executors.newSingleThreadExecutor();
        pdfGenerator = new PdfGenerator(getApplicationContext().getContentResolver());
        thumbnailLoader = new ThumbnailLoader(
                getApplicationContext().getContentResolver(),
                ContextCompat.getMainExecutor(this)
        );

        bindViews();
        configurePageList();
        configurePdfSettings();
        registerActivityResultLaunchers();
        configureWindowInsets();
        configureClickListeners();
        restoreSavedState(savedInstanceState);
        updateUiState();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (lastPdfResult == null) {
            return;
        }

        outState.putString(STATE_PDF_RESULT_URI, lastPdfResult.getUri().toString());
        outState.putString(STATE_PDF_RESULT_DISPLAY_NAME, lastPdfResult.getDisplayName());
        outState.putLong(STATE_PDF_RESULT_SIZE_BYTES, lastPdfResult.getSizeBytes());
        outState.putInt(STATE_PDF_RESULT_PAGE_COUNT, lastPdfResult.getPageCount());
    }

    @Override
    protected void onDestroy() {
        activityDestroyed = true;
        if (thumbnailLoader != null) {
            thumbnailLoader.shutdown();
        }
        if (pdfExecutor != null) {
            pdfExecutor.shutdownNow();
        }
        super.onDestroy();
    }

    private void bindViews() {
        selectImagesButton = findViewById(R.id.button_select_images);
        createPdfButton = findViewById(R.id.button_create_pdf);
        selectionStatusTextView = findViewById(R.id.text_selection_status);
        pageDragHintTextView = findViewById(R.id.text_page_drag_hint);
        operationStatusTextView = findViewById(R.id.text_operation_status);
        pdfResultLayout = findViewById(R.id.layout_pdf_result);
        pdfResultNameTextView = findViewById(R.id.text_pdf_result_name);
        pdfResultDetailsTextView = findViewById(R.id.text_pdf_result_details);
        openPdfButton = findViewById(R.id.button_open_pdf);
        sharePdfButton = findViewById(R.id.button_share_pdf);
        newDocumentButton = findViewById(R.id.button_new_document);
        progressBar = findViewById(R.id.progress_generation);
        pagesRecyclerView = findViewById(R.id.recycler_pages);
        pageSizeSpinner = findViewById(R.id.spinner_pdf_page_size);
        imagePlacementSpinner = findViewById(R.id.spinner_pdf_image_placement);
        marginSpinner = findViewById(R.id.spinner_pdf_margin);
    }

    private void configurePageList() {
        pageAdapter = new PageAdapter(
                thumbnailLoader,
                new PageAdapter.PageActionCallback() {
                    @Override
                    public void onRotate(int position) {
                        rotatePage(position);
                    }

                    @Override
                    public void onDelete(int position) {
                        deletePage(position);
                    }

                    @Override
                    public void onDragStart(RecyclerView.ViewHolder viewHolder) {
                        startPageDrag(viewHolder);
                    }

                    @Override
                    public boolean onMoveUp(int position) {
                        return movePage(position, position - 1);
                    }

                    @Override
                    public boolean onMoveDown(int position) {
                        return movePage(position, position + 1);
                    }
                }
        );
        pagesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        pagesRecyclerView.setAdapter(pageAdapter);
        pageTouchHelper = new ItemTouchHelper(new PageMoveCallback());
        pageTouchHelper.attachToRecyclerView(pagesRecyclerView);
    }

    private void configurePdfSettings() {
        setSpinnerSelectionsFromOptions(pdfOptions);

        AdapterView.OnItemSelectedListener optionsListener =
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(
                            AdapterView<?> parent,
                            View view,
                            int position,
                            long id
                    ) {
                        updatePdfOptionsFromControls();
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                    }
                };

        pageSizeSpinner.setOnItemSelectedListener(optionsListener);
        imagePlacementSpinner.setOnItemSelectedListener(optionsListener);
        marginSpinner.setOnItemSelectedListener(optionsListener);
    }

    private void registerActivityResultLaunchers() {
        photoPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.PickMultipleVisualMedia(),
                uris -> {
                    if (uris != null && !uris.isEmpty()) {
                        for (Uri uri : uris) {
                            pageItems.add(new PageItem(uri));
                        }
                        pagesVersion++;
                        transientStatusMessage = null;
                    } else {
                        transientStatusMessage = getString(R.string.status_image_selection_cancelled);
                    }
                    updateUiState();
                }
        );

        createDocumentLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument(PDF_MIME_TYPE),
                outputUri -> {
                    awaitingSaveLocation = false;
                    if (outputUri == null) {
                        pendingSuggestedFileName = null;
                        transientStatusMessage = getString(R.string.status_save_location_cancelled);
                        updateUiState();
                        return;
                    }
                    startPdfGeneration(outputUri);
                }
        );
    }

    private void configureWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(
                    systemBars.left + getResources().getDimensionPixelSize(R.dimen.screen_content_padding),
                    systemBars.top + getResources().getDimensionPixelSize(R.dimen.screen_content_padding),
                    systemBars.right + getResources().getDimensionPixelSize(R.dimen.screen_content_padding),
                    systemBars.bottom + getResources().getDimensionPixelSize(R.dimen.screen_content_padding)
            );
            return insets;
        });
    }

    private void configureClickListeners() {
        selectImagesButton.setOnClickListener(v -> launchPhotoPicker());
        createPdfButton.setOnClickListener(v -> launchCreateDocument());
        openPdfButton.setOnClickListener(v -> openLastPdf());
        sharePdfButton.setOnClickListener(v -> shareLastPdf());
        newDocumentButton.setOnClickListener(v -> createNewDocument());
    }

    private void launchPhotoPicker() {
        if (generationInProgress || awaitingSaveLocation) {
            return;
        }
        transientStatusMessage = null;
        PickVisualMediaRequest request = new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build();
        photoPickerLauncher.launch(request);
    }

    private void launchCreateDocument() {
        if (pageItems.isEmpty() || generationInProgress || awaitingSaveLocation) {
            return;
        }
        awaitingSaveLocation = true;
        transientStatusMessage = null;
        pendingSuggestedFileName = buildSuggestedFileName();
        updateUiState();
        createDocumentLauncher.launch(pendingSuggestedFileName);
    }

    private void rotatePage(int position) {
        if (!canEditPages() || position < 0 || position >= pageItems.size()) {
            return;
        }
        pageItems.set(position, pageItems.get(position).rotateClockwise());
        pagesVersion++;
        transientStatusMessage = null;
        updateUiState();
    }

    private void deletePage(int position) {
        if (!canEditPages() || position < 0 || position >= pageItems.size()) {
            return;
        }
        pageItems.remove(position);
        pagesVersion++;
        transientStatusMessage = null;
        updateUiState();
    }

    private void startPageDrag(RecyclerView.ViewHolder viewHolder) {
        if (!canEditPages()) {
            return;
        }
        pageTouchHelper.startDrag(viewHolder);
    }

    private boolean movePage(int fromPosition, int toPosition) {
        if (!canEditPages()
                || fromPosition < 0
                || fromPosition >= pageItems.size()
                || toPosition < 0
                || toPosition >= pageItems.size()) {
            return false;
        }

        boolean moved = PageOrderManager.move(pageItems, fromPosition, toPosition);
        if (!moved) {
            return false;
        }

        pagesVersion++;
        submittedPagesVersion = pagesVersion;
        transientStatusMessage = null;
        pageAdapter.submitMovedPages(pageItems, fromPosition, toPosition);
        return true;
    }

    private boolean canEditPages() {
        return !generationInProgress && !awaitingSaveLocation;
    }

    private void startPdfGeneration(Uri outputUri) {
        generationInProgress = true;
        transientStatusMessage = null;
        List<PageItem> pageSnapshot = new ArrayList<>(pageItems);
        PdfOptions pdfOptionsSnapshot = pdfOptions;
        String fallbackDisplayName = pendingSuggestedFileName;
        int pageCount = pageSnapshot.size();
        updateUiState();

        pdfGenerator.generate(
                pageSnapshot,
                pdfOptionsSnapshot,
                outputUri,
                pdfExecutor,
                ContextCompat.getMainExecutor(this),
                new PdfGenerationCallback() {
                    @Override
                    public void onSuccess(Uri savedUri) {
                        if (!isUiSafe()) {
                            return;
                        }
                        generationInProgress = false;
                        pendingSuggestedFileName = null;
                        lastPdfResult = buildPdfResult(savedUri, fallbackDisplayName, pageCount);
                        transientStatusMessage = getString(R.string.status_pdf_created);
                        updateUiState();
                        showToast(transientStatusMessage);
                    }

                    @Override
                    public void onError(Exception exception) {
                        if (!isUiSafe()) {
                            return;
                        }
                        generationInProgress = false;
                        pendingSuggestedFileName = null;
                        transientStatusMessage = mapErrorMessage(exception);
                        updateUiState();
                        showToast(transientStatusMessage);
                    }
                }
        );
    }

    private void openLastPdf() {
        if (lastPdfResult == null || generationInProgress) {
            return;
        }
        if (!canReadPdfResult(lastPdfResult, R.string.status_pdf_open_error)) {
            return;
        }

        Intent viewIntent = new Intent(Intent.ACTION_VIEW);
        viewIntent.setDataAndType(lastPdfResult.getUri(), PDF_MIME_TYPE);
        grantReadPermission(viewIntent, lastPdfResult);
        try {
            startActivity(viewIntent);
        } catch (ActivityNotFoundException exception) {
            showToast(getString(R.string.status_pdf_open_app_not_found));
        } catch (SecurityException exception) {
            showToast(getString(R.string.status_pdf_open_error));
        }
    }

    private void shareLastPdf() {
        if (lastPdfResult == null || generationInProgress) {
            return;
        }
        if (!canReadPdfResult(lastPdfResult, R.string.status_pdf_share_error)) {
            return;
        }

        Intent sendIntent = new Intent(Intent.ACTION_SEND);
        sendIntent.setType(PDF_MIME_TYPE);
        sendIntent.putExtra(Intent.EXTRA_STREAM, lastPdfResult.getUri());
        grantReadPermission(sendIntent, lastPdfResult);

        Intent chooserIntent = Intent.createChooser(
                sendIntent,
                getString(R.string.pdf_share_chooser_title)
        );
        chooserIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        chooserIntent.setClipData(sendIntent.getClipData());
        try {
            startActivity(chooserIntent);
        } catch (ActivityNotFoundException | SecurityException exception) {
            showToast(getString(R.string.status_pdf_share_error));
        }
    }

    private boolean canReadPdfResult(PdfResult result, int errorStringResId) {
        try (InputStream inputStream = getContentResolver().openInputStream(result.getUri())) {
            if (inputStream == null) {
                showToast(getString(errorStringResId));
                return false;
            }
            return true;
        } catch (IOException | SecurityException exception) {
            showToast(getString(errorStringResId));
            return false;
        }
    }

    private void grantReadPermission(Intent intent, PdfResult result) {
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setClipData(ClipData.newUri(
                getContentResolver(),
                result.getDisplayName(),
                result.getUri()
        ));
    }

    private void createNewDocument() {
        if (generationInProgress || awaitingSaveLocation) {
            return;
        }

        pageItems.clear();
        pagesVersion++;
        lastPdfResult = null;
        transientStatusMessage = null;
        pendingSuggestedFileName = null;
        updateUiState();
    }

    private PdfResult buildPdfResult(Uri savedUri, String fallbackDisplayName, int pageCount) {
        String displayName = normalizeFallbackDisplayName(fallbackDisplayName);
        long sizeBytes = PdfResult.UNKNOWN_SIZE_BYTES;

        PdfMetadata metadata = queryPdfMetadata(savedUri);
        if (metadata.displayName != null && !metadata.displayName.trim().isEmpty()) {
            displayName = metadata.displayName.trim();
        }
        if (metadata.sizeBytes != PdfResult.UNKNOWN_SIZE_BYTES) {
            sizeBytes = metadata.sizeBytes;
        }

        return new PdfResult(savedUri, displayName, sizeBytes, pageCount);
    }

    private PdfMetadata queryPdfMetadata(Uri savedUri) {
        try (Cursor cursor = getContentResolver().query(
                savedUri,
                new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE},
                null,
                null,
                null
        )) {
            if (cursor == null || !cursor.moveToFirst()) {
                return PdfMetadata.unknown();
            }

            String displayName = null;
            long sizeBytes = PdfResult.UNKNOWN_SIZE_BYTES;
            int displayNameColumnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            if (displayNameColumnIndex >= 0 && !cursor.isNull(displayNameColumnIndex)) {
                displayName = cursor.getString(displayNameColumnIndex);
            }

            int sizeColumnIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
            if (sizeColumnIndex >= 0 && !cursor.isNull(sizeColumnIndex)) {
                long cursorSize = cursor.getLong(sizeColumnIndex);
                if (cursorSize >= 0L) {
                    sizeBytes = cursorSize;
                }
            }
            return new PdfMetadata(displayName, sizeBytes);
        } catch (RuntimeException exception) {
            return PdfMetadata.unknown();
        }
    }

    private String normalizeFallbackDisplayName(String fallbackDisplayName) {
        if (fallbackDisplayName == null || fallbackDisplayName.trim().isEmpty()) {
            return getString(R.string.pdf_result_unknown_name);
        }
        return fallbackDisplayName.trim();
    }

    private void restoreSavedState(Bundle savedInstanceState) {
        if (savedInstanceState == null || !savedInstanceState.containsKey(STATE_PDF_RESULT_URI)) {
            return;
        }

        String uriValue = savedInstanceState.getString(STATE_PDF_RESULT_URI);
        if (uriValue == null || uriValue.isEmpty()) {
            return;
        }

        Uri uri = Uri.parse(uriValue);
        String displayName = savedInstanceState.getString(STATE_PDF_RESULT_DISPLAY_NAME, "");
        long sizeBytes = savedInstanceState.getLong(
                STATE_PDF_RESULT_SIZE_BYTES,
                PdfResult.UNKNOWN_SIZE_BYTES
        );
        int pageCount = savedInstanceState.getInt(STATE_PDF_RESULT_PAGE_COUNT, 0);
        lastPdfResult = new PdfResult(uri, displayName, sizeBytes, pageCount);
    }

    private String buildSuggestedFileName() {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
        String timestamp = formatter.format(new Date());
        return getString(R.string.pdf_file_name_template, timestamp);
    }

    private String mapErrorMessage(Exception exception) {
        if (exception instanceof SecurityException) {
            return getString(R.string.status_pdf_creation_permission_error);
        }
        if (exception instanceof IllegalArgumentException) {
            return getString(R.string.status_pdf_creation_invalid_input);
        }
        if (exception instanceof InterruptedIOException) {
            return getString(R.string.status_pdf_creation_interrupted);
        }
        return getString(R.string.status_pdf_creation_error);
    }

    private void updateUiState() {
        boolean controlsEnabled = !generationInProgress && !awaitingSaveLocation;
        boolean hasPages = !pageItems.isEmpty();

        selectImagesButton.setEnabled(controlsEnabled);
        selectImagesButton.setText(hasPages ? R.string.action_add_images : R.string.action_select_images);
        createPdfButton.setEnabled(controlsEnabled && hasPages);
        updatePdfSettingsEnabledState(controlsEnabled);
        progressBar.setVisibility(generationInProgress ? View.VISIBLE : View.GONE);
        pagesRecyclerView.setVisibility(hasPages ? View.VISIBLE : View.GONE);
        pageDragHintTextView.setVisibility(pageItems.size() >= 2 ? View.VISIBLE : View.GONE);
        if (submittedPagesVersion != pagesVersion) {
            pageAdapter.submitPages(pageItems);
            submittedPagesVersion = pagesVersion;
        }
        pageAdapter.setActionsEnabled(controlsEnabled);
        selectionStatusTextView.setText(buildSelectionStatusText());
        operationStatusTextView.setText(buildOperationStatusText());
        updatePdfResultState(controlsEnabled);
    }

    private void updatePdfResultState(boolean controlsEnabled) {
        if (lastPdfResult == null) {
            pdfResultLayout.setVisibility(View.GONE);
            openPdfButton.setEnabled(false);
            sharePdfButton.setEnabled(false);
            newDocumentButton.setEnabled(false);
            return;
        }

        pdfResultLayout.setVisibility(View.VISIBLE);
        pdfResultNameTextView.setText(buildPdfResultNameText(lastPdfResult));
        pdfResultDetailsTextView.setText(buildPdfResultDetailsText(lastPdfResult));
        openPdfButton.setEnabled(controlsEnabled);
        sharePdfButton.setEnabled(controlsEnabled);
        newDocumentButton.setEnabled(controlsEnabled);
    }

    private String buildPdfResultNameText(PdfResult pdfResult) {
        if (pdfResult.getDisplayName().isEmpty()) {
            return getString(R.string.pdf_result_unknown_name);
        }
        return pdfResult.getDisplayName();
    }

    private String buildPdfResultDetailsText(PdfResult pdfResult) {
        String pagesText = getResources().getQuantityString(
                R.plurals.pdf_result_pages_count,
                pdfResult.getPageCount(),
                pdfResult.getPageCount()
        );
        if (!pdfResult.hasKnownSize()) {
            return pagesText;
        }
        String sizeText = FileSizeFormatter.format(pdfResult.getSizeBytes(), Locale.getDefault());
        return getString(R.string.pdf_result_details_with_size, pagesText, sizeText);
    }

    private String buildSelectionStatusText() {
        return pageItems.isEmpty()
                ? getString(R.string.status_no_images_selected)
                : getResources().getQuantityString(
                        R.plurals.selected_images_count,
                        pageItems.size(),
                        pageItems.size()
                );
    }

    private String buildOperationStatusText() {
        if (generationInProgress) {
            return getString(R.string.status_pdf_generating);
        }

        if (transientStatusMessage == null || transientStatusMessage.isEmpty()) {
            return "";
        }

        return transientStatusMessage;
    }

    private void setSpinnerSelectionsFromOptions(PdfOptions options) {
        pageSizeSpinner.setSelection(options.getPageSizeMode() == PageSizeMode.A4 ? 0 : 1);
        imagePlacementSpinner.setSelection(
                options.getImagePlacementMode() == ImagePlacementMode.FIT ? 0 : 1
        );
        switch (options.getMarginPreset()) {
            case NONE:
                marginSpinner.setSelection(0);
                break;
            case SMALL:
                marginSpinner.setSelection(1);
                break;
            case STANDARD:
            default:
                marginSpinner.setSelection(2);
                break;
        }
    }

    private void updatePdfOptionsFromControls() {
        PdfOptions nextOptions = new PdfOptions(
                pageSizeFromSpinner(),
                imagePlacementFromSpinner(),
                marginPresetFromSpinner()
        );
        if (hasSameValues(pdfOptions, nextOptions)) {
            updateUiState();
            return;
        }

        pdfOptions = nextOptions;
        if (!generationInProgress && !awaitingSaveLocation) {
            transientStatusMessage = null;
        }
        updateUiState();
    }

    private void updatePdfSettingsEnabledState(boolean controlsEnabled) {
        boolean placementEnabled =
                controlsEnabled && pdfOptions.getPageSizeMode() == PageSizeMode.A4;
        pageSizeSpinner.setEnabled(controlsEnabled);
        marginSpinner.setEnabled(controlsEnabled);
        imagePlacementSpinner.setEnabled(placementEnabled);
        imagePlacementSpinner.setAlpha(placementEnabled ? 1f : 0.5f);
    }

    private PageSizeMode pageSizeFromSpinner() {
        return pageSizeSpinner.getSelectedItemPosition() == 1
                ? PageSizeMode.IMAGE
                : PageSizeMode.A4;
    }

    private ImagePlacementMode imagePlacementFromSpinner() {
        return imagePlacementSpinner.getSelectedItemPosition() == 1
                ? ImagePlacementMode.FILL
                : ImagePlacementMode.FIT;
    }

    private MarginPreset marginPresetFromSpinner() {
        int selectedPosition = marginSpinner.getSelectedItemPosition();
        if (selectedPosition == 0) {
            return MarginPreset.NONE;
        }
        if (selectedPosition == 1) {
            return MarginPreset.SMALL;
        }
        return MarginPreset.STANDARD;
    }

    private boolean hasSameValues(PdfOptions first, PdfOptions second) {
        return first.getPageSizeMode() == second.getPageSizeMode()
                && first.getImagePlacementMode() == second.getImagePlacementMode()
                && first.getMarginPreset() == second.getMarginPreset();
    }

    private void showToast(String message) {
        if (isUiSafe()) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isUiSafe() {
        return !activityDestroyed && !isFinishing() && !isDestroyed();
    }

    private static final class PdfMetadata {
        private final String displayName;
        private final long sizeBytes;

        private PdfMetadata(String displayName, long sizeBytes) {
            this.displayName = displayName;
            this.sizeBytes = sizeBytes;
        }

        private static PdfMetadata unknown() {
            return new PdfMetadata(null, PdfResult.UNKNOWN_SIZE_BYTES);
        }
    }

    private final class PageMoveCallback extends ItemTouchHelper.SimpleCallback {
        private static final float DRAG_ACTIVE_ALPHA = 0.9f;

        PageMoveCallback() {
            super(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
        }

        @Override
        public boolean isLongPressDragEnabled() {
            return false;
        }

        @Override
        public boolean isItemViewSwipeEnabled() {
            return false;
        }

        @Override
        public boolean onMove(
                RecyclerView recyclerView,
                RecyclerView.ViewHolder viewHolder,
                RecyclerView.ViewHolder target
        ) {
            int fromPosition = viewHolder.getBindingAdapterPosition();
            int toPosition = target.getBindingAdapterPosition();
            if (fromPosition == RecyclerView.NO_POSITION || toPosition == RecyclerView.NO_POSITION) {
                return false;
            }
            return movePage(fromPosition, toPosition);
        }

        @Override
        public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
        }

        @Override
        public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
            super.onSelectedChanged(viewHolder, actionState);
            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
                viewHolder.itemView.setAlpha(DRAG_ACTIVE_ALPHA);
                ViewCompat.setElevation(
                        viewHolder.itemView,
                        getResources().getDimension(R.dimen.page_item_drag_elevation)
                );
            }
        }

        @Override
        public void clearView(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
            super.clearView(recyclerView, viewHolder);
            viewHolder.itemView.setAlpha(1f);
            ViewCompat.setElevation(
                    viewHolder.itemView,
                    getResources().getDimension(R.dimen.page_item_card_elevation)
            );
        }
    }
}
