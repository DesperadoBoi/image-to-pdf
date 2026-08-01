package com.desperadoboi.imagetopdf.ui.idcard;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class IdCardCacheStorageContractTest {
    @Test public void generatedCameraNameIsAccepted() {
        assertTrue(IdCardCacheStorage.isGeneratedFileName(
                "idcard_550e8400-e29b-41d4-a716-446655440000.jpg"
        ));
    }

    @Test public void generatedPickerNameIsAccepted() {
        assertTrue(IdCardCacheStorage.isGeneratedFileName(
                "idcard_550e8400-e29b-41d4-a716-446655440000.img"
        ));
    }

    @Test public void traversalAndAbsolutePathsAreRejected() {
        assertFalse(IdCardCacheStorage.isGeneratedFileName("../idcard_value.jpg"));
        assertFalse(IdCardCacheStorage.isGeneratedFileName("C:\\idcard_value.jpg"));
        assertFalse(IdCardCacheStorage.isGeneratedFileName("id_card/idcard_value.jpg"));
    }

    @Test public void providerFilenameAndUnexpectedExtensionAreRejected() {
        assertFalse(IdCardCacheStorage.isGeneratedFileName("John_Doe_ID.jpg"));
        assertFalse(IdCardCacheStorage.isGeneratedFileName("idcard_value.pdf"));
        assertFalse(IdCardCacheStorage.isGeneratedFileName("idcard_value.png"));
    }
}
