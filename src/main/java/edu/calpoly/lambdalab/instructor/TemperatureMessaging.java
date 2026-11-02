package edu.calpoly.lambdalab.instructor;

import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

/**
 * Publishes temperature events independently to Amazon SNS and Amazon SQS, reporting each operation through structured observability logs.
 *
 * @author Javier Gonzalez-Sanchez
 * @version 2.0 (2026-10-29)
 */
final class TemperatureMessaging implements TemperatureLambda.Messaging {
  private final SnsClient sns = SnsClient.create();
  private final SqsClient sqs = SqsClient.create();
  private final String topicArn;
  private final String queueUrl;

  TemperatureMessaging(String topicArn, String queueUrl) {
    this.topicArn = topicArn;
    this.queueUrl = queueUrl;
  }

  @Override
  public void publish(String json) {
    String id = null;
    try { id = new ObjectMapper().readTree(json).path("id").asText(null); }
    catch (Exception ignored) { /* Never log full payloads. */ }
    long snsStart = System.nanoTime();
    try {
      sns.publish(PublishRequest.builder().topicArn(topicArn).message(json).build());
      TemperatureObservability.log(id, null, "SNS", "SNS_PUBLISHED", "SUCCESS", snsStart);
    } catch (RuntimeException e) {
      TemperatureObservability.log(id, null, "SNS", "SNS_PUBLISHED", "FAILURE", snsStart);
      throw e;
    }
    long sqsStart = System.nanoTime();
    try {
    sqs.sendMessage(SendMessageRequest.builder().queueUrl(queueUrl)
        .messageBody(json).build());
    TemperatureObservability.log(id, null, "SQS", "SQS_SENT", "SUCCESS", sqsStart);
    } catch (RuntimeException e) {
      TemperatureObservability.log(id, null, "SQS", "SQS_SENT", "FAILURE", sqsStart);
      throw e;
    }
  }
}
