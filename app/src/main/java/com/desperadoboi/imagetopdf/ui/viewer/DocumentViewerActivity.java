package com.desperadoboi.imagetopdf.ui.viewer;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.desperadoboi.imagetopdf.R;
import com.desperadoboi.imagetopdf.document.DocumentLoadException;
import com.desperadoboi.imagetopdf.document.DocumentType;
import com.desperadoboi.imagetopdf.document.IncomingDocument;
import com.desperadoboi.imagetopdf.document.IncomingDocumentLoader;
import com.desperadoboi.imagetopdf.document.TemporaryDocumentStore;
import com.desperadoboi.imagetopdf.document.image.ViewerImageLoader;
import com.desperadoboi.imagetopdf.document.pdf.PdfDocumentRenderer;
import com.desperadoboi.imagetopdf.document.pdf.PdfPageState;
import com.desperadoboi.imagetopdf.document.spreadsheet.CsvParser;
import com.desperadoboi.imagetopdf.document.spreadsheet.SpreadsheetData;
import com.desperadoboi.imagetopdf.document.spreadsheet.XlsxParseException;
import com.desperadoboi.imagetopdf.document.spreadsheet.XlsxSheet;
import com.desperadoboi.imagetopdf.document.spreadsheet.XlsxSpreadsheetParser;
import com.desperadoboi.imagetopdf.document.spreadsheet.XlsxWorkbook;
import com.desperadoboi.imagetopdf.document.text.TextDocumentReader;
import com.desperadoboi.imagetopdf.document.text.TextPreview;
import com.desperadoboi.imagetopdf.document.word.WordDocumentModel;
import com.desperadoboi.imagetopdf.document.word.WordParseException;
import com.desperadoboi.imagetopdf.ui.editor.ZoomableImageView;
import com.desperadoboi.imagetopdf.util.FileSizeFormatter;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.button.MaterialButton;

import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Locale;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class DocumentViewerActivity extends AppCompatActivity {
    public static final String ACTION_INTERNAL_VIEW =
            "com.desperadoboi.imagetopdf.action.VIEW_DOCUMENT";
    private static final String STATE_CURRENT_PAGE = "viewer_current_page";
    private static final String STATE_CURRENT_SHEET = "viewer_current_sheet";
    private static final String STATE_WORD_POSITION = "viewer_word_position";
    private static final String STATE_WORD_OFFSET = "viewer_word_offset";

    private final ExecutorService loadExecutor = Executors.newSingleThreadExecutor();
    private final AtomicLong generations = new AtomicLong();
    private final Runnable hideZoomIndicatorAction = this::hideZoomIndicator;

    private TemporaryDocumentStore temporaryDocumentStore;
    private IncomingDocument currentDocument;
    private AtomicBoolean currentCancellation;
    private PdfDocumentRenderer pdfRenderer;
    private ViewerImageLoader imageLoader;
    private PdfPageState pdfPageState;
    private Bitmap imageBitmap;
    private XlsxWorkbook xlsxWorkbook;
    private List<SpreadsheetCanvasModel> xlsxCanvasModels = Collections.emptyList();
    private WordDocumentModel wordDocument;
    @Nullable private WordBlockAdapter wordAdapter;
    private WordViewerViewModel wordViewModel;
    private boolean cacheWasShared;
    private int restoredPage;
    private int restoredSheet;
    private int selectedSheet;
    private int knownPageCount;
    private int restoredWordPosition;
    private int restoredWordOffset;
    private SpreadsheetStateStore spreadsheetStateStore = new SpreadsheetStateStore();

    private TextView titleView;
    private View shareButton;
    private View moreButton;
    private View loadingState;
    private TextView loadingText;
    private View errorState;
    private ImageView errorIcon;
    private TextView errorTitle;
    private TextView errorMessage;
    private View pdfContent;
    private ZoomableImageView pdfImage;
    private TextView pageCounter;
    private MaterialButton previousPageButton;
    private MaterialButton nextPageButton;
    private ZoomableImageView imageContent;
    private RecyclerView textContent;
    private RecyclerView wordContent;
    private View spreadsheetContent;
    private SpreadsheetCanvasView spreadsheetCanvasView;
    private TextView zoomIndicator;
    @Nullable private PopupMenu overflowPopupMenu;
    private View sheetControls;
    private TextView sheetNameView;
    private Spinner sheetSpinner;
    private TextView noticeView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        setTheme(R.style.Theme_ImageToPDF);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_document_viewer);
        temporaryDocumentStore = new TemporaryDocumentStore(this);
        bindViews();
        wordViewModel = new ViewModelProvider(this).get(WordViewerViewModel.class);
        wordViewModel.state().observe(this, this::handleWordState);
        configureInsets();
        configureActions();
        restoredPage = savedInstanceState == null
                ? 0
                : savedInstanceState.getInt(STATE_CURRENT_PAGE, 0);
        restoredSheet = savedInstanceState == null
                ? 0
                : savedInstanceState.getInt(STATE_CURRENT_SHEET, 0);
        restoredWordPosition = savedInstanceState == null
                ? 0
                : savedInstanceState.getInt(STATE_WORD_POSITION, 0);
        restoredWordOffset = savedInstanceState == null
                ? 0
                : savedInstanceState.getInt(STATE_WORD_OFFSET, 0);

        RetainedState retainedState = (RetainedState) getLastCustomNonConfigurationInstance();
        if (retainedState != null && retainedState.document != null) {
            currentDocument = retainedState.document;
            imageBitmap = retainedState.imageBitmap;
            xlsxWorkbook = retainedState.xlsxWorkbook;
            xlsxCanvasModels = retainedState.xlsxCanvasModels;
            wordDocument = retainedState.wordDocument;
            cacheWasShared = retainedState.cacheWasShared;
            spreadsheetStateStore = retainedState.spreadsheetStateStore;
            restoredSheet = spreadsheetStateStore.getSelectedSheet();
            restoredWordPosition = retainedState.wordPosition;
            restoredWordOffset = retainedState.wordOffset;
            displayLoadedDocument();
        } else {
            loadFromIntent(getIntent());
        }
        loadExecutor.execute(temporaryDocumentStore::cleanupOldFiles);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        loadFromIntent(intent);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        saveCurrentSpreadsheetState();
        captureWordPosition();
        outState.putInt(
                STATE_CURRENT_PAGE,
                pdfPageState == null ? restoredPage : pdfPageState.getCurrentPage()
        );
        outState.putInt(STATE_CURRENT_SHEET, selectedSheet);
        outState.putInt(STATE_WORD_POSITION, restoredWordPosition);
        outState.putInt(STATE_WORD_OFFSET, restoredWordOffset);
        super.onSaveInstanceState(outState);
    }

    @Override
    public Object onRetainCustomNonConfigurationInstance() {
        saveCurrentSpreadsheetState();
        captureWordPosition();
        return new RetainedState(
                currentDocument,
                imageBitmap,
                xlsxWorkbook,
                xlsxCanvasModels,
                wordDocument,
                cacheWasShared,
                spreadsheetStateStore.copy(),
                restoredWordPosition,
                restoredWordOffset
        );
    }

    @Override
    protected void onDestroy() {
        cancelActiveWork();
        if (zoomIndicator != null) {
            zoomIndicator.removeCallbacks(hideZoomIndicatorAction);
        }
        closeRenderers();
        closeWordAdapter();
        loadExecutor.shutdownNow();
        if (!isChangingConfigurations()) {
            wordViewModel.clearDocument();
            recycle(imageBitmap);
            imageBitmap = null;
            if (currentDocument != null && !cacheWasShared) {
                temporaryDocumentStore.delete(currentDocument.getCachedFile());
            }
        } else if (imageContent != null) {
            imageContent.setImageDrawable(null);
        }
        super.onDestroy();
    }

    private void bindViews() {
        titleView = findViewById(R.id.text_viewer_title);
        shareButton = findViewById(R.id.button_viewer_share);
        moreButton = findViewById(R.id.button_viewer_more);
        loadingState = findViewById(R.id.state_viewer_loading);
        loadingText = findViewById(R.id.text_viewer_loading);
        errorState = findViewById(R.id.state_viewer_error);
        errorIcon = findViewById(R.id.image_viewer_error);
        errorTitle = findViewById(R.id.text_viewer_error_title);
        errorMessage = findViewById(R.id.text_viewer_error_message);
        pdfContent = findViewById(R.id.content_viewer_pdf);
        pdfImage = findViewById(R.id.image_viewer_pdf_page);
        pageCounter = findViewById(R.id.text_viewer_page_counter);
        previousPageButton = findViewById(R.id.button_viewer_previous_page);
        nextPageButton = findViewById(R.id.button_viewer_next_page);
        imageContent = findViewById(R.id.content_viewer_image);
        textContent = findViewById(R.id.content_viewer_text);
        wordContent = findViewById(R.id.content_viewer_word);
        spreadsheetContent = findViewById(R.id.content_viewer_spreadsheet);
        spreadsheetCanvasView = findViewById(R.id.viewport_viewer_spreadsheet);
        zoomIndicator = findViewById(R.id.text_viewer_zoom_indicator);
        sheetControls = findViewById(R.id.layout_viewer_sheet_controls);
        sheetNameView = findViewById(R.id.text_viewer_sheet_name);
        sheetSpinner = findViewById(R.id.spinner_viewer_sheets);
        noticeView = findViewById(R.id.text_viewer_notice);
        textContent.setLayoutManager(new LinearLayoutManager(this));
        textContent.setAdapter(new TextLineAdapter());
        wordContent.setLayoutManager(new LinearLayoutManager(this));
        wordContent.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@androidx.annotation.NonNull RecyclerView recyclerView,
                    int dx, int dy) {
                updateOverflowMenu();
            }
        });
        spreadsheetCanvasView.setOnZoomChangeListener((scale, zoomMode, finished, userInitiated) -> {
            if (userInitiated && zoomMode == ZoomController.ZoomMode.MANUAL) {
                spreadsheetStateStore.recordManualZoom(selectedSheet, scale);
            }
            updateOverflowMenu();
            if (finished) {
                saveCurrentSpreadsheetState();
                showZoomIndicator(scale);
                spreadsheetCanvasView.announceForAccessibility(getString(
                        R.string.viewer_zoom_announcement,
                        ZoomController.percentageForIndicator(scale)
                ));
            }
        });
        sheetSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (xlsxWorkbook == null || position == selectedSheet) return;
                saveCurrentSpreadsheetState();
                selectedSheet = position;
                spreadsheetStateStore.setSelectedSheet(selectedSheet);
                showSelectedXlsxSheet();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // The current sheet remains selected.
            }
        });
    }

    private void configureInsets() {
        View root = findViewById(R.id.document_viewer_root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout()
            );
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
    }

    private void configureActions() {
        findViewById(R.id.button_viewer_back).setOnClickListener(ignored -> finish());
        shareButton.setOnClickListener(ignored -> shareDocument());
        moreButton.setOnClickListener(ignored -> showOverflowMenu());
        findViewById(R.id.button_viewer_cancel_loading).setOnClickListener(ignored -> finish());
        findViewById(R.id.button_viewer_close_error).setOnClickListener(ignored -> finish());
        previousPageButton.setOnClickListener(ignored -> changePage(-1));
        nextPageButton.setOnClickListener(ignored -> changePage(1));
        pdfImage.setOnSwipeListener(new ZoomableImageView.OnSwipeListener() {
            @Override
            public void onSwipeLeft() { changePage(1); }

            @Override
            public void onSwipeRight() { changePage(-1); }
        });
    }

    private void loadFromIntent(Intent intent) {
        releaseCurrentDocument();
        Uri uri = validateIntent(intent);
        if (uri == null) {
            showError(
                    R.drawable.ic_viewer_state_unsupported_48,
                    R.string.viewer_error_unsupported_title,
                    R.string.viewer_error_invalid_request
            );
            return;
        }
        spreadsheetStateStore.openDocument(uri.toString());
        titleView.setText(com.desperadoboi.imagetopdf.document.SafeDisplayName.sanitize(
                uri.getLastPathSegment()
        ));
        showLoading(R.string.viewer_loading_opening);
        long generation = generations.incrementAndGet();
        currentCancellation = new AtomicBoolean(false);
        AtomicBoolean cancellation = currentCancellation;
        IncomingDocumentLoader loader = new IncomingDocumentLoader(this, temporaryDocumentStore);
        loadExecutor.execute(() -> {
            try {
                IncomingDocument document = loader.load(uri, cancellation);
                runOnUiThread(() -> {
                    if (generation != generations.get() || isFinishing() || isDestroyed()) {
                        temporaryDocumentStore.delete(document.getCachedFile());
                        return;
                    }
                    currentDocument = document;
                    cacheWasShared = false;
                    displayLoadedDocument();
                });
            } catch (DocumentLoadException exception) {
                runOnUiThread(() -> {
                    if (generation == generations.get()) showLoadFailure(exception);
                });
            } catch (OutOfMemoryError error) {
                runOnUiThread(() -> {
                    if (generation == generations.get()) {
                        showError(
                                R.drawable.ic_viewer_state_too_large_48,
                                R.string.viewer_error_memory_title,
                                R.string.viewer_error_memory
                        );
                    }
                });
            }
        });
    }

    @Nullable
    private Uri validateIntent(Intent intent) {
        if (intent == null) return null;
        String action = intent.getAction();
        if (!Intent.ACTION_VIEW.equals(action) && !ACTION_INTERNAL_VIEW.equals(action)) return null;
        Uri uri = intent.getData();
        if (uri == null || uri.getScheme() == null) return null;
        String scheme = uri.getScheme();
        if (!"content".equalsIgnoreCase(scheme) && !"file".equalsIgnoreCase(scheme)) return null;
        return uri;
    }

    private void displayLoadedDocument() {
        if (currentDocument == null) return;
        titleView.setText(currentDocument.getDisplayName());
        DocumentType type = currentDocument.getDocumentType();
        shareButton.setEnabled(type.isViewable());
        if (!type.isViewable()) {
            showError(
                    R.drawable.ic_viewer_state_unsupported_48,
                    R.string.viewer_error_unsupported_title,
                    type == DocumentType.XLS
                            ? R.string.viewer_error_xls_not_supported
                            : type == DocumentType.DOC
                                    ? R.string.viewer_error_doc_not_supported
                                    : R.string.viewer_error_unknown_format
            );
            return;
        }
        if (type == DocumentType.PDF) {
            showPdf();
        } else if (type.isImage()) {
            showImage();
        } else if (type == DocumentType.XLSX) {
            showXlsx();
        } else if (type == DocumentType.DOCX) {
            showDocx();
        } else if (type.isSpreadsheetText()) {
            showSpreadsheet();
        } else {
            showText();
        }
    }

    private void showPdf() {
        showLoading(R.string.viewer_loading_pdf);
        pdfRenderer = new PdfDocumentRenderer(ContextCompat.getMainExecutor(this));
        pdfRenderer.open(currentDocument.getCachedFile(), new PdfDocumentRenderer.OpenCallback() {
            @Override
            public void onOpened(int pageCount) {
                knownPageCount = pageCount;
                if (pageCount <= 0) {
                    showError(
                            R.drawable.ic_viewer_state_unsupported_48,
                            R.string.viewer_error_empty_title,
                            R.string.viewer_error_empty_pdf
                    );
                    return;
                }
                pdfPageState = new PdfPageState(pageCount, restoredPage);
                renderCurrentPdfPage();
            }

            @Override
            public void onError(Exception exception) {
                if (isSecurityFailure(exception)) {
                    showError(
                            R.drawable.ic_viewer_state_encrypted_48,
                            R.string.viewer_error_encrypted_title,
                            R.string.viewer_error_encrypted
                    );
                } else {
                    showError(
                            R.drawable.ic_viewer_state_corrupted_48,
                            R.string.viewer_error_corrupted_title,
                            R.string.viewer_error_pdf_corrupted_or_encrypted
                    );
                }
            }
        });
    }

    private void renderCurrentPdfPage() {
        if (pdfRenderer == null || pdfPageState == null) return;
        showOnly(pdfContent);
        loadingState.setVisibility(View.VISIBLE);
        loadingText.setText(R.string.viewer_loading_page);
        updatePageControls();
        pdfImage.post(() -> pdfRenderer.renderPage(
                pdfPageState.getCurrentPage(),
                Math.max(1, pdfImage.getWidth()),
                Math.max(1, pdfImage.getHeight()),
                new PdfDocumentRenderer.RenderCallback() {
                    @Override
                    public void onRendered(int pageIndex, Bitmap bitmap) {
                        if (pdfPageState == null || pageIndex != pdfPageState.getCurrentPage()) {
                            return;
                        }
                        pdfImage.setImageBitmap(bitmap);
                        loadingState.setVisibility(View.GONE);
                        pdfImage.announceForAccessibility(getString(
                                R.string.viewer_page_announcement,
                                pageIndex + 1,
                                pdfPageState.getPageCount()
                        ));
                    }

                    @Override
                    public void onError(Exception exception) {
                        showError(
                                R.drawable.ic_viewer_state_corrupted_48,
                                R.string.viewer_error_corrupted_title,
                                R.string.viewer_error_pdf_page
                        );
                    }
                }
        ));
    }

    private void changePage(int delta) {
        if (pdfPageState == null) return;
        int before = pdfPageState.getCurrentPage();
        pdfPageState.setCurrentPage(before + delta);
        if (before != pdfPageState.getCurrentPage()) renderCurrentPdfPage();
    }

    private void updatePageControls() {
        int current = pdfPageState.getCurrentPage() + 1;
        int total = pdfPageState.getPageCount();
        pageCounter.setText(getString(R.string.viewer_page_counter, current, total));
        pageCounter.setContentDescription(getString(
                R.string.viewer_page_announcement,
                current,
                total
        ));
        previousPageButton.setEnabled(pdfPageState.hasPrevious());
        nextPageButton.setEnabled(pdfPageState.hasNext());
        previousPageButton.setContentDescription(getString(
                R.string.viewer_previous_page_content_description,
                Math.max(1, current - 1)
        ));
        nextPageButton.setContentDescription(getString(
                R.string.viewer_next_page_content_description,
                Math.min(total, current + 1)
        ));
    }

    private void showImage() {
        showLoading(R.string.viewer_loading_image);
        if (imageBitmap != null && !imageBitmap.isRecycled()) {
            showOnly(imageContent);
            imageContent.setImageBitmap(imageBitmap);
            return;
        }
        imageLoader = new ViewerImageLoader(ContextCompat.getMainExecutor(this));
        imageContent.post(() -> imageLoader.load(
                currentDocument.getCachedFile(),
                Math.max(1080, imageContent.getWidth() * 2),
                Math.max(1080, imageContent.getHeight() * 2),
                new ViewerImageLoader.Callback() {
                    @Override
                    public void onLoaded(Bitmap bitmap) {
                        imageBitmap = bitmap;
                        showOnly(imageContent);
                        imageContent.setImageBitmap(bitmap);
                    }

                    @Override
                    public void onError(Exception exception) {
                        showError(
                                R.drawable.ic_viewer_state_corrupted_48,
                                R.string.viewer_error_corrupted_title,
                                R.string.viewer_error_image_corrupted
                        );
                    }
                }
        ));
    }

    private void showText() {
        showLoading(R.string.viewer_loading_text);
        long generation = generations.get();
        loadExecutor.execute(() -> {
            try {
                TextPreview preview = new TextDocumentReader().read(currentDocument.getCachedFile());
                runOnUiThread(() -> {
                    if (generation != generations.get()) return;
                    if (preview.getLines().isEmpty()) {
                        showError(
                                R.drawable.ic_viewer_state_unsupported_48,
                                R.string.viewer_error_empty_title,
                                R.string.viewer_error_empty_text
                        );
                        return;
                    }
                    ((TextLineAdapter) textContent.getAdapter()).submit(preview.getLines());
                    showOnly(textContent);
                    noticeView.setText(R.string.viewer_notice_text_truncated);
                    noticeView.setVisibility(preview.isTruncated() ? View.VISIBLE : View.GONE);
                });
            } catch (Exception exception) {
                runOnUiThread(() -> showError(
                        R.drawable.ic_viewer_state_corrupted_48,
                        R.string.viewer_error_corrupted_title,
                        R.string.viewer_error_text_corrupted
                ));
            }
        });
    }

    private void showSpreadsheet() {
        showLoading(R.string.viewer_loading_table);
        sheetControls.setVisibility(View.GONE);
        sheetNameView.setVisibility(View.GONE);
        sheetSpinner.setVisibility(View.GONE);
        long generation = generations.get();
        float density = getResources().getDisplayMetrics().density;
        loadExecutor.execute(() -> {
            try {
                TextDocumentReader textReader = new TextDocumentReader();
                Charset charset = textReader.detectCharset(currentDocument.getCachedFile());
                Character delimiter = currentDocument.getDocumentType() == DocumentType.TSV
                        ? '\t'
                        : null;
                SpreadsheetData data;
                try (InputStreamReader reader = new InputStreamReader(
                        new FileInputStream(currentDocument.getCachedFile()),
                        charset
                )) {
                    data = new CsvParser().parse(reader, delimiter);
                }
                SpreadsheetCanvasModel canvasModel =
                        SpreadsheetCanvasModel.create(0, data, density);
                runOnUiThread(() -> {
                    if (generation != generations.get()) return;
                    if (data.getRows().isEmpty()) {
                        showError(
                                R.drawable.ic_viewer_state_unsupported_48,
                                R.string.viewer_error_empty_title,
                                R.string.viewer_error_empty_table
                        );
                        return;
                    }
                    submitSpreadsheetModel(
                            canvasModel,
                            spreadsheetStateStore.restore(0)
                    );
                    showOnly(spreadsheetContent);
                    noticeView.setText(R.string.viewer_notice_table_truncated);
                    noticeView.setVisibility(data.isTruncated() ? View.VISIBLE : View.GONE);
                });
            } catch (Exception exception) {
                runOnUiThread(() -> showError(
                        R.drawable.ic_viewer_state_corrupted_48,
                        R.string.viewer_error_corrupted_title,
                        R.string.viewer_error_table_corrupted
                ));
            }
        });
    }

    private void showXlsx() {
        if (xlsxWorkbook != null) {
            bindXlsxWorkbook();
            return;
        }
        showLoading(R.string.viewer_loading_xlsx);
        long generation = generations.get();
        float density = getResources().getDisplayMetrics().density;
        loadExecutor.execute(() -> {
            try {
                XlsxWorkbook workbook = new XlsxSpreadsheetParser().parse(
                        currentDocument.getCachedFile()
                );
                List<SpreadsheetCanvasModel> canvasModels = new ArrayList<>();
                for (int sheetIndex = 0;
                        sheetIndex < workbook.getSheets().size();
                        sheetIndex++) {
                    canvasModels.add(SpreadsheetCanvasModel.create(
                            sheetIndex,
                            workbook.getSheets().get(sheetIndex),
                            density
                    ));
                }
                runOnUiThread(() -> {
                    if (generation != generations.get()) return;
                    xlsxWorkbook = workbook;
                    xlsxCanvasModels = Collections.unmodifiableList(canvasModels);
                    bindXlsxWorkbook();
                });
            } catch (XlsxParseException exception) {
                runOnUiThread(() -> {
                    if (generation != generations.get()) return;
                    if (exception.getReason() == XlsxParseException.Reason.TOO_LARGE) {
                        showError(
                                R.drawable.ic_viewer_state_too_large_48,
                                R.string.viewer_error_too_large_title,
                                R.string.viewer_error_xlsx_too_large
                        );
                    } else {
                        showError(
                                R.drawable.ic_viewer_state_corrupted_48,
                                R.string.viewer_error_corrupted_title,
                                R.string.viewer_error_xlsx_corrupted
                        );
                    }
                });
            } catch (Exception exception) {
                runOnUiThread(() -> {
                    if (generation == generations.get()) {
                        showError(
                                R.drawable.ic_viewer_state_corrupted_48,
                                R.string.viewer_error_corrupted_title,
                                R.string.viewer_error_xlsx_corrupted
                        );
                    }
                });
            }
        });
    }

    private void showDocx() {
        if (wordDocument != null) {
            bindWordDocument();
            return;
        }
        showLoading(R.string.viewer_loading_docx);
        wordViewModel.load(currentDocument.getCachedFile());
    }

    private void handleWordState(@Nullable WordViewerViewModel.State state) {
        if (state == null || currentDocument == null
                || currentDocument.getDocumentType() != DocumentType.DOCX
                || !currentDocument.getCachedFile().getAbsolutePath()
                .equals(state.getPath())) {
            return;
        }
        if (state.isLoading()) {
            showLoading(R.string.viewer_loading_docx);
            return;
        }
        if (state.getDocument() != null) {
            wordDocument = state.getDocument();
            bindWordDocument();
            return;
        }
        Throwable failure = state.getFailure();
        if (failure instanceof WordParseException) {
            showWordFailure((WordParseException) failure);
        } else if (failure instanceof OutOfMemoryError) {
            showError(
                    R.drawable.ic_viewer_state_too_large_48,
                    R.string.viewer_error_memory_title,
                    R.string.viewer_error_memory
            );
        } else if (failure != null) {
            showError(
                    R.drawable.ic_viewer_state_corrupted_48,
                    R.string.viewer_error_corrupted_title,
                    R.string.viewer_error_docx_corrupted
            );
        }
    }

    private void bindWordDocument() {
        if (wordDocument == null || currentDocument == null) return;
        closeWordAdapter();
        wordAdapter = new WordBlockAdapter(
                currentDocument.getCachedFile(),
                ContextCompat.getMainExecutor(this),
                this::openSafeHyperlink
        );
        wordContent.setAdapter(wordAdapter);
        wordAdapter.submit(wordDocument);
        showOnly(wordContent);
        LinearLayoutManager layoutManager =
                (LinearLayoutManager) wordContent.getLayoutManager();
        if (layoutManager != null && restoredWordPosition > 0) {
            WordBlockAdapter adapter = wordAdapter;
            WordViewerPosition position = new WordViewerPosition(
                    restoredWordPosition,
                    restoredWordOffset
            ).clampToBlockCount(adapter.getItemCount());
            wordContent.post(() -> {
                if (wordContent.getAdapter() != adapter) return;
                layoutManager.scrollToPositionWithOffset(
                        position.getBlockPosition(),
                        position.getOffsetPixels()
                );
            });
        }
        updateOverflowMenu();
    }

    private void showWordFailure(WordParseException exception) {
        switch (exception.getReason()) {
            case TOO_LARGE:
                showError(
                        R.drawable.ic_viewer_state_too_large_48,
                        R.string.viewer_error_too_large_title,
                        R.string.viewer_error_docx_too_large
                );
                break;
            case ENCRYPTED:
                showError(
                        R.drawable.ic_viewer_state_encrypted_48,
                        R.string.viewer_error_encrypted_title,
                        R.string.viewer_error_docx_encrypted
                );
                break;
            case UNSUPPORTED:
                showError(
                        R.drawable.ic_viewer_state_unsupported_48,
                        R.string.viewer_error_unsupported_title,
                        R.string.viewer_error_docx_unsupported
                );
                break;
            case CORRUPTED:
            default:
                showError(
                        R.drawable.ic_viewer_state_corrupted_48,
                        R.string.viewer_error_corrupted_title,
                        R.string.viewer_error_docx_corrupted
                );
                break;
        }
    }

    private void openSafeHyperlink(String value) {
        if (value == null) return;
        Uri uri;
        try {
            uri = Uri.parse(value);
        } catch (RuntimeException exception) {
            return;
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) return;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(
                    this,
                    R.string.viewer_word_link_unavailable,
                    Toast.LENGTH_SHORT
            ).show();
        } catch (RuntimeException exception) {
            Toast.makeText(
                    this,
                    R.string.viewer_word_link_failed,
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private void bindXlsxWorkbook() {
        List<String> sheetNames = new ArrayList<>();
        for (XlsxSheet sheet : xlsxWorkbook.getSheets()) sheetNames.add(sheet.getName());
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                sheetNames
        );
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sheetSpinner.setAdapter(spinnerAdapter);
        selectedSheet = Math.max(0, Math.min(restoredSheet, sheetNames.size() - 1));
        spreadsheetStateStore.setSelectedSheet(selectedSheet);
        sheetSpinner.setSelection(selectedSheet, false);
        boolean hasMultipleSheets = sheetNames.size() > 1;
        sheetControls.setVisibility(View.VISIBLE);
        sheetNameView.setVisibility(hasMultipleSheets ? View.GONE : View.VISIBLE);
        sheetSpinner.setVisibility(hasMultipleSheets ? View.VISIBLE : View.GONE);
        showOnly(spreadsheetContent);
        showSelectedXlsxSheet();
    }

    private void showSelectedXlsxSheet() {
        if (xlsxWorkbook == null || xlsxWorkbook.getSheets().isEmpty()) return;
        selectedSheet = Math.max(
                0,
                Math.min(selectedSheet, xlsxWorkbook.getSheets().size() - 1)
        );
        XlsxSheet sheet = xlsxWorkbook.getSheets().get(selectedSheet);
        sheetNameView.setText(getString(R.string.viewer_current_sheet, sheet.getName()));
        sheetNameView.setContentDescription(getString(
                R.string.viewer_current_sheet_content_description,
                sheet.getName(),
                selectedSheet + 1,
                xlsxWorkbook.getSheets().size()
        ));
        spreadsheetCanvasView.submit(
                xlsxCanvasModels.get(selectedSheet),
                spreadsheetStateStore.restoreXlsx(selectedSheet)
        );
        updateOverflowMenu();
        noticeView.setText(R.string.viewer_notice_xlsx_truncated);
        noticeView.setVisibility(
                xlsxWorkbook.isTruncated() || sheet.getData().isTruncated()
                        ? View.VISIBLE
                        : View.GONE
        );
    }

    private void submitSpreadsheetModel(
            SpreadsheetCanvasModel model,
            SpreadsheetViewportState state
    ) {
        spreadsheetCanvasView.submit(model, state);
        updateOverflowMenu();
    }

    private void saveCurrentSpreadsheetState() {
        if (spreadsheetCanvasView == null
                || currentDocument == null
                || (!currentDocument.getDocumentType().isSpreadsheetText()
                && currentDocument.getDocumentType() != DocumentType.XLSX)) {
            return;
        }
        spreadsheetStateStore.save(selectedSheet, spreadsheetCanvasView.captureState());
        spreadsheetStateStore.setSelectedSheet(selectedSheet);
    }

    private void shareDocument() {
        if (currentDocument == null
                || !currentDocument.getDocumentType().isViewable()
                || !currentDocument.getCachedFile().isFile()) return;
        try {
            Uri shareUri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    currentDocument.getCachedFile()
            );
            Intent sendIntent = new Intent(Intent.ACTION_SEND);
            sendIntent.setType(currentDocument.getDocumentType().getMimeType());
            sendIntent.putExtra(Intent.EXTRA_STREAM, shareUri);
            sendIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            sendIntent.setClipData(ClipData.newUri(
                    getContentResolver(),
                    currentDocument.getDisplayName(),
                    shareUri
            ));
            Intent chooser = Intent.createChooser(sendIntent, getString(R.string.viewer_share_chooser));
            chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            chooser.setClipData(sendIntent.getClipData());
            startActivity(chooser);
            cacheWasShared = true;
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(this, R.string.viewer_share_unavailable, Toast.LENGTH_SHORT).show();
        } catch (RuntimeException exception) {
            Toast.makeText(this, R.string.viewer_share_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void showFileInfo() {
        if (currentDocument == null) return;
        boolean word = currentDocument.getDocumentType() == DocumentType.DOCX;
        String pages = word && wordDocument != null
                ? getString(
                        R.string.viewer_word_info_counts,
                        wordDocument.getParagraphCount(),
                        wordDocument.getTableCount(),
                        wordDocument.getImageCount()
                )
                : knownPageCount > 0
                        ? getResources().getQuantityString(
                        R.plurals.viewer_pages_count,
                        knownPageCount,
                        knownPageCount
                )
                        : getString(R.string.viewer_info_not_available);
        View dialogView = getLayoutInflater().inflate(
                R.layout.dialog_viewer_file_info,
                null,
                false
        );
        bindInfoRow(
                dialogView,
                R.id.row_viewer_info_name,
                R.id.text_viewer_info_name,
                R.string.viewer_info_name_label,
                currentDocument.getDisplayName()
        );
        bindInfoRow(
                dialogView,
                R.id.row_viewer_info_type,
                R.id.text_viewer_info_type,
                R.string.viewer_info_type_label,
                typeLabel(currentDocument.getDocumentType())
        );
        bindInfoRow(
                dialogView,
                R.id.row_viewer_info_size,
                R.id.text_viewer_info_size,
                R.string.viewer_info_size_label,
                FileSizeFormatter.format(currentDocument.getSizeBytes(), Locale.getDefault())
        );
        bindInfoRow(
                dialogView,
                R.id.row_viewer_info_pages,
                R.id.text_viewer_info_pages,
                word
                        ? R.string.viewer_info_contents_label
                        : R.string.viewer_info_pages_label,
                pages
        );
        bindInfoRow(
                dialogView,
                R.id.row_viewer_info_mode,
                R.id.text_viewer_info_mode,
                R.string.viewer_info_mode_label,
                getString(R.string.viewer_read_only)
        );
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.viewer_info_title)
                .setView(dialogView)
                .setPositiveButton(R.string.viewer_action_done, null)
                .show();
    }

    private void showOverflowMenu() {
        PopupMenu popupMenu = new PopupMenu(this, moreButton);
        popupMenu.inflate(R.menu.document_viewer_overflow);
        overflowPopupMenu = popupMenu;
        updateOverflowMenu();
        popupMenu.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.action_viewer_zoom_100) {
                spreadsheetCanvasView.resetTo100Percent();
                return true;
            }
            if (itemId == R.id.action_viewer_word_top) {
                wordContent.scrollToPosition(0);
                restoredWordPosition = 0;
                restoredWordOffset = 0;
                return true;
            }
            if (itemId == R.id.action_viewer_file_info) {
                showFileInfo();
                return true;
            }
            return false;
        });
        popupMenu.setOnDismissListener(dismissedMenu -> {
            if (overflowPopupMenu == dismissedMenu) overflowPopupMenu = null;
        });
        popupMenu.show();
    }

    private void updateOverflowMenu() {
        PopupMenu popupMenu = overflowPopupMenu;
        if (popupMenu == null) return;
        boolean spreadsheetVisible = spreadsheetContent.getVisibility() == View.VISIBLE;
        boolean resetVisible = spreadsheetVisible
                && ZoomController.shouldShowResetAction(
                        spreadsheetCanvasView.getCurrentScale()
                );
        popupMenu.getMenu().findItem(R.id.action_viewer_zoom_100)
                .setVisible(resetVisible);
        LinearLayoutManager wordLayoutManager = wordContent == null
                ? null
                : (LinearLayoutManager) wordContent.getLayoutManager();
        boolean wordTopVisible = wordContent != null
                && wordContent.getVisibility() == View.VISIBLE
                && wordLayoutManager != null
                && wordLayoutManager.findFirstVisibleItemPosition() > 0;
        popupMenu.getMenu().findItem(R.id.action_viewer_word_top)
                .setVisible(wordTopVisible);
    }

    private void showZoomIndicator(float scale) {
        zoomIndicator.setText(getString(
                R.string.viewer_zoom_indicator,
                ZoomController.percentageForIndicator(scale)
        ));
        zoomIndicator.setVisibility(View.VISIBLE);
        zoomIndicator.bringToFront();
        zoomIndicator.removeCallbacks(hideZoomIndicatorAction);
        zoomIndicator.postDelayed(hideZoomIndicatorAction, 1_000L);
    }

    private void hideZoomIndicator() {
        zoomIndicator.setVisibility(View.GONE);
    }

    private void bindInfoRow(
            View dialogView,
            int rowId,
            int valueId,
            @StringRes int labelResId,
            CharSequence value
    ) {
        ViewGroup row = dialogView.findViewById(rowId);
        TextView valueView = dialogView.findViewById(valueId);
        TextView labelView = (TextView) row.getChildAt(0);
        labelView.setText(labelResId);
        valueView.setText(value);
        row.setContentDescription(getString(
                R.string.viewer_info_row_content_description,
                getString(labelResId),
                value
        ));
        row.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        for (int index = 0; index < row.getChildCount(); index++) {
            row.getChildAt(index).setImportantForAccessibility(
                    View.IMPORTANT_FOR_ACCESSIBILITY_NO
            );
        }
    }

    private String typeLabel(DocumentType type) {
        switch (type) {
            case PDF: return getString(R.string.viewer_type_pdf);
            case CSV: return getString(R.string.viewer_type_csv);
            case TSV: return getString(R.string.viewer_type_tsv);
            case TEXT: return getString(R.string.viewer_type_text);
            case JPEG: return getString(R.string.viewer_type_jpeg);
            case PNG: return getString(R.string.viewer_type_png);
            case WEBP: return getString(R.string.viewer_type_webp);
            case HEIC: return getString(R.string.viewer_type_heic);
            case XLS: return getString(R.string.viewer_type_xls);
            case XLSX: return getString(R.string.viewer_type_xlsx);
            case DOC: return getString(R.string.viewer_type_doc);
            case DOCX: return getString(R.string.viewer_type_docx);
            default: return getString(R.string.viewer_type_unknown);
        }
    }

    private void showLoadFailure(DocumentLoadException exception) {
        switch (exception.getReason()) {
            case PERMISSION_LOST:
                showError(
                        R.drawable.ic_viewer_state_permission_48,
                        R.string.viewer_error_permission_title,
                        R.string.viewer_error_permission
                );
                break;
            case TOO_LARGE:
                showError(
                        R.drawable.ic_viewer_state_too_large_48,
                        R.string.viewer_error_too_large_title,
                        R.string.viewer_error_too_large
                );
                break;
            case CORRUPTED:
                showError(
                        R.drawable.ic_viewer_state_corrupted_48,
                        R.string.viewer_error_corrupted_title,
                        R.string.viewer_error_corrupted
                );
                break;
            case ENCRYPTED:
                showError(
                        R.drawable.ic_viewer_state_encrypted_48,
                        R.string.viewer_error_encrypted_title,
                        R.string.viewer_error_docx_encrypted
                );
                break;
            case UNSUPPORTED:
                showError(
                        R.drawable.ic_viewer_state_unsupported_48,
                        R.string.viewer_error_unsupported_title,
                        R.string.viewer_error_docx_unsupported
                );
                break;
            case CANCELLED:
                break;
            case UNREADABLE:
            default:
                showError(
                        R.drawable.ic_viewer_state_permission_48,
                        R.string.viewer_error_open_title,
                        R.string.viewer_error_open
                );
                break;
        }
    }

    private void showLoading(int messageResId) {
        hideAllStates();
        loadingText.setText(messageResId);
        loadingState.setVisibility(View.VISIBLE);
        loadingState.bringToFront();
        loadingState.announceForAccessibility(getString(
                R.string.viewer_state_announcement,
                getString(R.string.viewer_loading_title),
                getString(messageResId)
        ));
        shareButton.setEnabled(currentDocument != null);
    }

    private void showError(
            @DrawableRes int iconResId,
            @StringRes int titleResId,
            @StringRes int messageResId
    ) {
        hideAllStates();
        errorIcon.setImageResource(iconResId);
        errorTitle.setText(titleResId);
        errorMessage.setText(messageResId);
        errorState.setVisibility(View.VISIBLE);
        errorState.announceForAccessibility(getString(
                R.string.viewer_state_announcement,
                getString(titleResId),
                getString(messageResId)
        ));
    }

    private boolean isSecurityFailure(Exception exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof SecurityException) return true;
            cause = cause.getCause();
        }
        return false;
    }

    private void showOnly(View content) {
        hideAllStates();
        content.setVisibility(View.VISIBLE);
    }

    private void hideAllStates() {
        loadingState.setVisibility(View.GONE);
        errorState.setVisibility(View.GONE);
        pdfContent.setVisibility(View.GONE);
        imageContent.setVisibility(View.GONE);
        textContent.setVisibility(View.GONE);
        wordContent.setVisibility(View.GONE);
        spreadsheetContent.setVisibility(View.GONE);
        zoomIndicator.setVisibility(View.GONE);
        noticeView.setVisibility(View.GONE);
    }

    private void releaseCurrentDocument() {
        cancelActiveWork();
        closeRenderers();
        recycle(imageBitmap);
        imageBitmap = null;
        xlsxWorkbook = null;
        xlsxCanvasModels = Collections.emptyList();
        wordDocument = null;
        if (wordViewModel != null) wordViewModel.clearDocument();
        closeWordAdapter();
        if (spreadsheetCanvasView != null) spreadsheetCanvasView.clear();
        if (currentDocument != null && !cacheWasShared) {
            temporaryDocumentStore.delete(currentDocument.getCachedFile());
        }
        currentDocument = null;
        cacheWasShared = false;
        pdfPageState = null;
        knownPageCount = 0;
        restoredPage = 0;
        restoredSheet = 0;
        selectedSheet = 0;
        restoredWordPosition = 0;
        restoredWordOffset = 0;
        spreadsheetStateStore.clear();
        shareButton.setEnabled(false);
        generations.incrementAndGet();
    }

    private void cancelActiveWork() {
        if (currentCancellation != null) currentCancellation.set(true);
        currentCancellation = null;
    }

    private void closeRenderers() {
        if (pdfRenderer != null) pdfRenderer.close();
        if (imageLoader != null) imageLoader.close();
        pdfRenderer = null;
        imageLoader = null;
    }

    private void captureWordPosition() {
        if (wordContent == null || wordContent.getVisibility() != View.VISIBLE) return;
        LinearLayoutManager layoutManager =
                (LinearLayoutManager) wordContent.getLayoutManager();
        if (layoutManager == null) return;
        int position = layoutManager.findFirstVisibleItemPosition();
        if (position < 0) return;
        View first = layoutManager.findViewByPosition(position);
        restoredWordPosition = position;
        restoredWordOffset = first == null
                ? 0
                : first.getTop() - wordContent.getPaddingTop();
    }

    private void closeWordAdapter() {
        if (wordContent != null) wordContent.setAdapter(null);
        if (wordAdapter != null) wordAdapter.close();
        wordAdapter = null;
    }

    private void recycle(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
    }

    private static final class RetainedState {
        private final IncomingDocument document;
        private final Bitmap imageBitmap;
        private final XlsxWorkbook xlsxWorkbook;
        private final List<SpreadsheetCanvasModel> xlsxCanvasModels;
        private final WordDocumentModel wordDocument;
        private final boolean cacheWasShared;
        private final SpreadsheetStateStore spreadsheetStateStore;
        private final int wordPosition;
        private final int wordOffset;

        private RetainedState(
                IncomingDocument document,
                Bitmap imageBitmap,
                XlsxWorkbook xlsxWorkbook,
                List<SpreadsheetCanvasModel> xlsxCanvasModels,
                WordDocumentModel wordDocument,
                boolean cacheWasShared,
                SpreadsheetStateStore spreadsheetStateStore,
                int wordPosition,
                int wordOffset
        ) {
            this.document = document;
            this.imageBitmap = imageBitmap;
            this.xlsxWorkbook = xlsxWorkbook;
            this.xlsxCanvasModels = xlsxCanvasModels;
            this.wordDocument = wordDocument;
            this.cacheWasShared = cacheWasShared;
            this.spreadsheetStateStore = spreadsheetStateStore;
            this.wordPosition = wordPosition;
            this.wordOffset = wordOffset;
        }
    }
}
