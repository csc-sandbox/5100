
package edu.calpoly.lambdalab.adrian0427;

import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

final class DominantAffectMessaging
        implements DominantAffectLambda.Messaging {

    private final SnsClient sns;
    private final SqsClient sqs;
    private final String topicArn;
    private final String queueUrl;

    DominantAffectMessaging(String topicArn, String queueUrl) {
        this(SnsClient.create(), SqsClient.create(), topicArn, queueUrl);
    }

    DominantAffectMessaging(
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
        sns.publish(
                PublishRequest.builder()
                        .topicArn(topicArn)
                        .message(json)
                        .build());

        sqs.sendMessage(
                SendMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .messageBody(json)
                        .build());
    }
}
