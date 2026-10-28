package edu.calpoly.lambdalab.jjuangon03;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GazeClusterLambdaTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final GazeClusterLambda lambda = new GazeClusterLambda();

    private Map<String, Object> post(String body) {
        return lambda.handleRequest(Map.of("body", body), null);
    }

    private JsonNode json(Map<String, Object> response) throws Exception {
        return mapper.readTree(response.get("body").toString());
    }

    private void assertContractFields(JsonNode result) {
        assertFields(result, Set.of("numberOfClusters", "clusters"));
        for (JsonNode cluster : result.path("clusters")) {
            assertFields(cluster, Set.of("id", "centerX", "centerY", "points"));
        }
    }

    private void assertFields(JsonNode node, Set<String> expected) {
        Set<String> fields = new HashSet<>();
        Iterator<String> names = node.fieldNames();
        names.forEachRemaining(fields::add);
        assertEquals(expected, fields);
    }

    @Test
    void twoObviousGazeClusters() throws Exception {
        Map<String, Object> response = post("""
                {"points":[
                  {"x":0.10,"y":0.20},{"x":0.12,"y":0.21},{"x":0.11,"y":0.18},
                  {"x":0.75,"y":0.70},{"x":0.78,"y":0.72},{"x":0.76,"y":0.69}],
                 "epsilon":0.08,"minPoints":2}
                """);
        JsonNode result = json(response);
        assertEquals(200, response.get("statusCode"));
        assertContractFields(result);
        assertEquals(2, result.path("numberOfClusters").asInt());
        assertEquals(3, result.path("clusters").get(0).path("points").asInt());
        assertEquals(0.11, result.path("clusters").get(0).path("centerX").asDouble(), 0.01);
        assertEquals(3, result.path("clusters").get(1).path("points").asInt());
        assertEquals(0.76, result.path("clusters").get(1).path("centerX").asDouble(), 0.01);
    }

    @Test
    void oneGazeCluster() throws Exception {
        Map<String, Object> response = post("""
                {"points":[{"x":0.50,"y":0.50},{"x":0.52,"y":0.51},{"x":0.51,"y":0.49}],
                 "epsilon":0.05,"minPoints":2}
                """);
        JsonNode result = json(response);
        assertEquals(200, response.get("statusCode"));
        assertContractFields(result);
        assertEquals(1, result.path("numberOfClusters").asInt());
        assertEquals(3, result.path("clusters").get(0).path("points").asInt());
    }

    @Test
    void invalidInputReturns400() {
        assertEquals(400, post("{\"points\":[],\"epsilon\":0.08,\"minPoints\":2}")
                .get("statusCode"));
        assertEquals(400, post("{\"points\":[{\"x\":\"bad\",\"y\":0.2}],"
                + "\"epsilon\":0.08,\"minPoints\":2}").get("statusCode"));
        assertEquals(400, post("not-json").get("statusCode"));
    }
}
