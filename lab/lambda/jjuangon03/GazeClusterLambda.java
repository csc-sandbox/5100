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

/** API Gateway handler for the gaze-clustering endpoint. */
public class GazeClusterLambda
        implements RequestHandler<Map<String, Object>, Map<String, Object>> {
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        try {
            JsonNode request = readRequest(event);
            List<DBSCAN.GazePoint> points = readPoints(request.get("points"));
            double epsilon = readFiniteNumber(request.get("epsilon"), "epsilon");
            int minPoints = readPositiveInteger(request.get("minPoints"), "minPoints");
            if (epsilon <= 0) {
                throw new IllegalArgumentException("epsilon must be greater than zero");
            }

            List<DBSCAN.Cluster> clusters = DBSCAN.findClusters(points, epsilon, minPoints);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("numberOfClusters", clusters.size());
            result.put("clusters", toResponseClusters(clusters));
            return httpResponse(200, mapper.writeValueAsString(result));
        } catch (IllegalArgumentException exception) {
            return httpResponse(400, "{\"error\":\"Invalid request\"}");
        } catch (Exception exception) {
            if (context != null) {
                context.getLogger().log("GazeClusterLambda failure: "
                        + exception.getClass().getSimpleName());
            }
            return httpResponse(500, "{\"error\":\"Operation failed\"}");
        }
    }

    private JsonNode readRequest(Map<String, Object> event) {
        if (event == null || event.get("body") == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        try {
            JsonNode request = mapper.readTree(event.get("body").toString());
            if (request == null || !request.isObject()) {
                throw new IllegalArgumentException("Request body must be a JSON object");
            }
            return request;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Request body must be valid JSON", exception);
        }
    }

    private List<DBSCAN.GazePoint> readPoints(JsonNode value) {
        if (value == null || !value.isArray() || value.isEmpty()) {
            throw new IllegalArgumentException("points must be a non-empty array");
        }
        List<DBSCAN.GazePoint> points = new ArrayList<>();
        for (JsonNode point : value) {
            if (!point.isObject()) {
                throw new IllegalArgumentException("Each point must be a JSON object");
            }
            points.add(new DBSCAN.GazePoint(
                    readFiniteNumber(point.get("x"), "x"),
                    readFiniteNumber(point.get("y"), "y")));
        }
        return points;
    }

    private double readFiniteNumber(JsonNode value, String name) {
        if (value == null || !value.isNumber() || !Double.isFinite(value.asDouble())) {
            throw new IllegalArgumentException(name + " must be a finite number");
        }
        return value.asDouble();
    }

    private int readPositiveInteger(JsonNode value, String name) {
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()
                || value.intValue() <= 0) {
            throw new IllegalArgumentException(name + " must be a positive integer");
        }
        return value.intValue();
    }

    private List<Map<String, Object>> toResponseClusters(List<DBSCAN.Cluster> clusters) {
        List<Map<String, Object>> responseClusters = new ArrayList<>();
        for (DBSCAN.Cluster cluster : clusters) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", cluster.id());
            item.put("centerX", cluster.centerX());
            item.put("centerY", cluster.centerY());
            item.put("points", cluster.points());
            responseClusters.add(item);
        }
        return responseClusters;
    }

    private Map<String, Object> httpResponse(int statusCode, String body) {
        return Map.of(
                "statusCode", statusCode,
                "headers", Map.of("Content-Type", "application/json"),
                "body", body);
    }
}
