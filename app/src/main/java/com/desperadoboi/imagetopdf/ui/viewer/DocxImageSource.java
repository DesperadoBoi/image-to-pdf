package com.desperadoboi.imagetopdf.ui.viewer;

import androidx.annotation.Nullable;

import com.desperadoboi.imagetopdf.document.word.WordImage;

interface DocxImageSource {
    @Nullable
    String dataUri(WordImage image);
}
