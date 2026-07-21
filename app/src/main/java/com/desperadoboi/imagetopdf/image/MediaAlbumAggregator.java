package com.desperadoboi.imagetopdf.image;

import com.desperadoboi.imagetopdf.model.MediaAlbum;
import com.desperadoboi.imagetopdf.model.MediaImage;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class MediaAlbumAggregator {
    private MediaAlbumAggregator() {
    }

    public static List<MediaAlbum> aggregate(
            List<MediaImage> images,
            String missingBucketName
    ) {
        if (missingBucketName == null || missingBucketName.trim().isEmpty()) {
            throw new IllegalArgumentException("missingBucketName is required");
        }
        LinkedHashMap<String, MutableAlbum> albums = new LinkedHashMap<>();
        Set<Long> seenIds = new HashSet<>();
        for (MediaImage image : images) {
            if (!seenIds.add(image.getId())) {
                continue;
            }
            String bucketId = image.getBucketId().isEmpty()
                    ? "missing"
                    : image.getBucketId();
            String bucketName = image.getBucketName().isEmpty()
                    ? missingBucketName
                    : image.getBucketName();
            MutableAlbum album = albums.get(bucketId);
            if (album == null) {
                album = new MutableAlbum(bucketId, bucketName, image);
                albums.put(bucketId, album);
            } else {
                album.imageCount++;
                if (sortTimestamp(image) > sortTimestamp(album.cover)) {
                    album.cover = image;
                }
            }
        }

        ArrayList<MediaAlbum> result = new ArrayList<>(albums.size());
        for (Map.Entry<String, MutableAlbum> entry : albums.entrySet()) {
            MutableAlbum album = entry.getValue();
            result.add(new MediaAlbum(
                    album.bucketId,
                    album.displayName,
                    album.cover.getUri(),
                    album.imageCount
            ));
        }
        result.sort((left, right) -> {
            int priorityComparison = Integer.compare(
                    priority(left.getDisplayName()),
                    priority(right.getDisplayName())
            );
            if (priorityComparison != 0) {
                return priorityComparison;
            }
            return left.getDisplayName().compareToIgnoreCase(right.getDisplayName());
        });
        return result;
    }

    private static int priority(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        if (normalized.equals("camera") || normalized.equals("камера")) {
            return 0;
        }
        if (normalized.equals("screenshots") || normalized.equals("скриншоты")) {
            return 1;
        }
        if (normalized.equals("download") || normalized.equals("downloads")
                || normalized.equals("загрузки")) {
            return 2;
        }
        return 3;
    }

    private static long sortTimestamp(MediaImage image) {
        return image.getDateTaken() > 0L
                ? image.getDateTaken()
                : image.getDateAdded() * 1000L;
    }

    private static final class MutableAlbum {
        private final String bucketId;
        private final String displayName;
        private MediaImage cover;
        private int imageCount = 1;

        private MutableAlbum(String bucketId, String displayName, MediaImage cover) {
            this.bucketId = bucketId;
            this.displayName = displayName;
            this.cover = cover;
        }
    }
}
