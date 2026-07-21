package com.desperadoboi.imagetopdf.ui.smartscan;

import com.desperadoboi.imagetopdf.util.PageCountFormatter;

import java.util.Locale;
import java.util.Objects;

final class SmartScanDoneFormatter {
    private SmartScanDoneFormatter() {
    }

    static State format(
            int pageCount,
            Locale locale,
            String labelFormat,
            String contentDescriptionFormat,
            PageCountFormatter.Labels pageCountLabels
    ) {
        if (pageCount < 0) {
            throw new IllegalArgumentException("pageCount must be non-negative");
        }
        if (pageCount == 0) {
            return State.hidden();
        }
        Objects.requireNonNull(locale, "locale is required");
        Objects.requireNonNull(labelFormat, "labelFormat is required");
        Objects.requireNonNull(
                contentDescriptionFormat,
                "contentDescriptionFormat is required"
        );
        String pageCountText = PageCountFormatter.format(pageCount, pageCountLabels);
        return new State(
                true,
                String.format(locale, labelFormat, pageCount),
                String.format(locale, contentDescriptionFormat, pageCountText)
        );
    }

    static final class State {
        private static final State HIDDEN = new State(false, "", "");

        private final boolean visible;
        private final String label;
        private final String contentDescription;

        private State(boolean visible, String label, String contentDescription) {
            this.visible = visible;
            this.label = label;
            this.contentDescription = contentDescription;
        }

        private static State hidden() {
            return HIDDEN;
        }

        boolean isVisible() {
            return visible;
        }

        String getLabel() {
            return label;
        }

        String getContentDescription() {
            return contentDescription;
        }
    }
}
