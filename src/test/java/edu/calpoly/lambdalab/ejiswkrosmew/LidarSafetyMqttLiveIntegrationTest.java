package edu.calpoly.lambdalab.ejiswkrosmew;

import org.eclipse.paho.client.mqttv3.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
public class LidarSafetyMqttLiveIntegrationTest {
    public static final String DEFAULT_BROKER = "tcp://broker.hivemq.com:1883";
    public static final String EVENT_PREFIX = "csc5100/events/";
    public static final String STATUS_PREFIX = "csc5100/status/";
    public static final String OPERATION = "LidarSafetyLambda";

    @Test
    void receiveEventAndValidAck() throws MqttException, InterruptedException {
        String id = UUID.randomUUID().toString();
        CountDownLatch ackReceived = new CountDownLatch(1);
        LidarSafetyMqttSubscriber subscriber = new LidarSafetyMqttSubscriber();
        subscriber.start();

        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);
        options.setAutomaticReconnect(true);
        options.setConnectionTimeout(10);

        MqttClient ackSubscriber = null;
        MqttClient eventPublisher = null;
        try {
            ackSubscriber = new MqttClient(DEFAULT_BROKER, "csc5100-lidar-safety-live-integration-test-" + UUID.randomUUID());
            ackSubscriber.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) throws Exception {
                    String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
                    if (payload.contains("\"id\":\"" + id + "\"") && payload.contains("\"operation\":\"" + OPERATION + "\"") && payload.contains("\"status\":\"received\"")) {
                        ackReceived.countDown();
                    }
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                }
            });
            ackSubscriber.connect(options);
            ackSubscriber.subscribe(STATUS_PREFIX + id, 1);

            eventPublisher = new MqttClient(DEFAULT_BROKER, "csc5100-lidar-safety-live-integration-test-" + UUID.randomUUID());
            String event = "{\"id\":\"" + id + "\", \"operation\":\"" + OPERATION + "\", \"result\":{\"status\":\"WARNING\", \"minDist\":0.2}}";
            MqttMessage msg = new MqttMessage(event.getBytes(StandardCharsets.UTF_8));
            msg.setQos(1);
            eventPublisher.connect(options);
            eventPublisher.publish(EVENT_PREFIX + OPERATION, msg);

            assertTrue(ackReceived.await(10, TimeUnit.SECONDS), "Timed out waiting for ACK on topic " + STATUS_PREFIX + id);
        } finally {
            if (ackSubscriber != null) {
                if (ackSubscriber.isConnected()) {
                    ackSubscriber.disconnect();
                }
                ackSubscriber.close();
            }

            if (eventPublisher != null) {
                if (eventPublisher.isConnected()) {
                    eventPublisher.disconnect();
                }
                eventPublisher.close();
            }
        }
    }
}
