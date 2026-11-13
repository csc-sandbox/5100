package edu.calpoly.lambdalab.hwrd22;

import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

/**
 * Publishes/Sends Data Freshness as JSON via SNS and SQS.
 *
 * @author hwrd22
 * @version 1.0 (October 27, 2026)
 */
final class DataFreshnessMessaging implements DataFreshnessLambda.Messaging {
    private final SnsClient sns;
    private final SqsClient sqs;
    private final String topicArn;
    private final String queueUrl;

    DataFreshnessMessaging(String topicArn, String queueUrl) {
        this(SnsClient.create(), SqsClient.create(), topicArn, queueUrl);
    }

    DataFreshnessMessaging(SnsClient sns, SqsClient sqs, String topicArn, String queueUrl) {
        this.sns = sns;
        this.sqs = sqs;
        this.topicArn = topicArn;
        this.queueUrl = queueUrl;
    }

    @Override
    public void publish(String json) {
        RuntimeException failure = null;
        if (isConfigured(topicArn)) {
            try {
                sns.publish(PublishRequest.builder().topicArn(topicArn).message(json).build());
            } catch (RuntimeException exception) {
                failure = exception;
            }
        }
        if (isConfigured(queueUrl)) {
            try {
                sqs.sendMessage(SendMessageRequest.builder().queueUrl(queueUrl).messageBody(json).build());
            } catch (RuntimeException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private boolean isConfigured(String value) {
        return value != null && !value.isBlank();
    }
}
