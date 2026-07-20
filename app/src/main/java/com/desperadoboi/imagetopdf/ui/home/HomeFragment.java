package com.desperadoboi.imagetopdf.ui.home;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.desperadoboi.imagetopdf.R;
import com.desperadoboi.imagetopdf.model.DocumentSessionViewModel;
import com.google.android.material.button.MaterialButton;

import java.util.List;

public final class HomeFragment extends Fragment {
    public static final String TAG = "HomeFragment";

    private DocumentSessionViewModel sessionViewModel;
    private NavigationCallback navigationCallback;
    private ActivityResultLauncher<PickVisualMediaRequest> photoPickerLauncher;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof NavigationCallback) {
            navigationCallback = (NavigationCallback) context;
            return;
        }
        throw new IllegalStateException("Host activity must implement NavigationCallback");
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sessionViewModel = new ViewModelProvider(requireActivity())
                .get(DocumentSessionViewModel.class);
        photoPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.PickMultipleVisualMedia(),
                this::handleImageSelection
        );
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        MaterialButton selectImagesButton = view.findViewById(R.id.button_select_images);
        selectImagesButton.setOnClickListener(v -> launchPhotoPicker());
    }

    @Override
    public void onDetach() {
        navigationCallback = null;
        super.onDetach();
    }

    private void launchPhotoPicker() {
        PickVisualMediaRequest request = new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build();
        photoPickerLauncher.launch(request);
    }

    private void handleImageSelection(List<Uri> imageUris) {
        if (imageUris == null || imageUris.isEmpty()) {
            return;
        }
        sessionViewModel.replacePages(imageUris);
        if (navigationCallback != null) {
            navigationCallback.onImagesSelectedForEditing();
        }
    }

    public interface NavigationCallback {
        void onImagesSelectedForEditing();
    }
}
