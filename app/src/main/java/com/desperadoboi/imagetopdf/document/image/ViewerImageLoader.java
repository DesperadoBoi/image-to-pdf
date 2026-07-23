package com.desperadoboi.imagetopdf.document.image;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.exifinterface.media.ExifInterface;

import com.desperadoboi.imagetopdf.image.BitmapSampleSizeCalculator;
import com.desperadoboi.imagetopdf.image.ExifOrientationMapper;
import com.desperadoboi.imagetopdf.image.ImageBitmapTransformer;
import com.desperadoboi.imagetopdf.image.ImageTransform;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public final class ViewerImageLoader {
    private static final int MAX_EDGE = 4096;

    private final Executor callbackExecutor;
    private final ExecutorService decodeExecutor = Executors.newSingleThreadExecutor();
    private final AtomicLong requestIds = new AtomicLong();
    private volatile long activeRequestId;
    private volatile boolean closed;

    public ViewerImageLoader(Executor callbackExecutor) {
        this.callbackExecutor = callbackExecutor;
    }

    public void load(File file, int targetWidth, int targetHeight, Callback callback) {
        long requestId = requestIds.incrementAndGet();
        activeRequestId = requestId;
        decodeExecutor.execute(() -> decode(requestId, file, targetWidth, targetHeight, callback));
    }

    public void close() {
        closed = true;
        activeRequestId = requestIds.incrementAndGet();
        decodeExecutor.shutdownNow();
    }

    private void decode(long requestId, File file, int targetWidth, int targetHeight, Callback callback) {
        Bitmap bitmap = null;
        Exception failure = null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                throw new IOException("Unable to read image bounds");
            }
            int safeWidth = Math.max(1, Math.min(MAX_EDGE, targetWidth));
            int safeHeight = Math.max(1, Math.min(MAX_EDGE, targetHeight));
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            options.inSampleSize = BitmapSampleSizeCalculator.calculate(
                    bounds.outWidth,
                    bounds.outHeight,
                    safeWidth,
                    safeHeight
            );
            bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            if (bitmap == null) throw new IOException("Unable to decode image");
            ExifInterface exif = new ExifInterface(file.getAbsolutePath());
            ImageTransform transform = ExifOrientationMapper.map(exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
            ));
            bitmap = ImageBitmapTransformer.applyTransform(bitmap, transform);
            bitmap = ImageBitmapTransformer.scaleDownToFit(bitmap, MAX_EDGE, MAX_EDGE);
        } catch (Exception exception) {
            failure = exception;
            recycle(bitmap);
            bitmap = null;
        } catch (OutOfMemoryError error) {
            failure = new IOException("Not enough memory to decode image", error);
            recycle(bitmap);
            bitmap = null;
        }
        Bitmap result = bitmap;
        Exception resultFailure = failure;
        callbackExecutor.execute(() -> {
            if (closed || requestId != activeRequestId) {
                recycle(result);
            } else if (result != null) {
                callback.onLoaded(result);
            } else {
                callback.onError(resultFailure);
            }
        });
    }

    private void recycle(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
    }

    public interface Callback {
        void onLoaded(Bitmap bitmap);
        void onError(Exception exception);
    }
}
