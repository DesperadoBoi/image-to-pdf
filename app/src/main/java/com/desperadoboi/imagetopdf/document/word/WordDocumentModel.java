package com.desperadoboi.imagetopdf.document.word;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class WordDocumentModel {
    private final List<WordBlock> blocks;
    private final List<WordSectionProperties> sections;
    private final int paragraphCount;
    private final int tableCount;
    private final int imageCount;

    public WordDocumentModel(
            List<WordBlock> blocks,
            List<WordSectionProperties> sections,
            int paragraphCount,
            int tableCount,
            int imageCount
    ) {
        this.blocks = Collections.unmodifiableList(new ArrayList<>(blocks));
        this.sections = Collections.unmodifiableList(new ArrayList<>(sections));
        this.paragraphCount = Math.max(0, paragraphCount);
        this.tableCount = Math.max(0, tableCount);
        this.imageCount = Math.max(0, imageCount);
    }

    public List<WordBlock> getBlocks() { return blocks; }
    public List<WordSectionProperties> getSections() { return sections; }
    public int getParagraphCount() { return paragraphCount; }
    public int getTableCount() { return tableCount; }
    public int getImageCount() { return imageCount; }
}
