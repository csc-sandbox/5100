package edu.calpoly.lambdalab.hwrd22;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class DataFreshnessLambdaTest {

    @Test
    void freshStatus() {
        DataFreshnessLambda lambda = new DataFreshnessLambda();
        Map<String, Object> event = new HashMap<>();
        event.put("body", "{\"current\":100,\"sample\":95,\"threshold\":10}");
        Map<String, Object> response = lambda.handleRequest(event, null);
        assertEquals(200, response.get("statusCode"));
        assertTrue(response.get("body").toString().contains("\"status\":\"FRESH\""));
        assertTrue(response.get("body").toString().contains("\"age\":5"));
    }

    @Test
    void staleStatus() {
        DataFreshnessLambda lambda = new DataFreshnessLambda();
        Map<String, Object> event = new HashMap<>();
        event.put("body", "{\"current\":500,\"sample\":95,\"threshold\":10}");
        Map<String, Object> response = lambda.handleRequest(event, null);
        assertEquals(200, response.get("statusCode"));
        assertTrue(response.get("body").toString().contains("\"status\":\"STALE\""));
        assertTrue(response.get("body").toString().contains("\"age\":405"));
    }

    @Test
    void invalidNegativeTime() {
        DataFreshnessLambda lambda = new DataFreshnessLambda();
        Map<String, Object> event = new HashMap<>();
        event.put("body", "{\"current\":100,\"sample\":-95,\"threshold\":10}");
        Map<String, Object> response = lambda.handleRequest(event, null);
        assertEquals(400, response.get("statusCode"));
    }

    @Test
    void sampleAfterCurrent() {
        DataFreshnessLambda lambda = new DataFreshnessLambda();
        Map<String, Object> event = new HashMap<>();
        event.put("body", "{\"current\":50,\"sample\":95,\"threshold\":10}");
        Map<String, Object> response = lambda.handleRequest(event, null);
        assertEquals(400, response.get("statusCode"));
    }

    @Test
    void freshStatusUsingGet() {
        DataFreshnessLambda lambda = new DataFreshnessLambda();
        Map<String, Object> queryParameters = new HashMap<>();
        queryParameters.put("current", "105");
        queryParameters.put("sample", "95");
        queryParameters.put("threshold", "10");
        Map<String, Object> event = new HashMap<>();
        event.put("queryStringParameters", queryParameters);
        Map<String, Object> response = lambda.handleRequest(event, null);
        assertEquals(200, response.get("statusCode"));
        assertTrue(response.get("body").toString().contains("\"status\":\"FRESH\""));
        assertTrue(response.get("body").toString().contains("\"age\":10"));
    }

    @Test
    void postUsesOneGeneratedUUIDForStorageAndMessaging() throws Exception {
        class FakeStorage implements DataFreshnessLambda.Storage {
            String id;
            String json;

            @Override
            public void save(String saveId, String saveJson) {
                id = saveId;
                json = saveJson;
            }

            @Override
            public String get(String getId) {
                return getId.equals(id) ? json : null;
            }
        }

        FakeStorage storage = new FakeStorage();
        List<String> messages = new ArrayList<>();
        DataFreshnessLambda lambda = new DataFreshnessLambda(storage, messages::add);

        Map<String, Object> response = lambda.handleRequest(Map.of("httpMethod", "POST", "body", "{\"current\":100,\"sample\":95,\"threshold\":10}"), null);
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> body = mapper.readValue(
                response.get("body").toString(),
                new TypeReference<Map<String, Object>>() {}
        );
        String id = body.get("id").toString();

        assertEquals(200, response.get("statusCode"));
        assertEquals(id, storage.id);
        assertEquals(response.get("body"), storage.json);
        assertEquals(List.of(storage.json), messages);

        JsonNode storageBody = mapper.readTree(storage.json);
        assertEquals(id, storageBody.get("id").asText());
        assertEquals("DataFreshnessLambda", storageBody.get("operation").asText());
        assertEquals(5, storageBody.path("result").path("age").asDouble());
        assertEquals("FRESH", storageBody.path("result").path("status").asText());
    }

    @Test
    void getByUuidReturnsPersistedJsonAndMissingReturns404() {
        String id ="d2de736e-7c87-4810-b7b6-ccf04e618183";
        String json = "{\"id\":\"" + id + "\",\"result\":{}}";
        DataFreshnessLambda lambda = new DataFreshnessLambda(new DataFreshnessLambda.Storage() {
            @Override
            public void save(String id, String json) {}

            @Override
            public String get(String requestId) {
                return id.equals(requestId) ? json : null;
            }
        });

        Map<String, Object> exists = lambda.handleRequest(Map.of("httpMethod", "GET", "queryStringParameters", Map.of("id", id)), null);
        Map<String, Object> missing = lambda.handleRequest(Map.of("httpMethod", "GET", "queryStringParameters", Map.of("id", "8c16e689-0867-4978-a360-d1535b27830b")), null);

        assertEquals(200, exists.get("statusCode"));
        assertEquals(json, exists.get("body"));
        assertEquals(404, missing.get("statusCode"));
    }

    @Test
    void createUniqueUuidForEachRequest() throws Exception {
        List<String> messages = new ArrayList<>();
        DataFreshnessLambda lambda = new DataFreshnessLambda(null, messages::add);
        Map<String, Object> post = Map.of("httpMethod", "POST", "body", "{\"current\":100,\"sample\":95,\"threshold\":10}");

        ObjectMapper mapper = new ObjectMapper();

        JsonNode first = mapper.readTree(lambda.handleRequest(post, null).get("body").toString());
        JsonNode second = mapper.readTree(lambda.handleRequest(post, null).get("body").toString());

        assertNotEquals(first.get("id").asText(), second.get("id").asText());
        assertEquals(2, messages.size());
    }

    @Test
    void rejectInvalidIdAndMalformedPostBody() {
        DataFreshnessLambda lambda = new DataFreshnessLambda(new DataFreshnessLambda.Storage() {
            @Override
            public void save(String id, String json) {}

            @Override
            public String get(String id) {
                return null;
            }
        });

        Map<String, Object> invalidId = lambda.handleRequest(Map.of("httpMethod", "GET", "queryStringParameters", Map.of("id", "not_uuid")), null);
        Map<String, Object> malformedJson = lambda.handleRequest(Map.of("httpMethod", "POST", "body", "not_json"), null);

        assertEquals(400, invalidId.get("statusCode"));
        assertEquals(400, malformedJson.get("statusCode"));
    }

    @Test
    void supportsHttpApiV2MethodAndPathUuidLookup() {
        String id = "14caa8ee-ca63-41f4-b57f-074007b2affc";
        DataFreshnessLambda lambda =
                new DataFreshnessLambda(new DataFreshnessLambda.Storage() {
                    @Override
                    public void save(String id, String json) { }

                    @Override
                    public String get(String requestedId) {
                        return "{\"id\":\"" + requestedId + "\"}";
                    }
                });

        Map<String, Object> response = lambda.handleRequest(Map.of(
                "requestContext", Map.of("http", Map.of("method", "GET")),
                "pathParameters", Map.of("uuid", id)), null);

        assertEquals(200, response.get("statusCode"));
        assertTrue(response.get("body").toString().contains(id));
    }
}
