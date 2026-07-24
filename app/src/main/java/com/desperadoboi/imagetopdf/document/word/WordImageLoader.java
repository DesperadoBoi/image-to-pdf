package com.desperadoboi.imagetopdf.document.word;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.exifinterface.media.ExifInterface;

import com.desperadoboi.imagetopdf.image.BitmapSampleSizeCalculator;
import com.desperadoboi.imagetopdf.image.ExifOrientationMapper;
import com.desperadoboi.imagetopdf.image.ImageBitmapTransformer;
import com.desperadoboi.imagetopdf.image.ImageTransform;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class WordImageLoader {
    private static final int MAX_EDGE = 2_048;
    private static final long MAX_PIXELS = 4_000_000L;
    private static final long MAX_CACHE_BYTES = 16L * 1024L * 1024L;

    private final File packageFile;
    private final Executor callbackExecutor;
    private final ExecutorService decodeExecutor = Executors.newSingleThreadExecutor();
    private final LinkedHashMap<String, Bitmap> cache =
            new LinkedHashMap<>(8, 0.75f, true);
    private final Map<String, List<Callback>> pendingCallbacks = new LinkedHashMap<>();
    private long cacheBytes;
    private volatile boolean closed;

    public WordImageLoader(File packageFile, Executor callbackExecutor) {
        this.packageFile = packageFile;
        this.callbackExecutor = callbackExecutor;
    }

    public void load(
            String packagePath,
            int targetWidth,
            int targetHeight,
            Callback callback
    ) {
        Bitmap cached = cached(packagePath);
        if (cached != null && !cached.isRecycled()) {
            callback.onLoaded(packagePath, cached);
            return;
        }
        synchronized (cache) {
            if (closed) return;
            List<Callback> pending = pendingCallbacks.get(packagePath);
            if (pending != null) {
                pending.add(callback);
                return;
            }
            pending = new ArrayList<>();
            pending.add(callback);
            pendingCallbacks.put(packagePath, pending);
        }
        decodeExecutor.execute(() -> decode(packagePath, targetWidth, targetHeight));
    }

    public void close() {
        closed = true;
        decodeExecutor.shutdownNow();
        synchronized (cache) {
            for (Bitmap bitmap : cache.values()) recycle(bitmap);
            cache.clear();
            pendingCallbacks.clear();
            cacheBytes = 0L;
        }
    }

    private void decode(
            String packagePath,
            int targetWidth,
            int targetHeight
    ) {
        Bitmap bitmap = null;
        Exception failure = null;
        try (ZipFile zipFile = new ZipFile(packageFile)) {
            ZipEntry entry = zipFile.getEntry(packagePath);
            if (entry == null || entry.isDirectory()) {
                throw new IOException("DOCX image entry is missing");
            }
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream inputStream = zipFile.getInputStream(entry)) {
                BitmapFactory.decodeStream(inputStream, null, bounds);
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                throw new IOException("DOCX image bounds are unavailable");
            }
            int safeWidth = Math.max(1, Math.min(MAX_EDGE, targetWidth));
            int safeHeight = Math.max(1, Math.min(MAX_EDGE, targetHeight));
            long targetPixels = (long) safeWidth * safeHeight;
            if (targetPixels > MAX_PIXELS) {
                float reduction = (float) Math.sqrt(MAX_PIXELS / (double) targetPixels);
                safeWidth = Math.max(1, Math.round(safeWidth * reduction));
                safeHeight = Math.max(1, Math.round(safeHeight * reduction));
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            options.inSampleSize = BitmapSampleSizeCalculator.calculate(
                    bounds.outWidth,
                    bounds.outHeight,
                    safeWidth,
                    safeHeight
            );
            try (InputStream inputStream = zipFile.getInputStream(entry)) {
                bitmap = BitmapFactory.decodeStream(inputStream, null, options);
            }
            if (bitmap == null) throw new IOException("Unable to decode DOCX image");
            try (InputStream inputStream = zipFile.getInputStream(entry)) {
                ExifInterface exif = new ExifInterface(inputStream);
                ImageTransform transform = ExifOrientationMapper.map(exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                ));
                bitmap = ImageBitmapTransformer.applyTransform(bitmap, transform);
            } catch (IOException ignored) {
                // Raster entries without EXIF remain displayable in their decoded orientation.
            }
            bitmap = ImageBitmapTransformer.scaleDownToFit(bitmap, safeWidth, safeHeight);
        } catch (Exception exception) {
            failure = exception;
            recycle(bitmap);
            bitmap = null;
        } catch (OutOfMemoryError error) {
            failure = new IOException("Not enough memory to decode DOCX image", error);
            recycle(bitmap);
            bitmap = null;
        }
        Bitmap result = bitmap;
        Exception resultFailure = failure;
        callbackExecutor.execute(() -> {
            List<Callback> callbacks = takePendingCallbacks(packagePath);
            if (closed) {
                recycle(result);
            } else if (result != null) {
                put(packagePath, result);
                for (Callback callback : callbacks) {
                    callback.onLoaded(packagePath, result);
                }
            } else {
                for (Callback callback : callbacks) {
                    callback.onError(packagePath, resultFailure);
                }
            }
        });
    }

    private List<Callback> takePendingCallbacks(String path) {
        synchronized (cache) {
            List<Callback> callbacks = pendingCallbacks.remove(path);
            return callbacks == null ? new ArrayList<>() : callbacks;
        }
    }

    private Bitmap cached(String path) {
        synchronized (cache) {
            return cache.get(path);
        }
    }

    private void put(String path, Bitmap bitmap) {
        synchronized (cache) {
            Bitmap replaced = cache.put(path, bitmap);
            if (replaced != null && replaced != bitmap) {
                cacheBytes -= bytes(replaced);
                recycle(replaced);
            }
            cacheBytes += bytes(bitmap);
            while (cacheBytes > MAX_CACHE_BYTES && cache.size() > 1) {
                Map.Entry<String, Bitmap> eldest = cache.entrySet().iterator().next();
                cache.remove(eldest.getKey());
                cacheBytes -= bytes(eldest.getValue());
            }
        }
    }

    private long bytes(Bitmap bitmap) {
        return bitmap == null ? 0L : bitmap.getAllocationByteCount();
    }

    private static void recycle(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
    }

    public interface Callback {
        void onLoaded(String packagePath, Bitmap bitmap);
        void onError(String packagePath, Exception exception);
    }
}
