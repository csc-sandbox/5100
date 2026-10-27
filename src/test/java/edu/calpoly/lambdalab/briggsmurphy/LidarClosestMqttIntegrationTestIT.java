package edu.calpoly.lambdalab.briggsmurphy;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class LidarClosestMqttIntegrationTestIT {

    @Test
    @Tag("integration")
    void receivesEventAndPublishesAcknowledgment() throws Exception {
        String id = UUID.randomUUID().toString();
        String ackTopic =
                LidarClosestMqttConsumer.acknowledgmentTopic(id);

        CountDownLatch acknowledgmentReceived = new CountDownLatch(1);
        AtomicReference<String> receivedAck = new AtomicReference<>();

        String testClientId = "LidarAckTest-" + id;
        MqttClient acknowledgmentClient = new MqttClient(
                LidarClosestMqttConsumer.BROKER_URL,
                testClientId,
                new MemoryPersistence()
        );

        LidarClosestMqttConsumer consumer =
                new LidarClosestMqttConsumer();

        try {
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);

            acknowledgmentClient.connect(options);
            acknowledgmentClient.subscribe(ackTopic, 1);

            acknowledgmentClient.setCallback(
                    new org.eclipse.paho.client.mqttv3.MqttCallback() {
                        @Override
                        public void connectionLost(Throwable cause) {
                            // The test will fail if no acknowledgment arrives.
                        }

                        @Override
                        public void messageArrived(
                                String topic, MqttMessage message) {
                            receivedAck.set(new String(
                                    message.getPayload(),
                                    StandardCharsets.UTF_8
                            ));
                            acknowledgmentReceived.countDown();
                        }

                        @Override
                        public void deliveryComplete(
                                org.eclipse.paho.client.mqttv3
                                        .IMqttDeliveryToken token) {
                            // No action needed.
                        }
                    }
            );

            consumer.start();

            String eventJson = """
                    {
                      "id": "%s",
                      "operation": "LidarClosestLambda",
                      "result": {"distance": 12.5}
                    }
                    """.formatted(id);

            MqttMessage eventMessage = new MqttMessage(
                    eventJson.getBytes(StandardCharsets.UTF_8)
            );
            eventMessage.setQos(1);

            acknowledgmentClient.publish(
                    LidarClosestMqttConsumer.EVENT_TOPIC,
                    eventMessage
            );

            boolean received = acknowledgmentReceived.await(
                    10, TimeUnit.SECONDS
            );

            assertTrue(
                    received,
                    "Timed out waiting for the MQTT acknowledgment"
            );

            assertNotNull(receivedAck.get());
            assertTrue(receivedAck.get().contains(id));
            assertTrue(receivedAck.get().contains("\"status\":\"ACK\""));

        } finally {
            try {
                consumer.close();
            } finally {
                if (acknowledgmentClient.isConnected()) {
                    acknowledgmentClient.disconnect();
                }
                acknowledgmentClient.close();
            }
        }
    }
}
