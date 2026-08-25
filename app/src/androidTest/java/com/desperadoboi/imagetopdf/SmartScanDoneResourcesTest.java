package com.desperadoboi.imagetopdf;

import android.content.Context;
import android.content.res.Configuration;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Locale;

import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public final class SmartScanDoneResourcesTest {
    @Test public void russianDoneResourcesUsePlatformPluralRules() {
        assertDoneResources(
                localizedContext(new Locale("ru")),
                new int[]{1, 2, 5, 11, 21},
                new String[]{
                        "Завершить сканирование, 1 страница",
                        "Завершить сканирование, 2 страницы",
                        "Завершить сканирование, 5 страниц",
                        "Завершить сканирование, 11 страниц",
                        "Завершить сканирование, 21 страница"
                },
                "Готово · "
        );
    }

    @Test public void englishDoneResourcesUsePlatformPluralRules() {
        assertDoneResources(
                localizedContext(Locale.ENGLISH),
                new int[]{1, 2, 5, 11, 21},
                new String[]{
                        "Finish scanning, 1 page",
                        "Finish scanning, 2 pages",
                        "Finish scanning, 5 pages",
                        "Finish scanning, 11 pages",
                        "Finish scanning, 21 pages"
                },
                "Done · "
        );
    }

    private static void assertDoneResources(
            Context context,
            int[] counts,
            String[] descriptions,
            String labelPrefix
    ) {
        for (int index = 0; index < counts.length; index++) {
            int count = counts[index];
            assertEquals(labelPrefix + count,
                    context.getString(R.string.smart_scan_done, count));
            assertEquals(descriptions[index], context.getResources().getQuantityString(
                    R.plurals.smart_scan_done_content_description,
                    count,
                    count
            ));
        }
    }

    private static Context localizedContext(Locale locale) {
        Context base = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Configuration configuration = new Configuration(
                base.getResources().getConfiguration()
        );
        configuration.setLocale(locale);
        return base.createConfigurationContext(configuration);
    }
}
