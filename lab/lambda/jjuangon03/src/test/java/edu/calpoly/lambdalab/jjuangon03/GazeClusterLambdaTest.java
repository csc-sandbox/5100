package edu.calpoly.lambdalab.jjuangon03;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GazeClusterLambdaTest {
    private final GazeClusterLambda lambda = new GazeClusterLambda();
    private final ObjectMapper mapper = new ObjectMapper();

    private Map<String, Object> post(String body) {
        Map<String, Object> event = new HashMap<>();
        event.put("body", body);
        return lambda.handleRequest(event, null);
    }

    private JsonNode responseBody(Map<String, Object> response) throws Exception {
        return mapper.readTree(response.get("body").toString());
    }

    @Test
    void identifiesTwoSeparatedClusters() throws Exception {
        Map<String, Object> response = post("""
                {"points":[
                  {"x":0.10,"y":0.20},{"x":0.12,"y":0.21},{"x":0.11,"y":0.18},
                  {"x":0.75,"y":0.70},{"x":0.78,"y":0.72},{"x":0.76,"y":0.69}],
                 "epsilon":0.08,"minPoints":2}
                """);
        JsonNode body = responseBody(response);

        assertEquals(200, response.get("statusCode"));
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
    void identifiesOneClusterAndExcludesNoise() throws Exception {
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
        assertEquals(0.51, body.path("clusters").get(0).path("centerX").asDouble(), 0.01);
    }

    @Test
    void rejectsInvalidInputWith400() {
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
    void scatteredPointsReturnNoClusters() throws Exception {
        JsonNode body = responseBody(post("""
                {"points":[{"x":0.1,"y":0.1},{"x":0.9,"y":0.9}],
                 "epsilon":0.05,"minPoints":2}
                """));

        assertEquals(0, body.path("numberOfClusters").asInt());
        assertTrue(body.path("clusters").isEmpty());
    }
}