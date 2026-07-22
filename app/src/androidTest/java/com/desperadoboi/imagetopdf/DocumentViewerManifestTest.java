package com.desperadoboi.imagetopdf;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.desperadoboi.imagetopdf.ui.viewer.DocumentViewerActivity;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class DocumentViewerManifestTest {
    @Test
    public void actionViewResolvesOnlySupportedTypes() {
        assertViewerResolves("application/pdf");
        assertViewerResolves("text/plain");
        assertViewerResolves("text/csv");
        assertViewerResolves("text/tab-separated-values");
        assertViewerResolves("application/csv");
        assertViewerResolves("image/jpeg");
        assertViewerResolves("image/png");
        assertViewerResolves("image/webp");
        assertViewerDoesNotResolve("application/octet-stream");
        assertViewerDoesNotResolve("application/vnd.ms-excel");
        assertViewerDoesNotResolve(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        );
    }

    private void assertViewerResolves(String mimeType) {
        assertTrue(findViewer(mimeType));
    }

    private void assertViewerDoesNotResolve(String mimeType) {
        assertFalse(findViewer(mimeType));
    }

    private boolean findViewer(String mimeType) {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent intent = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(Uri.parse("content://fixture/document"), mimeType)
                .setPackage(context.getPackageName());
        List<ResolveInfo> matches = context.getPackageManager().queryIntentActivities(
                intent,
                PackageManager.MATCH_DEFAULT_ONLY
        );
        ComponentName expected = new ComponentName(context, DocumentViewerActivity.class);
        for (ResolveInfo match : matches) {
            ComponentName actual = new ComponentName(
                    match.activityInfo.packageName,
                    match.activityInfo.name
            );
            if (expected.equals(actual)) return true;
        }
        return false;
    }
}
