package edu.calpoly.lambdalab.oxlojalencas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

/**
 * tests for NearestSampleMessaging.
 *
 * @author Jess A
 * @version October 25, 2026
 */
class NearestSampleMessagingTest {

    private static final String TOPIC_ARN =
            "arn:aws:sns:us-west-2:123456789012:csc5100-events";
    private static final String QUEUE_URL =
            "https://sqs.us-west-2.amazonaws.com/123456789012/test-queue";
    private static final String JSON = "{\"id\":\"abc\"}";

    @Test
    void sendsSameJsonToTopicAndQueue() {
        StubSns sns = new StubSns();
        StubSqs sqs = new StubSqs();
        NearestSampleMessaging messaging =
                new NearestSampleMessaging(sns, sqs, TOPIC_ARN, QUEUE_URL);

        messaging.publish(JSON);

        assertEquals(TOPIC_ARN, sns.publishedRequest.topicArn());
        assertEquals(JSON, sns.publishedRequest.message());
        assertEquals(QUEUE_URL, sqs.sentRequest.queueUrl());
        assertEquals(JSON, sqs.sentRequest.messageBody());
    }

    @Test
    void snsFailureStillSendsToQueue() {
        StubSns sns = new StubSns();
        sns.fails = true;
        StubSqs sqs = new StubSqs();
        NearestSampleMessaging messaging =
                new NearestSampleMessaging(sns, sqs, TOPIC_ARN, QUEUE_URL);

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class, () -> messaging.publish(JSON));

        assertEquals("sns unavailable", thrown.getMessage());
        assertNotNull(sqs.sentRequest);
    }

    @Test
    void sqsFailureStillPublishesToTopic() {
        StubSns sns = new StubSns();
        StubSqs sqs = new StubSqs();
        sqs.fails = true;
        NearestSampleMessaging messaging =
                new NearestSampleMessaging(sns, sqs, TOPIC_ARN, QUEUE_URL);

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class, () -> messaging.publish(JSON));

        assertEquals("sqs unavailable", thrown.getMessage());
        assertNotNull(sns.publishedRequest);
    }

    /** records the published request instead of calling sns. */
    private static class StubSns implements SnsClient {
        private PublishRequest publishedRequest;
        private boolean fails;

        @Override
        public PublishResponse publish(PublishRequest request) {
            if (fails) {
                throw new IllegalStateException("sns unavailable");
            }
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

    /** records the sent message instead of calling sqs. */
    private static class StubSqs implements SqsClient {
        private SendMessageRequest sentRequest;
        private boolean fails;

        @Override
        public SendMessageResponse sendMessage(SendMessageRequest request) {
            if (fails) {
                throw new IllegalStateException("sqs unavailable");
            }
            sentRequest = request;
            return SendMessageResponse.builder().build();
        }

        @Override
        public String serviceName() {
            return "sqs";
        }

        @Override
        public void close() {
        }
    }
}
