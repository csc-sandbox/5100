package edu.calpoly.lambdalab.adrian0427;

import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;

final class RetryExecutor {

    private final int maxAttempts;
    private final Predicate<RuntimeException> retryable;
    private final Runnable sleeper;

    RetryExecutor(int maxAttempts,
                  Predicate<RuntimeException> retryable,
                  Runnable sleeper) {

        if (maxAttempts < 1) {
            throw new IllegalArgumentException(
                    "maxAttempts must be positive");
        }

        this.maxAttempts = maxAttempts;
        this.retryable = Objects.requireNonNull(retryable);
        this.sleeper = Objects.requireNonNull(sleeper);
    }

    <T> T execute(Supplier<T> operation) {
        Objects.requireNonNull(operation);

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return operation.get();

            } catch (RuntimeException failure) {
                if (attempt == maxAttempts
                        || !retryable.test(failure)) {
                    throw failure;
                }

                sleeper.run();
            }
        }

        throw new IllegalStateException("Retry attempts exhausted");
    }
}
