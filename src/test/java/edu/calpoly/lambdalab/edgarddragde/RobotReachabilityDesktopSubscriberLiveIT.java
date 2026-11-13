package edu.calpoly.lambdalab.edgarddragde;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Opt-in public-broker proof; it is not part of the regular unit suite. */
@Tag("live")
@EnabledIfEnvironmentVariable(named = "RUN_LIVE_HIVEMQ_TESTS", matches = "true")
class RobotReachabilityDesktopSubscriberLiveIT {
  @Test
  void receivesEventAndAcknowledgesItsUniqueUuid() throws Exception {
    String id = UUID.randomUUID().toString();
    String event = "{\"id\":\"" + id + "\",\"operation\":\"RobotReachabilityLambda\","
        + "\"result\":{\"distance\":1.0,\"reachable\":true}}";
    CountDownLatch ack = new CountDownLatch(1);
    MqttClient observer = new MqttClient(RobotReachabilityDesktopSubscriber.BROKER,
        "csc5100-robot-observer-" + id, new MemoryPersistence());
    MqttClient publisher = new MqttClient(RobotReachabilityDesktopSubscriber.BROKER,
        "csc5100-robot-publisher-" + id, new MemoryPersistence());
    RobotReachabilityDesktopSubscriber subscriber = RobotReachabilityDesktopSubscriber.connect(
        RobotReachabilityDesktopSubscriber.BROKER, "csc5100-robot-subscriber-" + id, ignored -> { });
    try {
      observer.connect();
      observer.subscribe("csc5100/status/" + id, 1, (IMqttMessageListener) (topic, message) -> {
        if (new String(message.getPayload(), StandardCharsets.UTF_8).contains(id)) ack.countDown();
      });
      subscriber.start();
      publisher.connect();
      MqttMessage message = new MqttMessage(event.getBytes(StandardCharsets.UTF_8));
      message.setQos(1);
      publisher.publish(RobotReachabilityDesktopSubscriber.EVENT_TOPIC, message);
      assertTrue(ack.await(Duration.ofSeconds(15).toMillis(), TimeUnit.MILLISECONDS),
          "Timed out waiting for ACK for " + id);
    } finally {
      subscriber.close();
      if (observer.isConnected()) observer.disconnect();
      observer.close();
      if (publisher.isConnected()) publisher.disconnect();
      publisher.close();
    }
  }
}
