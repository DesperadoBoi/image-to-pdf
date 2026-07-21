package com.desperadoboi.imagetopdf.ui.result;

import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Intent;

import com.desperadoboi.imagetopdf.model.PdfResult;

import java.util.Objects;

public final class PdfIntentFactory {
    private static final String PDF_MIME_TYPE = "application/pdf";

    private PdfIntentFactory() {
    }

    public static Intent createShareIntent(
            ContentResolver contentResolver,
            PdfResult result,
            String chooserTitle
    ) {
        Objects.requireNonNull(contentResolver, "contentResolver is required");
        Objects.requireNonNull(result, "result is required");
        Intent sendIntent = new Intent(Intent.ACTION_SEND);
        sendIntent.setType(PDF_MIME_TYPE);
        sendIntent.putExtra(Intent.EXTRA_STREAM, result.getUri());
        grantReadPermission(contentResolver, sendIntent, result);

        Intent chooser = Intent.createChooser(sendIntent, chooserTitle);
        chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        chooser.setClipData(sendIntent.getClipData());
        return chooser;
    }

    public static Intent createOpenIntent(
            ContentResolver contentResolver,
            PdfResult result
    ) {
        Objects.requireNonNull(contentResolver, "contentResolver is required");
        Objects.requireNonNull(result, "result is required");
        Intent viewIntent = new Intent(Intent.ACTION_VIEW);
        viewIntent.setDataAndType(result.getUri(), PDF_MIME_TYPE);
        grantReadPermission(contentResolver, viewIntent, result);
        return viewIntent;
    }

    private static void grantReadPermission(
            ContentResolver contentResolver,
            Intent intent,
            PdfResult result
    ) {
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setClipData(ClipData.newUri(
                contentResolver,
                result.getDisplayName(),
                result.getUri()
        ));
    }
}
