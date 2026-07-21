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
import com.desperadoboi.imagetopdf.image.BitmapSampleSizeCalculator;
import com.desperadoboi.imagetopdf.image.EditedImageGeometryCalculator;
import com.desperadoboi.imagetopdf.image.PageBitmapProcessor;
import com.desperadoboi.imagetopdf.image.PageProcessingMode;
import com.desperadoboi.imagetopdf.image.SourceResolutionCalculator;
import com.desperadoboi.imagetopdf.model.ImagePlacementMode;
import com.desperadoboi.imagetopdf.model.PageItem;
import com.desperadoboi.imagetopdf.model.PageSizeMode;
import com.desperadoboi.imagetopdf.model.PdfOptions;
import com.desperadoboi.imagetopdf.util.ImagePlacementCalculator;

import java.io.IOException;
import java.io.InputStream;
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
            CancellationToken cancellationToken,
            Executor backgroundExecutor,
            Executor callbackExecutor,
            PdfGenerationCallback callback
    ) {
        Objects.requireNonNull(pdfOptions, "pdfOptions is required");
        Objects.requireNonNull(cancellationToken, "cancellationToken is required");
        backgroundExecutor.execute(() -> {
            try {
                generateInternal(pageItems, pdfOptions, outputUri, cancellationToken, callbackExecutor, callback);
                callbackExecutor.execute(() -> callback.onSuccess(outputUri));
            } catch (PdfGenerationCancelledException exception) {
                deletePartialOutput(outputUri);
                callbackExecutor.execute(callback::onCancelled);
            } catch (Exception exception) {
                deletePartialOutput(outputUri);
                callbackExecutor.execute(() -> callback.onError(exception));
            }
        });
    }

    private void generateInternal(
            List<PageItem> pageItems,
            PdfOptions pdfOptions,
            Uri outputUri,
            CancellationToken cancellationToken,
            Executor callbackExecutor,
            PdfGenerationCallback callback
    ) throws IOException, PdfGenerationCancelledException {
        if (pageItems == null || pageItems.isEmpty()) {
            throw new IllegalArgumentException("No images selected");
        }
        if (pdfOptions == null) {
            throw new IllegalArgumentException("PDF options are required");
        }
        if (outputUri == null) {
            throw new IllegalArgumentException("Output Uri is required");
        }

        int totalPages = pageItems.size();
        notifyProgress(callbackExecutor, callback, 0, totalPages);
        PdfDocument pdfDocument = new PdfDocument();
        try (OutputStream outputStream = openOutputStream(outputUri)) {
            for (int index = 0; index < totalPages; index++) {
                throwIfCancelled(cancellationToken);

                PageItem pageItem = pageItems.get(index);
                ImageBounds rawBounds = readImageBounds(pageItem.getImageUri());
                ImageTransform imageTransform = imageOrientationReader.read(pageItem.getImageUri());
                boolean totalTransformSwapsDimensions =
                        imageTransform.swapsDimensions() ^ pageItem.swapsDimensions();
                ImageBounds orientedBounds = rawBounds.swapIf(totalTransformSwapsDimensions);
                EditedImageGeometryCalculator.Dimensions editedDimensions =
                        EditedImageGeometryCalculator.calculate(
                                orientedBounds.getWidth(),
                                orientedBounds.getHeight(),
                                pageItem.getEditSpec(),
                                PageProcessingMode.FINAL
                        );
                ImageBounds finalBounds = new ImageBounds(
                        editedDimensions.getWidth(),
                        editedDimensions.getHeight()
                );
                PdfPageLayout pageLayout = PdfPageLayoutCalculator.calculate(
                        pdfOptions,
                        finalBounds.getWidth(),
                        finalBounds.getHeight()
                );
                ImagePlacementMode placementMode = resolvePlacementMode(pdfOptions);
                RasterTarget rasterTarget = RasterTargetCalculator.calculate(
                        finalBounds.getWidth(),
                        finalBounds.getHeight(),
                        pageLayout.getContentLeft(),
                        pageLayout.getContentTop(),
                        pageLayout.getContentWidth(),
                        pageLayout.getContentHeight(),
                        placementMode,
                        pdfOptions.getQualityProfile().getTargetDpi()
                );
                EditedImageGeometryCalculator.Dimensions sourceTarget =
                        SourceResolutionCalculator.calculateForOutputTarget(
                                orientedBounds.getWidth(),
                                orientedBounds.getHeight(),
                                pageItem.getEditSpec(),
                                PageProcessingMode.FINAL,
                                rasterTarget.getTargetWidthPixels(),
                                rasterTarget.getTargetHeightPixels()
                        );
                Bitmap bitmap = decodeBitmap(
                        pageItem.getImageUri(),
                        rawBounds,
                        totalTransformSwapsDimensions
                                ? sourceTarget.getHeight()
                                : sourceTarget.getWidth(),
                        totalTransformSwapsDimensions
                                ? sourceTarget.getWidth()
                                : sourceTarget.getHeight()
                );
                try {
                    throwIfCancelled(cancellationToken);
                    bitmap = PageBitmapProcessor.process(
                            bitmap,
                            imageTransform,
                            pageItem.getManualRotationDegrees(),
                            pageItem.getEditSpec(),
                            PageProcessingMode.FINAL
                    );
                    throwIfCancelled(cancellationToken);
                    bitmap = ImageBitmapTransformer.scaleDownToFit(
                            bitmap,
                            rasterTarget.getTargetWidthPixels(),
                            rasterTarget.getTargetHeightPixels()
                    );
                    throwIfCancelled(cancellationToken);
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
                    notifyProgress(callbackExecutor, callback, index + 1, totalPages);
                } finally {
                    if (!bitmap.isRecycled()) {
                        bitmap.recycle();
                    }
                }
            }

            throwIfCancelled(cancellationToken);
            pdfDocument.writeTo(outputStream);
            throwIfCancelled(cancellationToken);
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
        decodeOptions.inSampleSize = BitmapSampleSizeCalculator.calculate(
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

    private void notifyProgress(
            Executor callbackExecutor,
            PdfGenerationCallback callback,
            int completedPages,
            int totalPages
    ) {
        int safeCompletedPages = Math.max(0, Math.min(completedPages, totalPages));
        callbackExecutor.execute(() -> callback.onProgress(safeCompletedPages, totalPages));
    }

    private void deletePartialOutput(Uri outputUri) {
        if (outputUri == null) {
            return;
        }
        try {
            contentResolver.delete(outputUri, null, null);
        } catch (RuntimeException exception) {
            // Some document providers do not support deleting a just-created document.
        }
    }

    private void throwIfCancelled(CancellationToken cancellationToken)
            throws PdfGenerationCancelledException {
        if (cancellationToken.isCancelled() || Thread.currentThread().isInterrupted()) {
            throw new PdfGenerationCancelledException();
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
