package com.desperadoboi.imagetopdf.document;

import android.net.Uri;

import java.io.File;

public final class IncomingDocument {
    private final Uri sourceUri;
    private final String displayName;
    private final String sourceMimeType;
    private final long sizeBytes;
    private final DocumentType documentType;
    private final File cachedFile;

    public IncomingDocument(
            Uri sourceUri,
            String displayName,
            String sourceMimeType,
            long sizeBytes,
            DocumentType documentType,
            File cachedFile
    ) {
        this.sourceUri = sourceUri;
        this.displayName = displayName;
        this.sourceMimeType = sourceMimeType;
        this.sizeBytes = sizeBytes;
        this.documentType = documentType;
        this.cachedFile = cachedFile;
    }

    public Uri getSourceUri() { return sourceUri; }
    public String getDisplayName() { return displayName; }
    public String getSourceMimeType() { return sourceMimeType; }
    public long getSizeBytes() { return sizeBytes; }
    public DocumentType getDocumentType() { return documentType; }
    public File getCachedFile() { return cachedFile; }
}
