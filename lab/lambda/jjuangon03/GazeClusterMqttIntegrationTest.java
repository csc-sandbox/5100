package edu.calpoly.lambdalab.jjuangon03;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.Assertions;

@Tag("integration")
@EnabledIfSystemProperty(named = "hivemq.integration", matches = "true")
class GazeClusterMqttIntegrationTest {

    @Test
    void receivesSyntheticEventOnPublicHiveMq() {
        Assertions.assertTimeoutPreemptively(Duration.ofSeconds(20), () -> {
            String id = UUID.randomUUID().toString();
            CountDownLatch received = new CountDownLatch(1);
            AtomicReference<GazeClusterMqttSubscriber.Event> actual = new AtomicReference<>();
            MqttClient client = new MqttClient(GazeClusterMqttSubscriber.BROKER_URI,
                    "jjuangon03-integration-" + UUID.randomUUID());
            try {
                MqttConnectOptions options = new MqttConnectOptions();
                options.setCleanSession(true);
                options.setConnectionTimeout(5);
                client.connect(options);
                client.subscribe(GazeClusterMqttSubscriber.EVENT_TOPIC, 1, (topic, message) -> {
                    GazeClusterMqttSubscriber.Event event =
                            GazeClusterMqttSubscriber.parseEvent(
                                    new String(message.getPayload(), StandardCharsets.UTF_8));
                    if (id.equals(event.id())) {
                        actual.set(event);
                        received.countDown();
                    }
                });

                String json = "{\"id\":\"" + id + "\","
                        + "\"operation\":\"GazeClusterLambda\","
                        + "\"numberOfClusters\":0,\"clusters\":[]}";
                MqttMessage message = new MqttMessage(json.getBytes(StandardCharsets.UTF_8));
                message.setQos(1);
                client.publish(GazeClusterMqttSubscriber.EVENT_TOPIC, message);

                assertTrue(received.await(10, TimeUnit.SECONDS),
                        "Timed out waiting for the synthetic HiveMQ event");
                assertNotNull(actual.get());
                assertEquals(id, actual.get().id());
            } finally {
                if (client.isConnected()) {
                    client.disconnect();
                }
                client.close();
            }
        });
    }
}
