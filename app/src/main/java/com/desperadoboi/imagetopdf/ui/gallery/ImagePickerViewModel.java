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
    private boolean loading;

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

    public void setGalleryData(List<MediaImage> images, List<MediaAlbum> albums) {
        this.images = Collections.unmodifiableList(new ArrayList<>(images));
        this.albums = Collections.unmodifiableList(new ArrayList<>(albums));
        loading = false;
    }

    public GalleryAccessState getAccessState() {
        return accessState;
    }

    public void setAccessState(GalleryAccessState accessState) {
        this.accessState = accessState;
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

    public boolean isLoading() {
        return loading;
    }

    public void setLoading(boolean loading) {
        this.loading = loading;
    }
}
