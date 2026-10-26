package edu.calpoly.lambdalab.oxlojalencas;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;

/**
 * tests for NearestSampleMessaging.
 *
 * @author Jess A
 * @version October 25, 2026
 */
class NearestSampleMessagingTest {

    private static final String TOPIC_ARN =
            "arn:aws:sns:us-west-2:123456789012:csc5100-events";

    @Test
    void publishesJsonToTopic() {
        StubSns sns = new StubSns();
        NearestSampleMessaging messaging =
                new NearestSampleMessaging(sns, TOPIC_ARN);

        messaging.publish("{\"id\":\"abc\"}");

        assertEquals(TOPIC_ARN, sns.publishedRequest.topicArn());
        assertEquals("{\"id\":\"abc\"}", sns.publishedRequest.message());
    }

    /** records the published request instead of calling sns. */
    private static class StubSns implements SnsClient {
        private PublishRequest publishedRequest;

        @Override
        public PublishResponse publish(PublishRequest request) {
            publishedRequest = request;
            return PublishResponse.builder().build();
        }

        @Override
        public String serviceName() {
            return "sns";
        }

        @Override
        public void close() {
        }
    }
}
