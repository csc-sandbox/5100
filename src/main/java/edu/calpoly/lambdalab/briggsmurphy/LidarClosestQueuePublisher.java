package edu.calpoly.lambdalab.briggsmurphy;

import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

/**
 * AWS SQS implementation of the QueuePublisher interface.
 *
 * @author Briggs Murphy
 * @version 1.0
 */
public class LidarClosestQueuePublisher implements QueuePublisher {

  private final SqsClient client;
  private final String queueUrl;

  public LidarClosestQueuePublisher(SqsClient client, String queueUrl) {
    this.client = client;
    this.queueUrl = queueUrl;
  }

  @Override
  public void send(String json) {
    if (queueUrl == null || queueUrl.isBlank()) {
      throw new IllegalStateException(
          "SQS_QUEUE_URL environment variable is not set");
    }

    client.sendMessage(SendMessageRequest.builder()
        .queueUrl(queueUrl)
        .messageBody(json)
        .build());
  }
}
