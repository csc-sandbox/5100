package edu.calpoly.lambdalab.paulmotter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Live integration test against the public HiveMQ broker. Publishes a synthetic
 * event with a unique id and waits (bounded) for the client's ACK.
 *
 * Skipped by default. Run with:
 * {@code mvn test -Dtest=RobotDistanceDesktopClientLiveTest -Dhivemq.live=true}
 *
 * @author Paul Motter (PaulMotter)
 * @version 1.0 (10/21/2026)
 */
@Tag("integration")
@EnabledIfSystemProperty(named = "hivemq.live", matches = "true")
class RobotDistanceDesktopClientLiveTest {
    private static final long ACK_TIMEOUT_SECONDS = 15;

    @Test
    @Timeout(value = 45, unit = TimeUnit.SECONDS)
    void acknowledgesSyntheticEventOverHiveMq() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String id = UUID.randomUUID().toString();
        CountDownLatch ackReceived = new CountDownLatch(1);
        AtomicReference<String> ackJson = new AtomicReference<>();
        List<String> displayed = Collections.synchronizedList(new ArrayList<>());


        RobotDistanceDesktopClient app =
            new RobotDistanceDesktopClient(RobotDistanceDesktopClient.DEFAULT_BROKER, displayed::add);
        MqttClient probe = new MqttClient(RobotDistanceDesktopClient.DEFAULT_BROKER,
            "csc5100-paulmotter-test-" + UUID.randomUUID(), new MemoryPersistence());
        try {
            app.start();

            // SETUP a probe that will listen for an acknowlagement.
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setConnectionTimeout(10);
            probe.connect(options);
            probe.subscribe(RobotDistanceEventHandler.STATUS_TOPIC+id, 1, (topic, message) -> {
                String json = new String(message.getPayload(), StandardCharsets.UTF_8);
                try {
                    if (id.equals(mapper.readTree(json).path("id").asText())) {
                        ackJson.set(json);
                        ackReceived.countDown();
                    }
                } catch (Exception ignored) {
                    // Unrelated non-JSON traffic.
                }
            });

            // Publish an event like SQS handler will publish.
            String event = "{\"id\":\"" + id + "\",\"operation\":\"RobotDistanceLambda\","
                + "\"result\":7.0,\"synthetic\":true}";
            MqttMessage message = new MqttMessage(event.getBytes(StandardCharsets.UTF_8));
            message.setQos(1);
            probe.publish(RobotDistanceEventHandler.EVENT_TOPIC, message);

            // Wait for the message aknowlagement.
            assertTrue(ackReceived.await(ACK_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                "No ACK for id " + id + " within " + ACK_TIMEOUT_SECONDS + "s");

            // read the aknowlagement.
            JsonNode ack = mapper.readTree(ackJson.get());

            // make sure the acknowlagement matches the sent values.
            assertEquals("ACK", ack.get("status").asText());
            assertEquals(7.0, ack.get("result").asDouble());
            // make sure the id was displayed in the DesktopClient.
            assertTrue(displayed.stream().anyMatch(line -> line.contains(id)),
                "event should be displayed");
        } finally {
            app.close();
            if (probe.isConnected()) probe.disconnect();
            probe.close();
        }
    }
}
