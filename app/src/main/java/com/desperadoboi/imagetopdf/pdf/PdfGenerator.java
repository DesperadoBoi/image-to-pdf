package com.desperadoboi.imagetopdf.pdf;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;

import com.desperadoboi.imagetopdf.image.ImageBitmapTransformer;
import com.desperadoboi.imagetopdf.image.ImageOrientationReader;
import com.desperadoboi.imagetopdf.image.ImageTransform;
import com.desperadoboi.imagetopdf.model.ImagePlacementMode;
import com.desperadoboi.imagetopdf.model.PageItem;
import com.desperadoboi.imagetopdf.model.PageSizeMode;
import com.desperadoboi.imagetopdf.model.PdfOptions;
import com.desperadoboi.imagetopdf.util.ImagePlacementCalculator;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

public class PdfGenerator {
    private final ContentResolver contentResolver;
    private final ImageOrientationReader imageOrientationReader;
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

    public PdfGenerator(ContentResolver contentResolver) {
        this.contentResolver = contentResolver;
        this.imageOrientationReader = new ImageOrientationReader(contentResolver);
    }

    public void generate(
            List<PageItem> pageItems,
            PdfOptions pdfOptions,
            Uri outputUri,
            Executor backgroundExecutor,
            Executor callbackExecutor,
            PdfGenerationCallback callback
    ) {
        Objects.requireNonNull(pdfOptions, "pdfOptions is required");
        backgroundExecutor.execute(() -> {
            try {
                generateInternal(pageItems, pdfOptions, outputUri);
                callbackExecutor.execute(() -> callback.onSuccess(outputUri));
            } catch (Exception exception) {
                callbackExecutor.execute(() -> callback.onError(exception));
            }
        });
    }

    private void generateInternal(
            List<PageItem> pageItems,
            PdfOptions pdfOptions,
            Uri outputUri
    ) throws IOException {
        if (pageItems == null || pageItems.isEmpty()) {
            throw new IllegalArgumentException("No images selected");
        }
        if (pdfOptions == null) {
            throw new IllegalArgumentException("PDF options are required");
        }
        if (outputUri == null) {
            throw new IllegalArgumentException("Output Uri is required");
        }

        PdfDocument pdfDocument = new PdfDocument();
        try (OutputStream outputStream = openOutputStream(outputUri)) {
            for (int index = 0; index < pageItems.size(); index++) {
                throwIfInterrupted();

                PageItem pageItem = pageItems.get(index);
                ImageBounds rawBounds = readImageBounds(pageItem.getImageUri());
                ImageTransform imageTransform = imageOrientationReader.read(pageItem.getImageUri());
                ImageBounds orientedBounds = rawBounds.swapIf(imageTransform.swapsDimensions());
                ImageBounds finalBounds = orientedBounds.swapIf(pageItem.swapsDimensions());
                PdfPageLayout pageLayout = PdfPageLayoutCalculator.calculate(
                        pdfOptions,
                        finalBounds.getWidth(),
                        finalBounds.getHeight()
                );
                ImagePlacementMode placementMode = resolvePlacementMode(pdfOptions);
                ImageBounds sampleTarget = calculateSampleTarget(
                        finalBounds,
                        pageLayout,
                        placementMode
                );
                boolean totalTransformSwapsDimensions =
                        imageTransform.swapsDimensions() ^ pageItem.swapsDimensions();
                Bitmap bitmap = decodeBitmap(
                        pageItem.getImageUri(),
                        rawBounds,
                        totalTransformSwapsDimensions
                                ? sampleTarget.getHeight()
                                : sampleTarget.getWidth(),
                        totalTransformSwapsDimensions
                                ? sampleTarget.getWidth()
                                : sampleTarget.getHeight()
                );
                try {
                    bitmap = ImageBitmapTransformer.applyTransform(bitmap, imageTransform);
                    bitmap = ImageBitmapTransformer.applyClockwiseRotation(
                            bitmap,
                            pageItem.getManualRotationDegrees()
                    );
                    PdfDocument.Page page = pdfDocument.startPage(
                            new PdfDocument.PageInfo.Builder(
                                    pageLayout.getPageWidth(),
                                    pageLayout.getPageHeight(),
                                    index + 1
                            ).create()
                    );
                    try {
                        drawPage(page.getCanvas(), bitmap, pageLayout, placementMode);
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

    private ImagePlacementMode resolvePlacementMode(PdfOptions pdfOptions) {
        if (pdfOptions.getPageSizeMode() == PageSizeMode.IMAGE) {
            return ImagePlacementMode.FIT;
        }
        return pdfOptions.getImagePlacementMode();
    }

    private ImageBounds calculateSampleTarget(
            ImageBounds finalBounds,
            PdfPageLayout pageLayout,
            ImagePlacementMode placementMode
    ) {
        ImagePlacementCalculator.ImageDrawPlan drawPlan =
                ImagePlacementCalculator.calculateDrawPlan(
                        finalBounds.getWidth(),
                        finalBounds.getHeight(),
                        pageLayout.getContentLeft(),
                        pageLayout.getContentTop(),
                        pageLayout.getContentWidth(),
                        pageLayout.getContentHeight(),
                        placementMode
                );
        ImagePlacementCalculator.PlacementRect destination = drawPlan.getDestinationRect();
        return new ImageBounds(
                Math.max(1, (int) Math.ceil(destination.getWidth())),
                Math.max(1, (int) Math.ceil(destination.getHeight()))
        );
    }

    private OutputStream openOutputStream(Uri outputUri) throws IOException {
        OutputStream outputStream = contentResolver.openOutputStream(outputUri);
        if (outputStream == null) {
            throw new IOException("Unable to open output stream");
        }
        return outputStream;
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
        decodeOptions.inSampleSize = calculateInSampleSize(
                bounds.getWidth(),
                bounds.getHeight(),
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

    private void drawPage(
            Canvas canvas,
            Bitmap bitmap,
            PdfPageLayout pageLayout,
            ImagePlacementMode placementMode
    ) {
        canvas.drawColor(Color.WHITE);

        ImagePlacementCalculator.ImageDrawPlan drawPlan =
                ImagePlacementCalculator.calculateDrawPlan(
                        bitmap.getWidth(),
                        bitmap.getHeight(),
                        pageLayout.getContentLeft(),
                        pageLayout.getContentTop(),
                        pageLayout.getContentWidth(),
                        pageLayout.getContentHeight(),
                        placementMode
                );

        ImagePlacementCalculator.PlacementRect sourceRect = drawPlan.getSourceRect();
        ImagePlacementCalculator.PlacementRect destinationRect = drawPlan.getDestinationRect();
        Rect source = new Rect(
                Math.max(0, (int) Math.floor(sourceRect.getLeft())),
                Math.max(0, (int) Math.floor(sourceRect.getTop())),
                Math.min(bitmap.getWidth(), (int) Math.ceil(sourceRect.getRight())),
                Math.min(bitmap.getHeight(), (int) Math.ceil(sourceRect.getBottom()))
        );
        RectF destination = new RectF(
                destinationRect.getLeft(),
                destinationRect.getTop(),
                destinationRect.getRight(),
                destinationRect.getBottom()
        );
        canvas.drawBitmap(bitmap, source, destination, bitmapPaint);
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
        while ((sourceWidth / (sampleSize * 2)) >= targetWidth
                && (sourceHeight / (sampleSize * 2)) >= targetHeight) {
            sampleSize *= 2;
        }
        return Math.max(1, sampleSize);
    }

    private void throwIfInterrupted() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedIOException("PDF generation interrupted");
        }
    }

    private static final class ImageBounds {
        private final int width;
        private final int height;

        private ImageBounds(int width, int height) {
            if (width <= 0 || height <= 0) {
                throw new IllegalArgumentException("Image dimensions must be positive");
            }
            this.width = width;
            this.height = height;
        }

        private int getWidth() {
            return width;
        }

        private int getHeight() {
            return height;
        }

        private ImageBounds swapIf(boolean shouldSwap) {
            if (!shouldSwap) {
                return this;
            }
            return new ImageBounds(height, width);
        }
    }
}
