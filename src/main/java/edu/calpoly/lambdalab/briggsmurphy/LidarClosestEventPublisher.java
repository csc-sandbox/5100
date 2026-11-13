package edu.calpoly.lambdalab.briggsmurphy;

import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

/**
 * AWS SNS implementation of the EventPublisher interface.
 *
 * @author Briggs Murphy
 * @version 1.0
 */
public class LidarClosestEventPublisher implements EventPublisher {

  private final SnsClient client;
  private final String topicArn;

  public LidarClosestEventPublisher(SnsClient client, String topicArn) {
    this.client = client;
    this.topicArn = topicArn;
  }

  @Override
  public void publish(String json) {
    if (topicArn == null || topicArn.isBlank()) {
      throw new IllegalStateException(
          "SNS_TOPIC_ARN environment variable is not set");
    }

    client.publish(PublishRequest.builder()
        .topicArn(topicArn)
        .message(json)
        .build());
  }
}
