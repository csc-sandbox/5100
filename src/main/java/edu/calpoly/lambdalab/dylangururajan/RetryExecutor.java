package edu.calpoly.lambdalab.dylangururajan;

import java.util.Objects;
import java.util.function.Predicate;

/** Executes an operation at most three times and retries only classified transient failures. */
final class RetryExecutor {
  @FunctionalInterface
  interface Operation<T> {
    T execute();
  }

  @FunctionalInterface
  interface Delay {
    void pause(int completedAttempts) throws InterruptedException;
  }

  private final int maxAttempts;
  private final Predicate<RuntimeException> isRetryable;
  private final Delay delay;

  RetryExecutor(
      int maxAttempts, Predicate<RuntimeException> isRetryable, Delay delay) {
    if (maxAttempts < 1 || maxAttempts > 3) {
      throw new IllegalArgumentException("maxAttempts must be between 1 and 3");
    }
    this.maxAttempts = maxAttempts;
    this.isRetryable = Objects.requireNonNull(isRetryable);
    this.delay = Objects.requireNonNull(delay);
  }

  <T> T execute(Operation<T> operation) {
    Objects.requireNonNull(operation);
    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      try {
        return operation.execute();
      } catch (RuntimeException failure) {
        if (attempt == maxAttempts || !isRetryable.test(failure)) {
          throw failure;
        }
        try {
          delay.pause(attempt);
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException("Retry delay interrupted", interrupted);
        }
      }
    }
    throw new IllegalStateException("Retry loop ended unexpectedly");
  }
}
