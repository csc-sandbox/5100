package edu.calpoly.lambdalab.aidenrodriguez;

import java.util.Objects;
import java.util.function.IntConsumer;
import java.util.function.Predicate;

import software.amazon.awssdk.core.exception.SdkException;

/** Runs an operation with a bounded retry count and injected failure policy. */
final class EncryptMessageRetryExecutor {
    static final int MAX_ATTEMPTS = 3;

    private final int maxAttempts;
    private final Predicate<RuntimeException> retryable;
    private final IntConsumer delay;

    EncryptMessageRetryExecutor(int maxAttempts, Predicate<RuntimeException> retryable,
            IntConsumer delay) {
        if (maxAttempts < 1) throw new IllegalArgumentException("At least one attempt is required");
        this.maxAttempts = maxAttempts;
        this.retryable = Objects.requireNonNull(retryable);
        this.delay = Objects.requireNonNull(delay);
    }

    static EncryptMessageRetryExecutor forAwsStorage() {
        return new EncryptMessageRetryExecutor(MAX_ATTEMPTS,
                failure -> failure instanceof SdkException sdkFailure && sdkFailure.retryable(),
                failedAttempt -> {
                    try {
                        Thread.sleep(25L * failedAttempt);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Storage retry interrupted", e);
                    }
                });
    }

    void execute(Runnable operation) {
        for (int attempt = 1; ; attempt++) {
            try {
                operation.run();
                return;
            } catch (RuntimeException failure) {
                if (attempt >= maxAttempts || !retryable.test(failure)) throw failure;
                delay.accept(attempt);
            }
        }
    }
}