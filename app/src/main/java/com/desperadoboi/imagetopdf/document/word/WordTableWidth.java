package com.desperadoboi.imagetopdf.document.word;

public final class WordTableWidth {
    public enum Type {
        AUTO,
        DXA,
        PERCENT,
        NIL
    }

    public static final WordTableWidth AUTO = new WordTableWidth(Type.AUTO, 0);

    private final Type type;
    private final int value;

    public WordTableWidth(Type type, int value) {
        this.type = type == null ? Type.AUTO : type;
        this.value = Math.max(0, value);
    }

    public Type getType() {
        return type;
    }

    public int getValue() {
        return value;
    }
}
