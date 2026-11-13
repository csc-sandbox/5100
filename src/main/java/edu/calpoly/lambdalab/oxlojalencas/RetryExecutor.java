package edu.calpoly.lambdalab.oxlojalencas;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * runs an operation with a fixed number of total attempts and retries only
 * the failures the caller marks as temporary.
 *
 * @author Jess A
 * @version November 10, 2026
 */
final class RetryExecutor {

    private final int maxAttempts;
    private final Predicate<RuntimeException> temporary;
    private final Runnable pause;

    /**
     * creates an executor.
     *
     * @param maxAttempts total attempts, including the first one
     * @param temporary tells whether a failure is worth retrying
     * @param pause runs between attempts, so tests can skip real delays
     */
    RetryExecutor(int maxAttempts, Predicate<RuntimeException> temporary,
            Runnable pause) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
        this.maxAttempts = maxAttempts;
        this.temporary = Objects.requireNonNull(temporary);
        this.pause = Objects.requireNonNull(pause);
    }

    /**
     * runs the operation; the last failure, or the first permanent one, is
     * rethrown unchanged.
     *
     * @param operation the work to run
     */
    void execute(Runnable operation) {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                operation.run();
                return;
            } catch (RuntimeException failure) {
                if (attempt == maxAttempts || !temporary.test(failure)) {
                    throw failure;
                }
                pause.run();
            }
        }
    }
}
