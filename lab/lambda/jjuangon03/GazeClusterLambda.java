package edu.calpoly.lambdalab.jjuangon03;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** API Gateway adapter for gaze clustering. */
public class GazeClusterLambda implements RequestHandler<Map<String, Object>, Map<String, Object>> {
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        try {
            JsonNode input = readInput(event);
            List<DBSCAN.GazePoint> points = parsePoints(input.path("points"));
            double epsilon = finiteNumber(input.get("epsilon"), "epsilon");
            int minPoints = positiveInteger(input.get("minPoints"), "minPoints");
            if (epsilon <= 0) throw new IllegalArgumentException("epsilon must be positive");

            List<DBSCAN.Cluster> clusters = DBSCAN.findClusters(points, epsilon, minPoints);
            List<Map<String, Object>> clusterJson = clusterJson(clusters);
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("numberOfClusters", clusters.size());
            output.put("clusters", clusterJson);
            return response(200, mapper.writeValueAsString(output));
        } catch (IllegalArgumentException exception) {
            return response(400, "{\"error\":\"Invalid request\"}");
        } catch (Exception exception) {
            if (context != null) {
                context.getLogger().log("GazeClusterLambda failure: "
                        + exception.getClass().getSimpleName());
            }
            return response(500, "{\"error\":\"Operation failed\"}");
        }
    }

    private JsonNode readInput(Map<String, Object> event) {
        if (event == null || event.get("body") == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        try {
            JsonNode input = mapper.readTree(event.get("body").toString());
            if (input == null || !input.isObject()) {
                throw new IllegalArgumentException("Request body must be an object");
            }
            return input;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Request body must be valid JSON", exception);
        }
    }

    private List<DBSCAN.GazePoint> parsePoints(JsonNode rawPoints) {
        if (rawPoints == null || !rawPoints.isArray() || rawPoints.isEmpty()) {
            throw new IllegalArgumentException("At least one point is required");
        }
        List<DBSCAN.GazePoint> points = new ArrayList<>();
        for (JsonNode point : rawPoints) {
            if (!point.isObject()) throw new IllegalArgumentException("Each point must be an object");
            points.add(new DBSCAN.GazePoint(finiteNumber(point.get("x"), "x"),
                    finiteNumber(point.get("y"), "y")));
        }
        return points;
    }

    private double finiteNumber(JsonNode value, String name) {
        if (value == null || !value.isNumber() || !Double.isFinite(value.asDouble())) {
            throw new IllegalArgumentException(name + " must be a finite number");
        }
        return value.asDouble();
    }

    private int positiveInteger(JsonNode value, String name) {
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()
                || value.intValue() <= 0) {
            throw new IllegalArgumentException(name + " must be a positive integer");
        }
        return value.intValue();
    }

    private List<Map<String, Object>> clusterJson(List<DBSCAN.Cluster> clusters) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (DBSCAN.Cluster cluster : clusters) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", cluster.id());
            item.put("centerX", cluster.centerX());
            item.put("centerY", cluster.centerY());
            item.put("points", cluster.points());
            result.add(item);
        }
        return result;
    }

    private Map<String, Object> response(int status, String body) {
        return Map.of("statusCode", status,
                "headers", Map.of("Content-Type", "application/json"), "body", body);
    }
}
