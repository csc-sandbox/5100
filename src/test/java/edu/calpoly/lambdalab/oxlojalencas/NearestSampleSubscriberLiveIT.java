package edu.calpoly.lambdalab.oxlojalencas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * live test against the public hivemq broker, run by hand only.
 * the name ends in LiveIT, so normal maven runs skip it. run it with:
 * mvn test -Dtest=NearestSampleSubscriberLiveIT
 *
 * @author Jess A
 * @version October 25, 2026
 */
@Tag("live")
class NearestSampleSubscriberLiveIT {

    private static final long TIMEOUT_MILLIS = 10_000;

    @Test
    void acknowledgesEventPublishedToTestTopic() throws Exception {
        String eventTopic = "csc5100/test/nearest-sample/" + UUID.randomUUID();
        String eventId = UUID.randomUUID().toString();
        NearestSampleSubscriber subscriber = new NearestSampleSubscriber(
                NearestSampleSubscriber.BROKER, eventTopic);
        MqttAsyncClient testClient = new MqttAsyncClient(
                NearestSampleSubscriber.BROKER,
                MqttAsyncClient.generateClientId(),
                new MemoryPersistence());
        CountDownLatch ackReceived = new CountDownLatch(1);
        AtomicReference<String> ackPayload = new AtomicReference<>();

        try {
            subscriber.start();
            testClient.connect().waitForCompletion(TIMEOUT_MILLIS);
            testClient.subscribe("csc5100/status/" + eventId, 1,
                    (topic, message) -> {
                        ackPayload.set(new String(
                                message.getPayload(), StandardCharsets.UTF_8));
                        ackReceived.countDown();
                    }).waitForCompletion(TIMEOUT_MILLIS);

            MqttMessage event = new MqttMessage(
                    eventJson(eventId).getBytes(StandardCharsets.UTF_8));
            event.setQos(1);
            testClient.publish(eventTopic, event).waitForCompletion(TIMEOUT_MILLIS);

            assertTrue(ackReceived.await(15, TimeUnit.SECONDS),
                    "no acknowledgment within 15 seconds");
            assertEquals("{\"id\":\"" + eventId + "\",\"status\":\"acknowledged\"}",
                    ackPayload.get());
        } finally {
            subscriber.stop();
            if (testClient.isConnected()) {
                testClient.disconnect().waitForCompletion(TIMEOUT_MILLIS);
            }
            testClient.close();
        }
    }

    // synthetic data only, because the broker is public
    private String eventJson(String id) {
        return "{\"id\":\"" + id + "\","
                + "\"operation\":\"NearestSampleLambda\","
                + "\"result\":{\"nearestSample\":{\"timestamp\":10.4,\"value\":5.1},"
                + "\"timeDifference\":0.1}}";
    }
}
