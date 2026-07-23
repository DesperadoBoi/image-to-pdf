package com.desperadoboi.imagetopdf.document.word;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

public interface WordDocumentParser {
    WordDocumentModel parse(File file, AtomicBoolean cancelled) throws IOException;
}
