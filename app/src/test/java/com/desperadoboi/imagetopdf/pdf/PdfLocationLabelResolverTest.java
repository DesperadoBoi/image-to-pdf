package com.desperadoboi.imagetopdf.pdf;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class PdfLocationLabelResolverTest {
    @Test
    public void downloadsAuthorityMapsToDownloads() {
        PdfLocationLabelResolver.ResolvedLocation location = PdfLocationLabelResolver.resolve(
                PdfLocationLabelResolver.DOWNLOADS_AUTHORITY,
                "42",
                null
        );

        assertEquals(PdfLocationLabelResolver.LocationKind.DOWNLOADS, location.getKind());
    }

    @Test
    public void externalDownloadsDocumentIdMapsToDownloads() {
        PdfLocationLabelResolver.ResolvedLocation location = PdfLocationLabelResolver.resolve(
                PdfLocationLabelResolver.EXTERNAL_STORAGE_AUTHORITY,
                "primary:Download/ImageToPDF/result.pdf",
                null
        );

        assertEquals(PdfLocationLabelResolver.LocationKind.DOWNLOADS, location.getKind());
    }

    @Test
    public void externalDocumentsDocumentIdMapsToDocuments() {
        PdfLocationLabelResolver.ResolvedLocation location = PdfLocationLabelResolver.resolve(
                PdfLocationLabelResolver.EXTERNAL_STORAGE_AUTHORITY,
                "primary:Documents/result.pdf",
                null
        );

        assertEquals(PdfLocationLabelResolver.LocationKind.DOCUMENTS, location.getKind());
    }

    @Test
    public void documentsAuthorityMapsToDocuments() {
        PdfLocationLabelResolver.ResolvedLocation location = PdfLocationLabelResolver.resolve(
                "com.android.documentsui.documents",
                "document:result.pdf",
                null
        );

        assertEquals(PdfLocationLabelResolver.LocationKind.DOCUMENTS, location.getKind());
    }

    @Test
    public void unknownProviderMapsToSelectedFolder() {
        PdfLocationLabelResolver.ResolvedLocation location = PdfLocationLabelResolver.resolve(
                "unknown.provider.documents",
                "secret-id",
                null
        );

        assertEquals(
                PdfLocationLabelResolver.LocationKind.SELECTED_FOLDER,
                location.getKind()
        );
    }

    @Test
    public void rawAuthorityIsNeverReturnedAsProviderName() {
        String authority = "com.example.storage.documents";
        PdfLocationLabelResolver.ResolvedLocation location = PdfLocationLabelResolver.resolve(
                authority,
                "result.pdf",
                authority
        );

        assertNotEquals(authority, location.getProviderDisplayName());
        assertEquals(
                PdfLocationLabelResolver.LocationKind.SELECTED_FOLDER,
                location.getKind()
        );
    }

    @Test
    public void humanProviderNameCanBeShown() {
        PdfLocationLabelResolver.ResolvedLocation location = PdfLocationLabelResolver.resolve(
                "com.google.android.apps.docs.storage",
                "result.pdf",
                "Google Drive"
        );

        assertEquals(PdfLocationLabelResolver.LocationKind.PROVIDER, location.getKind());
        assertEquals("Google Drive", location.getProviderDisplayName());
    }
}
