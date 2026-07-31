package com.desperadoboi.imagetopdf.ui.viewer;

final class DocxHtmlEscaper {
    private DocxHtmlEscaper() {
    }

    static String text(String value) {
        if (value == null || value.isEmpty()) return "";
        StringBuilder escaped = new StringBuilder(value.length() + 16);
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '&':
                    escaped.append("&amp;");
                    break;
                case '<':
                    escaped.append("&lt;");
                    break;
                case '>':
                    escaped.append("&gt;");
                    break;
                case '"':
                    escaped.append("&quot;");
                    break;
                case '\'':
                    escaped.append("&#39;");
                    break;
                default:
                    escaped.append(character);
                    break;
            }
        }
        return escaped.toString();
    }

    static String attribute(String value) {
        return text(value);
    }
}
