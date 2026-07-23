package com.desperadoboi.imagetopdf.document.word;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class WordSectionProperties {
    private final int pageWidthTwips;
    private final int pageHeightTwips;
    private final int marginTopTwips;
    private final int marginRightTwips;
    private final int marginBottomTwips;
    private final int marginLeftTwips;
    private final List<String> headerRelationshipIds;
    private final List<String> footerRelationshipIds;

    public WordSectionProperties(
            int pageWidthTwips,
            int pageHeightTwips,
            int marginTopTwips,
            int marginRightTwips,
            int marginBottomTwips,
            int marginLeftTwips,
            List<String> headerRelationshipIds,
            List<String> footerRelationshipIds
    ) {
        this.pageWidthTwips = Math.max(0, pageWidthTwips);
        this.pageHeightTwips = Math.max(0, pageHeightTwips);
        this.marginTopTwips = Math.max(0, marginTopTwips);
        this.marginRightTwips = Math.max(0, marginRightTwips);
        this.marginBottomTwips = Math.max(0, marginBottomTwips);
        this.marginLeftTwips = Math.max(0, marginLeftTwips);
        this.headerRelationshipIds = Collections.unmodifiableList(
                new ArrayList<>(headerRelationshipIds)
        );
        this.footerRelationshipIds = Collections.unmodifiableList(
                new ArrayList<>(footerRelationshipIds)
        );
    }

    public int getPageWidthTwips() { return pageWidthTwips; }
    public int getPageHeightTwips() { return pageHeightTwips; }
    public int getMarginTopTwips() { return marginTopTwips; }
    public int getMarginRightTwips() { return marginRightTwips; }
    public int getMarginBottomTwips() { return marginBottomTwips; }
    public int getMarginLeftTwips() { return marginLeftTwips; }
    public List<String> getHeaderRelationshipIds() { return headerRelationshipIds; }
    public List<String> getFooterRelationshipIds() { return footerRelationshipIds; }
}
