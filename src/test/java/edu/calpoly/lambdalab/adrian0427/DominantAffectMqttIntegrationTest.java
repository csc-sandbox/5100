package edu.calpoly.lambdalab.adrian0427;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
@EnabledIfSystemProperty(named = "runMqttIntegration", matches = "true")
class DominantAffectMqttIntegrationTest {

    private static final String BROKER =
            "tcp://broker.hivemq.com:1883";

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void receivesEventAndPublishesAck() throws Exception {
        String id = UUID.randomUUID().toString();
        String ackTopic = DominantAffectMqttEvent.ackTopic(id);

        CountDownLatch ackReceived = new CountDownLatch(1);
        AtomicReference<String> receivedAck = new AtomicReference<>();

        MqttClient observer =
                new MqttClient(BROKER, MqttClient.generateClientId());

        try {
            observer.connect();

            observer.subscribe(ackTopic, 1, (topic, message) -> {
                receivedAck.set(new String(
                        message.getPayload(), StandardCharsets.UTF_8));
                ackReceived.countDown();
            });

            try (DominantAffectMqttClient desktop =
                         new DominantAffectMqttClient(BROKER)) {

                desktop.connect();

                String eventJson = mapper.writeValueAsString(Map.of(
                        "id", id,
                        "operation", "DominantAffectLambda",
                        "result", Map.of(
                                "metric", "joy",
                                "value", 0.9)));

                MqttMessage message = new MqttMessage(
                        eventJson.getBytes(StandardCharsets.UTF_8));
                message.setQos(1);

                observer.publish(
                        DominantAffectMqttEvent.EVENT_TOPIC, message);

                assertTrue(
                        ackReceived.await(10, TimeUnit.SECONDS),
                        "No MQTT acknowledgment received within 10 seconds");

                Map<?, ?> ack =
                        mapper.readValue(receivedAck.get(), Map.class);

                assertEquals(id, ack.get("id"));
                assertEquals("DominantAffectLambda", ack.get("operation"));
                assertEquals("received", ack.get("status"));
            }
        } finally {
            if (observer.isConnected()) {
                observer.disconnect();
            }
            observer.close();
        }
    }
}
