package edu.calpoly.lambdalab.oxlojalencas;

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * one nearest sample event, as published by the lambda.
 *
 * @param id the event uuid
 * @param timestamp timestamp of the nearest sample
 * @param value value of the nearest sample
 * @param timeDifference distance from the requested event time
 * @author Jess A
 * @version October 25, 2026
 */
record NearestSampleEvent(
        String id,
        double timestamp,
        double value,
        double timeDifference) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * reads an event from json and checks the required fields.
     *
     * @param json event json text
     * @return the parsed event
     * @throws IllegalArgumentException if the json is not a valid event
     */
    static NearestSampleEvent parse(String json) {
        JsonNode event;
        try {
            event = MAPPER.readTree(json);
        } catch (Exception exception) {
            throw new IllegalArgumentException("event is not valid json");
        }

        String id = event.path("id").asText("");
        if (!isUuid(id)) {
            throw new IllegalArgumentException("event id is not a uuid");
        }
        if (!"NearestSampleLambda".equals(event.path("operation").asText())) {
            throw new IllegalArgumentException("event operation is not NearestSampleLambda");
        }

        JsonNode result = event.path("result");
        JsonNode sample = result.path("nearestSample");
        if (!sample.path("timestamp").isNumber()
                || !sample.path("value").isNumber()
                || !result.path("timeDifference").isNumber()) {
            throw new IllegalArgumentException("event result is missing fields");
        }

        return new NearestSampleEvent(
                id,
                sample.get("timestamp").asDouble(),
                sample.get("value").asDouble(),
                result.get("timeDifference").asDouble());
    }

    /**
     * describes the event on one line for the console.
     *
     * @return a short summary
     */
    String summary() {
        return "event " + id + ": nearest sample at " + timestamp
                + " with value " + value
                + " (time difference " + timeDifference + ")";
    }

    private static boolean isUuid(String id) {
        try {
            return UUID.fromString(id).toString().equals(id);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
