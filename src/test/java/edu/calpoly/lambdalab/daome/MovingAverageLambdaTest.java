package edu.calpoly.lambdalab.daome;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.calpoly.lambdalab.daome.Exceptions.MessageException;
import edu.calpoly.lambdalab.daome.data.MovingAverageData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class MovingAverageLambdaTest {
    private static final Map<String, Object> invalidPost1 = Map.of("httpMethod", "POST", "body", """
            {
            "samples": [1, 2, 3, 4, 5],
            "window": 10
            }
            """);
    private static final Map<String, Object> validPost2 = Map.of("httpMethod", "POST", "body", """
            {
            "samples": [1,2, 3, 4, 5],
            "window": 3
            }
            """);
    private static final Map<String, Object> validPost3 = Map.of("httpMethod", "POST", "body", """
            {
            "samples": [1,2, 3, 4, 5],
            "window": 5
            }
            """);
    private static final Map<String, Object> invalidGet1 =
            Map.of("httpMethod", "GET", "queryStringParameters", Map.of("id", "3f1c9a2e-7b4d-4e8a-9c21-5d6f0b7a1e44"));

    AtomicInteger attempts;
    AtomicInteger delays;
    Storage fake;
    Storage storage;

    private static RetryExecutor threeAttempts(AtomicInteger delays) {
        return new RetryExecutor(3, failure -> failure instanceof IllegalStateException, delays::incrementAndGet);
    }

    @BeforeEach
    void init() {
        attempts = new AtomicInteger();
        delays = new AtomicInteger();
        fake = new MockStorage();
        storage = new RetryStorage(fake, threeAttempts(delays));
    }

    @Test
    void calculateMovingAverage() {
        var lambda = new MovingAverageLambda(storage, new MockMessaging());
        Map<String, Object> response = lambda.handleRequest(validPost2, null);
        assertEquals(200, response.get("statusCode"));
        assertTrue(response.get("body").toString().contains("\"movingAverage\":[2.0,3.0,4.0]"));
    }

    @Test
    void calculateMovingAverageWindowBoundary() {
        var lambda = new MovingAverageLambda(storage, new MockMessaging());
        Map<String, Object> response = lambda.handleRequest(validPost3, null);
        assertEquals(200, response.get("statusCode"));
        assertTrue(response.get("body").toString().contains("\"movingAverage\":[3.0]"));
    }

    @Test
    void calculateMovingAverageInvalidWindowInput() {
        var lambda = new MovingAverageLambda(new MockStorage(), new MockMessaging());
        Map<String, Object> response = lambda.handleRequest(invalidPost1, null);
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
        var lambda = new MovingAverageLambda(mockStorage, new MockMessaging());
        Map<String, Object> response = lambda.handleRequest(invalidGet1, null);
        assertEquals(200, response.get("statusCode"));
        assertEquals(mockStorage.get(id), response.get("body"));
    }

    @Test
    void testPostThenGet() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var lambda = new MovingAverageLambda(storage, new MockMessaging());
        Map<String, Object> event = new HashMap<>(Map.of("httpMethod",
                "POST",
                "body",
                "{\"samples\": [1, 2, 3, 4, 5]," + "\"window\": " + "5}"));
        Map<String, Object> response = lambda.handleRequest(event, null);

        String id = (String) mapper
                .readValue(response.get("body").toString(), new TypeReference<Map<String, Object>>() {})
                .get("id");

        assertEquals(mapper.readTree(storage.get(id)), mapper.readTree((String) response.get("body")));

        Map<String, Object> getEvent =
                Map.of("httpMethod", "GET", "queryStringParameters", Map.of("id", id));
        Map<String, Object> getResponse = lambda.handleRequest(getEvent, null);
        assertEquals(200, getResponse.get("statusCode"));
    }

    @Test
    void rejectInvalidUUID() {
        Map<String, String> queryStringParameters = new HashMap<>();
        queryStringParameters.put("id", "invalid");
        Map<String, Object> getEvent =
                new HashMap<>(Map.of("httpMethod", "GET", "queryStringParameters", queryStringParameters));
        var lambda = new MovingAverageLambda(storage, new MockMessaging());
        Map<String, Object> getResponse = lambda.handleRequest(getEvent, null);
        assertEquals(400, getResponse.get("statusCode"));
    }

    @Test
    void MessageFail() {

        Map<String, Object> event = new HashMap<>(Map.of("httpMethod",
                "POST",
                "body",
                "{\"samples\": [1, 2, 3, 4, 5]," + "\"window\": " + "5}"));
        MovingAverageLambda.Message messaging =
                (id, json) -> {throw new MessageException("Simulated Message Failure");};
        var lambda = new MovingAverageLambda(storage, messaging);
        assertDoesNotThrow(() -> {lambda.handleRequest(event, null);});
    }

    private static final class MockMessaging implements MovingAverageLambda.Message {

        @Override
        public void publish(String id, String json) {
        }
    }

    private static class MockStorage implements Storage {
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
            Map<String, Object> map;
            try {
                map = mapper.readValue(json, new TypeReference<>() {});
                return mapper.writeValueAsString(map.get("responseJson"));
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
