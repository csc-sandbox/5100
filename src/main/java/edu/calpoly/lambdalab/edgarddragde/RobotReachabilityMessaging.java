package edu.calpoly.lambdalab.edgarddragde;

import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

/** Sends one unchanged result event directly to SNS and SQS. */
final class RobotReachabilityMessaging implements RobotReachabilityLambda.EventSink, AutoCloseable {
  private final SnsClient sns;
  private final SqsClient sqs;
  private final String topicArn;
  private final String queueUrl;

  RobotReachabilityMessaging(String topicArn, String queueUrl) {
    this(SnsClient.create(), SqsClient.create(), topicArn, queueUrl);
  }

  /** Injectable clients keep this adapter independently unit-testable. */
  RobotReachabilityMessaging(SnsClient sns, SqsClient sqs, String topicArn, String queueUrl) {
    this.sns = sns;
    this.sqs = sqs;
    this.topicArn = topicArn;
    this.queueUrl = queueUrl;
  }

  @Override
  public void publish(String json) {
    RuntimeException failure = null;
    try {
      sns.publish(PublishRequest.builder().topicArn(topicArn).message(json).build());
    } catch (RuntimeException e) {
      failure = e;
    }
    try {
      sqs.sendMessage(SendMessageRequest.builder().queueUrl(queueUrl).messageBody(json).build());
    } catch (RuntimeException e) {
      if (failure == null) failure = e;
      else failure.addSuppressed(e);
    }
    if (failure != null) throw failure;
  }

  @Override
  public void close() {
    sns.close();
    sqs.close();
  }
}
