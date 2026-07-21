package com.desperadoboi.imagetopdf.image;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class GalleryThumbnailLoader {
    private final ContentResolver contentResolver;
    private final ImageOrientationReader orientationReader;
    private final Executor callbackExecutor;
    private final ExecutorService decodeExecutor = Executors.newFixedThreadPool(3);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public GalleryThumbnailLoader(ContentResolver contentResolver, Executor callbackExecutor) {
        this.contentResolver = Objects.requireNonNull(
                contentResolver,
                "contentResolver is required"
        );
        this.orientationReader = new ImageOrientationReader(contentResolver);
        this.callbackExecutor = Objects.requireNonNull(
                callbackExecutor,
                "callbackExecutor is required"
        );
    }

    public void load(Uri uri, int width, int height, Callback callback) {
        if (closed.get()) {
            return;
        }
        String key = buildKey(uri, width, height);
        decodeExecutor.execute(() -> {
            Bitmap bitmap = null;
            try {
                bitmap = loadInternal(uri, width, height);
                if (closed.get()) {
                    recycle(bitmap);
                    return;
                }
                Bitmap loaded = bitmap;
                callbackExecutor.execute(() -> {
                    if (closed.get()) {
                        recycle(loaded);
                    } else {
                        callback.onLoaded(key, loaded);
                    }
                });
            } catch (IOException | RuntimeException exception) {
                recycle(bitmap);
                if (!closed.get()) {
                    callbackExecutor.execute(() -> callback.onError(key));
                }
            }
        });
    }

    public void shutdown() {
        closed.set(true);
        decodeExecutor.shutdownNow();
    }

    public static String buildKey(Uri uri, int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Thumbnail dimensions must be positive");
        }
        return uri + "#" + width + "x" + height;
    }

    private Bitmap loadInternal(Uri uri, int targetWidth, int targetHeight) throws IOException {
        Bounds bounds = readBounds(uri);
        ImageTransform transform = orientationReader.read(uri);
        int rawTargetWidth = transform.swapsDimensions() ? targetHeight : targetWidth;
        int rawTargetHeight = transform.swapsDimensions() ? targetWidth : targetHeight;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = BitmapSampleSizeCalculator.calculate(
                bounds.width,
                bounds.height,
                rawTargetWidth,
                rawTargetHeight
        );
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap;
        try (InputStream stream = open(uri)) {
            bitmap = BitmapFactory.decodeStream(stream, null, options);
        }
        if (bitmap == null) {
            throw new IOException("Unable to decode gallery thumbnail");
        }
        bitmap = ImageBitmapTransformer.applyTransform(bitmap, transform);
        return ImageBitmapTransformer.scaleDownToFit(bitmap, targetWidth, targetHeight);
    }

    private Bounds readBounds(Uri uri) throws IOException {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try (InputStream stream = open(uri)) {
            BitmapFactory.decodeStream(stream, null, options);
        }
        if (options.outWidth <= 0 || options.outHeight <= 0) {
            throw new IOException("Unable to read gallery image bounds");
        }
        return new Bounds(options.outWidth, options.outHeight);
    }

    private InputStream open(Uri uri) throws IOException {
        InputStream stream = contentResolver.openInputStream(uri);
        if (stream == null) {
            throw new IOException("Unable to open gallery image");
        }
        return stream;
    }

    private static void recycle(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }

    public interface Callback {
        void onLoaded(String key, Bitmap bitmap);

        void onError(String key);
    }

    private static final class Bounds {
        private final int width;
        private final int height;

        private Bounds(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }
}
