package edu.calpoly.lambdalab.jjuangon03;

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

/** Desktop console subscriber for validated gaze-clustering events and UUID-specific receipts. */
public final class GazeClusterMqttSubscriber implements AutoCloseable {
    public static final String DEFAULT_BROKER = "tcp://broker.hivemq.com:1883";
    public static final String EVENT_TOPIC = "csc5100/events/gaze-clusters";
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

    public GazeClusterMqttSubscriber() { this(DEFAULT_BROKER, System.out::println); }

    GazeClusterMqttSubscriber(String brokerUri, Consumer<String> display) {
        this.brokerUri = brokerUri;
        this.display = display;
    }

    /** Connects and subscribes at QoS 1. Call {@link #close()} when the desktop client stops. */
    public synchronized void start() throws Exception {
        if (client != null && client.isConnected()) return;
        client = new MqttClient(brokerUri, "csc5100-gaze-clusters-" + UUID.randomUUID(),
                new MemoryPersistence());
        client.setCallback(new MqttCallback() {
            @Override public void connectionLost(Throwable cause) {
                display.accept("MQTT connection lost: " + safeMessage(cause));
            }
            @Override public void messageArrived(String topic, MqttMessage message) {
                try {
                    handleEvent(new String(message.getPayload(), StandardCharsets.UTF_8),
                            GazeClusterMqttSubscriber.this::publishAcknowledgment);
                } catch (Exception exception) {
                    display.accept("Ignored invalid gaze-cluster event: " + safeMessage(exception));
                }
            }
            @Override public void deliveryComplete(IMqttDeliveryToken token) { }
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
        if (root == null || !root.isObject()) throw new IllegalArgumentException("Expected a JSON object");
        String id = root.path("id").asText();
        if (!isUuid(id)) throw new IllegalArgumentException("Invalid correlation UUID");
        if (!GazeClusterLambda.OPERATION.equals(root.path("operation").asText())) {
            throw new IllegalArgumentException("Unexpected operation");
        }
        JsonNode result = root.path("result");
        if (!result.path("numberOfClusters").canConvertToInt() || !result.path("clusters").isArray()) {
            throw new IllegalArgumentException("Missing gaze cluster result");
        }
        int count = result.path("numberOfClusters").asInt();
        if (count < 0 || count != result.path("clusters").size()) {
            throw new IllegalArgumentException("Inconsistent cluster result");
        }
        for (JsonNode cluster : result.path("clusters")) {
            if (!cluster.path("id").canConvertToInt() || !cluster.path("points").canConvertToInt()
                    || !cluster.path("centerX").isNumber() || !cluster.path("centerY").isNumber()
                    || cluster.path("points").asInt() <= 0
                    || !Double.isFinite(cluster.path("centerX").asDouble())
                    || !Double.isFinite(cluster.path("centerY").asDouble())) {
                throw new IllegalArgumentException("Invalid cluster summary");
            }
        }
        String text = String.format(Locale.ROOT, "GazeCluster event %s: %d cluster(s)", id, count);
        Map<String, Object> acknowledgment = new LinkedHashMap<>();
        acknowledgment.put("id", id);
        acknowledgment.put("operation", GazeClusterLambda.OPERATION);
        acknowledgment.put("status", "received");
        return new ProcessedEvent(id, text, mapper.writeValueAsString(acknowledgment));
    }

    private synchronized void publishAcknowledgment(String topic, String json) throws Exception {
        if (client == null || !client.isConnected()) throw new IllegalStateException("MQTT client is not connected");
        MqttMessage message = new MqttMessage(json.getBytes(StandardCharsets.UTF_8));
        message.setQos(QOS);
        client.publish(topic, message);
    }

    @Override public synchronized void close() {
        if (client == null) return;
        try { if (client.isConnected()) client.disconnect(); }
        catch (Exception exception) { display.accept("MQTT disconnect failed: " + safeMessage(exception)); }
        try { client.close(); }
        catch (Exception exception) { display.accept("MQTT close failed: " + safeMessage(exception)); }
        client = null;
    }

    private boolean isUuid(String value) {
        try { return UUID.fromString(value).toString().equals(value); }
        catch (Exception exception) { return false; }
    }
    private static String safeMessage(Throwable throwable) {
        return throwable == null || throwable.getMessage() == null ? "unknown error" : throwable.getMessage();
    }

    /** Runs until Ctrl+C. Supply a broker URI as the first optional argument. */
    public static void main(String[] args) throws Exception {
        String broker = args.length == 0 ? DEFAULT_BROKER : args[0];
        CountDownLatch stopped = new CountDownLatch(1);
        GazeClusterMqttSubscriber subscriber = new GazeClusterMqttSubscriber(broker, System.out::println);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> { subscriber.close(); stopped.countDown(); }));
        subscriber.start();
        stopped.await();
    }
}
