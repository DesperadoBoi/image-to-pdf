package com.desperadoboi.imagetopdf.model;

import java.util.Objects;

public final class PdfExportSummary {
    private static final String SEPARATOR = " \u00b7 ";

    private final PageSizeMode pageSize;
    private final PdfOrientationMode orientation;
    private final MarginPreset margins;

    public PdfExportSummary(
            PageSizeMode pageSize,
            PdfOrientationMode orientation,
            MarginPreset margins
    ) {
        this.pageSize = Objects.requireNonNull(pageSize, "pageSize is required");
        this.orientation = Objects.requireNonNull(orientation, "orientation is required");
        this.margins = Objects.requireNonNull(margins, "margins is required");
    }

    public static PdfExportSummary from(PdfExportDraft draft) {
        Objects.requireNonNull(draft, "draft is required");
        return new PdfExportSummary(
                draft.getPageSize(),
                draft.getOrientation(),
                draft.getMargins()
        );
    }

    public String format(Labels labels) {
        Objects.requireNonNull(labels, "labels are required");
        return labels.pageSize(pageSize)
                + SEPARATOR
                + labels.orientation(orientation)
                + SEPARATOR
                + labels.margins(margins);
    }

    public static final class Labels {
        private final String imagePage;
        private final String a4Page;
        private final String autoOrientation;
        private final String portraitOrientation;
        private final String landscapeOrientation;
        private final String noMargins;
        private final String smallMargins;
        private final String standardMargins;

        public Labels(
                String imagePage,
                String a4Page,
                String autoOrientation,
                String portraitOrientation,
                String landscapeOrientation,
                String noMargins,
                String smallMargins,
                String standardMargins
        ) {
            this.imagePage = requireLabel(imagePage, "imagePage");
            this.a4Page = requireLabel(a4Page, "a4Page");
            this.autoOrientation = requireLabel(autoOrientation, "autoOrientation");
            this.portraitOrientation = requireLabel(portraitOrientation, "portraitOrientation");
            this.landscapeOrientation = requireLabel(
                    landscapeOrientation,
                    "landscapeOrientation"
            );
            this.noMargins = requireLabel(noMargins, "noMargins");
            this.smallMargins = requireLabel(smallMargins, "smallMargins");
            this.standardMargins = requireLabel(standardMargins, "standardMargins");
        }

        private String pageSize(PageSizeMode value) {
            return value == PageSizeMode.IMAGE ? imagePage : a4Page;
        }

        private String orientation(PdfOrientationMode value) {
            if (value == PdfOrientationMode.PORTRAIT) {
                return portraitOrientation;
            }
            if (value == PdfOrientationMode.LANDSCAPE) {
                return landscapeOrientation;
            }
            return autoOrientation;
        }

        private String margins(MarginPreset value) {
            if (value == MarginPreset.NONE) {
                return noMargins;
            }
            if (value == MarginPreset.SMALL) {
                return smallMargins;
            }
            return standardMargins;
        }

        private static String requireLabel(String value, String name) {
            if (value == null || value.trim().isEmpty()) {
                throw new IllegalArgumentException(name + " is required");
            }
            return value;
        }
    }
}
