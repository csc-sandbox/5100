package edu.calpoly.lambdalab.daome;

import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkException;

import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Executes an operation with a bounded number of total attempts, retrying
 * only failures explicitly classified as transient by the caller.
 *
 * @author David Montiel
 * @version 1.0
 */
public class RetryExecutor {
    private final int maxAttempts;
    private final Predicate<RuntimeException> retryable;
    private final Runnable sleeper;

    RetryExecutor(int maxAttempts, Predicate<RuntimeException> retryable, Runnable sleeper) {
        if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be positive");
        this.maxAttempts = maxAttempts;
        this.retryable = Objects.requireNonNull(retryable);
        this.sleeper = Objects.requireNonNull(sleeper);
    }
    static boolean isTransient(RuntimeException e) {
        if (e instanceof AwsServiceException awsServiceException) {
            return awsServiceException.isThrottlingException() | awsServiceException.statusCode() >= 500;
        }
        return e instanceof SdkException se && se.retryable();
    }

    static Runnable sleep(long miliSeconds) {
        return () -> {
            try {
                Thread.sleep(miliSeconds);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
    }

    <T> T execute(Supplier<T> operation) {
        Objects.requireNonNull(operation);
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return operation.get();
            } catch (RuntimeException failure) {
                if (attempt == maxAttempts || !retryable.test(failure)) throw failure;
                sleeper.run();
            }
        }
        throw new AssertionError("unreachable host");
    }

}

