package edu.calpoly.lambdalab.aidenrodriguez;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

/**
 * Console subscriber for EncryptMessageLambda A04 events. It displays each
 * validated event and acknowledges its correlation UUID at QoS 1.
 */
public final class EncryptMessageDesktopSubscriber implements AutoCloseable {
    public static final String DEFAULT_BROKER = "tcp://broker.hivemq.com:1883";
    public static final String EVENT_TOPIC = "csc5100/events/encrypt";
    public static final String STATUS_TOPIC_PREFIX = "csc5100/status/";
    public static final String OPERATION = "EncryptMessageLambda";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final MqttClient client;

    public EncryptMessageDesktopSubscriber() throws MqttException {
        this(DEFAULT_BROKER, "csc5100-encrypt-desktop-" + UUID.randomUUID());
    }

    EncryptMessageDesktopSubscriber(String broker, String clientId) throws MqttException {
        client = new MqttClient(broker, clientId, new MemoryPersistence());
    }

    /** Connects and begins receiving function-specific events at QoS 1. */
    public void start() throws MqttException {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);
        options.setAutomaticReconnect(true);
        options.setConnectionTimeout(10);
        client.setCallback(new MqttCallback() {
            @Override public void connectionLost(Throwable cause) {
                System.err.println("MQTT connection lost: " + cause.getMessage());
            }

            @Override public void messageArrived(String topic, MqttMessage message) {
                try {
                    EncryptEvent event = parseEvent(new String(message.getPayload(), StandardCharsets.UTF_8));
                    System.out.printf("Received %s id=%s result=%s%n", event.operation(), event.id(),
                            event.result().toPrettyString());
                    publishAcknowledgement(event);
                } catch (Exception e) {
                    System.err.println("Ignored invalid EncryptMessage event: " + e.getMessage());
                }
            }

            @Override public void deliveryComplete(IMqttDeliveryToken token) { }
        });
        client.connect(options);
        client.subscribe(EVENT_TOPIC, 1);
        System.out.println("Subscribed to " + EVENT_TOPIC + " (QoS 1)");
    }

    static EncryptEvent parseEvent(String json) throws Exception {
        JsonNode root = MAPPER.readTree(json);
        String id = root.path("id").asText();
        try {
            if (!UUID.fromString(id).toString().equals(id)) throw new IllegalArgumentException("invalid id");
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Event must contain a UUID id", e);
        }
        String operation = root.path("operation").asText();
        if (!OPERATION.equals(operation)) {
            throw new IllegalArgumentException("Unexpected operation: " + operation);
        }
        JsonNode result = root.get("result");
        if (result == null || result.isNull()) throw new IllegalArgumentException("Event is missing result");
        return new EncryptEvent(id, operation, result);
    }

    static String acknowledgementFor(EncryptEvent event) throws Exception {
        return MAPPER.writeValueAsString(Map.of(
                "id", event.id(),
                "operation", event.operation(),
                "status", "received"));
    }

    static String statusTopicFor(EncryptEvent event) {
        return STATUS_TOPIC_PREFIX + event.id();
    }

    private void publishAcknowledgement(EncryptEvent event) throws Exception {
        MqttMessage acknowledgement = new MqttMessage(
                acknowledgementFor(event).getBytes(StandardCharsets.UTF_8));
        acknowledgement.setQos(1);
        String topic = statusTopicFor(event);
        client.getTopic(topic).publish(acknowledgement).waitForCompletion();
        System.out.println("Acknowledged id=" + event.id() + " on " + topic + " (QoS 1)");
    }

    @Override
    public void close() {
        try {
            if (client.isConnected()) client.disconnect();
        } catch (MqttException ignored) {
            // The client is closing; no retry is useful here.
        }
        try {
            client.close();
        } catch (MqttException ignored) {
            // The client is closing; no retry is useful here.
        }
    }

    /** Starts the desktop console and keeps it running until interrupted. */
    public static void main(String[] args) throws Exception {
        try (EncryptMessageDesktopSubscriber subscriber = new EncryptMessageDesktopSubscriber()) {
            subscriber.start();
            System.out.println("Waiting for REST-triggered EncryptMessageLambda events. Press Ctrl+C to exit.");
            new CountDownLatch(1).await();
        }
    }

    record EncryptEvent(String id, String operation, JsonNode result) { }
}
