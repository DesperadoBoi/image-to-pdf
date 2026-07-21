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
        load(pageItem, targetWidth, targetHeight, PageProcessingMode.FINAL, callback);
    }

    public void load(
            PageItem pageItem,
            int targetWidth,
            int targetHeight,
            PageProcessingMode mode,
            Callback callback
    ) {
        String key = buildKey(pageItem, targetWidth, targetHeight, mode);
        previewExecutor.execute(() -> {
            Bitmap bitmap = null;
            try {
                bitmap = loadInternal(pageItem, targetWidth, targetHeight, mode);
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
        return buildKey(
                pageItem,
                targetWidth,
                targetHeight,
                PageProcessingMode.FINAL
        );
    }

    public static String buildKey(
            PageItem pageItem,
            int targetWidth,
            int targetHeight,
            PageProcessingMode mode
    ) {
        return pageItem.getThumbnailKey()
                + "@" + targetWidth + "x" + targetHeight
                + "@" + mode.name();
    }

    private Bitmap loadInternal(
            PageItem pageItem,
            int targetWidth,
            int targetHeight,
            PageProcessingMode mode
    ) throws IOException {
        int boundedTargetWidth = clampTargetDimension(targetWidth);
        int boundedTargetHeight = clampTargetDimension(targetHeight);
        ImageTransform exifTransform = imageOrientationReader.read(pageItem.getImageUri());
        boolean swapsDimensions = exifTransform.swapsDimensions() ^ pageItem.swapsDimensions();
        ImageBounds rawBounds = readImageBounds(pageItem.getImageUri());
        int orientedWidth = swapsDimensions ? rawBounds.height : rawBounds.width;
        int orientedHeight = swapsDimensions ? rawBounds.width : rawBounds.height;
        EditedImageGeometryCalculator.Dimensions sourceTarget =
                SourceResolutionCalculator.calculateForFitTarget(
                        orientedWidth,
                        orientedHeight,
                        pageItem.getEditSpec(),
                        mode,
                        boundedTargetWidth,
                        boundedTargetHeight
                );
        Bitmap bitmap = decodeBitmap(
                pageItem.getImageUri(),
                rawBounds,
                swapsDimensions ? sourceTarget.getHeight() : sourceTarget.getWidth(),
                swapsDimensions ? sourceTarget.getWidth() : sourceTarget.getHeight()
        );
        bitmap = PageBitmapProcessor.process(
                bitmap,
                exifTransform,
                pageItem.getManualRotationDegrees(),
                pageItem.getEditSpec(),
                mode
        );
        return ImageBitmapTransformer.scaleDownToFit(bitmap, boundedTargetWidth, boundedTargetHeight);
    }

    private ImageBounds readImageBounds(Uri imageUri) throws IOException {
        BitmapFactory.Options boundsOptions = new BitmapFactory.Options();
        boundsOptions.inJustDecodeBounds = true;

        try (InputStream boundsStream = openInputStream(imageUri)) {
            BitmapFactory.decodeStream(boundsStream, null, boundsOptions);
        }

        if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) {
            throw new IOException("Unable to read image bounds");
        }
        return new ImageBounds(boundsOptions.outWidth, boundsOptions.outHeight);
    }

    private Bitmap decodeBitmap(
            Uri imageUri,
            ImageBounds bounds,
            int targetWidth,
            int targetHeight
    ) throws IOException {
        BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
        decodeOptions.inSampleSize = BitmapSampleSizeCalculator.calculate(
                bounds.width,
                bounds.height,
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

    private static final class ImageBounds {
        private final int width;
        private final int height;

        private ImageBounds(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }
}
