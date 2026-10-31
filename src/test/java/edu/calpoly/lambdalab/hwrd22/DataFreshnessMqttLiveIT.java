package edu.calpoly.lambdalab.hwrd22;

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

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Live test on public HiveMQ broker.
 * Run with mvn test -Dtest=DataFreshnessMqttLiveIT test
 *
 * @author hwrd22
 * @version 1.0 (October 27, 2026)
 */
@Tag("live")
public class DataFreshnessMqttLiveIT {

    @Test
    void receivesUniqueHiveMqEventAndPublishesAcknowledgementWithinTimeout() throws Exception {
        String id = UUID.randomUUID().toString();
        String acknowledgementTopic = "csc5100/status/" + id;
        String event = "{\"id\":\"" + id
                + "\",\"operation\":\"DataFreshnessLambda\","
                + "\"result\":{\"status\":\"FRESH\",\"age\":5}}";

        CountDownLatch acknowledgementReceived = new CountDownLatch(1);
        DataFreshnessMqttSubscriber subscriber = new DataFreshnessMqttSubscriber(DataFreshnessMqttSubscriber.BROKER, ignored -> { });
        MqttClient client = new MqttClient(
                DataFreshnessMqttSubscriber.BROKER,
                "csc5100-data-freshness-test-" + UUID.randomUUID(),
                new MemoryPersistence()
        );
        try {
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setConnectionTimeout(10);
            client.connect(options);
            client.subscribe(acknowledgementTopic, DataFreshnessMqttSubscriber.QOS, (topic, message) -> acknowledgementReceived.countDown());
            subscriber.start();

            MqttMessage message = new MqttMessage(event.getBytes(StandardCharsets.UTF_8));
            message.setQos(DataFreshnessMqttSubscriber.QOS);
            client.publish(DataFreshnessMqttSubscriber.TOPIC, message);

            assertTrue(acknowledgementReceived.await(15, TimeUnit.SECONDS), "Timed out waiting for ACK on " + acknowledgementTopic);
        } finally {
            subscriber.close();
            if (client.isConnected()) {
                client.disconnect();
            }
            client.close();
        }
    }
}
