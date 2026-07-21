package com.desperadoboi.imagetopdf.pdf;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import com.desperadoboi.imagetopdf.model.PdfResult;

import java.util.Objects;

public final class PdfResultMetadataReader {
    private final ContentResolver contentResolver;

    public PdfResultMetadataReader(ContentResolver contentResolver) {
        this.contentResolver = Objects.requireNonNull(
                contentResolver,
                "contentResolver is required"
        );
    }

    public PdfResult read(PdfResult fallback) {
        Objects.requireNonNull(fallback, "fallback is required");
        Uri uri = fallback.getUri();
        String displayName = fallback.getDisplayName();
        String locationLabel = buildLocationLabel(uri, fallback.getLocationLabel());
        Long sizeBytes = null;

        try (Cursor cursor = contentResolver.query(
                uri,
                new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE},
                null,
                null,
                null
        )) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameColumn >= 0 && !cursor.isNull(nameColumn)) {
                    String queriedName = cursor.getString(nameColumn);
                    if (queriedName != null && !queriedName.trim().isEmpty()) {
                        displayName = queriedName.trim();
                    }
                }
                int sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) {
                    long queriedSize = cursor.getLong(sizeColumn);
                    if (queriedSize >= 0L) {
                        sizeBytes = queriedSize;
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // Provider metadata is optional; the saved Uri remains usable for Open and Share.
        }

        if (sizeBytes != null) {
            return fallback.withKnownMetadata(displayName, sizeBytes, locationLabel);
        }
        return fallback.withDisplayNameAndLocation(displayName, locationLabel);
    }

    private String buildLocationLabel(Uri uri, String fallbackLabel) {
        if (fallbackLabel != null && !fallbackLabel.trim().isEmpty()) {
            return fallbackLabel.trim();
        }
        String authority = uri.getAuthority();
        return authority == null ? "" : authority.trim();
    }
}
