package com.desperadoboi.imagetopdf;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.desperadoboi.imagetopdf.image.ThumbnailLoader;
import com.desperadoboi.imagetopdf.model.PageItem;
import com.desperadoboi.imagetopdf.pdf.PdfGenerationCallback;
import com.desperadoboi.imagetopdf.pdf.PdfGenerator;
import com.desperadoboi.imagetopdf.ui.PageAdapter;
import com.google.android.material.button.MaterialButton;

import java.io.InterruptedIOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private MaterialButton selectImagesButton;
    private MaterialButton createPdfButton;
    private TextView selectionStatusTextView;
    private TextView operationStatusTextView;
    private ProgressBar progressBar;
    private RecyclerView pagesRecyclerView;

    private ActivityResultLauncher<PickVisualMediaRequest> photoPickerLauncher;
    private ActivityResultLauncher<String> createDocumentLauncher;

    private final List<PageItem> pageItems = new ArrayList<>();

    private ExecutorService pdfExecutor;
    private PdfGenerator pdfGenerator;
    private ThumbnailLoader thumbnailLoader;
    private PageAdapter pageAdapter;

    private boolean awaitingSaveLocation;
    private boolean generationInProgress;
    private boolean activityDestroyed;
    private String transientStatusMessage;
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
        registerActivityResultLaunchers();
        configureWindowInsets();
        configureClickListeners();
        updateUiState();
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
        operationStatusTextView = findViewById(R.id.text_operation_status);
        progressBar = findViewById(R.id.progress_generation);
        pagesRecyclerView = findViewById(R.id.recycler_pages);
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
                }
        );
        pagesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        pagesRecyclerView.setAdapter(pageAdapter);
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
                new ActivityResultContracts.CreateDocument("application/pdf"),
                outputUri -> {
                    awaitingSaveLocation = false;
                    if (outputUri == null) {
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
        updateUiState();
        createDocumentLauncher.launch(buildSuggestedFileName());
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

    private boolean canEditPages() {
        return !generationInProgress && !awaitingSaveLocation;
    }

    private void startPdfGeneration(Uri outputUri) {
        generationInProgress = true;
        transientStatusMessage = null;
        List<PageItem> pageSnapshot = new ArrayList<>(pageItems);
        updateUiState();

        pdfGenerator.generate(
                pageSnapshot,
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
                        transientStatusMessage = mapErrorMessage(exception);
                        updateUiState();
                        showToast(transientStatusMessage);
                    }
                }
        );
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
        progressBar.setVisibility(generationInProgress ? View.VISIBLE : View.GONE);
        pagesRecyclerView.setVisibility(hasPages ? View.VISIBLE : View.GONE);
        if (submittedPagesVersion != pagesVersion) {
            pageAdapter.submitPages(pageItems);
            submittedPagesVersion = pagesVersion;
        }
        pageAdapter.setActionsEnabled(controlsEnabled);
        selectionStatusTextView.setText(buildSelectionStatusText());
        operationStatusTextView.setText(buildOperationStatusText());
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

    private void showToast(String message) {
        if (isUiSafe()) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isUiSafe() {
        return !activityDestroyed && !isFinishing() && !isDestroyed();
    }
}
