package com.desperadoboi.imagetopdf.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ImageImportResult {
    public static final int NO_POSITION = -1;

    private static final ImageImportResult NO_CHANGE = new ImageImportResult(
            NO_POSITION,
            0,
            Collections.emptyList(),
            false
    );

    private final int firstInsertedPosition;
    private final int insertedCount;
    private final List<String> capturedFileNamesToDelete;

    public ImageImportResult(
            int firstInsertedPosition,
            int insertedCount,
            List<String> capturedFileNamesToDelete
    ) {
        this(firstInsertedPosition, insertedCount, capturedFileNamesToDelete, true);
    }

    private ImageImportResult(
            int firstInsertedPosition,
            int insertedCount,
            List<String> capturedFileNamesToDelete,
            boolean validate
    ) {
        if (validate && insertedCount <= 0) {
            throw new IllegalArgumentException("insertedCount must be positive");
        }
        if (validate && firstInsertedPosition < 0) {
            throw new IllegalArgumentException("firstInsertedPosition must not be negative");
        }
        this.firstInsertedPosition = firstInsertedPosition;
        this.insertedCount = insertedCount;
        this.capturedFileNamesToDelete = Collections.unmodifiableList(
                new ArrayList<>(capturedFileNamesToDelete)
        );
    }

    public static ImageImportResult noChange() {
        return NO_CHANGE;
    }

    public boolean hasChanges() {
        return insertedCount > 0;
    }

    public int getFirstInsertedPosition() {
        return firstInsertedPosition;
    }

    public int getInsertedCount() {
        return insertedCount;
    }

    public List<String> getCapturedFileNamesToDelete() {
        return capturedFileNamesToDelete;
    }
}
