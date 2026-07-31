package com.desperadoboi.imagetopdf.ui.viewer;

import com.desperadoboi.imagetopdf.document.DocumentLoadException;
import com.desperadoboi.imagetopdf.document.DocumentType;
import com.desperadoboi.imagetopdf.document.spreadsheet.XlsxParseException;
import com.desperadoboi.imagetopdf.document.word.WordParseException;

public final class ViewerErrorMapper {
    private ViewerErrorMapper() {
    }

    public static ViewerErrorType unsupportedType(DocumentType type) {
        if (type == DocumentType.XLS) return ViewerErrorType.UNSUPPORTED_LEGACY_XLS;
        if (type == DocumentType.DOC) return ViewerErrorType.UNSUPPORTED_LEGACY_DOC;
        if (type == DocumentType.XLSM) return ViewerErrorType.UNSUPPORTED_MACRO_XLSM;
        return ViewerErrorType.UNSUPPORTED_DOCUMENT_TYPE;
    }

    public static ViewerErrorType loadFailure(DocumentLoadException.Reason reason) {
        switch (reason) {
            case PERMISSION_LOST:
                return ViewerErrorType.PROVIDER_PERMISSION;
            case PROVIDER_UNREADABLE:
                return ViewerErrorType.PROVIDER_URI_UNREADABLE;
            case SPREADSHEET_TOO_LARGE:
                return ViewerErrorType.SPREADSHEET_TOO_LARGE;
            case SPREADSHEET_CORRUPTED:
                return ViewerErrorType.SPREADSHEET_CORRUPTED;
            case SPREADSHEET_ENCRYPTED:
                return ViewerErrorType.SPREADSHEET_ENCRYPTED;
            case SPREADSHEET_ACTIVE_CONTENT:
                return ViewerErrorType.SPREADSHEET_ACTIVE_CONTENT;
            case DOCX_TOO_LARGE:
                return ViewerErrorType.DOCX_TOO_LARGE;
            case DOCX_CORRUPTED:
                return ViewerErrorType.DOCX_CORRUPTED;
            case DOCX_ENCRYPTED:
                return ViewerErrorType.DOCX_ENCRYPTED;
            case DOCX_UNSUPPORTED:
                return ViewerErrorType.DOCX_UNSUPPORTED;
            case TOO_LARGE:
                return ViewerErrorType.GENERIC_TOO_LARGE;
            case ENCRYPTED:
            case CORRUPTED:
                return ViewerErrorType.GENERIC_CORRUPTED;
            case UNSUPPORTED:
                return ViewerErrorType.UNSUPPORTED_DOCUMENT_TYPE;
            case CANCELLED:
            default:
                return ViewerErrorType.CANCELLED;
        }
    }

    public static ViewerErrorType spreadsheetFailure(XlsxParseException.Reason reason) {
        switch (reason) {
            case TOO_LARGE:
                return ViewerErrorType.SPREADSHEET_TOO_LARGE;
            case ENCRYPTED:
                return ViewerErrorType.SPREADSHEET_ENCRYPTED;
            case ACTIVE_CONTENT:
                return ViewerErrorType.SPREADSHEET_ACTIVE_CONTENT;
            case UNSUPPORTED:
            case CORRUPTED:
            default:
                return ViewerErrorType.SPREADSHEET_CORRUPTED;
        }
    }

    public static ViewerErrorType wordFailure(WordParseException.Reason reason) {
        switch (reason) {
            case TOO_LARGE:
                return ViewerErrorType.DOCX_TOO_LARGE;
            case ENCRYPTED:
                return ViewerErrorType.DOCX_ENCRYPTED;
            case UNSUPPORTED:
                return ViewerErrorType.DOCX_UNSUPPORTED;
            case CANCELLED:
                return ViewerErrorType.CANCELLED;
            case CORRUPTED:
            default:
                return ViewerErrorType.DOCX_CORRUPTED;
        }
    }
}
