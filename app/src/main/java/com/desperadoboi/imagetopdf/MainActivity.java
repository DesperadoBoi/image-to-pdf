package com.desperadoboi.imagetopdf;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.desperadoboi.imagetopdf.model.DocumentSessionViewModel;
import com.desperadoboi.imagetopdf.model.ImageImportMode;
import com.desperadoboi.imagetopdf.model.ImageImportResult;
import com.desperadoboi.imagetopdf.model.PageItem;
import com.desperadoboi.imagetopdf.image.CapturedImageStorage;
import com.desperadoboi.imagetopdf.ui.about.AboutFragment;
import com.desperadoboi.imagetopdf.ui.about.PrivacyPolicyFragment;
import com.desperadoboi.imagetopdf.ui.editor.EditorFragment;
import com.desperadoboi.imagetopdf.ui.editor.PageEditFragment;
import com.desperadoboi.imagetopdf.ui.gallery.ImagePickerFragment;
import com.desperadoboi.imagetopdf.ui.home.HomeFragment;
import com.desperadoboi.imagetopdf.ui.result.PdfResultFragment;
import com.desperadoboi.imagetopdf.ui.smartscan.ScanReviewFragment;
import com.desperadoboi.imagetopdf.ui.smartscan.ScanSessionViewModel;
import com.desperadoboi.imagetopdf.ui.smartscan.SmartScanFragment;
import com.desperadoboi.imagetopdf.ui.tools.AllToolsFragment;

public class MainActivity extends AppCompatActivity
        implements HomeFragment.NavigationCallback,
        AboutFragment.NavigationCallback,
        EditorFragment.NavigationCallback,
        ImagePickerFragment.NavigationCallback,
        PdfResultFragment.NavigationCallback,
        SmartScanFragment.NavigationCallback,
        ScanReviewFragment.NavigationCallback {
    private DocumentSessionViewModel sessionViewModel;
    private ScanSessionViewModel scanSessionViewModel;
    private CapturedImageStorage capturedImageStorage;
    private boolean scanReviewNavigationPending;
    private boolean aboutNavigationPending;
    private boolean privacyNavigationPending;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.Theme_ImageToPDF);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        sessionViewModel = new ViewModelProvider(this).get(DocumentSessionViewModel.class);
        scanSessionViewModel = new ViewModelProvider(this).get(ScanSessionViewModel.class);
        capturedImageStorage = new CapturedImageStorage(this);
        configureWindowInsets();
        configureBackNavigation();

        if (savedInstanceState == null) {
            if (sessionViewModel.hasPages()) {
                showEditor();
            } else {
                showHome();
            }
        }
    }

    @Override
    public void onImagesSelectedForEditing() {
        showEditor();
    }

    @Override
    public void onImagePickerRequested(ImageImportMode mode) {
        showImagePicker(mode);
    }

    @Override
    public void onImagePickerCancelled(ImageImportMode mode) {
        if (mode == ImageImportMode.APPEND_TO_DOCUMENT) {
            showEditor();
        } else {
            showHome();
        }
    }

    @Override
    public void onImagesImported() {
        showEditor();
    }

    @Override
    public void onAllToolsRequested() {
        showAllTools();
    }

    @Override
    public void onAboutRequested() {
        showAbout();
    }

    @Override
    public void onPrivacyPolicyRequested() {
        showPrivacyPolicy();
    }

    @Override
    public void onSmartScanRequested() {
        deleteCapturedFiles(scanSessionViewModel.startNewSession());
        showSmartScan();
    }

    @Override
    public void onScanReviewRequested() {
        if (scanSessionViewModel.getState().getCurrentReviewPage() == null
                || getSupportFragmentManager().findFragmentByTag(ScanReviewFragment.TAG) != null
                || scanReviewNavigationPending
                || getSupportFragmentManager().isStateSaved()) {
            return;
        }
        scanReviewNavigationPending = true;
        getSupportFragmentManager()
                .beginTransaction()
                .setReorderingAllowed(true)
                .replace(
                        R.id.fragment_container,
                        new ScanReviewFragment(),
                        ScanReviewFragment.TAG
                )
                .addToBackStack(ScanReviewFragment.TAG)
                .commit();
    }

    @Override
    public void onScanReviewClosed() {
        scanReviewNavigationPending = false;
        getSupportFragmentManager().popBackStackImmediate(
                ScanReviewFragment.TAG,
                FragmentManager.POP_BACK_STACK_INCLUSIVE
        );
    }

    @Override
    public void onScanFinished() {
        scanReviewNavigationPending = false;
        java.util.List<PageItem> pages = scanSessionViewModel.finishPages();
        if (pages.isEmpty()) {
            return;
        }
        ImageImportResult result = sessionViewModel.importPreparedPages(
                ImageImportMode.NEW_DOCUMENT,
                pages
        );
        deleteCapturedFiles(result.getCapturedFileNamesToDelete());
        scanSessionViewModel.completeTransfer();
        getSupportFragmentManager().popBackStackImmediate(
                SmartScanFragment.TAG,
                FragmentManager.POP_BACK_STACK_INCLUSIVE
        );
        showEditor();
    }

    @Override
    public void onScanCancelled() {
        scanReviewNavigationPending = false;
        deleteCapturedFiles(scanSessionViewModel.cancelSession());
        boolean popped = getSupportFragmentManager().popBackStackImmediate(
                SmartScanFragment.TAG,
                FragmentManager.POP_BACK_STACK_INCLUSIVE
        );
        if (!popped) {
            showHome();
        }
    }

    @Override
    public void onReturnHomeRequested() {
        showHome();
    }

    @Override
    public void onPdfResultRequested() {
        showPdfResult();
    }

    @Override
    public void onPdfResultClosed() {
        closePdfResult();
    }

    @Override
    public void onEditPdfPagesRequested() {
        closePdfResult();
    }

    @Override
    public void onNewPdfDocumentRequested() {
        getSupportFragmentManager().popBackStackImmediate(
                PdfResultFragment.TAG,
                FragmentManager.POP_BACK_STACK_INCLUSIVE
        );
        showHome();
    }

    @Override
    public void onPageEditRequested(long pageId) {
        if (!sessionViewModel.canEditPages()
                || getSupportFragmentManager().findFragmentByTag(PageEditFragment.TAG) != null) {
            return;
        }
        getSupportFragmentManager()
                .beginTransaction()
                .setReorderingAllowed(true)
                .add(
                        R.id.fragment_container,
                        PageEditFragment.newInstance(pageId),
                        PageEditFragment.TAG
                )
                .addToBackStack(PageEditFragment.TAG)
                .commit();
    }

    private void configureWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (view, insets) -> {
            Insets systemBars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout()
            );
            int contentPadding = getResources().getDimensionPixelSize(R.dimen.screen_content_padding);
            view.setPadding(
                    systemBars.left + contentPadding,
                    systemBars.top + contentPadding,
                    systemBars.right + contentPadding,
                    systemBars.bottom + contentPadding
            );
            return insets;
        });
    }

    private void configureBackNavigation() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                PdfResultFragment resultFragment = findVisibleFragment(PdfResultFragment.TAG);
                if (resultFragment != null) {
                    resultFragment.handleBackPressed();
                    return;
                }
                ImagePickerFragment imagePickerFragment = findVisibleFragment(
                        ImagePickerFragment.TAG
                );
                if (imagePickerFragment != null) {
                    imagePickerFragment.handleBackPressed();
                    return;
                }
                PageEditFragment pageEditFragment = findVisibleFragment(PageEditFragment.TAG);
                if (pageEditFragment != null) {
                    pageEditFragment.handleBackPressed();
                    return;
                }
                ScanReviewFragment scanReviewFragment = findVisibleFragment(
                        ScanReviewFragment.TAG
                );
                if (scanReviewFragment != null) {
                    scanReviewFragment.handleBackPressed();
                    return;
                }
                SmartScanFragment smartScanFragment = findVisibleFragment(SmartScanFragment.TAG);
                if (smartScanFragment != null) {
                    smartScanFragment.handleBackPressed();
                    return;
                }
                if (findVisibleFragment(PrivacyPolicyFragment.TAG) != null
                        || findVisibleFragment(AboutFragment.TAG) != null) {
                    getSupportFragmentManager().popBackStack();
                    return;
                }
                if (getSupportFragmentManager().findFragmentByTag(AllToolsFragment.TAG) != null) {
                    getSupportFragmentManager().popBackStack();
                    return;
                }
                if (getSupportFragmentManager().findFragmentByTag(EditorFragment.TAG) != null) {
                    if (sessionViewModel.isGenerationInProgress()) {
                        return;
                    }
                    showHome();
                    return;
                }
                finish();
            }
        });
    }

    private void showHome() {
        getSupportFragmentManager()
                .beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.fragment_container, new HomeFragment(), HomeFragment.TAG)
                .commit();
    }

    private void showEditor() {
        getSupportFragmentManager()
                .beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.fragment_container, new EditorFragment(), EditorFragment.TAG)
                .commitNow();
    }

    private void showImagePicker(ImageImportMode mode) {
        getSupportFragmentManager()
                .beginTransaction()
                .setReorderingAllowed(true)
                .replace(
                        R.id.fragment_container,
                        ImagePickerFragment.newInstance(mode),
                        ImagePickerFragment.TAG
                )
                .commit();
    }

    private void showPdfResult() {
        if (sessionViewModel.getLastPdfResult() == null
                || getSupportFragmentManager().findFragmentByTag(PdfResultFragment.TAG) != null) {
            return;
        }
        getSupportFragmentManager()
                .beginTransaction()
                .setReorderingAllowed(true)
                .setCustomAnimations(
                        R.anim.pdf_result_enter,
                        R.anim.pdf_result_exit,
                        R.anim.pdf_result_pop_enter,
                        R.anim.pdf_result_pop_exit
                )
                .replace(
                        R.id.fragment_container,
                        new PdfResultFragment(),
                        PdfResultFragment.TAG
                )
                .addToBackStack(PdfResultFragment.TAG)
                .commit();
    }

    private void closePdfResult() {
        boolean popped = getSupportFragmentManager().popBackStackImmediate(
                PdfResultFragment.TAG,
                FragmentManager.POP_BACK_STACK_INCLUSIVE
        );
        if (!popped) {
            if (sessionViewModel.hasPages()) {
                showEditor();
            } else {
                showHome();
            }
        }
    }

    private void showAllTools() {
        getSupportFragmentManager()
                .beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.fragment_container, new AllToolsFragment(), AllToolsFragment.TAG)
                .addToBackStack(AllToolsFragment.TAG)
                .commit();
    }

    private void showAbout() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        if (aboutNavigationPending
                || fragmentManager.isStateSaved()
                || fragmentManager.findFragmentByTag(AboutFragment.TAG) != null) {
            return;
        }
        aboutNavigationPending = true;
        fragmentManager
                .beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.fragment_container, new AboutFragment(), AboutFragment.TAG)
                .addToBackStack(AboutFragment.TAG)
                .commit();
        fragmentManager.executePendingTransactions();
        aboutNavigationPending = false;
    }

    private void showPrivacyPolicy() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        if (privacyNavigationPending
                || fragmentManager.isStateSaved()
                || fragmentManager.findFragmentByTag(PrivacyPolicyFragment.TAG) != null) {
            return;
        }
        privacyNavigationPending = true;
        fragmentManager
                .beginTransaction()
                .setReorderingAllowed(true)
                .replace(
                        R.id.fragment_container,
                        new PrivacyPolicyFragment(),
                        PrivacyPolicyFragment.TAG
                )
                .addToBackStack(PrivacyPolicyFragment.TAG)
                .commit();
        fragmentManager.executePendingTransactions();
        privacyNavigationPending = false;
    }

    private void showSmartScan() {
        getSupportFragmentManager()
                .beginTransaction()
                .setReorderingAllowed(true)
                .replace(
                        R.id.fragment_container,
                        new SmartScanFragment(),
                        SmartScanFragment.TAG
                )
                .addToBackStack(SmartScanFragment.TAG)
                .commit();
    }

    private void deleteCapturedFiles(java.util.List<String> fileNames) {
        for (String fileName : fileNames) {
            capturedImageStorage.delete(fileName);
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends Fragment> T findVisibleFragment(String tag) {
        Fragment fragment = getSupportFragmentManager().findFragmentByTag(tag);
        return fragment != null && fragment.isVisible() ? (T) fragment : null;
    }
}
