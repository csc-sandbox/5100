package edu.calpoly.lambdalab.aidenrodriguez;

import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

/** Sends unchanged A04 routing envelopes to the csc5100-processing queue URL. */
final class EncryptMessageSqsMessaging implements EncryptMessageLambda.Messaging {
    private final SqsClient sqs;
    private final String queueUrl;

    EncryptMessageSqsMessaging(String queueUrl) {
        this(SqsClient.create(), queueUrl);
    }

    EncryptMessageSqsMessaging(SqsClient sqs, String queueUrl) {
        if (queueUrl == null || queueUrl.isBlank()) {
            throw new IllegalArgumentException("SQS queue URL is required");
        }
        this.sqs = sqs;
        this.queueUrl = queueUrl;
    }

    @Override
    public void publish(String json) {
        sqs.sendMessage(SendMessageRequest.builder().queueUrl(queueUrl).messageBody(json).build());
    }
}
