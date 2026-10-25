package edu.calpoly.lambdalab.oxlojalencas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Tests for NearestSampleLambda.
 *
 * @author Jess A
 * @version October 4, 2026
 */
class NearestSampleLambdaTest {

    @Test
    void returnsExactMatch() {
        NearestSampleLambda lambda = new NearestSampleLambda(null);

        String body = """
                {
                  "eventTimestamp": 10.0,
                  "samples": [
                    {"timestamp": 9.5, "value": 3.0},
                    {"timestamp": 10.0, "value": 4.2}
                  ]
                }
                """;

        Map<String, Object> response = lambda.handleRequest(event(body), null);

        assertEquals(200, response.get("statusCode"));
        assertTrue(response.get("body").toString()
                .contains("\"timestamp\":10.0"));
        assertTrue(response.get("body").toString()
                .contains("\"timeDifference\":0.0"));
    }

    @Test
    void returnsNearestNeighbor() {
        NearestSampleLambda lambda = new NearestSampleLambda(null);

        NearestSampleLambda.NearestResult result =
                lambda.findNearestSample(
                        10.25,
                        List.of(
                                new NearestSampleLambda.Sample(10.0, 4.2),
                                new NearestSampleLambda.Sample(10.4, 5.1)));

        assertEquals(10.4, result.sample().timestamp(), 0.0001);
        assertEquals(5.1, result.sample().value(), 0.0001);
        assertEquals(0.15, result.timeDifference(), 0.0001);
    }

    @Test
    void rejectsEmptySamples() {
        NearestSampleLambda lambda = new NearestSampleLambda(null);

        String body = """
                {
                  "eventTimestamp": 10.0,
                  "samples": []
                }
                """;

        Map<String, Object> response = lambda.handleRequest(event(body), null);

        assertEquals(400, response.get("statusCode"));
    }

    @Test
    void rejectsInvalidSample() {
        NearestSampleLambda lambda = new NearestSampleLambda(null);

        String body = """
                {
                  "eventTimestamp": 10.0,
                  "samples": [
                    {"timestamp": "not a number", "value": 4.2}
                  ]
                }
                """;

        Map<String, Object> response = lambda.handleRequest(event(body), null);

        assertEquals(400, response.get("statusCode"));
    }

    @Test
    void postAddsUuidAndKeepsOriginalFields() throws Exception {
        NearestSampleLambda lambda = new NearestSampleLambda(null);

        Map<String, Object> response =
                lambda.handleRequest(event(VALID_BODY), null);

        JsonNode json = readJson(response);
        String id = json.get("id").asText();
        assertEquals(200, response.get("statusCode"));
        assertEquals(id, UUID.fromString(id).toString());
        assertEquals(10.4, json.get("nearestSample").get("timestamp")
                .asDouble(), 0.0001);
        assertEquals(5.1, json.get("nearestSample").get("value")
                .asDouble(), 0.0001);
        assertEquals(0.1, json.get("timeDifference").asDouble(), 0.0001);
    }

    @Test
    void postStoresResultAndGetReturnsSameJson() throws Exception {
        FakeStorage storage = new FakeStorage();
        NearestSampleLambda lambda = new NearestSampleLambda(storage);

        Map<String, Object> posted =
                lambda.handleRequest(event(VALID_BODY), null);
        String id = readJson(posted).get("id").asText();
        Map<String, Object> fetched = lambda.handleRequest(getEvent(id), null);

        assertEquals(posted.get("body"), storage.results.get(id));
        assertEquals(200, fetched.get("statusCode"));
        assertEquals(posted.get("body"), fetched.get("body"));
    }

    @Test
    void getRejectsInvalidId() {
        NearestSampleLambda lambda = new NearestSampleLambda(new FakeStorage());

        Map<String, Object> response =
                lambda.handleRequest(getEvent("not-a-uuid"), null);

        assertEquals(400, response.get("statusCode"));
    }

    @Test
    void getRejectsMissingId() {
        NearestSampleLambda lambda = new NearestSampleLambda(new FakeStorage());
        Map<String, Object> event = getEvent(SAMPLE_ID);
        event.put("queryStringParameters", Map.of());

        Map<String, Object> response = lambda.handleRequest(event, null);

        assertEquals(400, response.get("statusCode"));
    }

    @Test
    void getReturnsNotFoundForUnknownId() {
        NearestSampleLambda lambda = new NearestSampleLambda(new FakeStorage());

        Map<String, Object> response =
                lambda.handleRequest(getEvent(SAMPLE_ID), null);

        assertEquals(404, response.get("statusCode"));
    }

    @Test
    void getReturnsUnavailableWithoutStorage() {
        NearestSampleLambda lambda = new NearestSampleLambda(null);

        Map<String, Object> response =
                lambda.handleRequest(getEvent(SAMPLE_ID), null);

        assertEquals(503, response.get("statusCode"));
    }

    @Test
    void postReturnsServerErrorWhenStorageFails() {
        FakeStorage storage = new FakeStorage();
        storage.fails = true;
        NearestSampleLambda lambda = new NearestSampleLambda(storage);

        Map<String, Object> response =
                lambda.handleRequest(event(VALID_BODY), null);

        assertEquals(500, response.get("statusCode"));
        assertTrue(response.get("body").toString().contains("Could not save"));
    }

    private static final String SAMPLE_ID =
            "c5f1b1b7-ccf5-4b98-80f9-f1123589a123";

    private static final String VALID_BODY = """
            {
              "eventTimestamp": 10.3,
              "samples": [
                {"timestamp": 10.0, "value": 4.2},
                {"timestamp": 10.4, "value": 5.1}
              ]
            }
            """;

    private JsonNode readJson(Map<String, Object> response) throws Exception {
        return new ObjectMapper().readTree(response.get("body").toString());
    }

    private Map<String, Object> getEvent(String id) {
        Map<String, Object> event = new HashMap<>();
        event.put("requestContext", Map.of("http", Map.of("method", "GET")));
        event.put("queryStringParameters", Map.of("id", id));
        return event;
    }

    private Map<String, Object> event(String body) {
        Map<String, Object> event = new HashMap<>();
        event.put("body", body);
        return event;
    }

    /** in-memory storage that can be made to fail. */
    private static class FakeStorage implements NearestSampleLambda.Storage {
        private final Map<String, String> results = new HashMap<>();
        private boolean fails;

        @Override
        public void save(String id, String json) {
            if (fails) {
                throw new IllegalStateException("save failed");
            }
            results.put(id, json);
        }

        @Override
        public String get(String id) {
            if (fails) {
                throw new IllegalStateException("get failed");
            }
            return results.get(id);
        }
    }
}
