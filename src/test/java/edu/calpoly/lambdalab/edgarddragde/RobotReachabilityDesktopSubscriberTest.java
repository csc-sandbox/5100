package edu.calpoly.lambdalab.edgarddragde;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RobotReachabilityDesktopSubscriberTest {
  private static final String ID = "14caa8ee-ca63-41f4-b57f-074007b2affc";
  private static final String EVENT = "{\"id\":\"" + ID + "\",\"operation\":\"RobotReachabilityLambda\","
      + "\"result\":{\"distance\":7.0710678118654755,\"reachable\":true}}";

  private static final class FakeTransport implements RobotReachabilityDesktopSubscriber.Transport {
    String subscribedTopic;
    int subscribedQos;
    RobotReachabilityDesktopSubscriber.MessageListener listener;
    String publishedTopic;
    byte[] publishedPayload;
    int publishedQos;
    @Override public void connect() { }
    @Override public void subscribe(String topic, int qos, RobotReachabilityDesktopSubscriber.MessageListener value) {
      subscribedTopic = topic; subscribedQos = qos; listener = value;
    }
    @Override public void publish(String topic, byte[] payload, int qos) {
      publishedTopic = topic; publishedPayload = payload; publishedQos = qos;
    }
    @Override public void close() { }
  }

  @Test
  void subscribesDisplaysAndAcknowledgesWithTheSameUuid() throws Exception {
    FakeTransport transport = new FakeTransport();
    List<RobotReachabilityDesktopSubscriber.Event> displayed = new ArrayList<>();
    RobotReachabilityDesktopSubscriber subscriber = new RobotReachabilityDesktopSubscriber(transport, displayed::add);
    subscriber.start();
    transport.listener.onMessage(RobotReachabilityDesktopSubscriber.EVENT_TOPIC, EVENT.getBytes(StandardCharsets.UTF_8));
    assertEquals(RobotReachabilityDesktopSubscriber.EVENT_TOPIC, transport.subscribedTopic);
    assertEquals(1, transport.subscribedQos);
    assertEquals(ID, displayed.get(0).id());
    assertEquals("csc5100/status/" + ID, transport.publishedTopic);
    assertEquals(1, transport.publishedQos);
    JsonNode ack = new ObjectMapper().readTree(transport.publishedPayload);
    assertEquals(ID, ack.path("id").asText());
    assertEquals("ACK", ack.path("status").asText());
  }

  @Test
  void rejectsInvalidEvents() {
    assertThrows(IllegalArgumentException.class, () -> RobotReachabilityDesktopSubscriber.parse("{}"));
    assertThrows(IllegalArgumentException.class, () -> RobotReachabilityDesktopSubscriber.parse(
        EVENT.replace("RobotReachabilityLambda", "UnknownLambda")));
  }
}
