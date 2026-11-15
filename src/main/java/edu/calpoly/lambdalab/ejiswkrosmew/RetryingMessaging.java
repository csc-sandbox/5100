package edu.calpoly.lambdalab.ejiswkrosmew;

import java.util.Objects;

final class RetryingMessaging implements Messaging{
    private final Messaging delegate;
    private final RetryExecutor retryExecutor;

    RetryingMessaging(Messaging delegate, RetryExecutor retryExecutor) {
        this.delegate = Objects.requireNonNull(delegate);
        this.retryExecutor = Objects.requireNonNull(retryExecutor);
    }

    @Override
    public void publish(String json) {
        retryExecutor.execute(() -> delegate.publish(json));
    }
}
