package com.desperadoboi.imagetopdf.model;

import android.net.FakeUri;
import android.net.Uri;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

public class PdfExportRequestTest {
    @Test
    public void validNameReceivesSinglePdfExtension() {
        PdfExportRequest request = request("Document");

        assertEquals("Document.pdf", request.getFileName());
    }

    @Test
    public void emptyNameIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> request("   "));
        assertThrows(IllegalArgumentException.class, () -> request(".pdf"));
    }

    @Test
    public void doubleExtensionIsNormalized() {
        assertEquals("Document.pdf", request("Document.pdf.pdf").getFileName());
        assertEquals("Document.pdf", request("Document.PDF").getFileName());
    }

    @Test
    public void invalidFileNameCharactersAreNormalized() {
        assertEquals("My_report_______.pdf", request("My/report:*?\"<>|").getFileName());
    }

    @Test
    public void allOptionsAndOutputUriAreRetained() {
        Uri output = FakeUri.create("content://test/result.pdf");
        PdfExportRequest request = new PdfExportRequest(
                "Report",
                PdfQualityProfile.HIGH,
                PageSizeMode.IMAGE,
                PdfOrientationMode.LANDSCAPE,
                MarginPreset.SMALL,
                output
        );

        assertEquals(PdfQualityProfile.HIGH, request.getQuality());
        assertEquals(PageSizeMode.IMAGE, request.getPageSize());
        assertEquals(PdfOrientationMode.LANDSCAPE, request.getOrientation());
        assertEquals(MarginPreset.SMALL, request.getMargins());
        assertSame(output, request.getOutputUri());
        assertEquals(ImagePlacementMode.FIT, request.toPdfOptions().getImagePlacementMode());
    }

    @Test
    public void withOutputCreatesNewImmutableRequest() {
        PdfExportRequest original = request("Document");
        Uri output = FakeUri.create("content://test/result.pdf");

        PdfExportRequest changed = original.withOutputUri(output);

        assertNotSame(original, changed);
        assertSame(output, changed.getOutputUri());
        assertEquals(original.getFileName(), changed.getFileName());
    }

    private PdfExportRequest request(String fileName) {
        return new PdfExportRequest(
                fileName,
                PdfQualityProfile.BALANCED,
                PageSizeMode.A4,
                PdfOrientationMode.AUTO,
                MarginPreset.STANDARD,
                null
        );
    }
}
