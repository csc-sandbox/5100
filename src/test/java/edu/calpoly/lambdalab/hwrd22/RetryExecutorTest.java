package edu.calpoly.lambdalab.hwrd22;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RetryExecutorTest {
    private static RetryExecutor threeAttempts(AtomicInteger delays) {
        return new RetryExecutor(3,
                failure -> failure instanceof IllegalStateException,
                delays::incrementAndGet);
    }

    @Test
    void transientFailureSucceedsOnThirdAttempt() {
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger delays = new AtomicInteger();
        Storage fakeStorage = new Storage() {
            String id;
            String json;
            @Override
            public void save(String saveId, String saveJson) {
                if (attempts.incrementAndGet() < 3) {
                    throw new IllegalStateException("Simulated temporary outage");
                }
                else {
                    id = saveId;
                    json = saveJson;
                }
            }

            @Override
            public String get(String getId) {
                return getId.equals(id) ? json : null;
            }
        };
        Storage storage = new RetryStorage(fakeStorage, threeAttempts(delays));

        storage.save("test", "{\"id\":\"test\"}");

        assertEquals(3, attempts.get());
        assertEquals(2, delays.get());

        assertEquals("{\"id\":\"test\"}", storage.get("test"));  // Proves the item saved
    }

    @Test
    void permanentFailureStopsAfterThreeAttempts() {
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger delays = new AtomicInteger();
        IllegalStateException outage = new IllegalStateException("Still unavailable");
        Storage fakeStorage = new Storage() {
            @Override
            public void save(String id, String json) {
                attempts.incrementAndGet();
                throw outage;
            }

            @Override
            public String get(String id) {
                return "";
            }
        };
        Storage storage = new RetryStorage(fakeStorage, threeAttempts(delays));

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> storage.save("test", "{\"id\":\"test\"}"));

        assertSame(outage, thrown);
        assertEquals(3, attempts.get());
        assertEquals(2, delays.get());
    }

    @Test
    void nonRetryableFailureStopsImmediately() {
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger delays = new AtomicInteger();
        Storage fakeStorage = new Storage() {
            @Override
            public void save(String id, String json) {
                attempts.incrementAndGet();
                throw new IllegalArgumentException("Invalid input");
            }

            @Override
            public String get(String id) {
                return "";
            }
        };
        Storage storage = new RetryStorage(fakeStorage, threeAttempts(delays));

        assertThrows(IllegalArgumentException.class, () -> storage.save("", "{}"));
        assertEquals(1, attempts.get());
        assertEquals(0, delays.get());
    }
}
