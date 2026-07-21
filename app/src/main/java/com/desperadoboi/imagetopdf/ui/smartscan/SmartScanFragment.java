package com.desperadoboi.imagetopdf.ui.smartscan;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraInfoUnavailableException;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.desperadoboi.imagetopdf.R;
import com.desperadoboi.imagetopdf.image.CapturedImageStorage;
import com.desperadoboi.imagetopdf.util.PageCountFormatter;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

public final class SmartScanFragment extends Fragment implements SensorEventListener {
    public static final String TAG = "SmartScanFragment";

    private static final int MENU_LOCAL_PROCESSING = 1;
    private static final int MENU_SETTINGS = 2;

    private ScanSessionViewModel sessionViewModel;
    private NavigationCallback navigationCallback;
    private CapturedImageStorage capturedImageStorage;
    private ActivityResultLauncher<String> permissionLauncher;
    private ActivityResultLauncher<PickVisualMediaRequest> galleryLauncher;

    private PreviewView previewView;
    private View cameraLayout;
    private View controlsLayout;
    private View permissionLayout;
    private View gridOverlay;
    private TextView permissionText;
    private TextView cameraStatusText;
    private ImageButton torchButton;
    private ImageButton gridButton;
    private ImageButton shutterButton;
    private MaterialButton doneButton;
    private MaterialButton allowPermissionButton;
    private MaterialButton settingsButton;
    private ProgressBar captureProgress;
    private LevelIndicatorView levelIndicator;
    private PageCountFormatter.Labels pageCountLabels;

    private ProcessCameraProvider cameraProvider;
    private boolean cameraProviderRequestPending;
    private Camera camera;
    private ImageCapture imageCapture;
    private ScanSessionViewModel.Observer sessionObserver;

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private boolean sensorRegistered;
    private LevelState levelState = LevelState.UNAVAILABLE;

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
        sessionViewModel = new ViewModelProvider(requireActivity())
                .get(ScanSessionViewModel.class);
        capturedImageStorage = new CapturedImageStorage(requireContext());
        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    sessionViewModel.recordPermissionResult(Boolean.TRUE.equals(granted));
                    renderPermissionState();
                    if (Boolean.TRUE.equals(granted)) {
                        startCamera();
                        registerLevelSensor();
                    }
                }
        );
        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.PickVisualMedia(),
                this::handleGalleryResult
        );
        sensorManager = (SensorManager) requireContext().getSystemService(
                Context.SENSOR_SERVICE
        );
        accelerometer = sensorManager == null
                ? null
                : sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.fragment_smart_scan, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bindViews(view);
        pageCountLabels = new PageCountFormatter.Labels(
                getString(R.string.smart_scan_page_word_one),
                getString(R.string.smart_scan_page_word_few),
                getString(R.string.smart_scan_page_word_many)
        );
        configureActions(view);
        ScanPage interrupted = sessionViewModel.recoverInterruptedCapture();
        deleteIfAppOwned(interrupted);
        int missingCount = sessionViewModel.removeMissingAppOwnedPages(
                capturedImageStorage::existsAndHasContent
        );
        if (missingCount > 0) {
            showToast(R.string.smart_scan_removed_page);
        }
        sessionObserver = this::renderSessionState;
        sessionViewModel.addObserver(sessionObserver);
        renderPermissionState();
        if (resolvePermissionState() == PermissionState.NOT_REQUESTED) {
            view.post(this::requestCameraPermission);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        renderPermissionState();
        if (hasCameraPermission()) {
            startCamera();
            registerLevelSensor();
        }
        openReviewIfReady();
    }

    @Override
    public void onPause() {
        disableTorch();
        unregisterLevelSensor();
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        unregisterLevelSensor();
        disableTorch();
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        cameraProvider = null;
        cameraProviderRequestPending = false;
        camera = null;
        imageCapture = null;
        if (sessionObserver != null) {
            sessionViewModel.removeObserver(sessionObserver);
            sessionObserver = null;
        }
        previewView = null;
        cameraLayout = null;
        controlsLayout = null;
        permissionLayout = null;
        gridOverlay = null;
        permissionText = null;
        cameraStatusText = null;
        torchButton = null;
        gridButton = null;
        shutterButton = null;
        doneButton = null;
        allowPermissionButton = null;
        settingsButton = null;
        captureProgress = null;
        levelIndicator = null;
        pageCountLabels = null;
        super.onDestroyView();
    }

    @Override
    public void onDetach() {
        navigationCallback = null;
        super.onDetach();
    }

    public void handleBackPressed() {
        if (sessionViewModel.getState().hasPages()) {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.smart_scan_exit_title)
                    .setMessage(R.string.smart_scan_exit_message)
                    .setNegativeButton(R.string.smart_scan_exit_keep, null)
                    .setPositiveButton(
                            R.string.smart_scan_exit_confirm,
                            (dialog, which) -> cancelScan()
                    )
                    .show();
            return;
        }
        cancelScan();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER || event.values.length < 3) {
            return;
        }
        LevelState previous = levelState;
        levelState = LevelStateResolver.resolve(
                event.values[0],
                event.values[1],
                event.values[2],
                previous
        );
        if (levelIndicator != null) {
            levelIndicator.setVisibility(
                    levelState == LevelState.UNAVAILABLE ? View.GONE : View.VISIBLE
            );
            levelIndicator.setLevel(
                    levelState,
                    LevelStateResolver.horizontalOffset(
                            event.values[0],
                            event.values[1],
                            event.values[2]
                    )
            );
            if (LevelStateResolver.shouldTriggerHaptic(previous, levelState)) {
                levelIndicator.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private void bindViews(View view) {
        previewView = view.findViewById(R.id.view_scan_preview);
        cameraLayout = view.findViewById(R.id.layout_scan_camera);
        controlsLayout = view.findViewById(R.id.layout_scan_controls);
        permissionLayout = view.findViewById(R.id.layout_scan_permission);
        gridOverlay = view.findViewById(R.id.view_scan_grid);
        permissionText = view.findViewById(R.id.text_scan_permission);
        cameraStatusText = view.findViewById(R.id.text_scan_camera_status);
        torchButton = view.findViewById(R.id.button_scan_torch);
        gridButton = view.findViewById(R.id.button_scan_grid);
        shutterButton = view.findViewById(R.id.button_scan_shutter);
        doneButton = view.findViewById(R.id.button_scan_done);
        allowPermissionButton = view.findViewById(R.id.button_scan_allow_permission);
        settingsButton = view.findViewById(R.id.button_scan_settings);
        captureProgress = view.findViewById(R.id.progress_scan_capture);
        levelIndicator = view.findViewById(R.id.view_scan_level);
    }

    private void configureActions(View view) {
        view.findViewById(R.id.button_scan_back).setOnClickListener(ignored -> handleBackPressed());
        torchButton.setOnClickListener(ignored -> toggleTorch());
        gridButton.setOnClickListener(ignored -> sessionViewModel.toggleGrid());
        view.findViewById(R.id.button_scan_more).setOnClickListener(this::showOverflowMenu);
        shutterButton.setOnClickListener(ignored -> capturePage());
        view.findViewById(R.id.button_scan_gallery).setOnClickListener(ignored -> openGallery());
        view.findViewById(R.id.button_scan_permission_gallery).setOnClickListener(
                ignored -> openGallery()
        );
        doneButton.setOnClickListener(ignored -> finishScan());
        allowPermissionButton.setOnClickListener(ignored -> requestCameraPermission());
        settingsButton.setOnClickListener(ignored -> openAppSettings());
    }

    private void renderSessionState(
            ScanSessionState state,
            ScanCameraState cameraState
    ) {
        if (shutterButton == null) {
            return;
        }
        int pageCount = state.getPageCount();
        boolean capturing = state.isCaptureInProgress();
        boolean cameraReady = imageCapture != null && hasCameraPermission();
        shutterButton.setEnabled(cameraReady && !capturing);
        captureProgress.setVisibility(capturing ? View.VISIBLE : View.GONE);
        SmartScanDoneFormatter.State doneState = SmartScanDoneFormatter.format(
                pageCount,
                currentLocale(),
                getString(R.string.smart_scan_done),
                getString(R.string.smart_scan_done_content_description),
                pageCountLabels
        );
        doneButton.setVisibility(doneState.isVisible() ? View.VISIBLE : View.GONE);
        doneButton.setEnabled(doneState.isVisible());
        doneButton.setText(doneState.getLabel());
        doneButton.setContentDescription(doneState.getContentDescription());
        gridOverlay.setVisibility(cameraState.isGridEnabled() ? View.VISIBLE : View.GONE);
        gridButton.setSelected(cameraState.isGridEnabled());
        torchButton.setEnabled(cameraState.isFlashAvailable() && cameraReady);
        torchButton.setSelected(cameraState.isTorchEnabled());
        openReviewIfReady();
    }

    private Locale currentLocale() {
        return getResources().getConfiguration().getLocales().get(0);
    }

    private void renderPermissionState() {
        if (permissionLayout == null) {
            return;
        }
        PermissionState state = resolvePermissionState();
        boolean granted = state == PermissionState.GRANTED;
        permissionLayout.setVisibility(granted ? View.GONE : View.VISIBLE);
        cameraLayout.setVisibility(granted ? View.VISIBLE : View.GONE);
        controlsLayout.setVisibility(granted ? View.VISIBLE : View.GONE);
        allowPermissionButton.setVisibility(
                state == PermissionState.PERMANENTLY_DENIED ? View.GONE : View.VISIBLE
        );
        settingsButton.setVisibility(
                state == PermissionState.PERMANENTLY_DENIED ? View.VISIBLE : View.GONE
        );
        permissionText.setText(state == PermissionState.RATIONALE
                ? R.string.smart_scan_permission_rationale
                : R.string.smart_scan_permission_message);
    }

    private PermissionState resolvePermissionState() {
        if (hasCameraPermission()) {
            return PermissionState.GRANTED;
        }
        if (!sessionViewModel.wasPermissionRequested()) {
            return PermissionState.NOT_REQUESTED;
        }
        if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            return PermissionState.RATIONALE;
        }
        return sessionViewModel.getPermissionDenialCount() >= 2
                ? PermissionState.PERMANENTLY_DENIED
                : PermissionState.DENIED;
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermission() {
        if (!isAdded() || hasCameraPermission()) {
            renderPermissionState();
            return;
        }
        sessionViewModel.markPermissionRequested();
        permissionLauncher.launch(Manifest.permission.CAMERA);
    }

    private void startCamera() {
        if (!hasCameraPermission()
                || previewView == null
                || cameraProvider != null
                || cameraProviderRequestPending) {
            return;
        }
        cameraProviderRequestPending = true;
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(
                requireContext()
        );
        future.addListener(() -> {
            cameraProviderRequestPending = false;
            if (!isAdded() || previewView == null) {
                return;
            }
            try {
                cameraProvider = future.get();
                bindCameraUseCases();
            } catch (ExecutionException | InterruptedException | RuntimeException exception) {
                if (exception instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                showCameraError(R.string.smart_scan_camera_bind_error);
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    private void bindCameraUseCases() {
        if (cameraProvider == null || previewView == null) {
            return;
        }
        try {
            if (!cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                showCameraError(R.string.smart_scan_no_camera);
                return;
            }
            Preview preview = new Preview.Builder().build();
            preview.setSurfaceProvider(previewView.getSurfaceProvider());
            ImageCapture.Builder captureBuilder = new ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY);
            if (previewView.getDisplay() != null) {
                captureBuilder.setTargetRotation(previewView.getDisplay().getRotation());
            }
            imageCapture = captureBuilder.build();
            cameraProvider.unbindAll();
            camera = cameraProvider.bindToLifecycle(
                    getViewLifecycleOwner(),
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture
            );
            sessionViewModel.setFlashAvailable(camera.getCameraInfo().hasFlashUnit());
            cameraStatusText.setVisibility(View.GONE);
            renderSessionState(sessionViewModel.getState(), sessionViewModel.getCameraState());
        } catch (CameraInfoUnavailableException | RuntimeException exception) {
            imageCapture = null;
            camera = null;
            sessionViewModel.setFlashAvailable(false);
            showCameraError(R.string.smart_scan_camera_bind_error);
        }
    }

    private void capturePage() {
        if (imageCapture == null || sessionViewModel.getState().isCaptureInProgress()) {
            return;
        }
        CapturedImageStorage.CapturedImage capturedImage;
        try {
            capturedImage = capturedImageStorage.createCapturedImage();
        } catch (IOException | RuntimeException exception) {
            showToast(R.string.smart_scan_temp_file_error);
            return;
        }
        ScanPage pending = sessionViewModel.startCapture(
                capturedImage.getUri(),
                capturedImage.getFileName(),
                System.currentTimeMillis()
        );
        if (pending == null) {
            capturedImageStorage.delete(capturedImage.getFileName());
            return;
        }
        if (previewView.getDisplay() != null) {
            imageCapture.setTargetRotation(previewView.getDisplay().getRotation());
        }
        ImageCapture.OutputFileOptions options = new ImageCapture.OutputFileOptions.Builder(
                capturedImage.getFile()
        ).build();
        imageCapture.takePicture(
                options,
                ContextCompat.getMainExecutor(requireContext()),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(
                            @NonNull ImageCapture.OutputFileResults outputFileResults
                    ) {
                        handleCaptureSaved(pending);
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        handleCaptureError(pending);
                    }
                }
        );
    }

    private void handleCaptureSaved(ScanPage pending) {
        if (!capturedImageStorage.existsAndHasContent(pending.getCapturedFileName())) {
            handleCaptureError(pending);
            return;
        }
        if (sessionViewModel.captureSucceeded(pending.getId())) {
            openReviewIfReady();
            return;
        }
        ScanPage currentPending = sessionViewModel.getState().getPendingCapture();
        if (currentPending == null || !currentPending.getId().equals(pending.getId())) {
            capturedImageStorage.delete(pending.getCapturedFileName());
        }
    }

    private void handleCaptureError(ScanPage pending) {
        ScanPage removed = sessionViewModel.captureFailed(pending.getId());
        deleteIfAppOwned(removed);
        if (removed != null) {
            showToast(R.string.smart_scan_capture_error);
            return;
        }
        ScanPage currentPending = sessionViewModel.getState().getPendingCapture();
        if (currentPending == null || !currentPending.getId().equals(pending.getId())) {
            capturedImageStorage.delete(pending.getCapturedFileName());
        }
    }

    private void openGallery() {
        PickVisualMediaRequest request = new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build();
        galleryLauncher.launch(request);
    }

    private void handleGalleryResult(Uri uri) {
        if (uri == null) {
            return;
        }
        ScanPage page = sessionViewModel.selectGalleryImage(uri, System.currentTimeMillis());
        if (page != null) {
            openReviewIfReady();
        }
    }

    private void openReviewIfReady() {
        if (!isAdded()
                || !isResumed()
                || getParentFragmentManager().isStateSaved()
                || sessionViewModel.getState().getCurrentReviewPage() == null
                || navigationCallback == null) {
            return;
        }
        navigationCallback.onScanReviewRequested();
    }

    private void toggleTorch() {
        if (camera == null || !sessionViewModel.getCameraState().isFlashAvailable()) {
            showToast(R.string.smart_scan_flash_unavailable);
            return;
        }
        if (!sessionViewModel.toggleTorch()) {
            return;
        }
        boolean enabled = sessionViewModel.getCameraState().isTorchEnabled();
        ListenableFuture<Void> future = camera.getCameraControl().enableTorch(enabled);
        future.addListener(() -> {
            try {
                future.get();
            } catch (ExecutionException | InterruptedException | RuntimeException exception) {
                if (exception instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                if (sessionViewModel.getCameraState().isTorchEnabled() == enabled) {
                    sessionViewModel.toggleTorch();
                }
                showToast(R.string.smart_scan_flash_unavailable);
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    private void disableTorch() {
        if (camera != null && sessionViewModel.getCameraState().isTorchEnabled()) {
            camera.getCameraControl().enableTorch(false);
        }
        sessionViewModel.resetTorchOnPause();
    }

    private void registerLevelSensor() {
        if (sensorRegistered || accelerometer == null || !hasCameraPermission()) {
            if (accelerometer == null && levelIndicator != null) {
                levelIndicator.setVisibility(View.GONE);
            }
            return;
        }
        sensorRegistered = sensorManager.registerListener(
                this,
                accelerometer,
                SensorManager.SENSOR_DELAY_UI
        );
        if (!sensorRegistered && levelIndicator != null) {
            levelIndicator.setVisibility(View.GONE);
        }
    }

    private void unregisterLevelSensor() {
        if (sensorManager != null && sensorRegistered) {
            sensorManager.unregisterListener(this);
        }
        sensorRegistered = false;
        levelState = LevelState.UNAVAILABLE;
        if (levelIndicator != null) {
            levelIndicator.setVisibility(View.GONE);
        }
    }

    private void showOverflowMenu(View anchor) {
        PopupMenu popupMenu = new PopupMenu(requireContext(), anchor);
        Menu menu = popupMenu.getMenu();
        menu.add(Menu.NONE, MENU_LOCAL_PROCESSING, 0, R.string.smart_scan_local_hint)
                .setEnabled(false);
        if (resolvePermissionState() == PermissionState.PERMANENTLY_DENIED) {
            menu.add(Menu.NONE, MENU_SETTINGS, 1, R.string.smart_scan_open_settings);
        }
        popupMenu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == MENU_SETTINGS) {
                openAppSettings();
                return true;
            }
            return false;
        });
        popupMenu.show();
    }

    private void openAppSettings() {
        Intent intent = new Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", requireContext().getPackageName(), null)
        );
        startActivity(intent);
    }

    private void finishScan() {
        if (sessionViewModel.getState().getPageCount() == 0 || navigationCallback == null) {
            return;
        }
        navigationCallback.onScanFinished();
    }

    private void cancelScan() {
        if (navigationCallback != null) {
            navigationCallback.onScanCancelled();
        }
    }

    private void showCameraError(int messageResId) {
        if (cameraStatusText == null) {
            return;
        }
        cameraStatusText.setText(messageResId);
        cameraStatusText.setVisibility(View.VISIBLE);
        imageCapture = null;
        if (shutterButton != null) {
            shutterButton.setEnabled(false);
        }
    }

    private void deleteIfAppOwned(ScanPage page) {
        if (page != null && page.isAppOwned()) {
            capturedImageStorage.delete(page.getCapturedFileName());
        }
    }

    private void showToast(int messageResId) {
        if (isAdded()) {
            Toast.makeText(requireContext(), messageResId, Toast.LENGTH_SHORT).show();
        }
    }

    private enum PermissionState {
        NOT_REQUESTED,
        RATIONALE,
        DENIED,
        PERMANENTLY_DENIED,
        GRANTED
    }

    public interface NavigationCallback {
        void onScanReviewRequested();

        void onScanFinished();

        void onScanCancelled();
    }
}
