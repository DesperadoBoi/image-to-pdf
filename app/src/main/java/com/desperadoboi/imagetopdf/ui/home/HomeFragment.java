package com.desperadoboi.imagetopdf.ui.home;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.desperadoboi.imagetopdf.R;
import com.desperadoboi.imagetopdf.image.CapturedImageStorage;
import com.desperadoboi.imagetopdf.model.DocumentSessionViewModel;
import com.desperadoboi.imagetopdf.model.ImageImportMode;
import com.desperadoboi.imagetopdf.model.ImageImportRequest;
import com.desperadoboi.imagetopdf.model.ImageImportResult;
import com.desperadoboi.imagetopdf.model.ImageImportSource;
import com.desperadoboi.imagetopdf.ui.importer.ImageImportCoordinator;
import com.desperadoboi.imagetopdf.ui.importer.ImagePickerLauncher;
import com.desperadoboi.imagetopdf.ui.importer.ImageSourceSheet;
import com.desperadoboi.imagetopdf.ui.tools.AllToolsFragment;
import com.desperadoboi.imagetopdf.ui.tools.ToolCatalog;
import com.desperadoboi.imagetopdf.ui.tools.ToolId;

import java.io.IOException;
import java.util.List;

public final class HomeFragment extends Fragment {
    public static final String TAG = "HomeFragment";
    private static final int HOME_COLUMN_COUNT = 4;

    private DocumentSessionViewModel sessionViewModel;
    private NavigationCallback navigationCallback;
    private ActivityResultLauncher<PickVisualMediaRequest> photoPickerLauncher;
    private ActivityResultLauncher<Uri> cameraLauncher;
    private ActivityResultLauncher<String[]> filesLauncher;
    private CapturedImageStorage capturedImageStorage;
    private ImagePickerLauncher imagePickerLauncher;

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
        capturedImageStorage = new CapturedImageStorage(requireContext());
        photoPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.PickMultipleVisualMedia(),
                this::handleImageSelection
        );
        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicture(),
                this::handleCameraResult
        );
        filesLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenMultipleDocuments(),
                this::handleFilesSelection
        );
        imagePickerLauncher = new ImageImportCoordinator()
                .register(ImageImportSource.GALLERY, request -> launchPhotoPicker())
                .register(ImageImportSource.CAMERA, request -> launchCamera())
                .register(ImageImportSource.FILES, request -> filesLauncher.launch(
                        new String[]{"image/*"}
                ));
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
        HomeToolAdapter adapter = new HomeToolAdapter(this::handleToolSelected);
        RecyclerView toolsRecyclerView = view.findViewById(R.id.recycler_home_tools);
        toolsRecyclerView.setLayoutManager(new GridLayoutManager(
                requireContext(),
                HOME_COLUMN_COUNT
        ));
        toolsRecyclerView.setAdapter(adapter);
        adapter.submitList(ToolCatalog.getHomeTools());
        configureImageSourceResult();
        configureCatalogToolResult();
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

    private void handleToolSelected(ToolId toolId) {
        switch (toolId) {
            case IMAGE_TO_PDF:
                showImageSourceSheet();
                break;
            case CAMERA:
                imagePickerLauncher.launch(new ImageImportRequest(
                        ImageImportSource.CAMERA,
                        ImageImportMode.NEW_DOCUMENT
                ));
                break;
            case MORE:
                if (navigationCallback != null) {
                    navigationCallback.onAllToolsRequested();
                }
                break;
            default:
                break;
        }
    }

    private void configureCatalogToolResult() {
        getParentFragmentManager().setFragmentResultListener(
                AllToolsFragment.RESULT_TOOL_REQUEST,
                getViewLifecycleOwner(),
                (requestKey, result) -> {
                    String toolName = result.getString(AllToolsFragment.RESULT_TOOL_ID);
                    if (ToolId.IMAGE_TO_PDF.name().equals(toolName)) {
                        showImageSourceSheet();
                    }
                }
        );
    }

    private void configureImageSourceResult() {
        getParentFragmentManager().setFragmentResultListener(
                ImageSourceSheet.RESULT_SOURCE_SELECTED,
                getViewLifecycleOwner(),
                (requestKey, result) -> imagePickerLauncher.launch(new ImageImportRequest(
                        ImageImportSource.valueOf(result.getString(ImageSourceSheet.RESULT_SOURCE)),
                        ImageImportMode.valueOf(result.getString(ImageSourceSheet.RESULT_MODE))
                ))
        );
    }

    private void showImageSourceSheet() {
        if (getParentFragmentManager().findFragmentByTag(ImageSourceSheet.TAG) != null) {
            return;
        }
        ImageSourceSheet.newInstance(ImageImportMode.NEW_DOCUMENT)
                .show(getParentFragmentManager(), ImageSourceSheet.TAG);
    }

    private void handleImageSelection(List<Uri> imageUris) {
        handleImportedUris(
                ImageImportSource.GALLERY,
                ImageImportMode.NEW_DOCUMENT,
                imageUris
        );
    }

    private void handleFilesSelection(List<Uri> imageUris) {
        if (imageUris == null || imageUris.isEmpty()) {
            return;
        }
        for (Uri imageUri : imageUris) {
            takePersistableReadPermission(imageUri);
        }
        handleImportedUris(
                ImageImportSource.FILES,
                ImageImportMode.NEW_DOCUMENT,
                imageUris
        );
    }

    private void launchCamera() {
        CapturedImageStorage.CapturedImage capturedImage;
        try {
            capturedImage = capturedImageStorage.createCapturedImage();
        } catch (IOException | RuntimeException exception) {
            showToast(getString(R.string.status_camera_destination_error));
            return;
        }

        sessionViewModel.setPendingCapturedImage(
                capturedImage.getUri(),
                capturedImage.getFileName(),
                DocumentSessionViewModel.CaptureTarget.HOME
        );
        try {
            cameraLauncher.launch(capturedImage.getUri());
        } catch (ActivityNotFoundException exception) {
            cleanupPendingCapture();
            showToast(getString(R.string.status_camera_app_not_found));
        } catch (RuntimeException exception) {
            cleanupPendingCapture();
            showToast(getString(R.string.status_camera_destination_error));
        }
    }

    private void handleCameraResult(Boolean isSuccessful) {
        DocumentSessionViewModel.PendingCapturedImage pendingCapturedImage =
                sessionViewModel.getPendingCapturedImage();
        if (pendingCapturedImage == null
                || pendingCapturedImage.getTarget() != DocumentSessionViewModel.CaptureTarget.HOME) {
            return;
        }

        pendingCapturedImage = sessionViewModel.clearPendingCapturedImage();
        if (!Boolean.TRUE.equals(isSuccessful)) {
            capturedImageStorage.delete(pendingCapturedImage.getCapturedFileName());
            return;
        }
        if (!capturedImageStorage.existsAndHasContent(pendingCapturedImage.getCapturedFileName())) {
            capturedImageStorage.delete(pendingCapturedImage.getCapturedFileName());
            showToast(getString(R.string.status_camera_empty_file));
            return;
        }

        ImageImportResult result = sessionViewModel.importCameraImage(
                new ImageImportRequest(
                        ImageImportSource.CAMERA,
                        ImageImportMode.NEW_DOCUMENT
                ),
                pendingCapturedImage.getUri(),
                pendingCapturedImage.getCapturedFileName()
        );
        finishNewDocumentImport(result);
    }

    private void handleImportedUris(
            ImageImportSource source,
            ImageImportMode mode,
            List<Uri> imageUris
    ) {
        ImageImportResult result = sessionViewModel.importImages(
                new ImageImportRequest(source, mode),
                imageUris
        );
        finishNewDocumentImport(result);
    }

    private void finishNewDocumentImport(ImageImportResult result) {
        if (!result.hasChanges()) {
            return;
        }
        deleteCapturedFiles(result.getCapturedFileNamesToDelete());
        if (navigationCallback != null) {
            navigationCallback.onImagesSelectedForEditing();
        }
    }

    private void takePersistableReadPermission(Uri imageUri) {
        try {
            requireContext().getContentResolver().takePersistableUriPermission(
                    imageUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            );
        } catch (SecurityException exception) {
            // Some document providers only grant access for the current session.
        }
    }

    private void cleanupPendingCapture() {
        DocumentSessionViewModel.PendingCapturedImage pendingCapturedImage =
                sessionViewModel.clearPendingCapturedImage();
        if (pendingCapturedImage != null) {
            capturedImageStorage.delete(pendingCapturedImage.getCapturedFileName());
        }
    }

    private void deleteCapturedFiles(List<String> capturedFileNames) {
        for (String capturedFileName : capturedFileNames) {
            capturedImageStorage.delete(capturedFileName);
        }
    }

    private void showToast(String message) {
        if (isAdded()) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
        }
    }

    public interface NavigationCallback {
        void onImagesSelectedForEditing();

        void onAllToolsRequested();
    }
}
