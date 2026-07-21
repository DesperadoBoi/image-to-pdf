package com.desperadoboi.imagetopdf.image;

import android.net.FakeUri;

import com.desperadoboi.imagetopdf.model.MediaAlbum;
import com.desperadoboi.imagetopdf.model.MediaImage;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class MediaAlbumAggregatorTest {
    @Test
    public void groupsBucketsAndCountsUniqueImages() {
        MediaImage cameraOld = image(1L, 10L, "camera", "Camera");
        MediaImage cameraNew = image(2L, 20L, "camera", "Camera");
        MediaImage screenshot = image(3L, 15L, "shots", "Screenshots");

        List<MediaAlbum> albums = MediaAlbumAggregator.aggregate(
                Arrays.asList(cameraOld, screenshot, cameraNew),
                "Other"
        );

        assertEquals(2, albums.size());
        assertEquals("camera", albums.get(0).getBucketId());
        assertEquals(2, albums.get(0).getImageCount());
        assertEquals("shots", albums.get(1).getBucketId());
    }

    @Test
    public void newestImageBecomesCoverEvenWhenInputIsUnsorted() {
        MediaImage oldImage = image(1L, 10L, "camera", "Camera");
        MediaImage newImage = image(2L, 50L, "camera", "Camera");

        MediaAlbum album = MediaAlbumAggregator.aggregate(
                Arrays.asList(oldImage, newImage),
                "Other"
        ).get(0);

        assertSame(newImage.getUri(), album.getCoverUri());
    }

    @Test
    public void missingBucketsUseFallbackAndAreGrouped() {
        MediaImage first = image(1L, 10L, "", "");
        MediaImage second = image(2L, 20L, "", "");

        MediaAlbum album = MediaAlbumAggregator.aggregate(
                Arrays.asList(first, second),
                "Other images"
        ).get(0);

        assertEquals("Other images", album.getDisplayName());
        assertEquals(2, album.getImageCount());
        assertSame(second.getUri(), album.getCoverUri());
    }

    @Test
    public void duplicateIdsAreIgnored() {
        MediaImage first = image(7L, 10L, "camera", "Camera");
        MediaImage duplicate = image(7L, 20L, "camera", "Camera");

        MediaAlbum album = MediaAlbumAggregator.aggregate(
                Arrays.asList(first, duplicate),
                "Other"
        ).get(0);

        assertEquals(1, album.getImageCount());
        assertSame(first.getUri(), album.getCoverUri());
    }

    private MediaImage image(
            long id,
            long dateTaken,
            String bucketId,
            String bucketName
    ) {
        return new MediaImage(
                FakeUri.create("content://test/" + id + "/" + dateTaken),
                id,
                "image.jpg",
                0L,
                dateTaken,
                100L,
                100,
                200,
                bucketId,
                bucketName
        );
    }
}
