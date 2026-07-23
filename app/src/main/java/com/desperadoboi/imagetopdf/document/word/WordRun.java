package com.desperadoboi.imagetopdf.document.word;

public final class WordRun {
    private final String text;
    private final WordRunStyle style;
    private final String hyperlink;

    public WordRun(String text, WordRunStyle style, String hyperlink) {
        this.text = text == null ? "" : text;
        this.style = style == null ? WordRunStyle.defaults() : style;
        this.hyperlink = hyperlink;
    }

    public String getText() { return text; }
    public WordRunStyle getStyle() { return style; }
    public String getHyperlink() { return hyperlink; }
}
