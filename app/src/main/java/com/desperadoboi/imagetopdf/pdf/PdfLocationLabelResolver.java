package com.desperadoboi.imagetopdf.pdf;

import android.content.Context;
import android.content.pm.ProviderInfo;
import android.net.Uri;
import android.provider.DocumentsContract;

import com.desperadoboi.imagetopdf.R;

import java.util.Locale;

public final class PdfLocationLabelResolver {
    public static final String DOWNLOADS_AUTHORITY =
            "com.android.providers.downloads.documents";
    public static final String EXTERNAL_STORAGE_AUTHORITY =
            "com.android.externalstorage.documents";

    private PdfLocationLabelResolver() {
    }

    public static ResolvedLocation resolve(
            String authority,
            String documentId,
            String providerDisplayName
    ) {
        String normalizedAuthority = normalize(authority);
        String normalizedDocumentId = normalize(documentId).toLowerCase(Locale.ROOT);

        if (DOWNLOADS_AUTHORITY.equals(normalizedAuthority)) {
            return ResolvedLocation.downloads();
        }
        if (EXTERNAL_STORAGE_AUTHORITY.equals(normalizedAuthority)) {
            if (normalizedDocumentId.startsWith("primary:download")) {
                return ResolvedLocation.downloads();
            }
            if (normalizedDocumentId.startsWith("primary:documents")
                    || normalizedDocumentId.startsWith("home:documents")) {
                return ResolvedLocation.documents();
            }
            return ResolvedLocation.selectedFolder();
        }
        if (isDocumentsAuthority(normalizedAuthority)) {
            return ResolvedLocation.documents();
        }

        String normalizedProviderName = normalize(providerDisplayName);
        if (isHumanReadableProviderName(normalizedProviderName, normalizedAuthority)) {
            return ResolvedLocation.provider(normalizedProviderName);
        }
        return ResolvedLocation.selectedFolder();
    }

    public static String resolveLabel(Context context, Uri uri) {
        String authority = uri == null ? "" : uri.getAuthority();
        ResolvedLocation location = resolve(
                authority,
                readDocumentId(context, uri),
                readProviderDisplayName(context, authority)
        );
        if (location.getKind() == LocationKind.DOWNLOADS) {
            return context.getString(R.string.pdf_location_downloads);
        }
        if (location.getKind() == LocationKind.DOCUMENTS) {
            return context.getString(R.string.pdf_location_documents);
        }
        if (location.getKind() == LocationKind.PROVIDER) {
            return location.getProviderDisplayName();
        }
        return context.getString(R.string.pdf_location_selected_folder);
    }

    private static boolean isDocumentsAuthority(String authority) {
        return "com.android.providers.documents".equals(authority)
                || "com.android.documentsui.documents".equals(authority)
                || "com.android.providers.media.documents".equals(authority);
    }

    private static boolean isHumanReadableProviderName(String value, String authority) {
        if (value.isEmpty() || value.equalsIgnoreCase(authority)) {
            return false;
        }
        return value.contains(" ") || !value.contains(".");
    }

    private static String readDocumentId(Context context, Uri uri) {
        if (uri == null) {
            return "";
        }
        try {
            if (DocumentsContract.isDocumentUri(context, uri)) {
                return DocumentsContract.getDocumentId(uri);
            }
        } catch (RuntimeException ignored) {
            // A provider may expose a content Uri without a DocumentsContract-compatible ID.
        }
        return "";
    }

    private static String readProviderDisplayName(Context context, String authority) {
        if (authority == null || authority.trim().isEmpty()) {
            return "";
        }
        try {
            ProviderInfo providerInfo = context.getPackageManager()
                    .resolveContentProvider(authority, 0);
            if (providerInfo == null) {
                return "";
            }
            CharSequence label = providerInfo.loadLabel(context.getPackageManager());
            return label == null ? "" : label.toString();
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    public enum LocationKind {
        DOWNLOADS,
        DOCUMENTS,
        PROVIDER,
        SELECTED_FOLDER
    }

    public static final class ResolvedLocation {
        private final LocationKind kind;
        private final String providerDisplayName;

        private ResolvedLocation(LocationKind kind, String providerDisplayName) {
            this.kind = kind;
            this.providerDisplayName = providerDisplayName;
        }

        public static ResolvedLocation downloads() {
            return new ResolvedLocation(LocationKind.DOWNLOADS, "");
        }

        public static ResolvedLocation documents() {
            return new ResolvedLocation(LocationKind.DOCUMENTS, "");
        }

        public static ResolvedLocation provider(String displayName) {
            return new ResolvedLocation(LocationKind.PROVIDER, displayName);
        }

        public static ResolvedLocation selectedFolder() {
            return new ResolvedLocation(LocationKind.SELECTED_FOLDER, "");
        }

        public LocationKind getKind() {
            return kind;
        }

        public String getProviderDisplayName() {
            return providerDisplayName;
        }
    }
}
