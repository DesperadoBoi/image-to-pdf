package com.desperadoboi.imagetopdf.document;

import java.io.IOException;

public class DocumentLoadException extends IOException {
    public enum Reason {
        PERMISSION_LOST,
        TOO_LARGE,
        CORRUPTED,
        ENCRYPTED,
        UNSUPPORTED,
        SPREADSHEET_TOO_LARGE,
        SPREADSHEET_CORRUPTED,
        SPREADSHEET_ENCRYPTED,
        SPREADSHEET_ACTIVE_CONTENT,
        DOCX_TOO_LARGE,
        DOCX_CORRUPTED,
        DOCX_ENCRYPTED,
        DOCX_UNSUPPORTED,
        CANCELLED,
        PROVIDER_UNREADABLE
    }

    private final Reason reason;

    public DocumentLoadException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public DocumentLoadException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
