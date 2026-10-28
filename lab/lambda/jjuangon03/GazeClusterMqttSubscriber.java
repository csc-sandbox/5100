package edu.calpoly.lambdalab.jjuangon03;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;

/** Console subscriber for gaze events and per-event acknowledgements. */
public final class GazeClusterMqttSubscriber {
    static final String BROKER_URI = "tcp://broker.hivemq.com:1883";
    static final String EVENT_TOPIC = "csc5100/events/gaze-clusters";
    private static final String ACK_TOPIC_PREFIX = "csc5100/status/";
    private static final String CONSUMER = "jjuangon03";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    record Event(String id, int numberOfClusters, String json) { }

    private GazeClusterMqttSubscriber() { }

    public static void main(String[] args) throws Exception {
        String brokerUri = args.length == 0 ? BROKER_URI : args[0];
        MqttClient client = new MqttClient(brokerUri,
                "jjuangon03-gaze-clusters-" + UUID.randomUUID());
        try {
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setAutomaticReconnect(true);
            options.setConnectionTimeout(10);
            client.connect(options);
            client.subscribe(EVENT_TOPIC, 1, (topic, message) -> {
                Event event = parseEvent(new String(message.getPayload(), StandardCharsets.UTF_8));
                System.out.println("Received " + topic + ": " + event.json());
                MqttMessage acknowledgement = new MqttMessage(acknowledgementJson(event)
                        .getBytes(StandardCharsets.UTF_8));
                acknowledgement.setQos(1);
                client.publish(acknowledgementTopic(event.id()), acknowledgement);
                System.out.println("Acknowledged " + event.id());
            });
            System.out.println("Listening on " + brokerUri + " " + EVENT_TOPIC);
            new CountDownLatch(1).await();
        } finally {
            if (client.isConnected()) {
                client.disconnect();
            }
            client.close();
        }
    }

    static Event parseEvent(String json) {
        try {
            JsonNode event = MAPPER.readTree(json);
            String id = event == null || !event.path("id").isTextual()
                    ? null : event.path("id").asText();
            int clusterCount = event == null ? -1 : event.path("numberOfClusters").asInt(-1);
            if (!isUuid(id) || event == null
                    || !"GazeClusterLambda".equals(event.path("operation").asText())
                    || clusterCount < 0 || !event.path("clusters").isArray()) {
                throw new IllegalArgumentException("Invalid gaze cluster event");
            }
            return new Event(id, clusterCount, json);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid gaze cluster event JSON", exception);
        }
    }

    static String acknowledgementTopic(String id) {
        if (!isUuid(id)) {
            throw new IllegalArgumentException("Invalid event UUID");
        }
        return ACK_TOPIC_PREFIX + id;
    }

    static String acknowledgementJson(Event event) throws JsonProcessingException {
        return MAPPER.writeValueAsString(java.util.Map.of(
                "id", event.id(),
                "status", "received",
                "consumer", CONSUMER));
    }

    private static boolean isUuid(String id) {
        try {
            return id != null && UUID.fromString(id).toString().equals(id);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
