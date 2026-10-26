package edu.calpoly.lambdalab.oxlojalencas;

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
final class NearestSampleMessaging implements NearestSampleLambda.Messaging {

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
        RuntimeException failure = null;

        // neither send depends on the other, so both are always attempted
        try {
            sns.publish(PublishRequest.builder()
                    .topicArn(topicArn)
                    .message(json)
                    .build());
        } catch (RuntimeException exception) {
            failure = exception;
        }

        try {
            sqs.sendMessage(SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(json)
                    .build());
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
}
