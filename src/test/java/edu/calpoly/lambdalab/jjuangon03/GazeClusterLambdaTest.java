package edu.calpoly.lambdalab.jjuangon03;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import edu.calpoly.lambdalab.jjuangon03.GazeClusterLambda;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GazeClusterLambdaTest {

    private final GazeClusterLambda lambda = new GazeClusterLambda();
    private final ObjectMapper mapper = new ObjectMapper();

    private Map<String, Object> post(String body) {
        Map<String, Object> event = new HashMap<>();
        event.put("body", body);
        return lambda.handleRequest(event, null);
    }

    private JsonNode bodyOf(Map<String, Object> response) throws Exception {
        return mapper.readTree(response.get("body").toString());
    }

    @Test
    void twoObviousGazeClusters() throws Exception {
        String body = """
                {"points":[
                  {"x":0.10,"y":0.20},{"x":0.12,"y":0.21},{"x":0.11,"y":0.18},
                  {"x":0.75,"y":0.70},{"x":0.78,"y":0.72},{"x":0.76,"y":0.69}],
                 "epsilon":0.08,"minPoints":2}
                """;

        Map<String, Object> response = post(body);
        JsonNode json = bodyOf(response);

        assertEquals(200, response.get("statusCode"));
        assertEquals(2, json.get("numberOfClusters").asInt());

        JsonNode first = json.get("clusters").get(0);
        assertEquals(0, first.get("id").asInt());
        assertEquals(0.11, first.get("centerX").asDouble(), 0.01);
        assertEquals(0.20, first.get("centerY").asDouble(), 0.01);
        assertEquals(3, first.get("points").asInt());

        JsonNode second = json.get("clusters").get(1);
        assertEquals(1, second.get("id").asInt());
        assertEquals(0.76, second.get("centerX").asDouble(), 0.01);
        assertEquals(0.70, second.get("centerY").asDouble(), 0.01);
        assertEquals(3, second.get("points").asInt());
    }

    @Test
    void oneGazeCluster() throws Exception {
        String body = """
                {"points":[
                  {"x":0.50,"y":0.50},{"x":0.52,"y":0.51},{"x":0.51,"y":0.49}],
                 "epsilon":0.05,"minPoints":2}
                """;

        Map<String, Object> response = post(body);
        JsonNode json = bodyOf(response);

        assertEquals(200, response.get("statusCode"));
        assertEquals(1, json.get("numberOfClusters").asInt());
        assertEquals(3, json.get("clusters").get(0).get("points").asInt());
        assertEquals(0.51, json.get("clusters").get(0).get("centerX").asDouble(), 0.01);
    }

    @Test
    void scatteredPointsProduceNoClusters() throws Exception {
        String body = """
                {"points":[{"x":0.1,"y":0.1},{"x":0.9,"y":0.9}],
                 "epsilon":0.05,"minPoints":2}
                """;

        Map<String, Object> response = post(body);
        JsonNode json = bodyOf(response);

        assertEquals(200, response.get("statusCode"));
        assertEquals(0, json.get("numberOfClusters").asInt());
        assertTrue(json.get("clusters").isEmpty());
    }

    @Test
    void emptyPointsReturns400() {
        Map<String, Object> response =
                post("{\"points\":[],\"epsilon\":0.08,\"minPoints\":2}");
        assertEquals(400, response.get("statusCode"));
    }

    @Test
    void missingPointsReturns400() {
        Map<String, Object> response =
                post("{\"epsilon\":0.08,\"minPoints\":2}");
        assertEquals(400, response.get("statusCode"));
    }

    @Test
    void invalidEpsilonReturns400() {
        String points = "[{\"x\":0.1,\"y\":0.1}]";
        assertEquals(400, post("{\"points\":" + points
                + ",\"epsilon\":0,\"minPoints\":2}").get("statusCode"));
        assertEquals(400, post("{\"points\":" + points
                + ",\"epsilon\":-1,\"minPoints\":2}").get("statusCode"));
        assertEquals(400, post("{\"points\":" + points
                + ",\"minPoints\":2}").get("statusCode"));
    }

    @Test
    void invalidMinPointsReturns400() {
        String points = "[{\"x\":0.1,\"y\":0.1}]";
        assertEquals(400, post("{\"points\":" + points
                + ",\"epsilon\":0.08,\"minPoints\":0}").get("statusCode"));
        assertEquals(400, post("{\"points\":" + points
                + ",\"epsilon\":0.08,\"minPoints\":\"two\"}").get("statusCode"));
    }

    @Test
    void malformedPointReturns400() {
        Map<String, Object> response = post(
                "{\"points\":[{\"x\":0.1}],\"epsilon\":0.08,\"minPoints\":2}");
        assertEquals(400, response.get("statusCode"));
    }

    @Test
    void malformedJsonAndMissingBodyReturn400() {
        assertEquals(400, post("not json").get("statusCode"));
        assertEquals(400, lambda.handleRequest(new HashMap<>(), null)
                .get("statusCode"));
    }

    @Test
    void postPersistsAndPublishesOneCorrelatedResult() throws Exception {
        class FakeStorage implements GazeClusterLambda.Storage {
            String id;
            String json;
            public void save(String savedId, String savedJson) { id = savedId; json = savedJson; }
            public String get(String requestedId) { return requestedId.equals(id) ? json : null; }
        }
        FakeStorage storage = new FakeStorage();
        List<String> messages = new ArrayList<>();
        UUID id = UUID.fromString("c5f1b1b7-ccf5-4b98-80f9-f1123589a123");
        GazeClusterLambda reliable = new GazeClusterLambda(storage, messages::add, () -> id);

        Map<String, Object> response = reliable.handleRequest(Map.of("httpMethod", "POST", "body",
                "{\"points\":[{\"x\":0.5,\"y\":0.5}],\"epsilon\":0.1,\"minPoints\":1}"), null);

        assertEquals(200, response.get("statusCode"));
        assertEquals(response.get("body"), storage.json);
        assertEquals(List.of(storage.json), messages);
        JsonNode body = bodyOf(response);
        assertEquals(id.toString(), body.path("id").asText());
        assertEquals("GazeClusterLambda", body.path("operation").asText());
        assertEquals(1, body.path("result").path("numberOfClusters").asInt());
    }

    @Test
    void getsStoredResultByUuidAndReportsMissingResult() {
        String id = "c5f1b1b7-ccf5-4b98-80f9-f1123589a123";
        GazeClusterLambda reliable = new GazeClusterLambda(new GazeClusterLambda.Storage() {
            public void save(String savedId, String json) { }
            public String get(String requestedId) { return id.equals(requestedId) ? "{\"id\":\"" + id + "\"}" : null; }
        });

        assertEquals(200, reliable.handleRequest(Map.of("httpMethod", "GET",
                "queryStringParameters", Map.of("id", id)), null).get("statusCode"));
        assertEquals(404, reliable.handleRequest(Map.of("httpMethod", "GET",
                "queryStringParameters", Map.of("id", "14caa8ee-ca63-41f4-b57f-074007b2affc")), null).get("statusCode"));
    }
}
