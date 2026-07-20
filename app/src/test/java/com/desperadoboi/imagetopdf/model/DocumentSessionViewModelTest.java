package com.desperadoboi.imagetopdf.model;

import android.net.FakeUri;
import android.net.Uri;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
    public void moveKeepsSource() {
        Uri firstUri = FakeUri.create("content://test/first");
        Uri secondUri = FakeUri.create("content://test/second");
        DocumentSessionViewModel viewModel = new DocumentSessionViewModel();
        viewModel.appendCameraPage(firstUri, "capture_first.jpg");
        viewModel.appendPages(Arrays.asList(secondUri));

        viewModel.movePage(0, 1);

        PageItem movedPage = viewModel.getPages().get(1);
        assertEquals(PageSource.CAMERA, movedPage.getSource());
        assertEquals("capture_first.jpg", movedPage.getCapturedFileName());
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

    @Test
    public void clearForNewDocumentReturnsOnlyCameraFileNames() {
        Uri galleryUri = FakeUri.create("content://test/gallery");
        Uri cameraUri = FakeUri.create("content://test/camera");
        DocumentSessionViewModel viewModel = new DocumentSessionViewModel();
        viewModel.replacePages(Arrays.asList(galleryUri));
        viewModel.appendCameraPage(cameraUri, "capture_camera.jpg");

        assertEquals(
                Arrays.asList("capture_camera.jpg"),
                viewModel.clearForNewDocument()
        );
    }

    @Test
    public void snapshotKeepsUriRotationAndSourceForPdfGeneration() {
        Uri galleryUri = FakeUri.create("content://test/gallery");
        Uri cameraUri = FakeUri.create("content://test/camera");
        DocumentSessionViewModel viewModel = new DocumentSessionViewModel();
        viewModel.replacePages(Arrays.asList(galleryUri));
        viewModel.appendCameraPage(cameraUri, "capture_camera.jpg");
        viewModel.rotatePage(0);
        viewModel.rotatePage(1);

        List<PageItem> snapshot = viewModel.getPagesSnapshot();

        assertSame(galleryUri, snapshot.get(0).getImageUri());
        assertEquals(90, snapshot.get(0).getManualRotationDegrees());
        assertEquals(PageSource.GALLERY, snapshot.get(0).getSource());
        assertSame(cameraUri, snapshot.get(1).getImageUri());
        assertEquals(90, snapshot.get(1).getManualRotationDegrees());
        assertEquals(PageSource.CAMERA, snapshot.get(1).getSource());
    }

    @Test
    public void cancelledGenerationKeepsPreviousSuccessfulResult() {
        Uri imageUri = FakeUri.create("content://test/image");
        Uri pdfUri = FakeUri.create("content://test/result.pdf");
        DocumentSessionViewModel viewModel = new DocumentSessionViewModel();
        viewModel.replacePages(Arrays.asList(imageUri));
        PdfResult previousResult = new PdfResult(pdfUri, "result.pdf", 1024L, 1);
        viewModel.setLastPdfResult(previousResult);
        DocumentSessionViewModel.GenerationOperation operation = viewModel.startGeneration(1);

        viewModel.requestCancelGeneration();
        viewModel.completeGenerationCancelled(operation.getOperationId());

        assertSame(previousResult, viewModel.getLastPdfResult());
        assertTrue(viewModel.getPdfGenerationState().isCancelled());
        assertFalse(viewModel.getPdfGenerationState().isSucceeded());
    }

    @Test
    public void oldOperationCallbackDoesNotChangeNewGenerationState() {
        Uri imageUri = FakeUri.create("content://test/image");
        DocumentSessionViewModel viewModel = new DocumentSessionViewModel();
        viewModel.replacePages(Arrays.asList(imageUri));
        DocumentSessionViewModel.GenerationOperation firstOperation = viewModel.startGeneration(1);
        viewModel.completeGenerationCancelled(firstOperation.getOperationId());
        DocumentSessionViewModel.GenerationOperation secondOperation = viewModel.startGeneration(1);

        viewModel.updateGenerationProgress(firstOperation.getOperationId(), 1, 1);

        PdfGenerationState state = viewModel.getPdfGenerationState();
        assertTrue(state.isRunning());
        assertEquals(secondOperation.getOperationId(), state.getOperationId());
        assertEquals(0, state.getCompletedPages());
    }

    @Test
    public void repeatedCancelRequestForOneOperationIsSafe() {
        Uri imageUri = FakeUri.create("content://test/image");
        DocumentSessionViewModel viewModel = new DocumentSessionViewModel();
        viewModel.replacePages(Arrays.asList(imageUri));
        viewModel.startGeneration(1);

        viewModel.requestCancelGeneration();
        viewModel.requestCancelGeneration();

        PdfGenerationState state = viewModel.getPdfGenerationState();
        assertTrue(state.isRunning());
        assertTrue(state.isCancellationRequested());
    }
}
