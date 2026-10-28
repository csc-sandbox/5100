package edu.calpoly.lambdalab.edgarddragde;

import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

/**
 * Publishes the result event JSON to the SNS topic (csc5100-events).
 *
 * <p>Independent of SQS: this class knows nothing about queues, and nothing in the SNS path
 * requires an SNS-to-SQS subscription.
 */
final class SnsEventPublisher implements RobotReachabilityLambda.EventSink {
  private final SnsClient sns;
  private final String topicArn;

  SnsEventPublisher(String topicArn) {
    this(SnsClient.create(), topicArn);
  }

  /** Client is injectable so unit tests can run without AWS. */
  SnsEventPublisher(SnsClient sns, String topicArn) {
    this.sns = sns;
    this.topicArn = topicArn;
  }

  @Override
  public void publish(String json) {
    sns.publish(PublishRequest.builder()
        .topicArn(topicArn)
        .message(json)
        .build());
  }
}
