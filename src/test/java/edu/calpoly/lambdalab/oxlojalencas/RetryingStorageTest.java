package edu.calpoly.lambdalab.oxlojalencas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.exception.SdkServiceException;

/**
 * tests for the bounded retries around a storage, using fakes only.
 *
 * @author Jess A
 * @version November 10, 2026
 */
class RetryingStorageTest {

    private static final String ID = "c5f1b1b7-ccf5-4b98-80f9-f1123589a123";
    private static final String JSON = "{\"id\":\"" + ID + "\"}";

    @Test
    void transientFailureSucceedsOnThirdAttempt() {
        FlakyStorage flaky = new FlakyStorage(2,
                SdkClientException.create("simulated timeout"));
        AtomicInteger pauses = new AtomicInteger();
        Storage storage = new RetryingStorage(flaky, threeAttempts(pauses));

        storage.save(ID, JSON);

        assertEquals(3, flaky.attempts);
        assertEquals(2, pauses.get());
        assertEquals(JSON, flaky.saved);
    }

    @Test
    void retryExhaustionStopsAfterThreeAttempts() {
        SdkClientException outage = SdkClientException.create("still down");
        FlakyStorage flaky = new FlakyStorage(Integer.MAX_VALUE, outage);
        AtomicInteger pauses = new AtomicInteger();
        Storage storage = new RetryingStorage(flaky, threeAttempts(pauses));

        SdkClientException thrown = assertThrows(SdkClientException.class,
                () -> storage.save(ID, JSON));

        assertSame(outage, thrown);
        assertEquals(3, flaky.attempts);
        assertEquals(2, pauses.get());
        assertNull(flaky.saved);
    }

    @Test
    void permanentFailureStopsImmediately() {
        FlakyStorage flaky = new FlakyStorage(Integer.MAX_VALUE, service(403));
        AtomicInteger pauses = new AtomicInteger();
        Storage storage = new RetryingStorage(flaky, threeAttempts(pauses));

        assertThrows(SdkServiceException.class, () -> storage.save(ID, JSON));

        assertEquals(1, flaky.attempts);
        assertEquals(0, pauses.get());
    }

    @Test
    void classifiesTemporaryAndPermanentFailures() {
        assertTrue(RetryingStorage.isTemporary(
                SdkClientException.create("connection reset")));
        assertTrue(RetryingStorage.isTemporary(service(503)));
        assertTrue(RetryingStorage.isTemporary(service(429)));
        assertFalse(RetryingStorage.isTemporary(service(403)));
        assertFalse(RetryingStorage.isTemporary(service(400)));
        assertFalse(RetryingStorage.isTemporary(
                new IllegalArgumentException("bad input")));
    }

    // the pause just counts, so the tests never really wait
    private RetryExecutor threeAttempts(AtomicInteger pauses) {
        return new RetryExecutor(3, RetryingStorage::isTemporary,
                pauses::incrementAndGet);
    }

    private SdkServiceException service(int status) {
        return SdkServiceException.builder()
                .statusCode(status)
                .message("simulated " + status)
                .build();
    }

    /** fails the first few saves with one failure, then saves normally. */
    private static class FlakyStorage implements Storage {
        private final int failures;
        private final RuntimeException failure;
        private int attempts;
        private String saved;

        FlakyStorage(int failures, RuntimeException failure) {
            this.failures = failures;
            this.failure = failure;
        }

        @Override
        public void save(String id, String json) {
            attempts++;
            if (attempts <= failures) {
                throw failure;
            }
            saved = json;
        }

        @Override
        public String get(String id) {
            return saved;
        }
    }
}
