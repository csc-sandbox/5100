package edu.calpoly.lambdalab.daome;

import edu.calpoly.lambdalab.daome.Exceptions.StorageException;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public class RetryStorage implements Storage {
    private final Storage delegate;
    private final RetryExecutor retryExecutor;

    RetryStorage(Storage delegate, RetryExecutor retryExecutor) {
        this.delegate = Objects.requireNonNull(delegate);
        this.retryExecutor = Objects.requireNonNull(retryExecutor);
    }

    @Override
    public void save(String id, String json) throws StorageException {
        retryExecutor.execute(() -> {
            delegate.save(id, json);
            return null;
        });
    }

    @Override
    public String get(String id) throws StorageException {
        AtomicReference<String> response = new AtomicReference<>();
        retryExecutor.execute(() -> {
            response.set(delegate.get(id));
            return null;
        });
        return response.get();
    }
}
