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
        Messaging fake = json -> {
            if (attempts.incrementAndGet() < 3) {
                throw new IllegalStateException("Simulated temporary outage");
            }
        };
        Messaging messaging = new RetryMessaging(fake, threeAttempts(delays));

        messaging.publish("{\"id\":\"test\"}");

        assertEquals(3, attempts.get());
        assertEquals(2, delays.get());
    }

    @Test
    void permanentFailureStopsAfterThreeAttempts() {
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger delays = new AtomicInteger();
        IllegalStateException outage = new IllegalStateException("Still unavailable");
        Messaging fake = json -> {
            attempts.incrementAndGet();
            throw outage;
        };
        Messaging messaging = new RetryMessaging(fake, threeAttempts(delays));

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> messaging.publish("{\"id\":\"test\"}"));

        assertSame(outage, thrown);
        assertEquals(3, attempts.get());
        assertEquals(2, delays.get());
    }

    @Test
    void nonRetryableFailureStopsImmediately() {
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger delays = new AtomicInteger();
        Messaging fake = json -> {
            attempts.incrementAndGet();
            throw new IllegalArgumentException("Invalid input");
        };
        Messaging messaging = new RetryMessaging(fake, threeAttempts(delays));

        assertThrows(IllegalArgumentException.class, () -> messaging.publish("{}"));
        assertEquals(1, attempts.get());
        assertEquals(0, delays.get());
    }
}
