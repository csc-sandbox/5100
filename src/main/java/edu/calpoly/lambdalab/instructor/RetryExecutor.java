package edu.calpoly.lambdalab.instructor;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * Executes an operation with a bounded number of total attempts, retrying
 * only failures explicitly classified as transient by the caller.
 *
 * @author Javier Gonzalez-Sanchez
 * @version 2.0 (2026-10-29)
 */
final class RetryExecutor {
  private final int maxAttempts;
  private final Predicate<RuntimeException> retryable;
  private final Runnable sleeper;

  RetryExecutor(int maxAttempts, Predicate<RuntimeException> retryable, Runnable sleeper) {
    if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be positive");
    this.maxAttempts = maxAttempts;
    this.retryable = Objects.requireNonNull(retryable);
    this.sleeper = Objects.requireNonNull(sleeper);
  }

  void execute(Runnable operation) {
    Objects.requireNonNull(operation);
    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      try {
        operation.run();
        return;
      } catch (RuntimeException failure) {
        if (attempt == maxAttempts || !retryable.test(failure)) throw failure;
        sleeper.run();
      }
    }
  }
}
