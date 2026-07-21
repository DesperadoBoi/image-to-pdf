package com.desperadoboi.imagetopdf.ui.gallery;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.desperadoboi.imagetopdf.R;
import com.desperadoboi.imagetopdf.image.CapturedImageStorage;
import com.desperadoboi.imagetopdf.image.GalleryThumbnailLoader;
import com.desperadoboi.imagetopdf.image.MediaGalleryRepository;
import com.desperadoboi.imagetopdf.model.DocumentSessionViewModel;
import com.desperadoboi.imagetopdf.model.GalleryAccessState;
import com.desperadoboi.imagetopdf.model.ImageImportEntry;
import com.desperadoboi.imagetopdf.model.ImageImportMode;
import com.desperadoboi.imagetopdf.model.ImageImportResult;
import com.desperadoboi.imagetopdf.model.ImageImportSource;
import com.desperadoboi.imagetopdf.model.MediaAlbum;
import com.desperadoboi.imagetopdf.model.MediaImage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ImagePickerFragment extends Fragment {
    public static final String TAG = "ImagePickerFragment";
    private static final String ARG_MODE = "image_import_mode";
    private static final int GRID_COLUMNS = 3;
    private static final int RECENT_LIMIT = 12;

    private DocumentSessionViewModel sessionViewModel;
    private ImagePickerViewModel pickerViewModel;
    private ImageImportMode importMode;
    private NavigationCallback navigationCallback;
    private CapturedImageStorage capturedImageStorage;
    private MediaGalleryRepository galleryRepository;
    private GalleryThumbnailLoader thumbnailLoader;

    private ActivityResultLauncher<String[]> permissionLauncher;
    private ActivityResultLauncher<PickVisualMediaRequest> photoPickerLauncher;
    private ActivityResultLauncher<String[]> filesLauncher;
    private ActivityResultLauncher<Uri> cameraLauncher;

    private GalleryImageAdapter recentAdapter;
    private GalleryImageAdapter gridAdapter;
    private MediaAlbumAdapter albumAdapter;
    private View albumScreen;
    private RecyclerView gridRecyclerView;
    private View accessPanel;
    private TextView accessTitle;
    private TextView accessMessage;
    private Button permissionButton;
    private Button photoPickerButton;
    private Button settingsButton;
    private TextView recentTitle;
    private RecyclerView recentRecyclerView;
    private TextView albumsTitle;
    private RecyclerView albumsRecyclerView;
    private ImageButton modeButton;
    private TextView currentAlbumText;
    private Button importButton;
    private ProgressBar progressBar;
    private boolean viewDestroyed;

    public static ImagePickerFragment newInstance(ImageImportMode mode) {
        ImagePickerFragment fragment = new ImagePickerFragment();
        Bundle arguments = new Bundle();
        arguments.putString(ARG_MODE, mode.name());
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
        importMode = ImageImportMode.valueOf(requireArguments().getString(ARG_MODE));
        sessionViewModel = new ViewModelProvider(requireActivity())
                .get(DocumentSessionViewModel.class);
        pickerViewModel = new ViewModelProvider(this).get(ImagePickerViewModel.class);
        capturedImageStorage = new CapturedImageStorage(requireContext());
        galleryRepository = new MediaGalleryRepository(
                requireContext().getApplicationContext().getContentResolver(),
                getString(R.string.gallery_missing_album_name)
        );
        thumbnailLoader = new GalleryThumbnailLoader(
                requireContext().getApplicationContext().getContentResolver(),
                ContextCompat.getMainExecutor(requireContext())
        );
        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                this::handlePermissionResult
        );
        photoPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.PickMultipleVisualMedia(),
                this::handlePhotoPickerResult
        );
        filesLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenMultipleDocuments(),
                this::handleFilesResult
        );
        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicture(),
                this::handleCameraResult
        );
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.fragment_image_picker, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewDestroyed = false;
        bindViews(view);
        configureLists();
        configureActions(view);
        refreshAccessState(false);
        render();
        if (pickerViewModel.getAccessState() == GalleryAccessState.NOT_REQUESTED) {
            view.post(this::requestGalleryAccess);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (albumScreen != null) {
            refreshAccessState(true);
        }
    }

    @Override
    public void onDestroyView() {
        viewDestroyed = true;
        albumScreen = null;
        gridRecyclerView = null;
        accessPanel = null;
        accessTitle = null;
        accessMessage = null;
        permissionButton = null;
        photoPickerButton = null;
        settingsButton = null;
        recentTitle = null;
        recentRecyclerView = null;
        albumsTitle = null;
        albumsRecyclerView = null;
        modeButton = null;
        currentAlbumText = null;
        importButton = null;
        progressBar = null;
        recentAdapter = null;
        gridAdapter = null;
        albumAdapter = null;
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        galleryRepository.shutdown();
        thumbnailLoader.shutdown();
        super.onDestroy();
    }

    @Override
    public void onDetach() {
        navigationCallback = null;
        super.onDetach();
    }

    public void handleBackPressed() {
        cleanupUnimportedCaptures();
        if (navigationCallback != null) {
            navigationCallback.onImagePickerCancelled(importMode);
        }
    }

    private void bindViews(View view) {
        albumScreen = view.findViewById(R.id.scroll_image_picker_albums);
        gridRecyclerView = view.findViewById(R.id.recycler_gallery_grid);
        accessPanel = view.findViewById(R.id.layout_gallery_access_state);
        accessTitle = view.findViewById(R.id.text_gallery_access_title);
        accessMessage = view.findViewById(R.id.text_gallery_access_message);
        permissionButton = view.findViewById(R.id.button_gallery_permission);
        photoPickerButton = view.findViewById(R.id.button_gallery_photo_picker);
        settingsButton = view.findViewById(R.id.button_gallery_settings);
        recentTitle = view.findViewById(R.id.text_gallery_recent_title);
        recentRecyclerView = view.findViewById(R.id.recycler_gallery_recent);
        albumsTitle = view.findViewById(R.id.text_gallery_albums_title);
        albumsRecyclerView = view.findViewById(R.id.recycler_gallery_albums);
        modeButton = view.findViewById(R.id.button_image_picker_mode);
        currentAlbumText = view.findViewById(R.id.text_gallery_current_album);
        importButton = view.findViewById(R.id.button_gallery_import);
        progressBar = view.findViewById(R.id.progress_gallery);
    }

    private void configureLists() {
        GalleryImageAdapter.Callback imageCallback = new GalleryImageAdapter.Callback() {
            @Override
            public void onImageClicked(MediaImage image) {
                pickerViewModel.toggleMediaImage(image);
                refreshSelectionUi();
            }

            @Override
            public void onCameraRequested() {
                launchCamera();
            }
        };
        recentAdapter = new GalleryImageAdapter(
                GalleryImageAdapter.Mode.RECENT,
                pickerViewModel.getSelection(),
                thumbnailLoader,
                imageCallback
        );
        gridAdapter = new GalleryImageAdapter(
                GalleryImageAdapter.Mode.GRID,
                pickerViewModel.getSelection(),
                thumbnailLoader,
                imageCallback
        );
        albumAdapter = new MediaAlbumAdapter(thumbnailLoader, this::openAlbum);

        recentRecyclerView.setLayoutManager(new LinearLayoutManager(
                requireContext(),
                RecyclerView.HORIZONTAL,
                false
        ));
        recentRecyclerView.setAdapter(recentAdapter);
        gridRecyclerView.setLayoutManager(new GridLayoutManager(requireContext(), GRID_COLUMNS));
        gridRecyclerView.setAdapter(gridAdapter);
        albumsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        albumsRecyclerView.setAdapter(albumAdapter);
    }

    private void configureActions(View view) {
        view.findViewById(R.id.button_image_picker_back).setOnClickListener(
                clicked -> handleBackPressed()
        );
        modeButton.setOnClickListener(clicked -> toggleDisplayMode());
        view.findViewById(R.id.row_gallery_files).setOnClickListener(
                clicked -> filesLauncher.launch(new String[]{"image/*"})
        );
        permissionButton.setOnClickListener(clicked -> {
            if (pickerViewModel.getGalleryUiState() == GalleryUiState.ERROR
                    && pickerViewModel.getAccessState().canReadMediaStore()) {
                loadGallery();
            } else {
                requestGalleryAccess();
            }
        });
        photoPickerButton.setOnClickListener(clicked -> launchPhotoPicker());
        settingsButton.setOnClickListener(clicked -> openAppSettings());
        importButton.setOnClickListener(clicked -> importSelection());
    }

    private void refreshAccessState(boolean fromResume) {
        GalleryAccessState previousState = pickerViewModel.getAccessState();
        GalleryAccessState state = resolveAccessState();
        pickerViewModel.setAccessState(state);
        if (state.canReadMediaStore()
                && !pickerViewModel.isGalleryLoadInProgress()
                && (!fromResume
                        || previousState != state
                        || pickerViewModel.hasCompletedGalleryLoad()
                        || pickerViewModel.getGalleryUiState() == GalleryUiState.ERROR)) {
            loadGallery();
        }
        render();
    }

    private GalleryAccessState resolveAccessState() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && hasPermission(Manifest.permission.READ_MEDIA_IMAGES)) {
            return GalleryAccessState.FULL;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                && hasPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)) {
            return GalleryAccessState.PARTIAL;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                && hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE)) {
            return GalleryAccessState.FULL;
        }
        return pickerViewModel.isPermissionRequested()
                ? GalleryAccessState.DENIED
                : GalleryAccessState.NOT_REQUESTED;
    }

    private boolean hasPermission(String permission) {
        return ContextCompat.checkSelfPermission(requireContext(), permission)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestGalleryAccess() {
        if (!isAdded()) {
            return;
        }
        pickerViewModel.setPermissionRequested(true);
        pickerViewModel.setPermanentlyDenied(false);
        permissionLauncher.launch(requiredPermissions());
    }

    private String[] requiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return new String[]{
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            };
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return new String[]{Manifest.permission.READ_MEDIA_IMAGES};
        }
        return new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
    }

    private void handlePermissionResult(Map<String, Boolean> ignored) {
        GalleryAccessState state = resolveAccessState();
        if (state == GalleryAccessState.DENIED) {
            boolean canExplainAny = false;
            for (String permission : requiredPermissions()) {
                canExplainAny |= shouldShowRequestPermissionRationale(permission);
            }
            pickerViewModel.setPermanentlyDenied(!canExplainAny);
        }
        pickerViewModel.setAccessState(state);
        if (state.canReadMediaStore()) {
            loadGallery();
        }
        render();
    }

    private void loadGallery() {
        if (pickerViewModel.isGalleryLoadInProgress()) {
            return;
        }
        long operationId = pickerViewModel.beginGalleryLoad();
        render();
        galleryRepository.load(
                ContextCompat.getMainExecutor(requireContext()),
                new MediaGalleryRepository.Callback() {
                    @Override
                    public void onLoaded(List<MediaImage> images, List<MediaAlbum> albums) {
                        if (!pickerViewModel.completeGalleryLoad(
                                operationId,
                                images,
                                albums
                        ) || viewDestroyed) {
                            return;
                        }
                        render();
                    }

                    @Override
                    public void onError(RuntimeException exception) {
                        if (!pickerViewModel.failGalleryLoad(operationId, exception)
                                || viewDestroyed) {
                            return;
                        }
                        render();
                    }
                }
        );
    }

    private void render() {
        if (viewDestroyed || albumScreen == null) {
            return;
        }
        GalleryAccessState state = pickerViewModel.getAccessState();
        GalleryUiState uiState = pickerViewModel.getGalleryUiState();
        boolean hasContent = uiState == GalleryUiState.CONTENT
                && !pickerViewModel.getImages().isEmpty();
        modeButton.setEnabled(state.canReadMediaStore() && hasContent);
        renderAccessPanel(state, uiState);
        recentTitle.setVisibility(hasContent ? View.VISIBLE : View.GONE);
        recentRecyclerView.setVisibility(hasContent ? View.VISIBLE : View.GONE);
        albumsTitle.setVisibility(hasContent ? View.VISIBLE : View.GONE);
        albumsRecyclerView.setVisibility(hasContent ? View.VISIBLE : View.GONE);

        List<MediaImage> recent = pickerViewModel.getImages().subList(
                0,
                Math.min(RECENT_LIMIT, pickerViewModel.getImages().size())
        );
        recentAdapter.submit(recent, true);
        albumAdapter.submit(
                pickerViewModel.getImages(),
                pickerViewModel.getAlbums(),
                getString(R.string.gallery_all_images)
        );
        submitCurrentGrid();
        renderDisplayMode();
        refreshSelectionUi();
        renderLoading();
    }

    private void renderAccessPanel(GalleryAccessState state, GalleryUiState uiState) {
        if (uiState == GalleryUiState.LOADING
                || (uiState == GalleryUiState.CONTENT && state == GalleryAccessState.FULL)) {
            accessPanel.setVisibility(View.GONE);
            return;
        }
        accessPanel.setVisibility(View.VISIBLE);
        settingsButton.setVisibility(View.GONE);
        permissionButton.setVisibility(View.VISIBLE);
        photoPickerButton.setVisibility(View.VISIBLE);
        photoPickerButton.setText(R.string.action_select_individual_images);
        if (uiState == GalleryUiState.ERROR) {
            accessTitle.setText(R.string.gallery_load_error_title);
            accessMessage.setText(R.string.gallery_load_error);
            permissionButton.setText(R.string.action_retry_gallery_load);
            return;
        }
        if (uiState == GalleryUiState.EMPTY) {
            accessTitle.setText(R.string.gallery_empty_title);
            accessMessage.setText(R.string.gallery_empty_message);
            if (state == GalleryAccessState.PARTIAL) {
                permissionButton.setText(R.string.action_change_gallery_access);
                photoPickerButton.setText(R.string.action_add_more_images);
            } else {
                permissionButton.setVisibility(View.GONE);
                photoPickerButton.setText(R.string.action_select_individual_images);
            }
            return;
        }
        if (uiState == GalleryUiState.CONTENT && state == GalleryAccessState.PARTIAL) {
            accessTitle.setText(R.string.gallery_access_partial_title);
            accessMessage.setText(R.string.gallery_access_partial_message);
            permissionButton.setText(R.string.action_change_gallery_access);
            photoPickerButton.setText(R.string.action_add_more_images);
            return;
        }
        accessTitle.setText(state == GalleryAccessState.NOT_REQUESTED
                ? R.string.gallery_access_required_title
                : R.string.gallery_access_denied_title);
        photoPickerButton.setText(R.string.action_select_individual_images);
        accessMessage.setText(pickerViewModel.isPermanentlyDenied()
                ? R.string.gallery_access_permanently_denied_message
                : (state == GalleryAccessState.NOT_REQUESTED
                        ? R.string.gallery_access_required_message
                        : R.string.gallery_access_denied_message));
        permissionButton.setText(R.string.action_allow_gallery_access);
        if (pickerViewModel.isPermanentlyDenied()) {
            permissionButton.setVisibility(View.GONE);
            settingsButton.setVisibility(View.VISIBLE);
        }
    }

    private void renderLoading() {
        if (progressBar != null) {
            progressBar.setVisibility(
                    pickerViewModel.getGalleryUiState() == GalleryUiState.LOADING
                            ? View.VISIBLE
                            : View.GONE
            );
        }
    }

    private void toggleDisplayMode() {
        if (pickerViewModel.getDisplayMode() == ImagePickerViewModel.DisplayMode.GRID) {
            pickerViewModel.showAlbums();
        } else {
            pickerViewModel.showAlbum(
                    pickerViewModel.getCurrentBucketId(),
                    pickerViewModel.getCurrentAlbumName() == null
                            ? getString(R.string.gallery_all_images)
                            : pickerViewModel.getCurrentAlbumName()
            );
        }
        renderDisplayMode();
    }

    private void openAlbum(String bucketId, String displayName) {
        pickerViewModel.showAlbum(bucketId, displayName);
        submitCurrentGrid();
        renderDisplayMode();
    }

    private void submitCurrentGrid() {
        String bucketId = pickerViewModel.getCurrentBucketId();
        ArrayList<MediaImage> images = new ArrayList<>();
        for (MediaImage image : pickerViewModel.getImages()) {
            if (bucketId == null || bucketId.equals(image.getBucketId())) {
                images.add(image);
            }
        }
        gridAdapter.submit(images, bucketId == null);
    }

    private void renderDisplayMode() {
        boolean albumsMode = pickerViewModel.getGalleryUiState() != GalleryUiState.CONTENT
                || pickerViewModel.getDisplayMode() == ImagePickerViewModel.DisplayMode.ALBUMS;
        albumScreen.setVisibility(albumsMode ? View.VISIBLE : View.GONE);
        gridRecyclerView.setVisibility(albumsMode ? View.GONE : View.VISIBLE);
        modeButton.setImageResource(albumsMode
                ? R.drawable.ic_action_grid_24
                : R.drawable.ic_action_albums_24);
        modeButton.setContentDescription(getString(albumsMode
                ? R.string.action_show_image_grid_content_description
                : R.string.action_show_album_list_content_description));
        currentAlbumText.setText(pickerViewModel.getCurrentAlbumName() == null
                ? getString(R.string.gallery_all_images)
                : pickerViewModel.getCurrentAlbumName());
    }

    private void refreshSelectionUi() {
        if (importButton == null) {
            return;
        }
        int count = pickerViewModel.getSelection().size();
        importButton.setEnabled(count > 0);
        importButton.setText(count == 0
                ? getString(R.string.action_import_images_empty)
                : getString(R.string.action_import_images, count));
        recentAdapter.notifySelectionChanged();
        gridAdapter.notifySelectionChanged();
    }

    private void launchPhotoPicker() {
        PickVisualMediaRequest request = new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build();
        photoPickerLauncher.launch(request);
    }

    private void handlePhotoPickerResult(List<Uri> uris) {
        if (uris == null || uris.isEmpty()) {
            return;
        }
        pickerViewModel.addExternalUris(uris, ImageImportSource.GALLERY);
        refreshSelectionUi();
    }

    private void handleFilesResult(List<Uri> uris) {
        if (uris == null || uris.isEmpty()) {
            return;
        }
        for (Uri uri : uris) {
            takePersistableReadPermission(uri);
        }
        pickerViewModel.addExternalUris(uris, ImageImportSource.FILES);
        refreshSelectionUi();
    }

    private void takePersistableReadPermission(Uri uri) {
        try {
            requireContext().getContentResolver().takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            );
        } catch (SecurityException exception) {
            // The current grant is still usable when a provider does not persist permissions.
        }
    }

    private void launchCamera() {
        CapturedImageStorage.CapturedImage image;
        try {
            image = capturedImageStorage.createCapturedImage();
        } catch (IOException | RuntimeException exception) {
            showToast(R.string.status_camera_destination_error);
            return;
        }
        sessionViewModel.setPendingCapturedImage(
                image.getUri(),
                image.getFileName(),
                DocumentSessionViewModel.CaptureTarget.PICKER
        );
        try {
            cameraLauncher.launch(image.getUri());
        } catch (ActivityNotFoundException exception) {
            cleanupPendingPickerCapture();
            showToast(R.string.status_camera_app_not_found);
        } catch (RuntimeException exception) {
            cleanupPendingPickerCapture();
            showToast(R.string.status_camera_destination_error);
        }
    }

    private void handleCameraResult(Boolean successful) {
        DocumentSessionViewModel.PendingCapturedImage pending =
                sessionViewModel.getPendingCapturedImage();
        if (pending == null
                || pending.getTarget() != DocumentSessionViewModel.CaptureTarget.PICKER) {
            return;
        }
        pending = sessionViewModel.clearPendingCapturedImage();
        if (!Boolean.TRUE.equals(successful)) {
            capturedImageStorage.delete(pending.getCapturedFileName());
            return;
        }
        if (!capturedImageStorage.existsAndHasContent(pending.getCapturedFileName())) {
            capturedImageStorage.delete(pending.getCapturedFileName());
            showToast(R.string.status_camera_empty_file);
            return;
        }
        pickerViewModel.addCamera(pending.getUri(), pending.getCapturedFileName());
        refreshSelectionUi();
    }

    private void importSelection() {
        List<ImageImportEntry> entries = pickerViewModel.getImportEntriesSnapshot();
        if (entries.isEmpty()) {
            return;
        }
        ImageImportResult result = sessionViewModel.importImages(importMode, entries);
        if (!result.hasChanges()) {
            return;
        }
        for (String fileName : result.getCapturedFileNamesToDelete()) {
            capturedImageStorage.delete(fileName);
        }
        if (importMode == ImageImportMode.APPEND_TO_DOCUMENT) {
            sessionViewModel.setPendingEditorScrollPosition(result.getFirstInsertedPosition());
        }
        pickerViewModel.clearSelection();
        if (navigationCallback != null) {
            navigationCallback.onImagesImported();
        }
    }

    private void cleanupUnimportedCaptures() {
        for (String fileName : pickerViewModel.getUnimportedCameraFileNames()) {
            capturedImageStorage.delete(fileName);
        }
        pickerViewModel.clearSelection();
        cleanupPendingPickerCapture();
    }

    private void cleanupPendingPickerCapture() {
        DocumentSessionViewModel.PendingCapturedImage pending =
                sessionViewModel.getPendingCapturedImage();
        if (pending == null
                || pending.getTarget() != DocumentSessionViewModel.CaptureTarget.PICKER) {
            return;
        }
        sessionViewModel.clearPendingCapturedImage();
        capturedImageStorage.delete(pending.getCapturedFileName());
    }

    private void openAppSettings() {
        Intent intent = new Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", requireContext().getPackageName(), null)
        );
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException exception) {
            showToast(R.string.gallery_access_denied_message);
        }
    }

    private void showToast(int stringResId) {
        if (isAdded()) {
            Toast.makeText(requireContext(), stringResId, Toast.LENGTH_SHORT).show();
        }
    }

    public interface NavigationCallback {
        void onImagePickerCancelled(ImageImportMode mode);

        void onImagesImported();
    }
}
