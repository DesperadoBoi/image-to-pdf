package com.desperadoboi.imagetopdf.ui.gallery;

import android.net.FakeUri;
import android.net.Uri;

import com.desperadoboi.imagetopdf.model.ImageImportSource;
import com.desperadoboi.imagetopdf.model.GalleryAccessState;
import com.desperadoboi.imagetopdf.model.MediaImage;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ImagePickerViewModelTest {
    @Test
    public void initialStateIsLoading() {
        ImagePickerViewModel viewModel = new ImagePickerViewModel();

        assertEquals(GalleryUiState.LOADING, viewModel.getGalleryUiState());
    }

    @Test
    public void successfulLoadWithImagesBecomesContent() {
        ImagePickerViewModel viewModel = new ImagePickerViewModel();
        long operationId = viewModel.beginGalleryLoad();

        viewModel.completeGalleryLoad(
                operationId,
                Collections.singletonList(image("content://test/current", 1L)),
                Collections.emptyList()
        );

        assertEquals(GalleryUiState.CONTENT, viewModel.getGalleryUiState());
    }

    @Test
    public void emptyIsPublishedOnlyAfterSuccessfulCompletedLoad() {
        ImagePickerViewModel viewModel = new ImagePickerViewModel();
        long operationId = viewModel.beginGalleryLoad();

        assertEquals(GalleryUiState.LOADING, viewModel.getGalleryUiState());

        viewModel.completeGalleryLoad(
                operationId,
                Collections.emptyList(),
                Collections.emptyList()
        );

        assertEquals(GalleryUiState.EMPTY, viewModel.getGalleryUiState());
    }

    @Test
    public void missingPermissionHasDedicatedState() {
        ImagePickerViewModel viewModel = new ImagePickerViewModel();

        viewModel.setAccessState(GalleryAccessState.DENIED);

        assertEquals(GalleryUiState.PERMISSION_REQUIRED, viewModel.getGalleryUiState());
    }

    @Test
    public void failedInitialLoadHasDedicatedErrorState() {
        ImagePickerViewModel viewModel = new ImagePickerViewModel();
        long operationId = viewModel.beginGalleryLoad();
        RuntimeException failure = new RuntimeException("query failed");

        viewModel.failGalleryLoad(operationId, failure);

        assertEquals(GalleryUiState.ERROR, viewModel.getGalleryUiState());
        assertSame(failure, viewModel.getGalleryLoadError());
    }

    @Test
    public void leavingGalleryDisplayModeDoesNotPublishEmpty() {
        ImagePickerViewModel viewModel = new ImagePickerViewModel();
        long operationId = viewModel.beginGalleryLoad();
        viewModel.completeGalleryLoad(
                operationId,
                Collections.singletonList(image("content://test/current", 1L)),
                Collections.emptyList()
        );

        viewModel.showAlbum("camera", "Camera");
        viewModel.showAlbums();

        assertEquals(GalleryUiState.CONTENT, viewModel.getGalleryUiState());
    }

    @Test
    public void staleOlderQueryCannotReplaceNewContent() {
        ImagePickerViewModel viewModel = new ImagePickerViewModel();
        long olderOperation = viewModel.beginGalleryLoad();
        long newerOperation = viewModel.beginGalleryLoad();
        MediaImage newerImage = image("content://test/newer", 2L);

        assertTrue(viewModel.completeGalleryLoad(
                newerOperation,
                Collections.singletonList(newerImage),
                Collections.emptyList()
        ));
        assertFalse(viewModel.completeGalleryLoad(
                olderOperation,
                Collections.emptyList(),
                Collections.emptyList()
        ));

        assertEquals(GalleryUiState.CONTENT, viewModel.getGalleryUiState());
        assertSame(newerImage, viewModel.getImages().get(0));
    }

    @Test
    public void selectionSurvivesAlbumAndDisplayModeSwitches() {
        Uri first = FakeUri.create("content://test/first");
        Uri second = FakeUri.create("content://test/second");
        ImagePickerViewModel viewModel = new ImagePickerViewModel();
        viewModel.addExternalUris(Arrays.asList(first, second), ImageImportSource.GALLERY);

        viewModel.showAlbum("camera", "Camera");
        viewModel.showAlbums();
        viewModel.showAlbum("screenshots", "Screenshots");

        assertEquals(2, viewModel.getSelection().snapshot().size());
        assertSame(first, viewModel.getSelection().snapshot().get(0));
        assertSame(second, viewModel.getSelection().snapshot().get(1));
        assertEquals(2, viewModel.getImportEntriesSnapshot().size());
    }

    private static MediaImage image(String uri, long id) {
        return new MediaImage(
                FakeUri.create(uri),
                id,
                "image.jpg",
                1L,
                1L,
                1L,
                100,
                100,
                "camera",
                "Camera"
        );
    }
}
