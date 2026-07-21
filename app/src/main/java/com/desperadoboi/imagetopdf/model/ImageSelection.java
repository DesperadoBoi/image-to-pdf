package com.desperadoboi.imagetopdf.model;

import android.net.Uri;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class ImageSelection {
    private final ArrayList<Uri> selectedUris = new ArrayList<>();

    public boolean toggle(Uri uri) {
        Objects.requireNonNull(uri, "uri is required");
        int position = indexOf(uri);
        if (position >= 0) {
            selectedUris.remove(position);
            return false;
        }
        selectedUris.add(uri);
        return true;
    }

    public boolean select(Uri uri) {
        Objects.requireNonNull(uri, "uri is required");
        if (indexOf(uri) >= 0) {
            return false;
        }
        selectedUris.add(uri);
        return true;
    }

    public boolean deselect(Uri uri) {
        int position = indexOf(Objects.requireNonNull(uri, "uri is required"));
        if (position < 0) {
            return false;
        }
        selectedUris.remove(position);
        return true;
    }

    public boolean isSelected(Uri uri) {
        return indexOf(uri) >= 0;
    }

    public int getSelectionNumber(Uri uri) {
        int position = indexOf(uri);
        return position < 0 ? 0 : position + 1;
    }

    public int size() {
        return selectedUris.size();
    }

    public boolean isEmpty() {
        return selectedUris.isEmpty();
    }

    public List<Uri> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(selectedUris));
    }

    public void clear() {
        selectedUris.clear();
    }

    private int indexOf(Uri uri) {
        Objects.requireNonNull(uri, "uri is required");
        String key = uri.toString();
        for (int index = 0; index < selectedUris.size(); index++) {
            if (selectedUris.get(index).toString().equals(key)) {
                return index;
            }
        }
        return -1;
    }
}
