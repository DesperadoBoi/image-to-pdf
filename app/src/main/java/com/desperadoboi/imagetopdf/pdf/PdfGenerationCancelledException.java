package com.desperadoboi.imagetopdf.pdf;

import java.io.IOException;

public final class PdfGenerationCancelledException extends IOException {
    public PdfGenerationCancelledException() {
        super("PDF generation cancelled");
    }
}
