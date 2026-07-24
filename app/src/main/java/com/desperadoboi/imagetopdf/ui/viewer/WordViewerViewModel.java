package com.desperadoboi.imagetopdf.ui.viewer;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.desperadoboi.imagetopdf.document.word.DocxDocumentParser;
import com.desperadoboi.imagetopdf.document.word.WordDocumentModel;
import com.desperadoboi.imagetopdf.document.word.WordParseException;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class WordViewerViewModel extends ViewModel {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicLong generations = new AtomicLong();
    private final MutableLiveData<State> state = new MutableLiveData<>();

    @Nullable private AtomicBoolean cancellation;
    @Nullable private String currentPath;

    LiveData<State> state() {
        return state;
    }

    void load(File packageFile, DocxHtmlRenderer.Labels labels) {
        if (packageFile == null) return;
        String path = packageFile.getAbsolutePath();
        State existing = state.getValue();
        if (path.equals(currentPath) && existing != null) {
            state.setValue(existing);
            return;
        }
        clearDocument();
        currentPath = path;
        long generation = generations.get();
        AtomicBoolean token = new AtomicBoolean(false);
        cancellation = token;
        state.setValue(State.loading(path));
        executor.execute(() -> {
            try {
                WordDocumentModel document = new DocxDocumentParser().parse(
                        packageFile,
                        token
                );
                if (token.get() || generation != generations.get()) return;
                DocxHtmlRenderer.Result rendered = render(packageFile, document, labels);
                if (!token.get() && generation == generations.get()) {
                    state.postValue(State.loaded(path, document, rendered));
                }
            } catch (WordParseException exception) {
                if (exception.getReason() != WordParseException.Reason.CANCELLED
                        && !token.get()
                        && generation == generations.get()) {
                    state.postValue(State.failed(path, exception));
                }
            } catch (OutOfMemoryError error) {
                if (!token.get() && generation == generations.get()) {
                    state.postValue(State.failed(path, error));
                }
            } catch (Exception exception) {
                if (!token.get() && generation == generations.get()) {
                    state.postValue(State.failed(path, exception));
                }
            }
        });
    }

    private DocxHtmlRenderer.Result render(
            File packageFile,
            WordDocumentModel document,
            DocxHtmlRenderer.Labels labels
    ) {
        DocxLocalImageStore imageStore = null;
        try {
            imageStore = new DocxLocalImageStore(packageFile);
            return new DocxHtmlRenderer(imageStore, labels).render(document);
        } catch (IOException ignored) {
            return new DocxHtmlRenderer(image -> null, labels).render(document);
        } finally {
            if (imageStore != null) {
                try {
                    imageStore.close();
                } catch (IOException ignored) {
                    // The bounded local package has already been rendered.
                }
            }
        }
    }

    void clearDocument() {
        generations.incrementAndGet();
        if (cancellation != null) cancellation.set(true);
        cancellation = null;
        currentPath = null;
        state.setValue(null);
    }

    @Override
    protected void onCleared() {
        clearDocument();
        executor.shutdownNow();
    }

    static final class State {
        private final String path;
        @Nullable private final WordDocumentModel document;
        @Nullable private final String html;
        @Nullable private final Throwable failure;
        private final boolean loading;
        private final int pageCount;

        private State(
                String path,
                @Nullable WordDocumentModel document,
                @Nullable String html,
                @Nullable Throwable failure,
                boolean loading,
                int pageCount
        ) {
            this.path = path;
            this.document = document;
            this.html = html;
            this.failure = failure;
            this.loading = loading;
            this.pageCount = Math.max(0, pageCount);
        }

        private static State loading(String path) {
            return new State(path, null, null, null, true, 0);
        }

        private static State loaded(
                String path,
                WordDocumentModel document,
                DocxHtmlRenderer.Result rendered
        ) {
            return new State(
                    path,
                    document,
                    rendered.getHtml(),
                    null,
                    false,
                    rendered.getPageCount()
            );
        }

        private static State failed(String path, Throwable failure) {
            return new State(path, null, null, failure, false, 0);
        }

        String getPath() {
            return path;
        }

        @Nullable WordDocumentModel getDocument() {
            return document;
        }

        @Nullable String getHtml() {
            return html;
        }

        @Nullable Throwable getFailure() {
            return failure;
        }

        boolean isLoading() {
            return loading;
        }

        int getPageCount() {
            return pageCount;
        }
    }
}
