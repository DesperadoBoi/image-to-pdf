package com.desperadoboi.imagetopdf.image;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import com.desperadoboi.imagetopdf.model.PageItem;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ThumbnailLoader {
    private final ContentResolver contentResolver;
    private final ImageOrientationReader imageOrientationReader;
    private final Executor mainExecutor;
    private final ExecutorService thumbnailExecutor = Executors.newFixedThreadPool(2);

    public ThumbnailLoader(ContentResolver contentResolver, Executor mainExecutor) {
        this.contentResolver = contentResolver;
        this.imageOrientationReader = new ImageOrientationReader(contentResolver);
        this.mainExecutor = mainExecutor;
    }

    public void load(PageItem pageItem, int targetWidth, int targetHeight, Callback callback) {
        String key = pageItem.getThumbnailKey();
        thumbnailExecutor.execute(() -> {
            Bitmap bitmap = null;
            try {
                bitmap = loadInternal(pageItem, targetWidth, targetHeight);
                Bitmap loadedBitmap = bitmap;
                mainExecutor.execute(() -> callback.onLoaded(key, loadedBitmap));
            } catch (IOException | RuntimeException exception) {
                if (bitmap != null && !bitmap.isRecycled()) {
                    bitmap.recycle();
                }
                mainExecutor.execute(() -> callback.onError(key));
            }
        });
    }

    public void shutdown() {
        thumbnailExecutor.shutdownNow();
    }

    private Bitmap loadInternal(PageItem pageItem, int targetWidth, int targetHeight) throws IOException {
        ImageTransform exifTransform = imageOrientationReader.read(pageItem.getImageUri());
        boolean swapsDimensions = exifTransform.swapsDimensions() ^ pageItem.swapsDimensions();
        Bitmap bitmap = decodeBitmap(
                pageItem.getImageUri(),
                swapsDimensions ? targetHeight : targetWidth,
                swapsDimensions ? targetWidth : targetHeight
        );
        bitmap = ImageBitmapTransformer.applyTransform(bitmap, exifTransform);
        bitmap = ImageBitmapTransformer.applyClockwiseRotation(
                bitmap,
                pageItem.getManualRotationDegrees()
        );
        return bitmap;
    }

    private Bitmap decodeBitmap(Uri imageUri, int targetWidth, int targetHeight) throws IOException {
        BitmapFactory.Options boundsOptions = new BitmapFactory.Options();
        boundsOptions.inJustDecodeBounds = true;

        try (InputStream boundsStream = openInputStream(imageUri)) {
            BitmapFactory.decodeStream(boundsStream, null, boundsOptions);
        }

        if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) {
            throw new IOException("Unable to read image bounds");
        }

        BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
        decodeOptions.inSampleSize = calculateInSampleSize(
                boundsOptions.outWidth,
                boundsOptions.outHeight,
                targetWidth,
                targetHeight
        );
        decodeOptions.inPreferredConfig = Bitmap.Config.ARGB_8888;

        try (InputStream bitmapStream = openInputStream(imageUri)) {
            Bitmap bitmap = BitmapFactory.decodeStream(bitmapStream, null, decodeOptions);
            if (bitmap == null) {
                throw new IOException("Unable to decode image");
            }
            return bitmap;
        }
    }

    private InputStream openInputStream(Uri imageUri) throws IOException {
        InputStream inputStream = contentResolver.openInputStream(imageUri);
        if (inputStream == null) {
            throw new IOException("Unable to open input stream");
        }
        return inputStream;
    }

    private int calculateInSampleSize(
            int sourceWidth,
            int sourceHeight,
            int targetWidth,
            int targetHeight
    ) {
        if (sourceWidth <= 0 || sourceHeight <= 0 || targetWidth <= 0 || targetHeight <= 0) {
            throw new IllegalArgumentException("Invalid bitmap dimensions");
        }

        int sampleSize = 1;
        while ((sourceWidth / sampleSize) > targetWidth || (sourceHeight / sampleSize) > targetHeight) {
            sampleSize *= 2;
        }
        return Math.max(1, sampleSize);
    }

    public interface Callback {
        void onLoaded(String key, Bitmap bitmap);

        void onError(String key);
    }
}
