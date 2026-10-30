package edu.calpoly.lambdalab.edgarddragde;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

class RobotReachabilityMessagingTest {
  private static final String TOPIC = "arn:aws:sns:us-west-2:123456789012:csc5100-events";
  private static final String QUEUE = "https://sqs.us-west-2.amazonaws.com/123456789012/csc5100-processing";
  private static final String JSON = "{\"id\":\"14caa8ee-ca63-41f4-b57f-074007b2affc\","
      + "\"operation\":\"RobotReachabilityLambda\",\"result\":{\"distance\":1.0,\"reachable\":true}}";

  @Test
  void sendsTheSameJsonDirectlyToSnsAndSqs() {
    AtomicReference<PublishRequest> snsRequest = new AtomicReference<>();
    AtomicReference<SendMessageRequest> sqsRequest = new AtomicReference<>();
    new RobotReachabilityMessaging(sns(snsRequest, false), sqs(sqsRequest, false), TOPIC, QUEUE).publish(JSON);
    assertEquals(TOPIC, snsRequest.get().topicArn());
    assertEquals(JSON, snsRequest.get().message());
    assertEquals(QUEUE, sqsRequest.get().queueUrl());
    assertEquals(JSON, sqsRequest.get().messageBody());
  }

  @Test
  void stillAttemptsSqsWhenSnsFails() {
    AtomicReference<SendMessageRequest> sqsRequest = new AtomicReference<>();
    RobotReachabilityMessaging messaging = new RobotReachabilityMessaging(
        sns(new AtomicReference<>(), true), sqs(sqsRequest, false), TOPIC, QUEUE);
    assertThrows(IllegalStateException.class, () -> messaging.publish(JSON));
    assertEquals(JSON, sqsRequest.get().messageBody());
  }

  @SuppressWarnings("unchecked")
  private static SnsClient sns(AtomicReference<PublishRequest> request, boolean fail) {
    return (SnsClient) Proxy.newProxyInstance(SnsClient.class.getClassLoader(), new Class<?>[] {SnsClient.class},
        (proxy, method, args) -> {
          if ("publish".equals(method.getName())) {
            request.set((PublishRequest) args[0]);
            if (fail) throw new IllegalStateException("SNS unavailable");
            return PublishResponse.builder().messageId("sns-message").build();
          }
          if ("serviceName".equals(method.getName())) return "sns";
          return null;
        });
  }

  @SuppressWarnings("unchecked")
  private static SqsClient sqs(AtomicReference<SendMessageRequest> request, boolean fail) {
    return (SqsClient) Proxy.newProxyInstance(SqsClient.class.getClassLoader(), new Class<?>[] {SqsClient.class},
        (proxy, method, args) -> {
          if ("sendMessage".equals(method.getName())) {
            request.set((SendMessageRequest) args[0]);
            if (fail) throw new IllegalStateException("SQS unavailable");
            return SendMessageResponse.builder().messageId("sqs-message").build();
          }
          if ("serviceName".equals(method.getName())) return "sqs";
          return null;
        });
  }
}
