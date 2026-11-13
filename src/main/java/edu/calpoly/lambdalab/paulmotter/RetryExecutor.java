package edu.calpoly.lambdalab.paulmotter;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import java.io.IOException;

import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.awscore.retry.AwsRetryStrategy;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.exception.ApiCallAttemptTimeoutException;
import software.amazon.awssdk.core.exception.SdkClientException;

/**
 * Runs an operation with a bounded number of total attempts, retrying only failures
 * classified as transient. Wrap each idempotent AWS call separately, and turn off
 * SDK retries on the client so attempts are not multiplied.
 *
 * @author Paul Motter
 * @version 1.0 (2026-11-10)
 */
class RetryExecutor {
    /** Total attempts (first try + retries) used by {@link #forAws()}. */
    static final int DEFAULT_MAX_ATTEMPTS = 3;
    /** Delay between attempts used by {@link #forAws()}. */
    static final long DEFAULT_DELAY_MILLIS = 200;

    private final int maxAttempts;
    private final Predicate<RuntimeException> retryable;
    private final Runnable sleeper;

    /**
     * @param maxAttempts total attempts, including the first; must be at least 1
     * @param retryable   returns true for failures worth retrying
     * @param sleeper     runs between attempts (a real delay in production, a no-op in tests)
     */
    RetryExecutor(int maxAttempts, Predicate<RuntimeException> retryable, Runnable sleeper) {
        if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be positive");
        this.maxAttempts = maxAttempts;
        this.retryable = Objects.requireNonNull(retryable);
        this.sleeper = Objects.requireNonNull(sleeper);
    }

    /** Production default: 3 attempts, transient AWS failures only, 200 ms between attempts. */
    static RetryExecutor forAws() {
        return new RetryExecutor(DEFAULT_MAX_ATTEMPTS, RetryExecutor::isTransientAwsFailure,
            sleepMillis(DEFAULT_DELAY_MILLIS));
    }

    /**
     * Client configuration that turns off the AWS SDK's built-in retries, so this
     * executor is the only retry layer and one call makes at most
     * {@code maxAttempts} requests (instead of SDK attempts x executor attempts).
     */
    static ClientOverrideConfiguration withoutSdkRetries() {
        return ClientOverrideConfiguration.builder()
            .retryStrategy(AwsRetryStrategy.doNotRetry())
            .build();
    }

    /**
     * Classifies AWS failures as worth retrying or permanent.
     */
    static boolean isTransientAwsFailure(RuntimeException failure) {
        if (failure instanceof AwsServiceException aws) {
            return aws.isThrottlingException() || aws.statusCode() >= 500;
        }
        if (failure instanceof SdkClientException) {
            return failure instanceof ApiCallAttemptTimeoutException || hasIoCause(failure);
        }
        return false;
    }

    private static boolean hasIoCause(Throwable failure) {
        for (Throwable t = failure.getCause(); t != null; t = t.getCause()) {
            if (t instanceof IOException) return true;
        }
        return false;
    }

    /** Sleeper that waits a fixed delay; stops waiting if the thread is interrupted. */
    static Runnable sleepMillis(long millis) {
        return () -> {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
    }

    /**
     * Runs an operation that returns a value, with retries.
     * @param operation the call to attempt
     * @param onRetry   receives the failed attempt's exception before each retry (e.g. to log it)
     * @return the operation's result from the first successful attempt
     * @throws RuntimeException the last failure, if it is not retryable or attempts run out
     */
    <T> T execute(Supplier<T> operation, Consumer<RuntimeException> onRetry) {
        Objects.requireNonNull(operation);
        Objects.requireNonNull(onRetry);
        for (int attempt = 1; ; attempt++) {
            try {
                return operation.get();
            } catch (RuntimeException failure) {
                if (attempt >= maxAttempts || !retryable.test(failure)) throw failure;
                onRetry.accept(failure);
                sleeper.run();
            }
        }
    }
}
