package com.desperadoboi.imagetopdf.document.word;

import java.io.IOException;

public final class WordParseException extends IOException {
    public enum Reason {
        CORRUPTED,
        TOO_LARGE,
        UNSUPPORTED,
        ENCRYPTED,
        CANCELLED
    }

    private final Reason reason;

    public WordParseException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public WordParseException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
