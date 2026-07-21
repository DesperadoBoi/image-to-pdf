package com.desperadoboi.imagetopdf.image;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

import com.desperadoboi.imagetopdf.model.MediaAlbum;
import com.desperadoboi.imagetopdf.model.MediaImage;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MediaGalleryRepository {
    private static final String[] PROJECTION = {
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME
    };
    private static final String SORT_ORDER = MediaStore.Images.Media.DATE_TAKEN + " DESC, "
            + MediaStore.Images.Media.DATE_ADDED + " DESC, "
            + MediaStore.Images.Media._ID + " DESC";

    private final ContentResolver contentResolver;
    private final String missingBucketName;
    private final ExecutorService queryExecutor = Executors.newSingleThreadExecutor();

    public MediaGalleryRepository(ContentResolver contentResolver, String missingBucketName) {
        this.contentResolver = Objects.requireNonNull(
                contentResolver,
                "contentResolver is required"
        );
        if (missingBucketName == null || missingBucketName.trim().isEmpty()) {
            throw new IllegalArgumentException("missingBucketName is required");
        }
        this.missingBucketName = missingBucketName.trim();
    }

    public void load(Executor callbackExecutor, Callback callback) {
        Objects.requireNonNull(callbackExecutor, "callbackExecutor is required");
        Objects.requireNonNull(callback, "callback is required");
        queryExecutor.execute(() -> {
            try {
                List<MediaImage> images = queryImages();
                List<MediaAlbum> albums = MediaAlbumAggregator.aggregate(
                        images,
                        missingBucketName
                );
                callbackExecutor.execute(() -> callback.onLoaded(images, albums));
            } catch (RuntimeException exception) {
                callbackExecutor.execute(() -> callback.onError(exception));
            }
        });
    }

    public void shutdown() {
        queryExecutor.shutdownNow();
    }

    private List<MediaImage> queryImages() {
        ArrayList<MediaImage> images = new ArrayList<>();
        Set<Long> seenIds = new HashSet<>();
        Uri collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        try (Cursor cursor = contentResolver.query(
                collection,
                PROJECTION,
                null,
                null,
                SORT_ORDER
        )) {
            if (cursor == null) {
                return images;
            }
            int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
            int nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME);
            int dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED);
            int dateTakenColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN);
            int sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE);
            int widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH);
            int heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT);
            int bucketIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID);
            int bucketNameColumn = cursor.getColumnIndexOrThrow(
                    MediaStore.Images.Media.BUCKET_DISPLAY_NAME
            );
            while (cursor.moveToNext()) {
                long id = cursor.getLong(idColumn);
                if (!seenIds.add(id)) {
                    continue;
                }
                images.add(new MediaImage(
                        ContentUris.withAppendedId(collection, id),
                        id,
                        getString(cursor, nameColumn),
                        getLong(cursor, dateAddedColumn),
                        getLong(cursor, dateTakenColumn),
                        getLong(cursor, sizeColumn),
                        getInt(cursor, widthColumn),
                        getInt(cursor, heightColumn),
                        getString(cursor, bucketIdColumn),
                        getString(cursor, bucketNameColumn)
                ));
            }
        }
        return images;
    }

    private static String getString(Cursor cursor, int column) {
        return cursor.isNull(column) ? "" : cursor.getString(column);
    }

    private static long getLong(Cursor cursor, int column) {
        return cursor.isNull(column) ? 0L : cursor.getLong(column);
    }

    private static int getInt(Cursor cursor, int column) {
        return cursor.isNull(column) ? 0 : cursor.getInt(column);
    }

    public interface Callback {
        void onLoaded(List<MediaImage> images, List<MediaAlbum> albums);

        void onError(RuntimeException exception);
    }
}
