package com.desperadoboi.imagetopdf.document.word;

public abstract class WordBlock {
    public enum Type {
        PARAGRAPH,
        TABLE,
        IMAGE,
        PAGE_BREAK
    }

    private final Type type;

    protected WordBlock(Type type) {
        this.type = type;
    }

    public final Type getType() {
        return type;
    }
}
