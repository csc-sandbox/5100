package edu.calpoly.lambdalab.daome;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.calpoly.lambdalab.daome.data.MovingAverageData;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class MovingAverageLambdaTest {

    @Test
    void calculateMovingAverage() {
        var lambda = new MovingAverageLambda(new MockStorage(), new MockMessaging());
        Map<String, Object> event =
                new HashMap<>(Map.of("httpMethod", "POST", "body", "{\"samples\": [1, 2, 3, 4, 5],\"window\": 3}"));
        Map<String, Object> response = lambda.handleRequest(event, null);
        assertEquals(200, response.get("statusCode"));
        assertTrue(response.get("body").toString().contains("\"movingAverage\":[2.0,3.0,4.0]"));
    }

    @Test
    void calculateMovingAverageWindowBoundary() {
        var lambda = new MovingAverageLambda(new MockStorage(), new MockMessaging());
        Map<String, Object> event = new HashMap<>(Map.of("httpMethod",
                "POST",
                "body",
                "{\"samples\": [1, 2, 3, 4, 5]," + "\"window\": " + "5}"));
        Map<String, Object> response = lambda.handleRequest(event, null);
        assertEquals(200, response.get("statusCode"));
        assertTrue(response.get("body").toString().contains("\"movingAverage\":[3.0]"));
    }

    @Test
    void calculateMovingAverageInvalidWindowInput() {
        var lambda = new MovingAverageLambda(new MockStorage(), new MockMessaging());
        Map<String, Object> event =
                new HashMap<>(Map.of("httpMethod", "POST", "body", "{\"samples\": [1, 2, 3, 4, 5],\"window\": 10}"));
        Map<String, Object> response = lambda.handleRequest(event, null);
        assertEquals(400, response.get("statusCode"));
    }

    @Test
    void testStorage() throws JsonProcessingException {
        var mockStorage = new MockStorage();
        var id = "3f1c9a2e-7b4d-4e8a-9c21-5d6f0b7a1e44";
        mockStorage.save(id,
                new ObjectMapper().writeValueAsString(new MovingAverageData(UUID.fromString(id),
                        List.of(3, 5, 7, 9),
                        2,
                        List.of(4.0, 6.0, 8.0))));
        var getEvent = new HashMap<String, Object>(Map.of("httpMethod",
                "GET",
                "queryStringParameters",
                "{\"id\": \"" + id + "\"}"));
        var lambda = new MovingAverageLambda(mockStorage, new MockMessaging());
        Map<String, Object> response = lambda.handleRequest(getEvent, null);
        assertEquals(200, response.get("statusCode"));
        assertTrue(((String) response.get("body")).contains("\"movingAverage\":[4.0,6.0,8.0]"));
    }


    @Test
    void testPostThenGet() {
        ObjectMapper mapper = new ObjectMapper();
        var lambda = new MovingAverageLambda(new MockStorage(), new MockMessaging());
        Map<String, Object> event = new HashMap<>(Map.of("httpMethod",
                "POST",
                "body",
                "{\"samples\": [1, 2, 3, 4, 5]," + "\"window\": " + "5}"));
        Map<String, Object> response = lambda.handleRequest(event, null);

        try {
            var id = mapper
                    .readValue(response.get("body").toString(), new TypeReference<Map<String, Object>>() {})
                    .get("id");

            var getEvent = new HashMap<String, Object>(Map.of("httpMethod",
                    "GET",
                    "queryStringParameters",
                    "{\"id\": \"" + id + "\"}"));
            Map<String, Object> getResponse = lambda.handleRequest(getEvent, null);
            assertEquals(200, getResponse.get("statusCode"));
            assertTrue(getResponse.get("body").toString().contains("movingAverage\":[3.0]"));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void rejectInvalidUUID() {
        Map<String, String> queryStringParameters = new HashMap<>();
        queryStringParameters.put("id", "invalid");
        Map<String, Object> getEvent =
                new HashMap<>(Map.of("httpMethod", "GET", "queryStringParameters", queryStringParameters));
        var lambda = new MovingAverageLambda(new MockStorage(), new MockMessaging());
        Map<String, Object> getResponse = lambda.handleRequest(getEvent, null);
        assertEquals(400, getResponse.get("statusCode"));
    }

    private String createMockStorageItem(UUID id, List<Integer> samples, Integer windowSize,
                                         List<Double> movingAverage) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        var json = new HashMap<>();
        json.put("owner", "DavidMontiel");
        json.put("operation", "moving-average");
        json.put("responseJson",
                mapper.readTree(mapper.writeValueAsString(new MovingAverageData(id,
                        samples,
                        windowSize,
                        movingAverage))));

        return mapper.writeValueAsString(json);
    }

    private static final class MockMessaging implements MovingAverageLambda.Message {
        private String message;

        @Override
        public void publish(String id, String json) {
            this.message = json;
        }
    }


    private static class MockStorage implements MovingAverageLambda.Storage {
        public final Map<String, Object> results = new LinkedHashMap<>();
        private final ObjectMapper mapper = new ObjectMapper();

        public MockStorage() {
        }

        @Override
        public void save(String id, String json) {
            var map = new LinkedHashMap<String, Object>();
            map.put("owner", "DavidMontiel");
            map.put("operation", "moving-average");
            try {
                map.put("responseJson", mapper.readTree(json));
                results.put(id, mapper.writeValueAsString(map));
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }

        }

        @Override
        public String get(String id) {
            var mapper = new ObjectMapper();
            var json = (String) results.get(id);
            Map<String, Object> map = null;
            try {
                map = mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
                return mapper.writeValueAsString(map.get("responseJson"));
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
