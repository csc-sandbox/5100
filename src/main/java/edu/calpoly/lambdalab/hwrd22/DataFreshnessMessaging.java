package edu.calpoly.lambdalab.hwrd22;

import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

/**
 * Publishes/Sends Data Freshness as JSON via SNS and SQS.
 *
 * @author Howard Jiang (hwrd22)
 * @version 1.1 (November 9, 2026)
 */
final class DataFreshnessMessaging implements Messaging {
    private final SnsClient sns;
    private final SqsClient sqs;
    private final String topicArn;
    private final String queueUrl;

    DataFreshnessMessaging(String topicArn, String queueUrl) {
        this(SnsClient.create(), SqsClient.builder()
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .retryPolicy(RetryPolicy.none()).build()).build(), topicArn, queueUrl);
    }

    DataFreshnessMessaging(SnsClient sns, SqsClient sqs, String topicArn, String queueUrl) {
        this.sns = sns;
        this.sqs = sqs;
        this.topicArn = topicArn;
        this.queueUrl = queueUrl;
    }

    @Override
    public void publish(String json) {
        String id = null;
        try { id = new ObjectMapper().readTree(json).path("id").asText(null); } catch (Exception ignored) { }

        long snsStart = System.nanoTime();
        if (isConfigured(topicArn)) {
            try {
                sns.publish(PublishRequest.builder().topicArn(topicArn).message(json).build());
                DataFreshnessObservability.log(id, null, "SNS", "SNS_PUBLISHED", "SUCCESS", snsStart);
            } catch (RuntimeException exception) {
                DataFreshnessObservability.log(id, null, "SNS", "SNS_PUBLISHED", "FAILURE", snsStart);
                throw exception;
            }
        }

        long sqsStart = System.nanoTime();
        if (isConfigured(queueUrl)) {
            try {
                sqs.sendMessage(SendMessageRequest.builder().queueUrl(queueUrl).messageBody(json).build());
                DataFreshnessObservability.log(id, null, "SQS", "SQS_SENT", "SUCCESS", sqsStart);
            } catch (RuntimeException exception) {
                DataFreshnessObservability.log(id, null, "SQS", "SQS_SENT", "FAILURE", sqsStart);
                throw exception;
            }
        }
    }

    private boolean isConfigured(String value) {
        return value != null && !value.isBlank();
    }
}
