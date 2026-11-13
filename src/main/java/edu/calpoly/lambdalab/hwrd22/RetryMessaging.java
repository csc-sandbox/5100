package edu.calpoly.lambdalab.hwrd22;

import java.util.Objects;

public class RetryMessaging implements Messaging {
    private final Messaging delegate;
    private final RetryExecutor retryExecutor;

    RetryMessaging(Messaging delegate, RetryExecutor retryExecutor) {
        this.delegate = Objects.requireNonNull(delegate);
        this.retryExecutor = Objects.requireNonNull(retryExecutor);
    }

    @Override
    public void publish(String json) {
        retryExecutor.execute(() -> delegate.publish(json));
    }
}
