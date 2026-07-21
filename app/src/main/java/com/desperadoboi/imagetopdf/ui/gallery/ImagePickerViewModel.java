package com.desperadoboi.imagetopdf.ui.gallery;

import android.net.Uri;

import androidx.lifecycle.ViewModel;

import com.desperadoboi.imagetopdf.model.GalleryAccessState;
import com.desperadoboi.imagetopdf.model.ImageImportEntry;
import com.desperadoboi.imagetopdf.model.ImageImportSource;
import com.desperadoboi.imagetopdf.model.ImageSelection;
import com.desperadoboi.imagetopdf.model.MediaAlbum;
import com.desperadoboi.imagetopdf.model.MediaImage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ImagePickerViewModel extends ViewModel {
    public enum DisplayMode {
        ALBUMS,
        GRID
    }

    private final ImageSelection selection = new ImageSelection();
    private final Map<String, ImageImportEntry> entriesByUri = new HashMap<>();
    private List<MediaImage> images = Collections.emptyList();
    private List<MediaAlbum> albums = Collections.emptyList();
    private GalleryAccessState accessState = GalleryAccessState.NOT_REQUESTED;
    private DisplayMode displayMode = DisplayMode.ALBUMS;
    private String currentBucketId;
    private String currentAlbumName;
    private boolean permissionRequested;
    private boolean permanentlyDenied;
    private GalleryUiState galleryUiState = GalleryUiState.LOADING;
    private RuntimeException galleryLoadError;
    private long nextGalleryLoadOperationId = 1L;
    private long activeGalleryLoadOperationId;

    public ImageSelection getSelection() {
        return selection;
    }

    public boolean toggleMediaImage(MediaImage image) {
        Uri uri = image.getUri();
        if (selection.isSelected(uri)) {
            selection.deselect(uri);
            entriesByUri.remove(uri.toString());
            return false;
        }
        selection.select(uri);
        entriesByUri.put(
                uri.toString(),
                ImageImportEntry.external(uri, ImageImportSource.IN_APP_GALLERY)
        );
        return true;
    }

    public void addExternalUris(List<Uri> uris, ImageImportSource source) {
        for (Uri uri : uris) {
            if (uri != null && selection.select(uri)) {
                entriesByUri.put(uri.toString(), ImageImportEntry.external(uri, source));
            }
        }
    }

    public void addCamera(Uri uri, String capturedFileName) {
        if (selection.select(uri)) {
            entriesByUri.put(uri.toString(), ImageImportEntry.camera(uri, capturedFileName));
        }
    }

    public ImageImportEntry remove(Uri uri) {
        selection.deselect(uri);
        return entriesByUri.remove(uri.toString());
    }

    public List<ImageImportEntry> getImportEntriesSnapshot() {
        ArrayList<ImageImportEntry> entries = new ArrayList<>(selection.size());
        for (Uri uri : selection.snapshot()) {
            ImageImportEntry entry = entriesByUri.get(uri.toString());
            if (entry != null) {
                entries.add(entry);
            }
        }
        return Collections.unmodifiableList(entries);
    }

    public List<String> getUnimportedCameraFileNames() {
        ArrayList<String> names = new ArrayList<>();
        for (ImageImportEntry entry : getImportEntriesSnapshot()) {
            if (entry.getSource() == ImageImportSource.CAMERA) {
                names.add(entry.getCapturedFileName());
            }
        }
        return names;
    }

    public void clearSelection() {
        selection.clear();
        entriesByUri.clear();
    }

    public List<MediaImage> getImages() {
        return images;
    }

    public List<MediaAlbum> getAlbums() {
        return albums;
    }

    public long beginGalleryLoad() {
        long operationId = nextGalleryLoadOperationId++;
        activeGalleryLoadOperationId = operationId;
        galleryLoadError = null;
        if (images.isEmpty() || galleryUiState != GalleryUiState.CONTENT) {
            galleryUiState = GalleryUiState.LOADING;
        }
        return operationId;
    }

    public boolean completeGalleryLoad(
            long operationId,
            List<MediaImage> images,
            List<MediaAlbum> albums
    ) {
        if (operationId != activeGalleryLoadOperationId) {
            return false;
        }
        this.images = Collections.unmodifiableList(new ArrayList<>(images));
        this.albums = Collections.unmodifiableList(new ArrayList<>(albums));
        activeGalleryLoadOperationId = 0L;
        galleryLoadError = null;
        galleryUiState = this.images.isEmpty()
                ? GalleryUiState.EMPTY
                : GalleryUiState.CONTENT;
        return true;
    }

    public boolean failGalleryLoad(long operationId, RuntimeException exception) {
        if (operationId != activeGalleryLoadOperationId) {
            return false;
        }
        activeGalleryLoadOperationId = 0L;
        galleryLoadError = exception;
        if (images.isEmpty()) {
            galleryUiState = GalleryUiState.ERROR;
        } else {
            galleryUiState = GalleryUiState.CONTENT;
        }
        return true;
    }

    public GalleryAccessState getAccessState() {
        return accessState;
    }

    public void setAccessState(GalleryAccessState accessState) {
        this.accessState = accessState;
        if (!accessState.canReadMediaStore()) {
            activeGalleryLoadOperationId = 0L;
            galleryLoadError = null;
            galleryUiState = GalleryUiState.PERMISSION_REQUIRED;
        }
    }

    public DisplayMode getDisplayMode() {
        return displayMode;
    }

    public void showAlbums() {
        displayMode = DisplayMode.ALBUMS;
    }

    public void showAlbum(String bucketId, String albumName) {
        currentBucketId = bucketId;
        currentAlbumName = albumName;
        displayMode = DisplayMode.GRID;
    }

    public String getCurrentBucketId() {
        return currentBucketId;
    }

    public String getCurrentAlbumName() {
        return currentAlbumName;
    }

    public boolean isPermissionRequested() {
        return permissionRequested;
    }

    public void setPermissionRequested(boolean permissionRequested) {
        this.permissionRequested = permissionRequested;
    }

    public boolean isPermanentlyDenied() {
        return permanentlyDenied;
    }

    public void setPermanentlyDenied(boolean permanentlyDenied) {
        this.permanentlyDenied = permanentlyDenied;
    }

    public GalleryUiState getGalleryUiState() {
        return galleryUiState;
    }

    public RuntimeException getGalleryLoadError() {
        return galleryLoadError;
    }

    public boolean isGalleryLoadInProgress() {
        return activeGalleryLoadOperationId != 0L;
    }

    public boolean hasCompletedGalleryLoad() {
        return galleryUiState == GalleryUiState.CONTENT
                || galleryUiState == GalleryUiState.EMPTY;
    }
}
