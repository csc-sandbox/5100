package edu.calpoly.lambdalab.dylangururajan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CoordinateNormalizerReliabilityTest {
  private static final String ID = "c5f1b1b7-ccf5-4b98-80f9-f1123589a123";

  @Test
  void messagingFailureReports500AfterStorageCompleted() {
    AtomicInteger saves = new AtomicInteger();
    Storage storage = new Storage() {
      @Override
      public void save(String id, String json) {
        saves.incrementAndGet();
      }

      @Override
      public String get(String id) {
        return null;
      }
    };
    Messaging failingMessaging = json -> {
      throw new RuntimeException("Simulated messaging failure");
    };
    CoordinateNormalizerLambda lambda =
        new CoordinateNormalizerLambda(storage, failingMessaging, () -> java.util.UUID.fromString(ID));

    Map<String, Object> response = lambda.handleRequest(validPost(), null);

    assertEquals(500, response.get("statusCode"));
    assertEquals(1, saves.get(), "storage completed before messaging failed");
  }

  @Test
  void storageFailurePreventsMessagingFromRunning() {
    AtomicInteger publishes = new AtomicInteger();
    Storage failingStorage = new Storage() {
      @Override
      public void save(String id, String json) {
        throw new RuntimeException("Simulated storage failure");
      }

      @Override
      public String get(String id) {
        return null;
      }
    };
    CoordinateNormalizerLambda lambda = new CoordinateNormalizerLambda(
        failingStorage, json -> publishes.incrementAndGet(), () -> java.util.UUID.fromString(ID));

    Map<String, Object> response = lambda.handleRequest(validPost(), null);

    assertEquals(500, response.get("statusCode"));
    assertEquals(0, publishes.get(), "messaging must not run after storage fails");
  }

  @Test
  void transientLookupFailureSucceedsOnThirdAttempt() {
    AtomicInteger attempts = new AtomicInteger();
    AtomicInteger delays = new AtomicInteger();
    Storage transientStorage = lookupOnlyStorage(id -> {
      if (attempts.incrementAndGet() < 3) {
        throw new TransientFailure();
      }
      return "{\"id\":\"" + id + "\"}";
    });
    RetryingStorage storage = new RetryingStorage(
        transientStorage,
        new RetryExecutor(3, failure -> failure instanceof TransientFailure,
            completedAttempts -> delays.incrementAndGet()));

    String result = storage.get(ID);

    assertEquals("{\"id\":\"" + ID + "\"}", result);
    assertEquals(3, attempts.get());
    assertEquals(2, delays.get());
  }

  @Test
  void retryableLookupFailureStopsAfterThreeAttempts() {
    AtomicInteger attempts = new AtomicInteger();
    Storage alwaysFailing = lookupOnlyStorage(id -> {
      attempts.incrementAndGet();
      throw new TransientFailure();
    });
    RetryingStorage storage = new RetryingStorage(
        alwaysFailing,
        new RetryExecutor(3, failure -> failure instanceof TransientFailure,
            completedAttempts -> { }));

    TransientFailure failure = assertThrows(TransientFailure.class, () -> storage.get(ID));

    assertEquals(3, attempts.get());
    assertSame(TransientFailure.class, failure.getClass());
  }

  @Test
  void nonRetryableLookupFailureStopsImmediately() {
    AtomicInteger attempts = new AtomicInteger();
    IllegalArgumentException expected = new IllegalArgumentException("invalid request");
    Storage invalidLookup = lookupOnlyStorage(id -> {
      attempts.incrementAndGet();
      throw expected;
    });
    RetryingStorage storage = new RetryingStorage(
        invalidLookup,
        new RetryExecutor(3, failure -> failure instanceof TransientFailure,
            completedAttempts -> { }));

    IllegalArgumentException actual =
        assertThrows(IllegalArgumentException.class, () -> storage.get(ID));

    assertSame(expected, actual);
    assertEquals(1, attempts.get());
  }

  private Map<String, Object> validPost() {
    return Map.of(
        "httpMethod", "POST",
        "body", "{\"x\":960,\"y\":540,\"width\":1920,\"height\":1080}");
  }

  private Storage lookupOnlyStorage(Lookup lookup) {
    return new Storage() {
      @Override
      public void save(String id, String json) {
        throw new AssertionError("save was not expected");
      }

      @Override
      public String get(String id) {
        return lookup.get(id);
      }
    };
  }

  @FunctionalInterface
  private interface Lookup {
    String get(String id);
  }

  private static final class TransientFailure extends RuntimeException { }
}
