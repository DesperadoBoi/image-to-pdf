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

public final class PreviewImageLoader {
    private static final int MAX_TARGET_EDGE_PX = 3072;

    private final ContentResolver contentResolver;
    private final ImageOrientationReader imageOrientationReader;
    private final Executor mainExecutor;
    private final ExecutorService previewExecutor = Executors.newSingleThreadExecutor();

    public PreviewImageLoader(ContentResolver contentResolver, Executor mainExecutor) {
        this.contentResolver = contentResolver;
        this.imageOrientationReader = new ImageOrientationReader(contentResolver);
        this.mainExecutor = mainExecutor;
    }

    public void load(
            PageItem pageItem,
            int targetWidth,
            int targetHeight,
            Callback callback
    ) {
        String key = buildKey(pageItem, targetWidth, targetHeight);
        previewExecutor.execute(() -> {
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
        previewExecutor.shutdownNow();
    }

    public static String buildKey(PageItem pageItem, int targetWidth, int targetHeight) {
        return pageItem.getThumbnailKey() + "@" + targetWidth + "x" + targetHeight;
    }

    private Bitmap loadInternal(PageItem pageItem, int targetWidth, int targetHeight) throws IOException {
        int boundedTargetWidth = clampTargetDimension(targetWidth);
        int boundedTargetHeight = clampTargetDimension(targetHeight);
        ImageTransform exifTransform = imageOrientationReader.read(pageItem.getImageUri());
        boolean swapsDimensions = exifTransform.swapsDimensions() ^ pageItem.swapsDimensions();
        Bitmap bitmap = decodeBitmap(
                pageItem.getImageUri(),
                swapsDimensions ? boundedTargetHeight : boundedTargetWidth,
                swapsDimensions ? boundedTargetWidth : boundedTargetHeight
        );
        bitmap = ImageBitmapTransformer.applyTransform(bitmap, exifTransform);
        bitmap = ImageBitmapTransformer.applyClockwiseRotation(
                bitmap,
                pageItem.getManualRotationDegrees()
        );
        return ImageBitmapTransformer.scaleDownToFit(bitmap, boundedTargetWidth, boundedTargetHeight);
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
            if (sampleSize > Integer.MAX_VALUE / 2) {
                break;
            }
            sampleSize *= 2;
        }
        return Math.max(1, sampleSize);
    }

    private int clampTargetDimension(int value) {
        if (value <= 0) {
            throw new IllegalArgumentException("Target dimension must be positive");
        }
        return Math.min(value, MAX_TARGET_EDGE_PX);
    }

    public interface Callback {
        void onLoaded(String key, Bitmap bitmap);

        void onError(String key);
    }
}
