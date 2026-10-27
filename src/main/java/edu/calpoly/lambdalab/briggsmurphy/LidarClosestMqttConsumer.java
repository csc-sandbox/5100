package edu.calpoly.lambdalab.briggsmurphy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class LidarClosestMqttConsumer implements MqttCallback, AutoCloseable {

    public static final String BROKER_URL =
            "tcp://broker.hivemq.com:1883";

    public static final String EVENT_TOPIC =
            "csc5100/events/LidarClosestLambda";

    public static final String STATUS_TOPIC =
            "csc5100/status/";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MqttClient client;

    public LidarClosestMqttConsumer() throws MqttException {
        String clientId = "LidarConsumer-" + UUID.randomUUID();
        client = new MqttClient(
                BROKER_URL,
                clientId,
                new MemoryPersistence()
        );
        client.setCallback(this);
    }

    public void start() throws MqttException {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);

        client.connect(options);
        client.subscribe(EVENT_TOPIC, 1);

        System.out.println("Connected to: " + BROKER_URL);
        System.out.println("Listening on: " + EVENT_TOPIC);
        System.out.println("Press Ctrl+C to stop.");
    }

    public static JsonNode parseEvent(String json) throws Exception {
        JsonNode event = new ObjectMapper().readTree(json);

        if (event == null || !event.isObject()) {
            throw new IllegalArgumentException(
                    "Event must be a JSON object"
            );
        }

        JsonNode id = event.get("id");
        JsonNode operation = event.get("operation");

        if (id == null || !id.isTextual() || id.asText().isBlank()) {
            throw new IllegalArgumentException(
                    "Event must contain a non-empty string id"
            );
        }

        if (operation == null || !operation.isTextual()
                || operation.asText().isBlank()) {
            throw new IllegalArgumentException(
                    "Event must contain a non-empty string operation"
            );
        }

        return event;
    }

    public static String acknowledgmentTopic(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(
                    "Acknowledgment ID cannot be empty"
            );
        }

        return STATUS_TOPIC + id;
    }

    public static String acknowledgmentPayload(String id)
            throws Exception {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(
                    "Acknowledgment ID cannot be empty"
            );
        }

        ObjectMapper mapper = new ObjectMapper();
        var acknowledgment = mapper.createObjectNode();
        acknowledgment.put("id", id);
        acknowledgment.put("status", "ACK");

        return mapper.writeValueAsString(acknowledgment);
    }

    @Override
    public void messageArrived(String topic, MqttMessage message)
            throws Exception {
        String json = new String(
                message.getPayload(),
                StandardCharsets.UTF_8
        );

        System.out.println("\nReceived event on " + topic);
        System.out.println(json);

        JsonNode event = parseEvent(json);
        String id = event.get("id").asText();

        String ackTopic = acknowledgmentTopic(id);
        String ackJson = acknowledgmentPayload(id);

        MqttMessage acknowledgment = new MqttMessage(
                ackJson.getBytes(StandardCharsets.UTF_8)
        );
        acknowledgment.setQos(1);

        client.publish(ackTopic, acknowledgment);

        System.out.println("Published acknowledgment to: " + ackTopic);
        System.out.println(ackJson);
    }

    @Override
    public void connectionLost(Throwable cause) {
        System.err.println("MQTT connection lost: " + cause);
    }

    @Override
    public void deliveryComplete(
            org.eclipse.paho.client.mqttv3.IMqttDeliveryToken token) {
        // No additional action is needed after publishing.
    }

    @Override
    public void close() throws MqttException {
        if (client.isConnected()) {
            client.disconnect();
        }
        client.close();
    }

    public static void main(String[] args) throws Exception {
        LidarClosestMqttConsumer consumer =
                new LidarClosestMqttConsumer();

        consumer.start();

        Runtime.getRuntime().addShutdownHook(
                new Thread(() -> {
                    try {
                        consumer.close();
                    } catch (MqttException e) {
                        System.err.println(
                                "Error closing MQTT client: " + e.getMessage()
                        );
                    }
                })
        );
    }
}
