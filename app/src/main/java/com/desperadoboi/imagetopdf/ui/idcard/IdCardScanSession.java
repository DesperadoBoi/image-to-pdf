package com.desperadoboi.imagetopdf.ui.idcard;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class IdCardScanSession {
    private final String sessionId;
    private final IdCardSideRecord front;
    private final IdCardSideRecord back;

    public IdCardScanSession(
            String sessionId,
            IdCardSideRecord front,
            IdCardSideRecord back
    ) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            throw new IllegalArgumentException("sessionId is required");
        }
        this.sessionId = sessionId.trim();
        this.front = requireSide(front, IdCardSide.FRONT);
        this.back = requireSide(back, IdCardSide.BACK);
    }

    public static IdCardScanSession empty(String sessionId) {
        return new IdCardScanSession(
                sessionId,
                IdCardSideRecord.empty(IdCardSide.FRONT),
                IdCardSideRecord.empty(IdCardSide.BACK)
        );
    }

    public String getSessionId() {
        return sessionId;
    }

    public IdCardSideRecord get(IdCardSide side) {
        return side == IdCardSide.FRONT ? front : back;
    }

    public boolean canExport() {
        return front.isReady() || back.isReady();
    }

    public int getReadyCount() {
        int count = 0;
        if (front.isReady()) count++;
        if (back.isReady()) count++;
        return count;
    }

    public boolean hasBusySide() {
        return front.isBusy() || back.isBusy();
    }

    public List<IdCardImage> getReadyImages() {
        ArrayList<IdCardImage> images = new ArrayList<>(2);
        if (front.isReady()) images.add(front.getCurrentImage());
        if (back.isReady()) images.add(back.getCurrentImage());
        return images;
    }

    public List<String> collectCacheFileNames() {
        Set<String> names = new LinkedHashSet<>();
        collect(names, front);
        collect(names, back);
        return new ArrayList<>(names);
    }

    public IdCardScanSession begin(IdCardSide side, IdCardSideState busyState) {
        if (busyState != IdCardSideState.CAPTURING
                && busyState != IdCardSideState.PROCESSING) {
            throw new IllegalArgumentException("busy state is required");
        }
        return replace(side, get(side).begin(busyState));
    }

    public IdCardScanSession reviewCurrent(IdCardSide side) {
        return replace(side, get(side).reviewCurrent());
    }

    public IdCardScanSession withPending(IdCardSide side, IdCardImage image) {
        return replace(side, get(side).withPending(image));
    }

    public IdCardScanSession updatePending(IdCardSide side, IdCardImage image) {
        return replace(side, get(side).updatePending(image));
    }

    public IdCardScanSession acceptPending(IdCardSide side) {
        return replace(side, get(side).acceptPending());
    }

    public IdCardScanSession cancelPending(IdCardSide side) {
        return replace(side, get(side).cancelPending());
    }

    public IdCardScanSession fail(IdCardSide side, IdCardError error) {
        return replace(side, get(side).fail(error));
    }

    public IdCardScanSession delete(IdCardSide side) {
        return replace(side, IdCardSideRecord.empty(side));
    }

    public IdCardScanSession rotate(IdCardSide side) {
        return replace(side, get(side).rotate());
    }

    public IdCardScanSession swap() {
        if (!front.isReady() || !back.isReady()) {
            return this;
        }
        return new IdCardScanSession(
                sessionId,
                front.withCurrent(back.getCurrentImage()),
                back.withCurrent(front.getCurrentImage())
        );
    }

    public IdCardScanSession restoreMissingFile(IdCardSide side) {
        return replace(side, get(side).fail(IdCardError.FILE_UNAVAILABLE));
    }

    private IdCardScanSession replace(IdCardSide side, IdCardSideRecord record) {
        Objects.requireNonNull(side, "side is required");
        if (record == get(side)) {
            return this;
        }
        return side == IdCardSide.FRONT
                ? new IdCardScanSession(sessionId, record, back)
                : new IdCardScanSession(sessionId, front, record);
    }

    private static IdCardSideRecord requireSide(
            IdCardSideRecord record,
            IdCardSide expected
    ) {
        Objects.requireNonNull(record, "side record is required");
        if (record.getSide() != expected) {
            throw new IllegalArgumentException("unexpected side record");
        }
        return record;
    }

    private static void collect(Set<String> names, IdCardSideRecord record) {
        if (record.getCurrentImage() != null) {
            names.add(record.getCurrentImage().getCacheFileName());
        }
        if (record.getPendingImage() != null) {
            names.add(record.getPendingImage().getCacheFileName());
        }
    }
}
