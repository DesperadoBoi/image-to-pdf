package com.desperadoboi.imagetopdf.document.spreadsheet;

import java.io.IOException;

public final class XlsxParseException extends IOException {
    public enum Reason {
        CORRUPTED,
        TOO_LARGE,
        UNSUPPORTED
    }

    private final Reason reason;

    public XlsxParseException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public XlsxParseException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
