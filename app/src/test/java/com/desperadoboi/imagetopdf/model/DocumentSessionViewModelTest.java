package com.desperadoboi.imagetopdf.model;

import android.net.FakeUri;
import android.net.Uri;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
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
    public void idBasedEditsChangeOnlyTargetAndKeepIdentity() {
        Uri firstUri = FakeUri.create("content://test/first");
        Uri secondUri = FakeUri.create("content://test/second");
        DocumentSessionViewModel viewModel = new DocumentSessionViewModel();
        viewModel.replacePages(Arrays.asList(firstUri, secondUri));
        PageItem firstBefore = viewModel.getPages().get(0);
        PageItem secondBefore = viewModel.getPages().get(1);
        CropRect crop = new CropRect(0.1f, 0.2f, 0.8f, 0.9f);

        PageItem updated = viewModel.updatePageCrop(firstBefore.getId(), crop);
        updated = viewModel.rotatePageLeft(firstBefore.getId());

        assertEquals(firstBefore.getId(), updated.getId());
        assertSame(firstUri, updated.getImageUri());
        assertEquals(PageSource.GALLERY, updated.getSource());
        assertEquals(270, updated.getManualRotationDegrees());
        assertEquals(crop.rotateCounterClockwise(), updated.getEditSpec().getCropRect());
        assertSame(secondBefore, viewModel.getPages().get(1));
    }

    @Test
    public void perspectiveUpdateResetsCropAndSnapshotKeepsEditSpec() {
        Uri uri = FakeUri.create("content://test/page");
        DocumentSessionViewModel viewModel = new DocumentSessionViewModel();
        viewModel.replacePages(Arrays.asList(uri));
        long pageId = viewModel.getPages().get(0).getId();
        viewModel.updatePageCrop(pageId, new CropRect(0.1f, 0.1f, 0.9f, 0.9f));
        PerspectiveQuad quad = new PerspectiveQuad(
                new NormalizedPoint(0.1f, 0.1f),
                new NormalizedPoint(0.9f, 0.1f),
                new NormalizedPoint(0.8f, 0.9f),
                new NormalizedPoint(0.2f, 0.9f)
        );

        viewModel.updatePagePerspective(pageId, quad);
        PageItem snapshot = viewModel.getPagesSnapshot().get(0);

        assertSame(CropRect.FULL, snapshot.getEditSpec().getCropRect());
        assertSame(quad, snapshot.getEditSpec().getPerspectiveQuad());
        assertEquals(pageId, snapshot.getId());
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
    public void successfulGenerationQueuesResultNavigationOnlyOnce() {
        Uri imageUri = FakeUri.create("content://test/image");
        Uri pdfUri = FakeUri.create("content://test/result.pdf");
        DocumentSessionViewModel viewModel = new DocumentSessionViewModel();
        viewModel.replacePages(Arrays.asList(imageUri));
        DocumentSessionViewModel.GenerationOperation operation = viewModel.startGeneration(1);

        viewModel.completeGenerationSuccess(
                operation.getOperationId(),
                pdfUri,
                "result.pdf",
                1
        );

        assertTrue(viewModel.consumePendingPdfResultNavigation(operation.getOperationId()));
        assertFalse(viewModel.consumePendingPdfResultNavigation(operation.getOperationId()));
        assertSame(pdfUri, viewModel.getLastPdfResult().getUri());
        assertFalse(viewModel.getLastPdfResult().hasKnownSize());
        assertTrue(viewModel.getLastPdfResult().getTimestamp() > 0L);
    }

    @Test
    public void pageEditsKeepLastSuccessfulPdfResult() {
        Uri firstImageUri = FakeUri.create("content://test/first");
        Uri secondImageUri = FakeUri.create("content://test/second");
        Uri pdfUri = FakeUri.create("content://test/result.pdf");
        DocumentSessionViewModel viewModel = new DocumentSessionViewModel();
        viewModel.replacePages(Arrays.asList(firstImageUri, secondImageUri));
        PdfResult previousResult = new PdfResult(pdfUri, "result.pdf", 1024L, 2);
        viewModel.setLastPdfResult(previousResult);

        viewModel.rotatePage(0);
        viewModel.movePage(0, 1);
        viewModel.deletePage(1);

        assertSame(previousResult, viewModel.getLastPdfResult());
        assertEquals(1, viewModel.getPageCount());
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

    @Test
    public void newDocumentImportReplacesPagesAndPreservesUriOrder() {
        Uri oldUri = FakeUri.create("content://test/old");
        Uri firstUri = FakeUri.create("content://test/first");
        Uri secondUri = FakeUri.create("content://test/second");
        DocumentSessionViewModel viewModel = new DocumentSessionViewModel();
        viewModel.replacePages(Arrays.asList(oldUri));

        ImageImportResult result = viewModel.importImages(
                new ImageImportRequest(
                        ImageImportSource.FILES,
                        ImageImportMode.NEW_DOCUMENT
                ),
                Arrays.asList(firstUri, secondUri)
        );

        assertTrue(result.hasChanges());
        assertEquals(0, result.getFirstInsertedPosition());
        assertEquals(2, result.getInsertedCount());
        assertSame(firstUri, viewModel.getPages().get(0).getImageUri());
        assertSame(secondUri, viewModel.getPages().get(1).getImageUri());
        assertEquals(PageSource.FILES, viewModel.getPages().get(0).getSource());
    }

    @Test
    public void appendImportAddsPagesAtEndAndKeepsPreviousResult() {
        Uri oldUri = FakeUri.create("content://test/old");
        Uri firstUri = FakeUri.create("content://test/first");
        Uri secondUri = FakeUri.create("content://test/second");
        Uri pdfUri = FakeUri.create("content://test/result.pdf");
        DocumentSessionViewModel viewModel = new DocumentSessionViewModel();
        viewModel.replacePages(Arrays.asList(oldUri));
        PdfResult previousResult = new PdfResult(pdfUri, "result.pdf", 1024L, 1);
        viewModel.setLastPdfResult(previousResult);

        ImageImportResult result = viewModel.importImages(
                new ImageImportRequest(
                        ImageImportSource.GALLERY,
                        ImageImportMode.APPEND_TO_DOCUMENT
                ),
                Arrays.asList(firstUri, secondUri)
        );

        assertEquals(1, result.getFirstInsertedPosition());
        assertEquals(3, viewModel.getPageCount());
        assertSame(oldUri, viewModel.getPages().get(0).getImageUri());
        assertSame(firstUri, viewModel.getPages().get(1).getImageUri());
        assertSame(secondUri, viewModel.getPages().get(2).getImageUri());
        assertSame(previousResult, viewModel.getLastPdfResult());
    }

    @Test
    public void emptyImportIsNoOpAndKeepsPreviousResult() {
        Uri imageUri = FakeUri.create("content://test/image");
        Uri pdfUri = FakeUri.create("content://test/result.pdf");
        DocumentSessionViewModel viewModel = new DocumentSessionViewModel();
        viewModel.replacePages(Arrays.asList(imageUri));
        PdfResult previousResult = new PdfResult(pdfUri, "result.pdf", 1024L, 1);
        viewModel.setLastPdfResult(previousResult);

        ImageImportResult result = viewModel.importImages(
                new ImageImportRequest(
                        ImageImportSource.FILES,
                        ImageImportMode.NEW_DOCUMENT
                ),
                java.util.Collections.emptyList()
        );

        assertFalse(result.hasChanges());
        assertEquals(1, viewModel.getPageCount());
        assertSame(previousResult, viewModel.getLastPdfResult());
    }

    @Test
    public void duplicateUrisReceiveDifferentStableIds() {
        Uri repeatedUri = FakeUri.create("content://test/repeated");
        DocumentSessionViewModel viewModel = new DocumentSessionViewModel();

        viewModel.importImages(
                new ImageImportRequest(
                        ImageImportSource.GALLERY,
                        ImageImportMode.NEW_DOCUMENT
                ),
                Arrays.asList(repeatedUri, repeatedUri)
        );

        assertNotEquals(
                viewModel.getPages().get(0).getId(),
                viewModel.getPages().get(1).getId()
        );
    }

    @Test
    public void reorderKeepsFilesSource() {
        Uri galleryUri = FakeUri.create("content://test/gallery");
        Uri filesUri = FakeUri.create("content://test/files");
        DocumentSessionViewModel viewModel = new DocumentSessionViewModel();
        viewModel.replacePages(Arrays.asList(galleryUri));
        viewModel.importImages(
                new ImageImportRequest(
                        ImageImportSource.FILES,
                        ImageImportMode.APPEND_TO_DOCUMENT
                ),
                Arrays.asList(filesUri)
        );

        viewModel.movePage(1, 0);

        assertEquals(PageSource.FILES, viewModel.getPages().get(0).getSource());
        assertSame(filesUri, viewModel.getPages().get(0).getImageUri());
    }

    @Test
    public void mixedPickerImportKeepsSelectionOrderAndSources() {
        Uri mediaUri = FakeUri.create("content://test/media");
        Uri cameraUri = FakeUri.create("content://test/camera");
        Uri filesUri = FakeUri.create("content://test/files");
        DocumentSessionViewModel viewModel = new DocumentSessionViewModel();

        ImageImportResult result = viewModel.importImages(
                ImageImportMode.NEW_DOCUMENT,
                Arrays.asList(
                        ImageImportEntry.external(mediaUri, ImageImportSource.IN_APP_GALLERY),
                        ImageImportEntry.camera(cameraUri, "capture_picker.jpg"),
                        ImageImportEntry.external(filesUri, ImageImportSource.FILES)
                )
        );

        assertEquals(3, result.getInsertedCount());
        assertSame(mediaUri, viewModel.getPages().get(0).getImageUri());
        assertEquals(PageSource.GALLERY, viewModel.getPages().get(0).getSource());
        assertSame(cameraUri, viewModel.getPages().get(1).getImageUri());
        assertEquals(PageSource.CAMERA, viewModel.getPages().get(1).getSource());
        assertSame(filesUri, viewModel.getPages().get(2).getImageUri());
        assertEquals(PageSource.FILES, viewModel.getPages().get(2).getSource());
    }
}
