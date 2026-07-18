package com.desperadoboi.imagetopdf.image;

import android.content.ContentResolver;
import android.net.Uri;

import androidx.exifinterface.media.ExifInterface;

import java.io.IOException;
import java.io.InputStream;

public final class ImageOrientationReader {
    private final ContentResolver contentResolver;

    public ImageOrientationReader(ContentResolver contentResolver) {
        this.contentResolver = contentResolver;
    }

    public ImageTransform read(Uri imageUri) {
        try (InputStream inputStream = contentResolver.openInputStream(imageUri)) {
            if (inputStream == null) {
                return ExifOrientationMapper.map(ExifInterface.ORIENTATION_UNDEFINED);
            }

            ExifInterface exifInterface = new ExifInterface(inputStream);
            int orientation = exifInterface.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_UNDEFINED
            );
            return ExifOrientationMapper.map(orientation);
        } catch (IOException | RuntimeException exception) {
            return ExifOrientationMapper.map(ExifInterface.ORIENTATION_UNDEFINED);
        }
    }
}
