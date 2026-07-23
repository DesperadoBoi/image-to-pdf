package com.desperadoboi.imagetopdf.document.word;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class WordParagraph extends WordBlock {
    public enum Role {
        BODY,
        HEADER,
        FOOTER,
        FOOTNOTE,
        ENDNOTE
    }

    private final List<WordRun> runs;
    private final WordParagraphStyle style;
    private final String listMarker;
    private final Role role;

    public WordParagraph(
            List<WordRun> runs,
            WordParagraphStyle style,
            String listMarker,
            Role role
    ) {
        super(Type.PARAGRAPH);
        this.runs = Collections.unmodifiableList(new ArrayList<>(runs));
        this.style = style == null ? WordParagraphStyle.defaults() : style;
        this.listMarker = listMarker == null ? "" : listMarker;
        this.role = role == null ? Role.BODY : role;
    }

    public List<WordRun> getRuns() { return runs; }
    public WordParagraphStyle getStyle() { return style; }
    public String getListMarker() { return listMarker; }
    public Role getRole() { return role; }

    public String getPlainText() {
        StringBuilder text = new StringBuilder(listMarker);
        for (WordRun run : runs) text.append(run.getText());
        return text.toString();
    }
}
