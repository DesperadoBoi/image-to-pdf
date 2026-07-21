package com.desperadoboi.imagetopdf.pdf;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import com.desperadoboi.imagetopdf.model.PdfResult;

import java.io.IOException;
import java.util.Objects;

public final class PdfResultMetadataReader {
    private final Context applicationContext;
    private final ContentResolver contentResolver;

    public PdfResultMetadataReader(Context context) {
        applicationContext = Objects.requireNonNull(context, "context is required")
                .getApplicationContext();
        contentResolver = applicationContext.getContentResolver();
    }

    public PdfResult read(PdfResult fallback) {
        Objects.requireNonNull(fallback, "fallback is required");
        Uri uri = fallback.getUri();
        Metadata queriedMetadata = queryMetadata(uri);
        String displayName = queriedMetadata.displayName.isEmpty()
                ? fallback.getDisplayName()
                : queriedMetadata.displayName;
        Long statSize = queriedMetadata.sizeBytes == null
                || queriedMetadata.sizeBytes == 0L
                ? readStatSize(uri)
                : null;
        Long sizeBytes = resolveSize(queriedMetadata.sizeBytes, statSize, fallback);
        String locationLabel = PdfLocationLabelResolver.resolveLabel(applicationContext, uri);

        if (sizeBytes != null) {
            return fallback.withKnownMetadata(displayName, sizeBytes, locationLabel);
        }
        return fallback.withDisplayNameAndLocation(displayName, locationLabel);
    }

    private Metadata queryMetadata(Uri uri) {
        String displayName = "";
        Long sizeBytes = null;
        try (Cursor cursor = contentResolver.query(
                uri,
                new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE},
                null,
                null,
                null
        )) {
            if (cursor == null || !cursor.moveToFirst()) {
                return new Metadata(displayName, sizeBytes);
            }
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
        } catch (RuntimeException ignored) {
            // Provider metadata is optional; the saved Uri remains usable for Open and Share.
        }
        return new Metadata(displayName, sizeBytes);
    }

    private Long readStatSize(Uri uri) {
        try (ParcelFileDescriptor descriptor = contentResolver.openFileDescriptor(uri, "r")) {
            if (descriptor == null) {
                return null;
            }
            long statSize = descriptor.getStatSize();
            return statSize >= 0L ? statSize : null;
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    private Long resolveSize(Long queriedSize, Long statSize, PdfResult fallback) {
        if (queriedSize != null && queriedSize > 0L) {
            return queriedSize;
        }
        if (statSize != null && statSize > 0L) {
            return statSize;
        }
        if (fallback.hasKnownSize() && fallback.getSizeBytes() > 0L) {
            return fallback.getSizeBytes();
        }
        if (queriedSize != null) {
            return queriedSize;
        }
        if (statSize != null) {
            return statSize;
        }
        if (fallback.hasKnownSize()) {
            return fallback.getSizeBytes();
        }
        return null;
    }

    private static final class Metadata {
        private final String displayName;
        private final Long sizeBytes;

        private Metadata(String displayName, Long sizeBytes) {
            this.displayName = displayName;
            this.sizeBytes = sizeBytes;
        }
    }
}
