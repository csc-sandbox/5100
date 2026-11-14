package edu.calpoly.lambdalab.daome;

import edu.calpoly.lambdalab.daome.Exceptions.StorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class RetryExecutorTest {

    AtomicInteger attempts;
    AtomicInteger delays;
    Storage fake;
    Storage storage;

    private static RetryExecutor threeAttempts(AtomicInteger delays) {
        return new RetryExecutor(3, failure -> failure instanceof IllegalStateException, delays::incrementAndGet);
    }

    @BeforeEach
    void init() {
        attempts = new AtomicInteger();
        delays = new AtomicInteger();
        fake = new Storage() {

            @Override
            public void save(String id, String json) throws StorageException {
                if (attempts.incrementAndGet() < 3) {throw new IllegalStateException("Simulated temporary outage");}
            }

            @Override
            public String get(String id) throws StorageException {
                return "";
            }
        };
        storage = new RetryStorage(fake, threeAttempts(delays));
    }

    @Test
    void transientFailsSucceedsOnThirdAttempt() {
        storage.save("test", "{\"id\": \"test\"}");

        assertEquals(3, this.attempts.get());
        assertEquals(2, this.delays.get());
    }

    @Test
    void permanentFailure() {
        IllegalStateException outage = new IllegalStateException("Not avaliable");
        fake = new Storage() {

            @Override
            public void save(String id, String json) throws StorageException {
                if (attempts.incrementAndGet() < 3) {throw new IllegalStateException("Simulated temporary outage");}
                throw outage;
            }

            @Override
            public String get(String id) throws StorageException {
                return "";
            }
        };
        storage = new RetryStorage(fake, threeAttempts(delays));
        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> storage.save("test", "{\"id\":" +
                " \"test\"}"));

        assertSame(outage, thrown);
        assertEquals(3, attempts.get());
        assertEquals(2, delays.get());
    }


}
