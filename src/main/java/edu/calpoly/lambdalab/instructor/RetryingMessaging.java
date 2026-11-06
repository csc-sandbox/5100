package edu.calpoly.lambdalab.instructor;

import java.util.Objects;

/**
 * Decorates a Messaging implementation with bounded retries.
 *
 * <p>Only use this decorator when repeating the entire publish operation is
 * safe. In particular, TemperatureMessaging publishes to SNS and then SQS:
 * if SNS succeeds but SQS fails, retrying the entire operation can publish
 * a duplicate SNS event. Production use requires idempotency/deduplication
 * or retries scoped to individual safe operations.
 *
 * @author Javier Gonzalez-Sanchez
 * @version 2.0 (2026-10-29)
 */
final class RetryingMessaging implements Messaging {
  private final Messaging delegate;
  private final RetryExecutor retryExecutor;

  RetryingMessaging(Messaging delegate, RetryExecutor retryExecutor) {
    this.delegate = Objects.requireNonNull(delegate);
    this.retryExecutor = Objects.requireNonNull(retryExecutor);
  }

  @Override
  public void publish(String json) {
    retryExecutor.execute(() -> delegate.publish(json));
  }
}
