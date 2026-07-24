package com.desperadoboi.imagetopdf.ui.viewer;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.desperadoboi.imagetopdf.document.word.DocxDocumentParser;
import com.desperadoboi.imagetopdf.document.word.WordDocumentModel;
import com.desperadoboi.imagetopdf.document.word.WordParseException;

import java.io.File;
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

    void load(File packageFile) {
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
                if (!token.get() && generation == generations.get()) {
                    state.postValue(State.loaded(path, document));
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
        @Nullable private final Throwable failure;
        private final boolean loading;

        private State(
                String path,
                @Nullable WordDocumentModel document,
                @Nullable Throwable failure,
                boolean loading
        ) {
            this.path = path;
            this.document = document;
            this.failure = failure;
            this.loading = loading;
        }

        private static State loading(String path) {
            return new State(path, null, null, true);
        }

        private static State loaded(String path, WordDocumentModel document) {
            return new State(path, document, null, false);
        }

        private static State failed(String path, Throwable failure) {
            return new State(path, null, failure, false);
        }

        String getPath() {
            return path;
        }

        @Nullable WordDocumentModel getDocument() {
            return document;
        }

        @Nullable Throwable getFailure() {
            return failure;
        }

        boolean isLoading() {
            return loading;
        }
    }
}
