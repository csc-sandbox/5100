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
            ackSubscriber = new MqttClient(LidarSafetyMqttSubscriber.DEFAULT_BROKER, "csc5100-lidar-safety-live-integration-test-" + UUID.randomUUID());
            ackSubscriber.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) throws Exception {
                    String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
                    if (payload.contains("\"id\":\"" + id + "\"") && payload.contains("\"operation\":\"" + LidarSafetyMqttSubscriber.OPERATION + "\"") && payload.contains("\"status\":\"received\"")) {
                        ackReceived.countDown();
                    }
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                }
            });
            ackSubscriber.connect(options);
            ackSubscriber.subscribe(LidarSafetyMqttSubscriber.STATUS_PREFIX + LidarSafetyMqttSubscriber.OPERATION);

            eventPublisher = new MqttClient(LidarSafetyMqttSubscriber.DEFAULT_BROKER, "csc5100-lidar-safety-live-integration-test-" + UUID.randomUUID());
            String event = "{\"id\":\"" + id + "\", \"operation\":\"" + LidarSafetyMqttSubscriber.OPERATION + "\", \"result\":{\"status\":\"WARNING\", \"minDist\":0.2}}";
            MqttMessage msg = new MqttMessage(event.getBytes(StandardCharsets.UTF_8));
            msg.setQos(1);
            eventPublisher.connect(options);
            eventPublisher.publish(LidarSafetyMqttSubscriber.EVENT_PREFIX + LidarSafetyMqttSubscriber.OPERATION, msg);

            assertTrue(ackReceived.await(10, TimeUnit.SECONDS));
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
