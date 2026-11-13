package edu.calpoly.lambdalab.edgarddragde;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;

class SnsEventPublisherTest {

  private static final String TOPIC_ARN = "arn:aws:sns:us-west-2:123456789012:csc5100-events";

  /** Minimal SNS: records the last publish request, or fails on demand. */
  private static final class StubSns implements SnsClient {
    PublishRequest lastPublish;
    int publishCount;
    boolean failing;

    @Override
    public String serviceName() {
      return "sns";
    }

    @Override
    public void close() { }

    @Override
    public PublishResponse publish(PublishRequest request) {
      if (failing) {
        throw new IllegalStateException("simulated SNS outage");
      }
      lastPublish = request;
      publishCount++;
      return PublishResponse.builder().messageId("stub-message-id").build();
    }
  }

  @Test
  void publishesTheExactJsonToTheConfiguredTopic() {
    StubSns sns = new StubSns();
    String json = "{\"id\":\"14caa8ee-ca63-41f4-b57f-074007b2affc\","
        + "\"operation\":\"RobotReachabilityLambda\","
        + "\"result\":{\"distance\":0.37416573867739417,\"reachable\":true}}";

    new SnsEventPublisher(sns, TOPIC_ARN).publish(json);

    assertEquals(1, sns.publishCount);
    assertEquals(TOPIC_ARN, sns.lastPublish.topicArn());
    assertEquals(json, sns.lastPublish.message());
  }

  @Test
  void sdkFailurePropagatesSoTheCallerCanReportIt() {
    StubSns sns = new StubSns();
    sns.failing = true;

    assertThrows(IllegalStateException.class, () -> new SnsEventPublisher(sns, TOPIC_ARN).publish("{}"));
    assertEquals(0, sns.publishCount);
  }
}
