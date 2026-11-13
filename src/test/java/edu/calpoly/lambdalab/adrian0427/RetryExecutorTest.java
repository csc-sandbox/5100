package edu.calpoly.lambdalab.adrian0427;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RetryExecutorTest {

    private static RetryExecutor threeAttempts(
            AtomicInteger delays) {

        return new RetryExecutor(
                3,
                failure -> failure instanceof IllegalStateException,
                delays::incrementAndGet);
    }

    @Test
    void transientFailureSucceedsOnThirdAttempt() {
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger delays = new AtomicInteger();

        Storage fake = new Storage() {
            @Override
            public void save(String id, String json) { }

            @Override
            public String get(String id) {
                if (attempts.incrementAndGet() < 3) {
                    throw new IllegalStateException(
                            "Temporary storage failure");
                }

                return "{\"metric\":\"joy\",\"value\":0.9}";
            }
        };

        Storage storage =
                new RetryingStorage(fake, threeAttempts(delays));

        String result = storage.get("test-id");

        assertEquals("{\"metric\":\"joy\",\"value\":0.9}", result);
        assertEquals(3, attempts.get());
        assertEquals(2, delays.get());
    }

    @Test
    void permanentFailureStopsAfterThreeAttempts() {
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger delays = new AtomicInteger();

        IllegalStateException outage =
                new IllegalStateException("Storage unavailable");

        Storage fake = new Storage() {
            @Override
            public void save(String id, String json) { }

            @Override
            public String get(String id) {
                attempts.incrementAndGet();
                throw outage;
            }
        };

        Storage storage =
                new RetryingStorage(fake, threeAttempts(delays));

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> storage.get("test-id"));

        assertSame(outage, thrown);
        assertEquals(3, attempts.get());
        assertEquals(2, delays.get());
    }

    @Test
    void nonRetryableFailureStopsImmediately() {
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger delays = new AtomicInteger();

        Storage fake = new Storage() {
            @Override
            public void save(String id, String json) { }

            @Override
            public String get(String id) {
                attempts.incrementAndGet();

                throw new IllegalArgumentException(
                        "Invalid storage request");
            }
        };

        Storage storage =
                new RetryingStorage(fake, threeAttempts(delays));

        assertThrows(
                IllegalArgumentException.class,
                () -> storage.get("test-id"));

        assertEquals(1, attempts.get());
        assertEquals(0, delays.get());
    }
}
