
package edu.calpoly.lambdalab.adrian0427;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

final class DominantAffectMqttEvent {

    static final String OPERATION = "DominantAffectLambda";
    static final String EVENT_TOPIC = "csc5100/events/dominant-affect";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    record Event(String id, String metric, double value) {}

    static Event parse(String json) {
        try {
            JsonNode root = MAPPER.readTree(json);

            if (root == null || !root.isObject()
                    || !OPERATION.equals(root.path("operation").asText())) {
                throw new IllegalArgumentException("Invalid operation");
            }

            String id = root.path("id").asText();

            if (!UUID.fromString(id).toString().equals(id)) {
                throw new IllegalArgumentException("Invalid UUID");
            }

            JsonNode result = root.path("result");

            if (!result.isObject()
                    || !result.path("metric").isTextual()
                    || !result.path("value").isNumber()) {
                throw new IllegalArgumentException("Invalid result");
            }

            String metric = result.get("metric").asText();
            double value = result.get("value").asDouble();

            if (metric.isBlank() || !Double.isFinite(value)) {
                throw new IllegalArgumentException("Invalid affect value");
            }

            return new Event(id, metric, value);

        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Invalid MQTT event", e);
        }
    }

    static String ackTopic(String id) {
        UUID.fromString(id);
        return "csc5100/status/" + id;
    }

    static String ackJson(Event event) {
        try {
            return MAPPER.writeValueAsString(Map.of(
                    "id", event.id(),
                    "operation", OPERATION,
                    "status", "received"));
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Could not create acknowledgment", e);
        }
    }
}
