package com.desperadoboi.imagetopdf.document;

import java.io.IOException;

public class DocumentLoadException extends IOException {
    public enum Reason {
        PERMISSION_LOST,
        TOO_LARGE,
        CORRUPTED,
        CANCELLED,
        UNREADABLE
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
