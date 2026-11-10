package edu.calpoly.lambdalab.briggsmurphy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.exception.SdkClientException;

class RetryExecutorTest {

  @Test
  void succeedsAfterTwoFailures() {
    AtomicInteger attempts = new AtomicInteger();

    RetryExecutor executor = new RetryExecutor(
        3,
        failure -> failure instanceof SdkClientException,
        () -> { });

    executor.execute(() -> {
      if (attempts.incrementAndGet() < 3) {
        throw SdkClientException.create("Temporary network failure");
      }
    });

    assertEquals(3, attempts.get());
  }

  @Test
  void stopsAfterThreeFailedAttempts() {
    AtomicInteger attempts = new AtomicInteger();

    RetryExecutor executor = new RetryExecutor(
        3,
        failure -> failure instanceof SdkClientException,
        () -> { });

    assertThrows(SdkClientException.class, () ->
        executor.execute(() -> {
          attempts.incrementAndGet();
          throw SdkClientException.create("Temporary network failure");
        }));

    assertEquals(3, attempts.get());
  }

  @Test
  void doesNotRetryNonRetryableFailure() {
    AtomicInteger attempts = new AtomicInteger();

    RetryExecutor executor = new RetryExecutor(
        3,
        failure -> failure instanceof SdkClientException,
        () -> { });

    assertThrows(IllegalArgumentException.class, () ->
        executor.execute(() -> {
          attempts.incrementAndGet();
          throw new IllegalArgumentException("Invalid request");
        }));

    assertEquals(1, attempts.get());
  }
}