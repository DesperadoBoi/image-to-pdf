package com.desperadoboi.imagetopdf.ui.viewer;

import com.desperadoboi.imagetopdf.document.DocumentLoadException;
import com.desperadoboi.imagetopdf.document.DocumentType;
import com.desperadoboi.imagetopdf.document.spreadsheet.XlsxParseException;
import com.desperadoboi.imagetopdf.document.word.WordParseException;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public final class ViewerErrorMapperTest {
    @Test
    public void legacyXlsNeverUsesWordError() {
        ViewerErrorType type = ViewerErrorMapper.unsupportedType(DocumentType.XLS);
        assertEquals(ViewerErrorType.UNSUPPORTED_LEGACY_XLS, type);
        assertNotEquals(ViewerErrorType.DOCX_UNSUPPORTED, type);
    }

    @Test
    public void xlsxFailuresNeverUseWordErrors() {
        for (XlsxParseException.Reason reason : XlsxParseException.Reason.values()) {
            ViewerErrorType type = ViewerErrorMapper.spreadsheetFailure(reason);
            assertNotEquals(ViewerErrorType.DOCX_UNSUPPORTED, type);
            assertNotEquals(ViewerErrorType.DOCX_CORRUPTED, type);
            assertNotEquals(ViewerErrorType.DOCX_ENCRYPTED, type);
            assertNotEquals(ViewerErrorType.DOCX_TOO_LARGE, type);
        }
    }

    @Test
    public void docxErrorMappingRemainsFormatSpecific() {
        assertEquals(
                ViewerErrorType.DOCX_TOO_LARGE,
                ViewerErrorMapper.wordFailure(WordParseException.Reason.TOO_LARGE)
        );
        assertEquals(
                ViewerErrorType.DOCX_ENCRYPTED,
                ViewerErrorMapper.wordFailure(WordParseException.Reason.ENCRYPTED)
        );
        assertEquals(
                ViewerErrorType.DOCX_UNSUPPORTED,
                ViewerErrorMapper.wordFailure(WordParseException.Reason.UNSUPPORTED)
        );
        assertEquals(
                ViewerErrorType.DOCX_CORRUPTED,
                ViewerErrorMapper.wordFailure(WordParseException.Reason.CORRUPTED)
        );
    }

    @Test
    public void loaderSpreadsheetReasonsRemainSpreadsheetSpecific() {
        assertEquals(
                ViewerErrorType.SPREADSHEET_CORRUPTED,
                ViewerErrorMapper.loadFailure(
                        DocumentLoadException.Reason.SPREADSHEET_CORRUPTED
                )
        );
        assertEquals(
                ViewerErrorType.SPREADSHEET_ENCRYPTED,
                ViewerErrorMapper.loadFailure(
                        DocumentLoadException.Reason.SPREADSHEET_ENCRYPTED
                )
        );
        assertEquals(
                ViewerErrorType.SPREADSHEET_ACTIVE_CONTENT,
                ViewerErrorMapper.loadFailure(
                        DocumentLoadException.Reason.SPREADSHEET_ACTIVE_CONTENT
                )
        );
    }

    @Test
    public void macroWorkbookUsesDedicatedError() {
        assertEquals(
                ViewerErrorType.UNSUPPORTED_MACRO_XLSM,
                ViewerErrorMapper.unsupportedType(DocumentType.XLSM)
        );
    }

    @Test
    public void providerReadFailureUsesDedicatedError() {
        assertEquals(
                ViewerErrorType.PROVIDER_URI_UNREADABLE,
                ViewerErrorMapper.loadFailure(
                        DocumentLoadException.Reason.PROVIDER_UNREADABLE
                )
        );
    }
}
