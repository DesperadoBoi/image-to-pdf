package com.desperadoboi.imagetopdf.ui.gallery;

import android.net.FakeUri;
import android.net.Uri;

import com.desperadoboi.imagetopdf.model.ImageImportSource;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class ImagePickerViewModelTest {
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
}
