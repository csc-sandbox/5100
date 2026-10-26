package edu.calpoly.lambdalab.oxlojalencas;

import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

/**
 * publishes nearest sample events to an sns topic.
 *
 * @author Jess A
 * @version October 25, 2026
 */
final class NearestSampleMessaging implements NearestSampleLambda.Messaging {

    private final SnsClient sns;
    private final String topicArn;

    NearestSampleMessaging(String topicArn) {
        this(SnsClient.create(), topicArn);
    }

    NearestSampleMessaging(SnsClient sns, String topicArn) {
        this.sns = sns;
        this.topicArn = topicArn;
    }

    @Override
    public void publish(String json) {
        sns.publish(PublishRequest.builder()
                .topicArn(topicArn)
                .message(json)
                .build());
    }
}
