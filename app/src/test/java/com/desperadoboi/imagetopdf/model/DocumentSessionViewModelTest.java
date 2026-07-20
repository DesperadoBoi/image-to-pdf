package com.desperadoboi.imagetopdf.model;

import android.net.FakeUri;
import android.net.Uri;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class DocumentSessionViewModelTest {
    @Test
    public void moveKeepsStableIdUriAndRotation() {
        Uri firstUri = FakeUri.create("content://test/first");
        Uri secondUri = FakeUri.create("content://test/second");
        DocumentSessionViewModel viewModel = new DocumentSessionViewModel();
        viewModel.replacePages(Arrays.asList(firstUri, secondUri));
        viewModel.rotatePage(0);
        long firstPageId = viewModel.getPages().get(0).getId();

        viewModel.movePage(0, 1);

        PageItem movedPage = viewModel.getPages().get(1);
        assertEquals(firstPageId, movedPage.getId());
        assertSame(firstUri, movedPage.getImageUri());
        assertEquals(90, movedPage.getManualRotationDegrees());
    }

    @Test
    public void rotateChangesOnlyTargetPageRotation() {
        Uri firstUri = FakeUri.create("content://test/first");
        Uri secondUri = FakeUri.create("content://test/second");
        DocumentSessionViewModel viewModel = new DocumentSessionViewModel();
        viewModel.replacePages(Arrays.asList(firstUri, secondUri));
        PageItem secondPageBeforeRotation = viewModel.getPages().get(1);

        viewModel.rotatePage(0);

        assertEquals(90, viewModel.getPages().get(0).getManualRotationDegrees());
        assertSame(secondPageBeforeRotation, viewModel.getPages().get(1));
        assertEquals(0, viewModel.getPages().get(1).getManualRotationDegrees());
    }

    @Test
    public void clearForNewDocumentRemovesPagesAndResultWithoutTouchingSavedUri() {
        Uri imageUri = FakeUri.create("content://test/image");
        Uri pdfUri = FakeUri.create("content://test/result.pdf");
        DocumentSessionViewModel viewModel = new DocumentSessionViewModel();
        viewModel.replacePages(Arrays.asList(imageUri));
        PdfResult result = new PdfResult(pdfUri, "result.pdf", 1024L, 1);
        viewModel.setLastPdfResult(result);

        viewModel.clearForNewDocument();

        assertTrue(viewModel.getPages().isEmpty());
        assertNull(viewModel.getLastPdfResult());
        assertSame(pdfUri, result.getUri());
    }
}
