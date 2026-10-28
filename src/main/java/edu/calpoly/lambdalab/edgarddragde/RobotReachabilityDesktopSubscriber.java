package edu.calpoly.lambdalab.edgarddragde;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

/** Desktop QoS-1 subscriber for RobotReachability result events on the public HiveMQ broker. */
public final class RobotReachabilityDesktopSubscriber implements AutoCloseable {
  public static final String BROKER = "tcp://broker.hivemq.com:1883";
  public static final String EVENT_TOPIC = "csc5100/events/robot-reachability";
  private static final ObjectMapper MAPPER = new ObjectMapper();

  interface Transport extends AutoCloseable {
    void connect() throws Exception;
    void subscribe(String topic, int qos, MessageListener listener) throws Exception;
    void publish(String topic, byte[] payload, int qos) throws Exception;
    @Override void close() throws Exception;
  }

  interface MessageListener {
    void onMessage(String topic, byte[] payload) throws Exception;
  }

  interface Display {
    void show(Event event);
  }

  record Event(String id, String operation, double distance, boolean reachable) { }

  private final Transport transport;
  private final Display display;

  RobotReachabilityDesktopSubscriber(Transport transport, Display display) {
    this.transport = transport;
    this.display = display;
  }

  static RobotReachabilityDesktopSubscriber connect(String broker, String clientId, Display display)
      throws Exception {
    return new RobotReachabilityDesktopSubscriber(new PahoTransport(broker, clientId), display);
  }

  /** Connects and subscribes. The process remains alive until the caller closes it. */
  public void start() throws Exception {
    transport.connect();
    transport.subscribe(EVENT_TOPIC, 1, this::received);
  }

  void received(String topic, byte[] payload) throws Exception {
    if (!EVENT_TOPIC.equals(topic)) return;
    Event event = parse(new String(payload, StandardCharsets.UTF_8));
    display.show(event);
    String ack = MAPPER.writeValueAsString(Map.of(
        "id", event.id(), "operation", event.operation(), "status", "ACK"));
    transport.publish("csc5100/status/" + event.id(), ack.getBytes(StandardCharsets.UTF_8), 1);
  }

  static Event parse(String json) throws Exception {
    JsonNode root = MAPPER.readTree(json);
    JsonNode result = root == null ? null : root.path("result");
    String id = root == null ? "" : root.path("id").asText();
    String operation = root == null ? "" : root.path("operation").asText();
    if (root == null || !root.isObject() || !isCanonicalUuid(id)
        || !RobotReachabilityLambda.OPERATION.equals(operation)
        || result == null || !result.path("distance").isNumber()
        || !result.path("reachable").isBoolean()) {
      throw new IllegalArgumentException("Invalid RobotReachability event");
    }
    return new Event(id, operation, result.path("distance").asDouble(), result.path("reachable").asBoolean());
  }

  private static boolean isCanonicalUuid(String id) {
    try { return UUID.fromString(id).toString().equals(id); }
    catch (Exception e) { return false; }
  }

  @Override
  public void close() throws Exception {
    transport.close();
  }

  /** Starts the console app; stop it with Ctrl+C. */
  public static void main(String[] args) throws Exception {
    String clientId = "csc5100-robot-desktop-" + UUID.randomUUID();
    try (RobotReachabilityDesktopSubscriber subscriber = connect(BROKER, clientId,
        event -> System.out.printf("id=%s distance=%s reachable=%s%n",
            event.id(), event.distance(), event.reachable()))) {
      subscriber.start();
      Thread.currentThread().join();
    }
  }

  private static final class PahoTransport implements Transport {
    private final MqttClient client;

    PahoTransport(String broker, String clientId) throws Exception {
      this.client = new MqttClient(broker, clientId, new MemoryPersistence());
    }

    @Override public void connect() throws Exception {
      MqttConnectOptions options = new MqttConnectOptions();
      options.setConnectionTimeout(5);
      options.setCleanSession(true);
      options.setAutomaticReconnect(false);
      client.connect(options);
    }

    @Override public void subscribe(String topic, int qos, MessageListener listener) throws Exception {
      client.subscribe(topic, qos, (IMqttMessageListener) (receivedTopic, message) ->
          listener.onMessage(receivedTopic, message.getPayload()));
    }

    @Override public void publish(String topic, byte[] payload, int qos) throws Exception {
      MqttMessage message = new MqttMessage(payload);
      message.setQos(qos);
      client.publish(topic, message);
    }

    @Override public void close() throws Exception {
      if (client.isConnected()) client.disconnect();
      client.close();
    }
  }
}
