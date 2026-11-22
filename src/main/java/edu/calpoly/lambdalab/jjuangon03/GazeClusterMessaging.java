package edu.calpoly.lambdalab.jjuangon03;

import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

/** Independently fans out one immutable correlated result to SNS and SQS. */
final class GazeClusterMessaging implements GazeClusterLambda.Messaging {
    private final SnsClient sns;
    private final SqsClient sqs;
    private final String topicArn;
    private final String queueUrl;
    GazeClusterMessaging(String topicArn, String queueUrl) { this(SnsClient.create(), SqsClient.create(), topicArn, queueUrl); }
    GazeClusterMessaging(SnsClient sns, SqsClient sqs, String topicArn, String queueUrl) {
        this.sns = sns; this.sqs = sqs; this.topicArn = topicArn; this.queueUrl = queueUrl;
    }
    @Override public void publish(String json) {
        RuntimeException failure = null;
        if (configured(topicArn)) try {
            sns.publish(PublishRequest.builder().topicArn(topicArn).message(json).build());
        } catch (RuntimeException exception) { failure = exception; }
        if (configured(queueUrl)) try {
            sqs.sendMessage(SendMessageRequest.builder().queueUrl(queueUrl).messageBody(json).build());
        } catch (RuntimeException exception) {
            if (failure == null) failure = exception; else failure.addSuppressed(exception);
        }
        if (failure != null) throw failure;
    }
    private boolean configured(String value) { return value != null && !value.isBlank(); }
}
