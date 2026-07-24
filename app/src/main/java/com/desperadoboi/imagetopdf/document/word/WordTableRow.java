package com.desperadoboi.imagetopdf.document.word;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class WordTableRow {
    private final List<WordTableCell> cells;
    private final int heightTwips;

    public WordTableRow(List<WordTableCell> cells, int heightTwips) {
        this.cells = Collections.unmodifiableList(new ArrayList<>(cells));
        this.heightTwips = Math.max(0, heightTwips);
    }

    public List<WordTableCell> getCells() { return cells; }
    public int getHeightTwips() { return heightTwips; }
}
