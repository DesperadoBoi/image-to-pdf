package com.desperadoboi.imagetopdf.pdf;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;

import com.desperadoboi.imagetopdf.image.BitmapSampleSizeCalculator;
import com.desperadoboi.imagetopdf.image.EditedImageGeometryCalculator;
import com.desperadoboi.imagetopdf.image.ImageBitmapTransformer;
import com.desperadoboi.imagetopdf.image.ImageOrientationReader;
import com.desperadoboi.imagetopdf.image.ImageTransform;
import com.desperadoboi.imagetopdf.image.PageBitmapProcessor;
import com.desperadoboi.imagetopdf.image.PageProcessingMode;
import com.desperadoboi.imagetopdf.image.SourceResolutionCalculator;
import com.desperadoboi.imagetopdf.model.ImagePlacementMode;
import com.desperadoboi.imagetopdf.model.PageItem;
import com.desperadoboi.imagetopdf.ui.idcard.IdCardExportOptions;
import com.desperadoboi.imagetopdf.ui.idcard.IdCardImage;
import com.desperadoboi.imagetopdf.ui.idcard.IdCardSide;
import com.desperadoboi.imagetopdf.util.ImagePlacementCalculator;

import java.io.FilterOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

public final class IdCardPdfGenerator {
    private static final int TARGET_DPI = 180;
    private static final String TEMP_DIRECTORY = "id_card_pdf";
    private static final String TEMP_PREFIX = "idcard_pdf_";

    private final ContentResolver contentResolver;
    private final File temporaryDirectory;
    private final ImageOrientationReader orientationReader;
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

    public IdCardPdfGenerator(Context context) {
        Context applicationContext = Objects.requireNonNull(context).getApplicationContext();
        this.contentResolver = applicationContext.getContentResolver();
        this.temporaryDirectory = new File(applicationContext.getCacheDir(), TEMP_DIRECTORY);
        orientationReader = new ImageOrientationReader(contentResolver);
    }

    public void generate(
            List<SideImage> sideImages,
            IdCardExportOptions options,
            Uri outputUri,
            CancellationToken cancellationToken,
            Executor backgroundExecutor,
            Executor callbackExecutor,
            PdfGenerationCallback callback
    ) {
        Objects.requireNonNull(cancellationToken, "cancellationToken is required");
        backgroundExecutor.execute(() -> {
            try {
                long size = generateInternal(
                        sideImages,
                        options,
                        outputUri,
                        cancellationToken,
                        callbackExecutor,
                        callback
                );
                callbackExecutor.execute(() -> callback.onSuccess(outputUri, size));
            } catch (PdfGenerationCancelledException exception) {
                deletePartialOutput(outputUri);
                callbackExecutor.execute(callback::onCancelled);
            } catch (OutOfMemoryError error) {
                deletePartialOutput(outputUri);
                IOException exception = new IOException("Insufficient memory for ID-card PDF", error);
                callbackExecutor.execute(() -> callback.onError(exception));
            } catch (Exception exception) {
                deletePartialOutput(outputUri);
                callbackExecutor.execute(() -> callback.onError(exception));
            }
        });
    }

    private long generateInternal(
            List<SideImage> sideImages,
            IdCardExportOptions options,
            Uri outputUri,
            CancellationToken cancellationToken,
            Executor callbackExecutor,
            PdfGenerationCallback callback
    ) throws IOException, PdfGenerationCancelledException {
        if (sideImages == null || sideImages.isEmpty() || sideImages.size() > 2) {
            throw new IllegalArgumentException("one or two ID-card sides are required");
        }
        if (options == null || !options.isValid()) {
            throw new IllegalArgumentException("valid ID-card export options are required");
        }
        if (outputUri == null) {
            throw new IllegalArgumentException("output Uri is required");
        }

        ArrayList<IdCardSide> sides = new ArrayList<>(sideImages.size());
        for (SideImage sideImage : sideImages) sides.add(sideImage.getSide());
        IdCardPageLayout layout = IdCardPageLayoutCalculator.calculate(options.getPreset(), sides);
        notifyProgress(callbackExecutor, callback, 0, sideImages.size());

        File temporaryFile = createTemporaryFile();
        try {
            writePdfToTemporaryFile(
                    sideImages,
                    options,
                    layout,
                    temporaryFile,
                    cancellationToken,
                    callbackExecutor,
                    callback
            );
            throwIfCancelled(cancellationToken);
            return copyToOutput(temporaryFile, outputUri, cancellationToken);
        } finally {
            deleteTemporaryFile(temporaryFile);
        }
    }

    private void writePdfToTemporaryFile(
            List<SideImage> sideImages,
            IdCardExportOptions options,
            IdCardPageLayout layout,
            File temporaryFile,
            CancellationToken cancellationToken,
            Executor callbackExecutor,
            PdfGenerationCallback callback
    ) throws IOException, PdfGenerationCancelledException {
        PdfDocument document = new PdfDocument();
        ArrayList<Bitmap> renderedBitmaps = new ArrayList<>(sideImages.size());
        try (OutputStream output = new FileOutputStream(temporaryFile, false)) {
            PdfDocument.Page page = document.startPage(new PdfDocument.PageInfo.Builder(
                    layout.getPageWidth(),
                    layout.getPageHeight(),
                    1
            ).create());
            boolean pageFinished = false;
            try {
                Canvas canvas = page.getCanvas();
                canvas.drawColor(Color.WHITE);
                for (int index = 0; index < sideImages.size(); index++) {
                    throwIfCancelled(cancellationToken);
                    SideImage sideImage = sideImages.get(index);
                    IdCardPageLayout.Placement placement = layout.getPlacements().get(index);
                    Bitmap bitmap = loadForPlacement(sideImage.getImage(), placement);
                    renderedBitmaps.add(bitmap);
                    throwIfCancelled(cancellationToken);
                    RectF drawnRect = drawImage(canvas, bitmap, placement);
                    if (options.isWatermarkEnabled()) {
                        drawWatermark(canvas, drawnRect, options.getWatermarkText().trim());
                    }
                    notifyProgress(callbackExecutor, callback, index + 1, sideImages.size());
                }
                document.finishPage(page);
                pageFinished = true;
            } finally {
                if (!pageFinished) {
                    try {
                        document.finishPage(page);
                    } catch (RuntimeException ignored) {
                        // The output is deleted by the outer failure handler.
                    }
                }
                recycleAll(renderedBitmaps);
            }

            throwIfCancelled(cancellationToken);
            document.writeTo(output);
            throwIfCancelled(cancellationToken);
        } finally {
            recycleAll(renderedBitmaps);
            document.close();
        }
    }

    private long copyToOutput(
            File temporaryFile,
            Uri outputUri,
            CancellationToken cancellationToken
    ) throws IOException, PdfGenerationCancelledException {
        try (InputStream input = new FileInputStream(temporaryFile);
             CountingOutputStream output = new CountingOutputStream(openOutputStream(outputUri))) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                throwIfCancelled(cancellationToken);
                output.write(buffer, 0, read);
            }
            output.flush();
            throwIfCancelled(cancellationToken);
            return output.getCount();
        } catch (PdfGenerationCancelledException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new PdfOutputException("Unable to save ID-card PDF", exception);
        }
    }

    private File createTemporaryFile() throws IOException {
        if (!temporaryDirectory.isDirectory()
                && !temporaryDirectory.mkdirs()
                && !temporaryDirectory.isDirectory()) {
            throw new IOException("Unable to create PDF cache directory");
        }
        return File.createTempFile(TEMP_PREFIX, ".tmp", temporaryDirectory);
    }

    public static int cleanupExpiredTemporaryFiles(
            Context context,
            long nowMillis,
            long ttlMillis
    ) {
        if (context == null || ttlMillis < 0L) return 0;
        File directory = new File(context.getCacheDir(), TEMP_DIRECTORY);
        File[] files = directory.listFiles();
        if (files == null) return 0;
        int removed = 0;
        long cutoff = nowMillis - ttlMillis;
        for (File file : files) {
            if (file.isFile()
                    && file.getName().startsWith(TEMP_PREFIX)
                    && file.getName().endsWith(".tmp")
                    && file.lastModified() < cutoff
                    && file.delete()) {
                removed++;
            }
        }
        return removed;
    }

    private void deleteTemporaryFile(File file) {
        if (file == null || !file.exists()) return;
        try {
            file.delete();
        } catch (SecurityException ignored) {
            // The TTL cleanup can remove a rare file that the platform kept open.
        }
    }

    private Bitmap loadForPlacement(
            IdCardImage image,
            IdCardPageLayout.Placement placement
    ) throws IOException {
        PageItem pageItem = image.toPageItem();
        ImageBounds raw = readBounds(pageItem.getImageUri());
        ImageTransform transform = orientationReader.read(pageItem.getImageUri());
        boolean swaps = transform.swapsDimensions() ^ pageItem.swapsDimensions();
        int orientedWidth = swaps ? raw.height : raw.width;
        int orientedHeight = swaps ? raw.width : raw.height;
        EditedImageGeometryCalculator.Dimensions finalDimensions =
                EditedImageGeometryCalculator.calculate(
                        orientedWidth,
                        orientedHeight,
                        pageItem.getEditSpec(),
                        PageProcessingMode.FINAL
                );
        ImagePlacementCalculator.PlacementRect destination =
                ImagePlacementCalculator.calculateCenteredFit(
                        finalDimensions.getWidth(),
                        finalDimensions.getHeight(),
                        placement.getLeft(),
                        placement.getTop(),
                        placement.getWidth(),
                        placement.getHeight()
                );
        int targetWidth = Math.max(1, (int) Math.ceil(
                destination.getWidth() * TARGET_DPI / 72f
        ));
        int targetHeight = Math.max(1, (int) Math.ceil(
                destination.getHeight() * TARGET_DPI / 72f
        ));
        EditedImageGeometryCalculator.Dimensions sourceTarget =
                SourceResolutionCalculator.calculateForOutputTarget(
                        orientedWidth,
                        orientedHeight,
                        pageItem.getEditSpec(),
                        PageProcessingMode.FINAL,
                        targetWidth,
                        targetHeight
                );
        Bitmap bitmap = decode(
                pageItem.getImageUri(),
                raw,
                swaps ? sourceTarget.getHeight() : sourceTarget.getWidth(),
                swaps ? sourceTarget.getWidth() : sourceTarget.getHeight()
        );
        try {
            bitmap = PageBitmapProcessor.process(
                    bitmap,
                    transform,
                    pageItem.getManualRotationDegrees(),
                    pageItem.getEditSpec(),
                    PageProcessingMode.FINAL
            );
            return ImageBitmapTransformer.scaleDownToFit(bitmap, targetWidth, targetHeight);
        } catch (RuntimeException | Error exception) {
            if (!bitmap.isRecycled()) bitmap.recycle();
            throw exception;
        }
    }

    private RectF drawImage(
            Canvas canvas,
            Bitmap bitmap,
            IdCardPageLayout.Placement placement
    ) {
        ImagePlacementCalculator.ImageDrawPlan plan =
                ImagePlacementCalculator.calculateDrawPlan(
                        bitmap.getWidth(),
                        bitmap.getHeight(),
                        placement.getLeft(),
                        placement.getTop(),
                        placement.getWidth(),
                        placement.getHeight(),
                        ImagePlacementMode.FIT
                );
        ImagePlacementCalculator.PlacementRect destination = plan.getDestinationRect();
        RectF destinationRect = new RectF(
                destination.getLeft(),
                destination.getTop(),
                destination.getRight(),
                destination.getBottom()
        );
        canvas.drawBitmap(
                bitmap,
                new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight()),
                destinationRect,
                bitmapPaint
        );
        return destinationRect;
    }

    private void drawWatermark(Canvas canvas, RectF bounds, String text) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.rgb(50, 55, 68));
        paint.setAlpha(82);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        float textSize = Math.max(16f, Math.min(38f, bounds.height() * 0.28f));
        paint.setTextSize(textSize);
        float measured = paint.measureText(text);
        if (measured > bounds.width() * 0.82f && measured > 0f) {
            paint.setTextSize(textSize * (bounds.width() * 0.82f / measured));
        }
        float centerX = bounds.centerX();
        float centerY = bounds.centerY() - ((paint.ascent() + paint.descent()) / 2f);
        canvas.save();
        canvas.rotate(-24f, bounds.centerX(), bounds.centerY());
        canvas.drawText(text, centerX, centerY, paint);
        canvas.restore();
    }

    private ImageBounds readBounds(Uri uri) throws IOException {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try (InputStream input = openInputStream(uri)) {
            BitmapFactory.decodeStream(input, null, options);
        }
        if (options.outWidth <= 0 || options.outHeight <= 0) {
            throw new IOException("Unable to read ID-card image bounds");
        }
        return new ImageBounds(options.outWidth, options.outHeight);
    }

    private Bitmap decode(Uri uri, ImageBounds bounds, int width, int height) throws IOException {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = BitmapSampleSizeCalculator.calculate(
                bounds.width,
                bounds.height,
                width,
                height
        );
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        try (InputStream input = openInputStream(uri)) {
            Bitmap bitmap = BitmapFactory.decodeStream(input, null, options);
            if (bitmap == null) throw new IOException("Unable to decode ID-card image");
            return bitmap;
        }
    }

    private InputStream openInputStream(Uri uri) throws IOException {
        InputStream stream = contentResolver.openInputStream(uri);
        if (stream == null) throw new IOException("Unable to open ID-card image");
        return stream;
    }

    private OutputStream openOutputStream(Uri uri) throws IOException {
        try {
            OutputStream stream = contentResolver.openOutputStream(uri, "wt");
            if (stream == null) throw new IOException("Unable to open PDF output");
            return stream;
        } catch (IOException | RuntimeException exception) {
            throw new PdfOutputException("Unable to open PDF output", exception);
        }
    }

    private void deletePartialOutput(Uri uri) {
        if (uri == null) return;
        try {
            contentResolver.delete(uri, null, null);
        } catch (RuntimeException ignored) {
            // Some SAF providers do not support delete; the write used truncate mode.
        }
    }

    private void notifyProgress(
            Executor executor,
            PdfGenerationCallback callback,
            int completed,
            int total
    ) {
        executor.execute(() -> callback.onProgress(completed, total));
    }

    private void throwIfCancelled(CancellationToken token)
            throws PdfGenerationCancelledException {
        if (token.isCancelled() || Thread.currentThread().isInterrupted()) {
            throw new PdfGenerationCancelledException();
        }
    }

    private static void recycleAll(List<Bitmap> bitmaps) {
        for (Bitmap bitmap : bitmaps) {
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
        }
        bitmaps.clear();
    }

    public static final class SideImage {
        private final IdCardSide side;
        private final IdCardImage image;

        public SideImage(IdCardSide side, IdCardImage image) {
            this.side = Objects.requireNonNull(side, "side is required");
            this.image = Objects.requireNonNull(image, "image is required");
        }

        public IdCardSide getSide() {
            return side;
        }

        public IdCardImage getImage() {
            return image;
        }
    }

    private static final class ImageBounds {
        private final int width;
        private final int height;

        private ImageBounds(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }

    private static final class CountingOutputStream extends FilterOutputStream {
        private long count;

        private CountingOutputStream(OutputStream outputStream) {
            super(outputStream);
        }

        @Override
        public void write(int value) throws IOException {
            out.write(value);
            count++;
        }

        @Override
        public void write(byte[] values, int offset, int length) throws IOException {
            out.write(values, offset, length);
            count += length;
        }

        private long getCount() {
            return count;
        }
    }

    public static final class PdfOutputException extends IOException {
        private PdfOutputException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
