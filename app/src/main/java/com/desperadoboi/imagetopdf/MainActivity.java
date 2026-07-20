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
import com.desperadoboi.imagetopdf.ui.editor.EditorFragment;
import com.desperadoboi.imagetopdf.ui.home.HomeFragment;

public class MainActivity extends AppCompatActivity
        implements HomeFragment.NavigationCallback, EditorFragment.NavigationCallback {
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
    public void onReturnHomeRequested() {
        showHome();
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
}
