package com.desperadoboi.imagetopdf.pdf;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CancellationTokenTest {
    @Test
    public void tokenIsNotCancelledInitially() {
        CancellationToken token = new CancellationToken();

        assertFalse(token.isCancelled());
    }

    @Test
    public void cancelMarksTokenCancelled() {
        CancellationToken token = new CancellationToken();

        token.cancel();

        assertTrue(token.isCancelled());
    }

    @Test
    public void repeatedCancelIsSafe() {
        CancellationToken token = new CancellationToken();

        token.cancel();
        token.cancel();

        assertTrue(token.isCancelled());
    }
}
