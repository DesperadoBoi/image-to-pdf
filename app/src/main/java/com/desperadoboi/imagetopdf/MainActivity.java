package com.desperadoboi.imagetopdf;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.desperadoboi.imagetopdf.model.DocumentSessionViewModel;
import com.desperadoboi.imagetopdf.model.ImageImportMode;
import com.desperadoboi.imagetopdf.ui.editor.EditorFragment;
import com.desperadoboi.imagetopdf.ui.editor.PageEditFragment;
import com.desperadoboi.imagetopdf.ui.gallery.ImagePickerFragment;
import com.desperadoboi.imagetopdf.ui.home.HomeFragment;
import com.desperadoboi.imagetopdf.ui.result.PdfResultFragment;
import com.desperadoboi.imagetopdf.ui.tools.AllToolsFragment;

public class MainActivity extends AppCompatActivity
        implements HomeFragment.NavigationCallback,
        EditorFragment.NavigationCallback,
        ImagePickerFragment.NavigationCallback,
        PdfResultFragment.NavigationCallback {
    private DocumentSessionViewModel sessionViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        sessionViewModel = new ViewModelProvider(this).get(DocumentSessionViewModel.class);
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
    public void onReturnHomeRequested() {
        showHome();
    }

    @Override
    public void onPdfResultRequested() {
        showPdfResult();
    }

    @Override
    public void onPdfResultClosed() {
        if (sessionViewModel.hasPages()) {
            showEditor();
        } else {
            showHome();
        }
    }

    @Override
    public void onEditPdfPagesRequested() {
        showEditor();
    }

    @Override
    public void onNewPdfDocumentRequested() {
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
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
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
                PdfResultFragment resultFragment = (PdfResultFragment)
                        getSupportFragmentManager().findFragmentByTag(PdfResultFragment.TAG);
                if (resultFragment != null) {
                    resultFragment.handleBackPressed();
                    return;
                }
                ImagePickerFragment imagePickerFragment = (ImagePickerFragment)
                        getSupportFragmentManager().findFragmentByTag(ImagePickerFragment.TAG);
                if (imagePickerFragment != null) {
                    imagePickerFragment.handleBackPressed();
                    return;
                }
                PageEditFragment pageEditFragment = (PageEditFragment)
                        getSupportFragmentManager().findFragmentByTag(PageEditFragment.TAG);
                if (pageEditFragment != null) {
                    pageEditFragment.handleBackPressed();
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
                .replace(R.id.fragment_container, new HomeFragment(), HomeFragment.TAG)
                .commit();
    }

    private void showEditor() {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, new EditorFragment(), EditorFragment.TAG)
                .commit();
    }

    private void showImagePicker(ImageImportMode mode) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(
                        R.id.fragment_container,
                        ImagePickerFragment.newInstance(mode),
                        ImagePickerFragment.TAG
                )
                .commit();
    }

    private void showPdfResult() {
        if (sessionViewModel.getLastPdfResult() == null) {
            return;
        }
        getSupportFragmentManager()
                .beginTransaction()
                .replace(
                        R.id.fragment_container,
                        new PdfResultFragment(),
                        PdfResultFragment.TAG
                )
                .commit();
    }

    private void showAllTools() {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, new AllToolsFragment(), AllToolsFragment.TAG)
                .addToBackStack(AllToolsFragment.TAG)
                .commit();
    }
}
