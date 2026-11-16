package edu.calpoly.lambdalab.jjuangon03;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Opt-in public-broker integration test; run with -Dhivemq.integration=true -Dgroups=integration. */
@Tag("integration")
class GazeClusterMqttLiveIT {
    @Test
    void receivesHiveMqEventAndPublishesUuidSpecificReceipt() throws Exception {
        if (!Boolean.getBoolean("hivemq.integration")) return;
        String id = UUID.randomUUID().toString();
        String event = "{\"id\":\"" + id + "\",\"operation\":\"GazeClusterLambda\","
                + "\"result\":{\"numberOfClusters\":0,\"clusters\":[]}}";
        CountDownLatch received = new CountDownLatch(1);
        GazeClusterMqttSubscriber subscriber = new GazeClusterMqttSubscriber(
                GazeClusterMqttSubscriber.DEFAULT_BROKER, ignored -> { });
        MqttClient publisher = new MqttClient(GazeClusterMqttSubscriber.DEFAULT_BROKER,
                "csc5100-gaze-test-" + UUID.randomUUID(), new MemoryPersistence());
        try {
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setConnectionTimeout(10);
            publisher.connect(options);
            publisher.subscribe(GazeClusterMqttSubscriber.STATUS_PREFIX + id, GazeClusterMqttSubscriber.QOS,
                    (topic, message) -> received.countDown());
            subscriber.start();
            MqttMessage message = new MqttMessage(event.getBytes(StandardCharsets.UTF_8));
            message.setQos(GazeClusterMqttSubscriber.QOS);
            publisher.publish(GazeClusterMqttSubscriber.EVENT_TOPIC, message);
            assertTrue(received.await(15, TimeUnit.SECONDS), "Timed out waiting for acknowledgment");
        } finally {
            subscriber.close();
            if (publisher.isConnected()) publisher.disconnect();
            publisher.close();
        }
    }
}
