package edu.calpoly.lambdalab.ejiswkrosmew;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class LidarSafetyRetryingMessagingTest {
    private static RetryExecutor threeAttempts(AtomicInteger delays) {
        return new RetryExecutor(3, failure -> failure instanceof IllegalStateException, delays::incrementAndGet);
    }

    @Test
    void transientFailureSucceedsOnThird() {
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger delays = new AtomicInteger();
        Messaging fake = json -> {
            if (attempts.incrementAndGet() < 3) {
                throw new IllegalStateException("Simulated temp outage");
            }
        };
        Messaging messaging = new RetryingMessaging(fake, threeAttempts(delays));

        messaging.publish("{\"id\":\"test\"}");

        assertEquals(3, attempts.get());
        assertEquals(2, delays.get());
    }

    @Test
    void permanentFailureStopsProperly() {
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger delays = new AtomicInteger();
        IllegalStateException e = new IllegalStateException("Simulated permanent outage");
        Messaging fake = json -> {
            attempts.incrementAndGet();
            throw e;
        };
        Messaging messaging = new RetryingMessaging(fake, threeAttempts(delays));

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> messaging.publish("{\"id\":\"test\"}"));

        assertSame(e, thrown);
        assertEquals(3, attempts.get());
        assertEquals(2, delays.get());
    }

    @Test
    void nonRetryableHaltsImmediately() {
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger delays = new AtomicInteger();
        IllegalArgumentException e = new IllegalArgumentException("Invalid input");
        Messaging fake = json -> {
            attempts.incrementAndGet();
            throw e;
        };
        Messaging messaging = new RetryingMessaging(fake, threeAttempts(delays));

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> messaging.publish("{\"id\":\"test\"}"));

        assertSame(e, thrown);
        assertEquals(1, attempts.get());
        assertEquals(0, delays.get());
    }
}
