package com.desperadoboi.imagetopdf.util;

import java.util.Objects;

public final class PageCountFormatter {
    private PageCountFormatter() {
    }

    public static String format(int pageCount, Labels labels) {
        if (pageCount < 0) {
            throw new IllegalArgumentException("pageCount must be non-negative");
        }
        Objects.requireNonNull(labels, "labels are required");
        int mod100 = pageCount % 100;
        String word;
        if (mod100 >= 11 && mod100 <= 14) {
            word = labels.many;
        } else {
            int mod10 = pageCount % 10;
            if (mod10 == 1) {
                word = labels.one;
            } else if (mod10 >= 2 && mod10 <= 4) {
                word = labels.few;
            } else {
                word = labels.many;
            }
        }
        return pageCount + " " + word;
    }

    public static final class Labels {
        private final String one;
        private final String few;
        private final String many;

        public Labels(String one, String few, String many) {
            this.one = requireLabel(one, "one");
            this.few = requireLabel(few, "few");
            this.many = requireLabel(many, "many");
        }

        private static String requireLabel(String value, String name) {
            if (value == null || value.trim().isEmpty()) {
                throw new IllegalArgumentException(name + " label is required");
            }
            return value.trim();
        }
    }
}
