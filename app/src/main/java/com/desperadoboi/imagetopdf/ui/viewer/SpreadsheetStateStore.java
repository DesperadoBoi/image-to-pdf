package com.desperadoboi.imagetopdf.ui.viewer;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

final class SpreadsheetStateStore {
    private final Map<Integer, SpreadsheetViewportState> sheetStates = new HashMap<>();
    private String documentKey;
    private int selectedSheet;

    SpreadsheetStateStore() {
    }

    private SpreadsheetStateStore(SpreadsheetStateStore source) {
        documentKey = source.documentKey;
        selectedSheet = source.selectedSheet;
        sheetStates.putAll(source.sheetStates);
    }

    void openDocument(String newDocumentKey) {
        if (Objects.equals(documentKey, newDocumentKey)) return;
        documentKey = newDocumentKey;
        selectedSheet = 0;
        sheetStates.clear();
    }

    void clear() {
        documentKey = null;
        selectedSheet = 0;
        sheetStates.clear();
    }

    void setSelectedSheet(int selectedSheet) {
        this.selectedSheet = Math.max(0, selectedSheet);
    }

    int getSelectedSheet() {
        return selectedSheet;
    }

    void save(int sheet, SpreadsheetViewportState state) {
        sheetStates.put(Math.max(0, sheet), state);
    }

    SpreadsheetViewportState restore(int sheet) {
        SpreadsheetViewportState state = sheetStates.get(Math.max(0, sheet));
        return state == null ? SpreadsheetViewportState.initialNormal() : state;
    }

    void recordManualZoom(int sheet, float scale) {
        save(sheet, restore(sheet).withManualScale(scale));
    }

    SpreadsheetStateStore copy() {
        return new SpreadsheetStateStore(this);
    }

    int size() {
        return sheetStates.size();
    }
}
