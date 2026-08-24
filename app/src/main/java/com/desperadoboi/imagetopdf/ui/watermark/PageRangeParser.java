package com.desperadoboi.imagetopdf.ui.watermark;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public final class PageRangeParser {
    public Result parse(String input, int pageCount) {
        if (pageCount <= 0) return Result.invalid(Error.ABOVE_MAXIMUM);
        String value = input == null ? "" : input.trim();
        if (value.isEmpty()) return Result.invalid(Error.EMPTY);

        Set<Integer> pages = new TreeSet<>();
        String[] segments = value.split(",", -1);
        for (String rawSegment : segments) {
            String segment = rawSegment.trim();
            if (segment.isEmpty()) return Result.invalid(Error.MALFORMED);
            int dash = segment.indexOf('-');
            if (dash < 0) {
                Integer page = parsePositiveInteger(segment);
                Error error = validatePage(page, pageCount);
                if (error != null) return Result.invalid(error);
                pages.add(page - 1);
                continue;
            }
            if (dash == 0 || dash != segment.lastIndexOf('-')
                    || dash == segment.length() - 1) {
                return Result.invalid(Error.MALFORMED);
            }
            Integer start = parsePositiveInteger(segment.substring(0, dash).trim());
            Integer end = parsePositiveInteger(segment.substring(dash + 1).trim());
            Error startError = validatePage(start, pageCount);
            if (startError != null) return Result.invalid(startError);
            Error endError = validatePage(end, pageCount);
            if (endError != null) return Result.invalid(endError);
            if (start > end) return Result.invalid(Error.REVERSED_RANGE);
            for (int page = start; page <= end; page++) pages.add(page - 1);
        }
        if (pages.isEmpty()) return Result.invalid(Error.EMPTY);
        return Result.valid(new ArrayList<>(pages));
    }

    private Integer parsePositiveInteger(String value) {
        if (value.isEmpty()) return null;
        for (int index = 0; index < value.length(); index++) {
            if (!Character.isDigit(value.charAt(index))) return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Error validatePage(Integer page, int pageCount) {
        if (page == null) return Error.MALFORMED;
        if (page <= 0) return Error.NON_POSITIVE;
        return page > pageCount ? Error.ABOVE_MAXIMUM : null;
    }

    public enum Error {
        NONE,
        EMPTY,
        NON_POSITIVE,
        REVERSED_RANGE,
        ABOVE_MAXIMUM,
        MALFORMED
    }

    public static final class Result {
        private final List<Integer> zeroBasedPages;
        private final Error error;

        private Result(List<Integer> zeroBasedPages, Error error) {
            this.zeroBasedPages = zeroBasedPages;
            this.error = error;
        }

        private static Result valid(List<Integer> pages) {
            return new Result(
                    Collections.unmodifiableList(new ArrayList<>(pages)),
                    Error.NONE
            );
        }

        private static Result invalid(Error error) {
            return new Result(Collections.emptyList(), error);
        }

        public boolean isValid() { return error == Error.NONE; }
        public List<Integer> getZeroBasedPages() { return zeroBasedPages; }
        public Error getError() { return error; }

        public String normalized() {
            if (!isValid()) return "";
            StringBuilder builder = new StringBuilder();
            int index = 0;
            while (index < zeroBasedPages.size()) {
                int start = zeroBasedPages.get(index) + 1;
                int end = start;
                while (index + 1 < zeroBasedPages.size()
                        && zeroBasedPages.get(index + 1) + 1 == end + 1) {
                    index++;
                    end = zeroBasedPages.get(index) + 1;
                }
                if (builder.length() > 0) builder.append(',');
                builder.append(start);
                if (end != start) builder.append('-').append(end);
                index++;
            }
            return builder.toString();
        }
    }
}
