package edu.calpoly.lambdalab.aidenrodriguez;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Opt-in public-broker check; it publishes synthetic data only. */
@Tag("live")
@EnabledIfEnvironmentVariable(named = "RUN_HIVEMQ_LIVE_TESTS", matches = "true")
class EncryptMessageHiveMqLiveIT {
    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void receivesSyntheticEventAndAcknowledgesMatchingUuid() throws Exception {
        String id = UUID.randomUUID().toString();
        String expectedTopic = EncryptMessageDesktopSubscriber.STATUS_TOPIC_PREFIX + id;
        CountDownLatch acknowledgementReceived = new CountDownLatch(1);
        MqttClient acknowledgementListener = client("status-listener");
        EncryptMessageDesktopSubscriber desktopSubscriber = new EncryptMessageDesktopSubscriber(
                EncryptMessageDesktopSubscriber.DEFAULT_BROKER,
                "csc5100-encrypt-live-desktop-" + UUID.randomUUID());
        MqttClient publisher = client("publisher");
        try {
            connect(acknowledgementListener);
            acknowledgementListener.subscribe(expectedTopic, 1,
                    (topic, message) -> {
                        try {
                            JsonNode body = MAPPER.readTree(
                                    new String(message.getPayload(), StandardCharsets.UTF_8));
                            if (expectedTopic.equals(topic) && id.equals(body.path("id").asText())
                                    && "received".equals(body.path("status").asText())) {
                                acknowledgementReceived.countDown();
                            }
                        } catch (Exception ignored) {
                            // Ignore malformed messages on the public test broker.
                        }
                    });

            desktopSubscriber.start();

            connect(publisher);
            String event = "{\"id\":\"" + id + "\",\"operation\":\"EncryptMessageLambda\","
                    + "\"result\":{\"synthetic\":true}}";
            MqttMessage message = new MqttMessage(event.getBytes(StandardCharsets.UTF_8));
            message.setQos(1);
            publisher.getTopic(EncryptMessageDesktopSubscriber.EVENT_TOPIC)
                    .publish(message).waitForCompletion();

            assertTrue(acknowledgementReceived.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS),
                    "Timed out waiting for acknowledgement of " + id);
        } finally {
            close(publisher);
            desktopSubscriber.close();
            close(acknowledgementListener);
        }
    }

    private static MqttClient client(String purpose) throws Exception {
        return new MqttClient(EncryptMessageDesktopSubscriber.DEFAULT_BROKER,
                "csc5100-encrypt-live-" + purpose + "-" + UUID.randomUUID(), new MemoryPersistence());
    }

    private static void connect(MqttClient client) throws Exception {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);
        options.setConnectionTimeout(10);
        client.connect(options);
    }

    private static void close(MqttClient client) {
        try {
            if (client.isConnected()) client.disconnect();
        } catch (MqttException ignored) {
            // This is test cleanup; the public client is being discarded.
        }
        try {
            client.close();
        } catch (MqttException ignored) {
            // This is test cleanup; the public client is being discarded.
        }
    }
}
