
package edu.calpoly.lambdalab.adrian0427;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DominantAffectLambdaTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void returnsClearMaximum() {
        DominantAffectLambda lambda = new DominantAffectLambda();

        Map<String, Object> event = new HashMap<>();
        event.put("body",
                "{\"stress\":0.2,\"engagement\":0.8,"
                        + "\"focus\":0.6,\"interest\":0.5}");

        Map<String, Object> response = lambda.handleRequest(event, null);

        assertEquals(200, response.get("statusCode"));
        assertTrue(response.get("body").toString()
                .contains("\"metric\":\"engagement\""));
        assertTrue(response.get("body").toString()
                .contains("\"value\":0.8"));
    }

    @Test
    void resolvesTieAlphabetically() {
        DominantAffectLambda lambda = new DominantAffectLambda();

        Map<String, Object> event = new HashMap<>();
        event.put("body",
                "{\"stress\":0.8,\"engagement\":0.8,"
                        + "\"focus\":0.6,\"interest\":0.5}");

        Map<String, Object> response = lambda.handleRequest(event, null);

        assertEquals(200, response.get("statusCode"));
        assertTrue(response.get("body").toString()
                .contains("\"metric\":\"engagement\""));
        assertTrue(response.get("body").toString()
                .contains("\"value\":0.8"));
    }

    @Test
    void rejectsInvalidInput() {
        DominantAffectLambda lambda = new DominantAffectLambda();

        Map<String, Object> event = new HashMap<>();
        event.put("body",
                "{\"stress\":\"high\",\"engagement\":0.8}");

        Map<String, Object> response = lambda.handleRequest(event, null);

        assertEquals(400, response.get("statusCode"));
    }

    @Test
    void generatesValidUuid() throws Exception {
        DominantAffectLambda lambda = new DominantAffectLambda();

        Map<String, Object> event = Map.of(
                "httpMethod", "POST",
                "body", "{\"joy\":0.9,\"stress\":0.2}");

        Map<String, Object> response = lambda.handleRequest(event, null);

        assertEquals(200, response.get("statusCode"));

        Map<?, ?> body = mapper.readValue(
                response.get("body").toString(), Map.class);

        String id = body.get("id").toString();

        assertEquals(id, UUID.fromString(id).toString());
        assertEquals("joy", body.get("metric"));
        assertEquals(0.9, ((Number) body.get("value")).doubleValue());
    }

    @Test
    void generatesDifferentUuids() throws Exception {
        DominantAffectLambda lambda = new DominantAffectLambda();

        Map<String, Object> event = Map.of(
                "httpMethod", "POST",
                "body", "{\"joy\":0.9,\"stress\":0.2}");

        Map<String, Object> first = lambda.handleRequest(event, null);
        Map<String, Object> second = lambda.handleRequest(event, null);

        assertEquals(200, first.get("statusCode"));
        assertEquals(200, second.get("statusCode"));

        Map<?, ?> firstBody = mapper.readValue(
                first.get("body").toString(), Map.class);
        Map<?, ?> secondBody = mapper.readValue(
                second.get("body").toString(), Map.class);

        assertNotEquals(firstBody.get("id"), secondBody.get("id"));
    }

    @Test
    void rejectsInvalidUuid() {
        DominantAffectLambda lambda = new DominantAffectLambda();

        Map<String, Object> event = Map.of(
                "httpMethod", "GET",
                "queryStringParameters", Map.of("id", "invalid-uuid"));

        Map<String, Object> response = lambda.handleRequest(event, null);

        assertEquals(400, response.get("statusCode"));
    }

    @Test
    void returnsNotFoundForUnknownUuid() {
        FakeStorage storage = new FakeStorage();
        DominantAffectLambda lambda = new DominantAffectLambda(storage);

        Map<String, Object> event = Map.of(
                "httpMethod", "GET",
                "queryStringParameters",
                Map.of("id", UUID.randomUUID().toString()));

        Map<String, Object> response = lambda.handleRequest(event, null);

        assertEquals(404, response.get("statusCode"));
    }

    @Test
    void retrievesStoredResult() throws Exception {
        FakeStorage storage = new FakeStorage();
        DominantAffectLambda lambda = new DominantAffectLambda(storage);

        Map<String, Object> postEvent = Map.of(
                "httpMethod", "POST",
                "body", "{\"joy\":0.9,\"stress\":0.2}");

        Map<String, Object> postResponse =
                lambda.handleRequest(postEvent, null);

        assertEquals(200, postResponse.get("statusCode"));

        Map<?, ?> postBody = mapper.readValue(
                postResponse.get("body").toString(), Map.class);

        String id = postBody.get("id").toString();

        Map<String, Object> getEvent = Map.of(
                "httpMethod", "GET",
                "queryStringParameters", Map.of("id", id));

        Map<String, Object> getResponse =
                lambda.handleRequest(getEvent, null);

        assertEquals(200, getResponse.get("statusCode"));

        Map<?, ?> getBody = mapper.readValue(
                getResponse.get("body").toString(), Map.class);

        assertEquals(postBody, getBody);
        assertEquals("joy", getBody.get("metric"));
    }

    private static class FakeStorage
            implements DominantAffectLambda.Storage {

        private final Map<String, String> records = new HashMap<>();

        @Override
        public void save(String id, String json) {
            records.put(id, json);
        }

        @Override
        public String get(String id) {
            return records.get(id);
        }
    }

    @Test
    void publishesEventWithMatchingUuid() throws Exception {
        FakeStorage storage = new FakeStorage();
        FakeMessaging messaging = new FakeMessaging();
        DominantAffectLambda lambda =
                new DominantAffectLambda(storage, messaging);

        Map<String, Object> event = Map.of(
                "httpMethod", "POST",
                "body", "{\"joy\":0.9,\"stress\":0.2}");

        Map<String, Object> response = lambda.handleRequest(event, null);

        assertEquals(200, response.get("statusCode"));
        assertNotNull(messaging.publishedJson);

        Map<?, ?> responseBody = mapper.readValue(
                response.get("body").toString(), Map.class);
        Map<?, ?> message = mapper.readValue(
                messaging.publishedJson, Map.class);

        assertEquals(responseBody.get("id"), message.get("id"));
        assertEquals("DominantAffectLambda", message.get("operation"));

        Map<?, ?> result = (Map<?, ?>) message.get("result");
        assertEquals(responseBody.get("metric"), result.get("metric"));
        assertEquals(responseBody.get("value"), result.get("value"));
    }

    @Test
    void doesNotPublishInvalidRequests() {
        FakeMessaging messaging = new FakeMessaging();
        DominantAffectLambda lambda =
                new DominantAffectLambda(new FakeStorage(), messaging);

        Map<String, Object> event = Map.of(
                "httpMethod", "POST",
                "body", "{\"joy\":\"invalid\"}");

        Map<String, Object> response = lambda.handleRequest(event, null);

        assertEquals(400, response.get("statusCode"));
        assertNull(messaging.publishedJson);
    }

    private static class FakeMessaging
            implements DominantAffectLambda.Messaging {

        private String publishedJson;

        @Override
        public void publish(String json) {
            publishedJson = json;
        }
    }

}
