package com.desperadoboi.imagetopdf.pdf;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;

import com.desperadoboi.imagetopdf.image.ImageBitmapTransformer;
import com.desperadoboi.imagetopdf.image.ImageOrientationReader;
import com.desperadoboi.imagetopdf.image.ImageTransform;
import com.desperadoboi.imagetopdf.model.PageItem;
import com.desperadoboi.imagetopdf.util.ImagePlacementCalculator;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.Executor;

public class PdfGenerator {
    private static final int PAGE_WIDTH_POINTS = 595;
    private static final int PAGE_HEIGHT_POINTS = 842;
    private static final int PAGE_MARGIN_POINTS = 24;
    private static final int CONTENT_WIDTH_POINTS = PAGE_WIDTH_POINTS - (PAGE_MARGIN_POINTS * 2);
    private static final int CONTENT_HEIGHT_POINTS = PAGE_HEIGHT_POINTS - (PAGE_MARGIN_POINTS * 2);

    private final ContentResolver contentResolver;
    private final ImageOrientationReader imageOrientationReader;
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

    public PdfGenerator(ContentResolver contentResolver) {
        this.contentResolver = contentResolver;
        this.imageOrientationReader = new ImageOrientationReader(contentResolver);
    }

    public void generate(
            List<PageItem> pageItems,
            Uri outputUri,
            Executor backgroundExecutor,
            Executor callbackExecutor,
            PdfGenerationCallback callback
    ) {
        backgroundExecutor.execute(() -> {
            try {
                generateInternal(pageItems, outputUri);
                callbackExecutor.execute(() -> callback.onSuccess(outputUri));
            } catch (Exception exception) {
                callbackExecutor.execute(() -> callback.onError(exception));
            }
        });
    }

    private void generateInternal(List<PageItem> pageItems, Uri outputUri) throws IOException {
        if (pageItems == null || pageItems.isEmpty()) {
            throw new IllegalArgumentException("No images selected");
        }
        if (outputUri == null) {
            throw new IllegalArgumentException("Output Uri is required");
        }

        PdfDocument pdfDocument = new PdfDocument();
        try (OutputStream outputStream = openOutputStream(outputUri)) {
            for (int index = 0; index < pageItems.size(); index++) {
                throwIfInterrupted();

                PageItem pageItem = pageItems.get(index);
                ImageTransform imageTransform = imageOrientationReader.read(pageItem.getImageUri());
                boolean swapsDimensions = imageTransform.swapsDimensions() ^ pageItem.swapsDimensions();
                Bitmap bitmap = decodeBitmap(
                        pageItem.getImageUri(),
                        swapsDimensions ? CONTENT_HEIGHT_POINTS : CONTENT_WIDTH_POINTS,
                        swapsDimensions ? CONTENT_WIDTH_POINTS : CONTENT_HEIGHT_POINTS
                );
                try {
                    bitmap = ImageBitmapTransformer.applyTransform(bitmap, imageTransform);
                    bitmap = ImageBitmapTransformer.applyClockwiseRotation(
                            bitmap,
                            pageItem.getManualRotationDegrees()
                    );
                    PdfDocument.Page page = pdfDocument.startPage(
                            new PdfDocument.PageInfo.Builder(
                                    PAGE_WIDTH_POINTS,
                                    PAGE_HEIGHT_POINTS,
                                    index + 1
                            ).create()
                    );
                    try {
                        drawPage(page.getCanvas(), bitmap);
                    } finally {
                        pdfDocument.finishPage(page);
                    }
                } finally {
                    if (!bitmap.isRecycled()) {
                        bitmap.recycle();
                    }
                }
            }

            throwIfInterrupted();
            pdfDocument.writeTo(outputStream);
        } finally {
            pdfDocument.close();
        }
    }

    private OutputStream openOutputStream(Uri outputUri) throws IOException {
        OutputStream outputStream = contentResolver.openOutputStream(outputUri);
        if (outputStream == null) {
            throw new IOException("Unable to open output stream");
        }
        return outputStream;
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

    private void drawPage(Canvas canvas, Bitmap bitmap) {
        canvas.drawColor(Color.WHITE);

        ImagePlacementCalculator.PlacementRect placementRect =
                ImagePlacementCalculator.calculateCenteredFit(
                        bitmap.getWidth(),
                        bitmap.getHeight(),
                        PAGE_MARGIN_POINTS,
                        PAGE_MARGIN_POINTS,
                        CONTENT_WIDTH_POINTS,
                        CONTENT_HEIGHT_POINTS
                );

        RectF destination = new RectF(
                placementRect.getLeft(),
                placementRect.getTop(),
                placementRect.getRight(),
                placementRect.getBottom()
        );
        canvas.drawBitmap(bitmap, null, destination, bitmapPaint);
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

    private void throwIfInterrupted() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedIOException("PDF generation interrupted");
        }
    }
}
