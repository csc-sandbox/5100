package edu.calpoly.lambdalab.dylangururajan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

/**
 * Console MQTT client for coordinate-normalizer events.
 *
 * <p>It subscribes at QoS 1, displays validated events, and publishes a JSON acknowledgment to a
 * UUID-specific status topic.</p>
 */
public final class CoordinateNormalizerMqttSubscriber implements AutoCloseable {
  public static final String DEFAULT_BROKER = "tcp://broker.hivemq.com:1883";
  public static final String EVENT_TOPIC = "csc5100/events/coordinate-normalizer";
  public static final String STATUS_PREFIX = "csc5100/status/";
  public static final int QOS = 1;

  @FunctionalInterface
  interface AcknowledgmentPublisher {
    void publish(String topic, String json) throws Exception;
  }

  record ProcessedEvent(String id, String displayText, String acknowledgmentJson) { }

  private final String brokerUri;
  private final Consumer<String> display;
  private final ObjectMapper mapper = new ObjectMapper();
  private MqttClient client;

  public CoordinateNormalizerMqttSubscriber() {
    this(DEFAULT_BROKER, System.out::println);
  }

  CoordinateNormalizerMqttSubscriber(String brokerUri, Consumer<String> display) {
    this.brokerUri = brokerUri;
    this.display = display;
  }

  /** Connects and subscribes; the caller owns this instance and should close it. */
  public synchronized void start() throws Exception {
    if (client != null && client.isConnected()) {
      return;
    }
    client = new MqttClient(
        brokerUri,
        "csc5100-coordinate-normalizer-" + UUID.randomUUID(),
        new MemoryPersistence());
    client.setCallback(new MqttCallback() {
      @Override
      public void connectionLost(Throwable cause) {
        display.accept("MQTT connection lost: " + safeMessage(cause));
      }

      @Override
      public void messageArrived(String topic, MqttMessage message) {
        try {
          handleEvent(
              new String(message.getPayload(), StandardCharsets.UTF_8),
              CoordinateNormalizerMqttSubscriber.this::publishAcknowledgment);
        } catch (Exception exception) {
          display.accept("Ignored invalid coordinate-normalizer event: " + safeMessage(exception));
        }
      }

      @Override
      public void deliveryComplete(IMqttDeliveryToken token) {
        // No UI update is needed for acknowledgment delivery completion.
      }
    });

    MqttConnectOptions options = new MqttConnectOptions();
    options.setAutomaticReconnect(true);
    options.setCleanSession(true);
    options.setConnectionTimeout(10);
    client.connect(options);
    client.subscribe(EVENT_TOPIC, QOS);
    display.accept("Listening on " + EVENT_TOPIC + " via " + brokerUri);
  }

  ProcessedEvent handleEvent(String json, AcknowledgmentPublisher publisher) throws Exception {
    ProcessedEvent event = parseEvent(json);
    display.accept(event.displayText());
    publisher.publish(STATUS_PREFIX + event.id(), event.acknowledgmentJson());
    return event;
  }

  ProcessedEvent parseEvent(String json) throws Exception {
    JsonNode root = mapper.readTree(json);
    if (root == null || !root.isObject()) {
      throw new IllegalArgumentException("Expected a JSON object");
    }
    String id = root.path("id").asText();
    if (!isUuid(id)) {
      throw new IllegalArgumentException("Invalid correlation UUID");
    }
    if (!CoordinateNormalizerLambda.OPERATION.equals(root.path("operation").asText())) {
      throw new IllegalArgumentException("Unexpected operation");
    }
    JsonNode result = root.path("result");
    if (!result.has("normalizedX") || !result.get("normalizedX").isNumber()
        || !result.has("normalizedY") || !result.get("normalizedY").isNumber()) {
      throw new IllegalArgumentException("Missing normalized coordinate result");
    }

    double normalizedX = result.get("normalizedX").asDouble();
    double normalizedY = result.get("normalizedY").asDouble();
    if (!Double.isFinite(normalizedX) || !Double.isFinite(normalizedY)) {
      throw new IllegalArgumentException("Non-finite normalized coordinate result");
    }
    String displayText = String.format(
        Locale.ROOT,
        "CoordinateNormalizer event %s: normalizedX=%.6f, normalizedY=%.6f",
        id, normalizedX, normalizedY);

    Map<String, Object> acknowledgment = new LinkedHashMap<>();
    acknowledgment.put("id", id);
    acknowledgment.put("operation", CoordinateNormalizerLambda.OPERATION);
    acknowledgment.put("status", "received");
    return new ProcessedEvent(id, displayText, mapper.writeValueAsString(acknowledgment));
  }

  private synchronized void publishAcknowledgment(String topic, String json) throws Exception {
    if (client == null || !client.isConnected()) {
      throw new IllegalStateException("MQTT client is not connected");
    }
    MqttMessage message = new MqttMessage(json.getBytes(StandardCharsets.UTF_8));
    message.setQos(QOS);
    client.publish(topic, message);
  }

  @Override
  public synchronized void close() {
    if (client == null) {
      return;
    }
    try {
      if (client.isConnected()) {
        client.disconnect();
      }
    } catch (Exception exception) {
      display.accept("MQTT disconnect failed: " + safeMessage(exception));
    }
    try {
      client.close();
    } catch (Exception exception) {
      display.accept("MQTT close failed: " + safeMessage(exception));
    }
    client = null;
  }

  private boolean isUuid(String value) {
    try {
      return UUID.fromString(value).toString().equals(value);
    } catch (Exception exception) {
      return false;
    }
  }

  private static String safeMessage(Throwable throwable) {
    return throwable == null || throwable.getMessage() == null
        ? "unknown error"
        : throwable.getMessage();
  }

  /** Runs the desktop console until Ctrl+C. An optional first argument overrides the broker URI. */
  public static void main(String[] args) throws Exception {
    String broker = args.length == 0 ? DEFAULT_BROKER : args[0];
    CountDownLatch stopped = new CountDownLatch(1);
    CoordinateNormalizerMqttSubscriber subscriber =
        new CoordinateNormalizerMqttSubscriber(broker, System.out::println);
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      subscriber.close();
      stopped.countDown();
    }));
    subscriber.start();
    stopped.await();
  }
}
