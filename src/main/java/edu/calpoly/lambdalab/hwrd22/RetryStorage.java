package edu.calpoly.lambdalab.hwrd22;

import java.util.Objects;

public class RetryStorage implements Storage {
    private final Storage delegate;
    private final RetryExecutor retryExecutor;

    RetryStorage(Storage delegate, RetryExecutor retryExecutor) {
        this.delegate = Objects.requireNonNull(delegate);
        this.retryExecutor = Objects.requireNonNull(retryExecutor);
    }

    @Override
    public void save(String id, String json) {
        retryExecutor.execute(() -> delegate.save(id, json));
    }

    @Override
    public String get(String id) {
        return delegate.get(id);
    }
}
