package edu.calpoly.lambdalab.dylangururajan;

import java.util.Objects;

/** Adds bounded retries to the idempotent storage lookup while leaving writes single-attempt. */
final class RetryingStorage implements Storage {
  private final Storage delegate;
  private final RetryExecutor retryExecutor;

  RetryingStorage(Storage delegate, RetryExecutor retryExecutor) {
    this.delegate = Objects.requireNonNull(delegate);
    this.retryExecutor = Objects.requireNonNull(retryExecutor);
  }

  @Override
  public void save(String id, String json) {
    delegate.save(id, json);
  }

  @Override
  public String get(String id) {
    return retryExecutor.execute(() -> delegate.get(id));
  }
}
