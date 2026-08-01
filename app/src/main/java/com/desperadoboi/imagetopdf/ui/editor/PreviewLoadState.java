package com.desperadoboi.imagetopdf.ui.editor;

public final class PreviewLoadState {
    private Status status = Status.EMPTY;
    private String activeKey;

    public boolean start(String key) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("key is required");
        }
        if (status == Status.LOADING && key.equals(activeKey)) {
            return false;
        }
        activeKey = key;
        status = Status.LOADING;
        return true;
    }

    public boolean loaded(String key) {
        if (!isActiveLoading(key)) {
            return false;
        }
        status = Status.LOADED;
        return true;
    }

    public boolean failed(String key) {
        if (!isActiveLoading(key)) {
            return false;
        }
        status = Status.ERROR;
        return true;
    }

    public void showEmpty() {
        activeKey = null;
        status = Status.EMPTY;
    }

    public boolean isCurrent(String key) {
        return key != null && key.equals(activeKey);
    }

    public Status getStatus() {
        return status;
    }

    private boolean isActiveLoading(String key) {
        return status == Status.LOADING && isCurrent(key);
    }

    public enum Status {
        EMPTY,
        LOADING,
        ERROR,
        LOADED
    }
}
