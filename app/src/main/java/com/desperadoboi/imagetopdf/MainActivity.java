package com.desperadoboi.imagetopdf;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.desperadoboi.imagetopdf.pdf.PdfGenerationCallback;
import com.desperadoboi.imagetopdf.pdf.PdfGenerator;
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

    private ActivityResultLauncher<PickVisualMediaRequest> photoPickerLauncher;
    private ActivityResultLauncher<String> createDocumentLauncher;

    private final List<Uri> selectedImageUris = new ArrayList<>();

    private ExecutorService pdfExecutor;
    private PdfGenerator pdfGenerator;

    private boolean awaitingSaveLocation;
    private boolean generationInProgress;
    private boolean activityDestroyed;
    private String transientStatusMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        pdfExecutor = Executors.newSingleThreadExecutor();
        pdfGenerator = new PdfGenerator(getApplicationContext().getContentResolver());

        bindViews();
        registerActivityResultLaunchers();
        configureWindowInsets();
        configureClickListeners();
        updateUiState();
    }

    @Override
    protected void onDestroy() {
        activityDestroyed = true;
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
    }

    private void registerActivityResultLaunchers() {
        photoPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.PickMultipleVisualMedia(),
                uris -> {
                    if (uris != null && !uris.isEmpty()) {
                        selectedImageUris.clear();
                        selectedImageUris.addAll(uris);
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
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void configureClickListeners() {
        selectImagesButton.setOnClickListener(v -> launchPhotoPicker());
        createPdfButton.setOnClickListener(v -> launchCreateDocument());
    }

    private void launchPhotoPicker() {
        transientStatusMessage = null;
        PickVisualMediaRequest request = new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build();
        photoPickerLauncher.launch(request);
    }

    private void launchCreateDocument() {
        if (selectedImageUris.isEmpty() || generationInProgress || awaitingSaveLocation) {
            return;
        }
        awaitingSaveLocation = true;
        transientStatusMessage = null;
        updateUiState();
        createDocumentLauncher.launch(buildSuggestedFileName());
    }

    private void startPdfGeneration(Uri outputUri) {
        generationInProgress = true;
        transientStatusMessage = null;
        updateUiState();

        pdfGenerator.generate(
                new ArrayList<>(selectedImageUris),
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

        selectImagesButton.setEnabled(controlsEnabled);
        createPdfButton.setEnabled(controlsEnabled && !selectedImageUris.isEmpty());
        progressBar.setVisibility(generationInProgress ? View.VISIBLE : View.GONE);
        selectionStatusTextView.setText(buildSelectionStatusText());
        operationStatusTextView.setText(buildOperationStatusText());
    }

    private String buildSelectionStatusText() {
        return selectedImageUris.isEmpty()
                ? getString(R.string.status_no_images_selected)
                : getResources().getQuantityString(
                        R.plurals.selected_images_count,
                        selectedImageUris.size(),
                        selectedImageUris.size()
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
