package com.desperadoboi.imagetopdf.document.text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TextPreview {
    private final List<String> lines;
    private final boolean truncated;

    public TextPreview(List<String> lines, boolean truncated) {
        this.lines = Collections.unmodifiableList(new ArrayList<>(lines));
        this.truncated = truncated;
    }

    public List<String> getLines() { return lines; }
    public boolean isTruncated() { return truncated; }
}
