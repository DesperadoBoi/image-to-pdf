package com.desperadoboi.imagetopdf.ui.idcard;

import java.util.Objects;

public final class IdCardSideRecord {
    private final IdCardSide side;
    private final IdCardSideState state;
    private final IdCardImage currentImage;
    private final IdCardImage pendingImage;
    private final IdCardError error;

    private IdCardSideRecord(
            IdCardSide side,
            IdCardSideState state,
            IdCardImage currentImage,
            IdCardImage pendingImage,
            IdCardError error
    ) {
        this.side = Objects.requireNonNull(side, "side is required");
        this.state = Objects.requireNonNull(state, "state is required");
        this.currentImage = currentImage;
        this.pendingImage = pendingImage;
        this.error = Objects.requireNonNull(error, "error is required");
    }

    public static IdCardSideRecord empty(IdCardSide side) {
        return new IdCardSideRecord(side, IdCardSideState.EMPTY, null, null, IdCardError.NONE);
    }

    public IdCardSide getSide() {
        return side;
    }

    public IdCardSideState getState() {
        return state;
    }

    public IdCardImage getCurrentImage() {
        return currentImage;
    }

    public IdCardImage getPendingImage() {
        return pendingImage;
    }

    public IdCardError getError() {
        return error;
    }

    public boolean isBusy() {
        return state == IdCardSideState.CAPTURING || state == IdCardSideState.PROCESSING;
    }

    public boolean isReady() {
        return state == IdCardSideState.READY && currentImage != null;
    }

    IdCardSideRecord begin(IdCardSideState busyState) {
        if (isBusy()) {
            return this;
        }
        return new IdCardSideRecord(side, busyState, currentImage, null, IdCardError.NONE);
    }

    IdCardSideRecord reviewCurrent() {
        if (!isReady()) {
            return this;
        }
        return new IdCardSideRecord(
                side,
                IdCardSideState.PROCESSING,
                currentImage,
                currentImage,
                IdCardError.NONE
        );
    }

    IdCardSideRecord withPending(IdCardImage image) {
        if (!isBusy()) {
            return this;
        }
        return new IdCardSideRecord(
                side,
                IdCardSideState.PROCESSING,
                currentImage,
                Objects.requireNonNull(image),
                IdCardError.NONE
        );
    }

    IdCardSideRecord updatePending(IdCardImage image) {
        if (state != IdCardSideState.PROCESSING || pendingImage == null) {
            return this;
        }
        return new IdCardSideRecord(
                side,
                state,
                currentImage,
                Objects.requireNonNull(image),
                error
        );
    }

    IdCardSideRecord acceptPending() {
        if (state != IdCardSideState.PROCESSING || pendingImage == null) {
            return this;
        }
        return new IdCardSideRecord(
                side,
                IdCardSideState.READY,
                pendingImage,
                null,
                IdCardError.NONE
        );
    }

    IdCardSideRecord cancelPending() {
        return new IdCardSideRecord(
                side,
                currentImage == null ? IdCardSideState.EMPTY : IdCardSideState.READY,
                currentImage,
                null,
                IdCardError.NONE
        );
    }

    IdCardSideRecord fail(IdCardError newError) {
        return new IdCardSideRecord(
                side,
                IdCardSideState.ERROR,
                currentImage,
                null,
                Objects.requireNonNull(newError)
        );
    }

    IdCardSideRecord rotate() {
        if (!isReady()) {
            return this;
        }
        return new IdCardSideRecord(
                side,
                IdCardSideState.READY,
                currentImage.rotateClockwise(),
                null,
                IdCardError.NONE
        );
    }

    IdCardSideRecord withCurrent(IdCardImage image) {
        return new IdCardSideRecord(
                side,
                image == null ? IdCardSideState.EMPTY : IdCardSideState.READY,
                image,
                null,
                IdCardError.NONE
        );
    }
}
