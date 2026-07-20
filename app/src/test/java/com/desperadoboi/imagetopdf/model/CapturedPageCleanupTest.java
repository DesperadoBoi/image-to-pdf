package com.desperadoboi.imagetopdf.model;

import android.net.FakeUri;
import android.net.Uri;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class CapturedPageCleanupTest {
    @Test
    public void collectsOnlyCameraFileNames() {
        Uri galleryUri = FakeUri.create("content://test/gallery");
        Uri cameraUri = FakeUri.create("content://test/camera");

        assertEquals(
                Arrays.asList("capture_camera.jpg"),
                CapturedPageCleanup.collectCapturedFileNames(Arrays.asList(
                        new PageItem(galleryUri),
                        PageItem.camera(cameraUri, "capture_camera.jpg")
                ))
        );
    }

    @Test
    public void galleryUriIsNotMarkedForDeletion() {
        Uri galleryUri = FakeUri.create("content://test/gallery");

        assertEquals(
                0,
                CapturedPageCleanup.collectCapturedFileNames(Arrays.asList(
                        new PageItem(galleryUri)
                )).size()
        );
    }
}
