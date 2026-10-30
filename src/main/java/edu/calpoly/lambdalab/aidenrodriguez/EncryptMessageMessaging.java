package edu.calpoly.lambdalab.aidenrodriguez;

import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

/** Publishes EncryptMessageLambda events to the configured csc5100-events ARN. */
final class EncryptMessageMessaging implements EncryptMessageLambda.Messaging {
    private final SnsClient sns;
    private final String topicArn;

    EncryptMessageMessaging(String topicArn) {
        this(SnsClient.create(), topicArn);
    }

    EncryptMessageMessaging(SnsClient sns, String topicArn) {
        if (topicArn == null || topicArn.isBlank()) {
            throw new IllegalArgumentException("SNS topic ARN is required");
        }
        this.sns = sns;
        this.topicArn = topicArn;
    }

    @Override
    public void publish(String json) {
        sns.publish(PublishRequest.builder().topicArn(topicArn).message(json).build());
    }
}
