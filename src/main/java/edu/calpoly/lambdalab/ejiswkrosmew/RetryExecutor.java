package edu.calpoly.lambdalab.ejiswkrosmew;

import java.util.Objects;
import java.util.function.Predicate;

final class RetryExecutor {
    private final int maxAttempts;
    private final Predicate<RuntimeException> retryable;
    private final Runnable sleeper;

    RetryExecutor(int maxAttempts, Predicate<RuntimeException> retryable, Runnable sleeper) {
        if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be positive");
        this.maxAttempts = maxAttempts;
        this.retryable = retryable;
        this.sleeper = sleeper;
    }

    void execute(Runnable operation) {
        Objects.requireNonNull(operation);
        for (int i = 1; i <= maxAttempts; i++) {
            try {
                operation.run();
            } catch (RuntimeException e) {
                if (i == maxAttempts || !retryable.test(e)) throw e;
                sleeper.run();
            }
        }
    }
}
