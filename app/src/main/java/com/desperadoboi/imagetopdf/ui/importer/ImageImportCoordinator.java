package com.desperadoboi.imagetopdf.ui.importer;

import com.desperadoboi.imagetopdf.model.ImageImportRequest;
import com.desperadoboi.imagetopdf.model.ImageImportSource;

import java.util.EnumMap;
import java.util.Objects;

public final class ImageImportCoordinator implements ImagePickerLauncher {
    private final EnumMap<ImageImportSource, SourceLauncher> sourceLaunchers =
            new EnumMap<>(ImageImportSource.class);

    public ImageImportCoordinator register(
            ImageImportSource source,
            SourceLauncher sourceLauncher
    ) {
        sourceLaunchers.put(
                Objects.requireNonNull(source, "source is required"),
                Objects.requireNonNull(sourceLauncher, "sourceLauncher is required")
        );
        return this;
    }

    @Override
    public void launch(ImageImportRequest request) {
        Objects.requireNonNull(request, "request is required");
        SourceLauncher sourceLauncher = sourceLaunchers.get(request.getSource());
        if (sourceLauncher == null) {
            throw new IllegalStateException("No launcher registered for " + request.getSource());
        }
        sourceLauncher.launch(request);
    }

    public interface SourceLauncher {
        void launch(ImageImportRequest request);
    }
}
