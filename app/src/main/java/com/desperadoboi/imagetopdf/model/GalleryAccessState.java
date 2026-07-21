package com.desperadoboi.imagetopdf.model;

public enum GalleryAccessState {
    FULL(true, true, false),
    PARTIAL(true, true, true),
    DENIED(false, true, true),
    NOT_REQUESTED(false, true, true);

    private final boolean mediaStoreReadable;
    private final boolean fallbackSelectionAllowed;
    private final boolean permissionActionAllowed;

    GalleryAccessState(
            boolean mediaStoreReadable,
            boolean fallbackSelectionAllowed,
            boolean permissionActionAllowed
    ) {
        this.mediaStoreReadable = mediaStoreReadable;
        this.fallbackSelectionAllowed = fallbackSelectionAllowed;
        this.permissionActionAllowed = permissionActionAllowed;
    }

    public boolean canReadMediaStore() {
        return mediaStoreReadable;
    }

    public boolean canUseFallbackSelection() {
        return fallbackSelectionAllowed;
    }

    public boolean canRequestOrChangeAccess() {
        return permissionActionAllowed;
    }

    public boolean representsWholeGallery() {
        return this == FULL;
    }
}
