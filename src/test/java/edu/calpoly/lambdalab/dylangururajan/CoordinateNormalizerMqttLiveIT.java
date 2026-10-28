package edu.calpoly.lambdalab.dylangururajan;

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

/** Opt-in public-broker test. Run with: mvn -Plive-mqtt verify. */
@Tag("integration")
class CoordinateNormalizerMqttLiveIT {

  @Test
  void receivesUniqueHiveMqEventAndPublishesAckWithinTimeout() throws Exception {
    String id = UUID.randomUUID().toString();
    String ackTopic = CoordinateNormalizerMqttSubscriber.STATUS_PREFIX + id;
    String event = "{\"id\":\"" + id
        + "\",\"operation\":\"CoordinateNormalizerLambda\","
        + "\"result\":{\"normalizedX\":0.25,\"normalizedY\":0.75}}";
    CountDownLatch acknowledgmentReceived = new CountDownLatch(1);

    CoordinateNormalizerMqttSubscriber subscriber =
        new CoordinateNormalizerMqttSubscriber(
            CoordinateNormalizerMqttSubscriber.DEFAULT_BROKER, ignored -> { });
    MqttClient testClient = new MqttClient(
        CoordinateNormalizerMqttSubscriber.DEFAULT_BROKER,
        "csc5100-coordinate-test-" + UUID.randomUUID(),
        new MemoryPersistence());
    try {
      MqttConnectOptions options = new MqttConnectOptions();
      options.setCleanSession(true);
      options.setConnectionTimeout(10);
      testClient.connect(options);
      testClient.subscribe(ackTopic, CoordinateNormalizerMqttSubscriber.QOS,
          (topic, message) -> acknowledgmentReceived.countDown());
      subscriber.start();

      MqttMessage message = new MqttMessage(event.getBytes(StandardCharsets.UTF_8));
      message.setQos(CoordinateNormalizerMqttSubscriber.QOS);
      testClient.publish(CoordinateNormalizerMqttSubscriber.EVENT_TOPIC, message);

      assertTrue(acknowledgmentReceived.await(15, TimeUnit.SECONDS),
          "Timed out waiting for ACK on " + ackTopic);
    } finally {
      subscriber.close();
      if (testClient.isConnected()) {
        testClient.disconnect();
      }
      testClient.close();
    }
  }
}
