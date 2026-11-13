package edu.calpoly.lambdalab.oxlojalencas;

import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

/**
 * publishes nearest sample events to sns and sqs, each on its own.
 *
 * @author Jess A
 * @version October 25, 2026
 */
final class NearestSampleMessaging implements Messaging {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SnsClient sns;
    private final SqsClient sqs;
    private final String topicArn;
    private final String queueUrl;

    NearestSampleMessaging(String topicArn, String queueUrl) {
        this(SnsClient.create(), SqsClient.create(), topicArn, queueUrl);
    }

    NearestSampleMessaging(
            SnsClient sns,
            SqsClient sqs,
            String topicArn,
            String queueUrl) {
        this.sns = sns;
        this.sqs = sqs;
        this.topicArn = topicArn;
        this.queueUrl = queueUrl;
    }

    @Override
    public void publish(String json) {
        String id = idOf(json);
        RuntimeException failure = null;

        // neither send depends on the other, so both are always attempted
        try {
            NearestSampleObservability.timed(id, "SNS", "SNS_PUBLISHED", () ->
                    sns.publish(PublishRequest.builder()
                            .topicArn(topicArn)
                            .message(json)
                            .build()));
        } catch (RuntimeException exception) {
            failure = exception;
        }

        try {
            NearestSampleObservability.timed(id, "SQS", "SQS_SENT", () ->
                    sqs.sendMessage(SendMessageRequest.builder()
                            .queueUrl(queueUrl)
                            .messageBody(json)
                            .build()));
        } catch (RuntimeException exception) {
            if (failure == null) {
                failure = exception;
            } else {
                failure.addSuppressed(exception);
            }
        }

        if (failure != null) {
            throw failure;
        }
    }

    // the id is only read for the logs; the event json itself is never logged
    private static String idOf(String json) {
        try {
            return MAPPER.readTree(json).path("id").asText(null);
        } catch (Exception exception) {
            return null;
        }
    }
}
