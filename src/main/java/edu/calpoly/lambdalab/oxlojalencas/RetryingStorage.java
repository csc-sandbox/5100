package edu.calpoly.lambdalab.oxlojalencas;

import java.util.Objects;

import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.exception.SdkServiceException;

/**
 * wraps a storage so a failed save is retried a limited number of times.
 *
 * <p>repeating a save is safe: the same id always overwrites the same s3
 * object and dynamodb item with the same json, so no duplicate is created.
 * the aws sdk already retries on its own, so this class is not used by the
 * production lambda; turning it on would also need the sdk clients limited
 * to one attempt, or the two layers would multiply the attempts.
 *
 * @author Jess A
 * @version November 10, 2026
 */
final class RetryingStorage implements Storage {

    private final Storage delegate;
    private final RetryExecutor retryExecutor;

    RetryingStorage(Storage delegate, RetryExecutor retryExecutor) {
        this.delegate = Objects.requireNonNull(delegate);
        this.retryExecutor = Objects.requireNonNull(retryExecutor);
    }

    /**
     * tells whether a failed call may succeed if tried again: network
     * problems, throttling and server errors do; invalid input and 4xx
     * responses such as access denied do not.
     *
     * @param failure the failure to classify
     * @return true if the call is worth retrying
     */
    static boolean isTemporary(RuntimeException failure) {
        if (failure instanceof SdkServiceException service) {
            return service.isThrottlingException() || service.statusCode() >= 500;
        }
        return failure instanceof SdkClientException;
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
