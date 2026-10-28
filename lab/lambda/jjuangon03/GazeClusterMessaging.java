package edu.calpoly.lambdalab.jjuangon03;

import java.util.function.Consumer;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

/** Publishes each result independently to configured SNS and SQS destinations. */
final class GazeClusterMessaging implements GazeClusterLambda.Messaging {
    private final Consumer<String> snsPublisher;
    private final Consumer<String> sqsPublisher;

    GazeClusterMessaging(String topicArn, String queueUrl) {
        if (topicArn == null || topicArn.isBlank()) {
            snsPublisher = null;
        } else {
            SnsClient sns = SnsClient.create();
            snsPublisher = json -> sns.publish(
                    PublishRequest.builder().topicArn(topicArn).message(json).build());
        }
        if (queueUrl == null || queueUrl.isBlank()) {
            sqsPublisher = null;
        } else {
            SqsClient sqs = SqsClient.create();
            sqsPublisher = json -> sqs.sendMessage(SendMessageRequest.builder()
                    .queueUrl(queueUrl).messageBody(json).build());
        }
    }

    GazeClusterMessaging(Consumer<String> snsPublisher, Consumer<String> sqsPublisher) {
        this.snsPublisher = snsPublisher;
        this.sqsPublisher = sqsPublisher;
    }

    @Override
    public void publish(String json) {
        RuntimeException failure = null;
        if (snsPublisher != null) {
            try {
                snsPublisher.accept(json);
            } catch (RuntimeException exception) {
                failure = exception;
            }
        }
        if (sqsPublisher != null) {
            try {
                sqsPublisher.accept(json);
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
}
