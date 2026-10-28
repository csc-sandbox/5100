package edu.calpoly.lambdalab.jjuangon03;

<<<<<<< HEAD
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GazeClusterLambdaTest {
    private final GazeClusterLambda lambda = new GazeClusterLambda();
    private final ObjectMapper mapper = new ObjectMapper();

    private Map<String, Object> post(String body) {
        return lambda.handleRequest(Map.of("body", body), null);
    }

    private JsonNode responseBody(Map<String, Object> response) throws Exception {
=======
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GazeClusterLambdaTest {

    private final GazeClusterLambda lambda = new GazeClusterLambda(null, null);
    private final ObjectMapper mapper = new ObjectMapper();

    private Map<String, Object> post(String body) {
        Map<String, Object> event = new HashMap<>();
        event.put("body", body);
        return lambda.handleRequest(event, null);
    }

    private JsonNode bodyOf(Map<String, Object> response) throws Exception {
>>>>>>> 9bebfb9b4a392da0b58bd662d47300c7bd594324
        return mapper.readTree(response.get("body").toString());
    }

    @Test
<<<<<<< HEAD
    void clustersTwoSeparatedGroups() throws Exception {
        Map<String, Object> response = post("""
=======
    void twoObviousGazeClusters() throws Exception {
        String body = """
>>>>>>> 9bebfb9b4a392da0b58bd662d47300c7bd594324
                {"points":[
                  {"x":0.10,"y":0.20},{"x":0.12,"y":0.21},{"x":0.11,"y":0.18},
                  {"x":0.75,"y":0.70},{"x":0.78,"y":0.72},{"x":0.76,"y":0.69}],
                 "epsilon":0.08,"minPoints":2}
<<<<<<< HEAD
                """);
        JsonNode body = responseBody(response);

        assertEquals(200, response.get("statusCode"));
        assertEquals("application/json",
                ((Map<?, ?>) response.get("headers")).get("Content-Type"));
        assertEquals(2, body.path("numberOfClusters").asInt());
        assertEquals(0, body.path("clusters").get(0).path("id").asInt());
        assertEquals(3, body.path("clusters").get(0).path("points").asInt());
        assertEquals(0.11, body.path("clusters").get(0).path("centerX").asDouble(), 0.01);
        assertEquals(0.20, body.path("clusters").get(0).path("centerY").asDouble(), 0.01);
        assertEquals(1, body.path("clusters").get(1).path("id").asInt());
        assertEquals(3, body.path("clusters").get(1).path("points").asInt());
        assertEquals(0.76, body.path("clusters").get(1).path("centerX").asDouble(), 0.01);
        assertEquals(0.70, body.path("clusters").get(1).path("centerY").asDouble(), 0.01);
    }

    @Test
    void clustersOneGroupAndOmitsNoise() throws Exception {
        Map<String, Object> response = post("""
                {"points":[
                  {"x":0.50,"y":0.50},{"x":0.52,"y":0.51},{"x":0.51,"y":0.49},
                  {"x":0.95,"y":0.95}],
                 "epsilon":0.05,"minPoints":2}
                """);
        JsonNode body = responseBody(response);

        assertEquals(200, response.get("statusCode"));
        assertEquals(1, body.path("numberOfClusters").asInt());
        assertEquals(1, body.path("clusters").size());
        assertEquals(3, body.path("clusters").get(0).path("points").asInt());
    }

    @Test
    void rejectsInvalidOrInsufficientInputWith400() {
        assertEquals(400, post("{\"points\":[],\"epsilon\":0.08,\"minPoints\":2}")
                .get("statusCode"));
        assertEquals(400, post("{\"points\":[{\"x\":0.1}],"
                + "\"epsilon\":0.08,\"minPoints\":2}").get("statusCode"));
        assertEquals(400, post("{\"points\":[{\"x\":0.1,\"y\":0.2}],"
                + "\"epsilon\":0,\"minPoints\":2}").get("statusCode"));
        assertEquals(400, post("{\"points\":[{\"x\":0.1,\"y\":0.2}],"
                + "\"epsilon\":0.08,\"minPoints\":1.5}").get("statusCode"));
        assertEquals(400, post("not-json").get("statusCode"));
        assertEquals(400, lambda.handleRequest(Map.of(), null).get("statusCode"));
    }

    @Test
    void scatteredPointsReturnAnEmptyClusterList() throws Exception {
        JsonNode body = responseBody(post("""
                {"points":[{"x":0.1,"y":0.1},{"x":0.9,"y":0.9}],
                 "epsilon":0.05,"minPoints":2}
                """));
        assertEquals(0, body.path("numberOfClusters").asInt());
        assertTrue(body.path("clusters").isEmpty());
    }
}
=======
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
    void postPersistsAndPublishesOneCorrelatedJsonDocument() throws Exception {
        Map<String, String> stored = new HashMap<>();
        List<String> published = new java.util.ArrayList<>();
        GazeClusterLambda lambda = new GazeClusterLambda(
                new GazeClusterLambda.Storage() {
                    @Override
                    public void save(String id, String json) {
                        stored.put(id, json);
                    }

                    @Override
                    public String get(String id) {
                        return stored.get(id);
                    }
                },
                published::add);

        Map<String, Object> response = lambda.handleRequest(Map.of(
                "httpMethod", "POST",
                "body", "{\"points\":[{\"x\":0.1,\"y\":0.1}],"
                        + "\"epsilon\":0.08,\"minPoints\":1}"), null);
        JsonNode result = bodyOf(response);
        String id = result.path("id").asText();

        assertEquals(200, response.get("statusCode"));
        assertEquals(id, UUID.fromString(id).toString());
        assertEquals("GazeClusterLambda", result.path("operation").asText());
        assertTrue(result.hasNonNull("result"));
        assertEquals(1, published.size());
        assertEquals(stored.get(id), response.get("body"));
        assertEquals(stored.get(id), published.get(0));
    }

    @Test
    void getRetrievesStoredResultByQueryOrPathUuid() throws Exception {
        String id = "c5f1b1b7-ccf5-4b98-80f9-f1123589a123";
        String resultJson = "{\"id\":\"" + id + "\",\"numberOfClusters\":0,\"clusters\":[]}";
        GazeClusterLambda lambda = new GazeClusterLambda(new GazeClusterLambda.Storage() {
            @Override
            public void save(String key, String json) { }

            @Override
            public String get(String key) {
                return id.equals(key) ? resultJson : null;
            }
        });

        Map<String, Object> queryResponse = lambda.handleRequest(Map.of(
                "httpMethod", "GET",
                "queryStringParameters", Map.of("id", id)), null);
        Map<String, Object> pathResponse = lambda.handleRequest(Map.of(
                "requestContext", Map.of("http", Map.of("method", "GET")),
                "pathParameters", Map.of("id", id)), null);

        assertEquals(200, queryResponse.get("statusCode"));
        assertEquals(resultJson, queryResponse.get("body"));
        assertEquals(200, pathResponse.get("statusCode"));
        assertEquals(resultJson, pathResponse.get("body"));
    }

    @Test
    void getRejectsInvalidUuidAndReturns404ForMissingResult() {
        GazeClusterLambda lambda = new GazeClusterLambda(new GazeClusterLambda.Storage() {
            @Override
            public void save(String id, String json) { }

            @Override
            public String get(String id) {
                return null;
            }
        });
        assertEquals(400, lambda.handleRequest(Map.of("httpMethod", "GET",
                "queryStringParameters", Map.of("id", "invalid")), null).get("statusCode"));
        assertEquals(404, lambda.handleRequest(Map.of("httpMethod", "GET",
                "queryStringParameters", Map.of(
                        "id", "c5f1b1b7-ccf5-4b98-80f9-f1123589a123")), null)
                .get("statusCode"));
    }

    @Test
    void rejectsNonFiniteAndNonNumericCoordinates() {
        assertEquals(400, post("{\"points\":[{\"x\":1e999,\"y\":0.2}],"
                + "\"epsilon\":0.08,\"minPoints\":2}").get("statusCode"));
        assertEquals(400, post("{\"points\":[{\"x\":\"0.1\",\"y\":0.2}],"
                + "\"epsilon\":0.08,\"minPoints\":2}").get("statusCode"));
    }

    @Test
    void parsesDesktopEventAndBuildsUuidAcknowledgement() throws Exception {
        String id = "c5f1b1b7-ccf5-4b98-80f9-f1123589a123";
        GazeClusterMqttSubscriber.Event event = GazeClusterMqttSubscriber.parseEvent(
                "{\"id\":\"" + id + "\",\"operation\":\"GazeClusterLambda\","
                        + "\"numberOfClusters\":2,\"clusters\":[]}");

        assertEquals(id, event.id());
        assertEquals(2, event.numberOfClusters());
        assertEquals("csc5100/status/" + id,
                GazeClusterMqttSubscriber.acknowledgementTopic(event.id()));
        assertTrue(GazeClusterMqttSubscriber.acknowledgementJson(event).contains("\"received\""));
        assertThrows(IllegalArgumentException.class,
                () -> GazeClusterMqttSubscriber.parseEvent("{\"id\":\"bad\"}"));
    }
}
>>>>>>> 9bebfb9b4a392da0b58bd662d47300c7bd594324
