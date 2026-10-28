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
import java.util.UUID;

/** API Gateway adapter for GazeClusterLambda A04 cloud integration. */
public class GazeClusterLambda
        implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    interface Storage {
        void save(String id, String json);
        String get(String id);
    }

    interface Messaging {
        void publish(String json);
    }

    private final ObjectMapper mapper = new ObjectMapper();
    private final Storage storage;
    private final Messaging messaging;
    private final boolean storageMisconfigured;
    private final boolean messagingMisconfigured;

    public GazeClusterLambda() {
        this(System.getenv("A04_DYNAMODB_TABLE"), System.getenv("A04_S3_BUCKET"),
                System.getenv("A04_SNS_TOPIC_ARN"), System.getenv("A04_SQS_QUEUE_URL"));
    }

    private GazeClusterLambda(String table, String bucket, String topicArn, String queueUrl) {
        boolean hasTable = isConfigured(table);
        boolean hasBucket = isConfigured(bucket);
        storageMisconfigured = !hasTable || !hasBucket;
        storage = hasTable && hasBucket ? new GazeClusterStorage(table, bucket) : null;
        boolean hasTopic = isConfigured(topicArn);
        boolean hasQueue = isConfigured(queueUrl);
        messagingMisconfigured = !hasTopic || !hasQueue;
        messaging = hasTopic || hasQueue
                ? new GazeClusterMessaging(topicArn, queueUrl) : null;
    }

    GazeClusterLambda(Storage storage) {
        this(storage, null);
    }

    GazeClusterLambda(Storage storage, Messaging messaging) {
        this.storage = storage;
        this.messaging = messaging;
        this.storageMisconfigured = false;
        this.messagingMisconfigured = false;
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        String method = requestMethod(event);
        if ("GET".equals(method)) {
            return getById(event, context);
        }
        if (!"POST".equals(method)) {
            return httpResponse(400, "{\"error\":\"Unsupported method\"}");
        }
        if (storageMisconfigured || messagingMisconfigured) {
            return httpResponse(503, "{\"error\":\"A04 integration configuration incomplete\"}");
        }

        try {
            JsonNode input = readInput(event);
            List<DBSCAN.GazePoint> points = parsePoints(input.path("points"));
            double epsilon = finiteNumber(input.get("epsilon"), "epsilon");
            int minPoints = positiveInteger(input.get("minPoints"), "minPoints");
            if (epsilon <= 0 || points.isEmpty()) {
                throw new IllegalArgumentException("Invalid gaze clustering input");
            }

            List<DBSCAN.Cluster> clusters = DBSCAN.findClusters(points, epsilon, minPoints);
            Map<String, Object> output = new LinkedHashMap<>();
            String id = UUID.randomUUID().toString();
            List<Map<String, Object>> clusterJson = toClusterJson(clusters);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("numberOfClusters", clusters.size());
            result.put("clusters", clusterJson);
            output.put("id", id);
            output.put("operation", "GazeClusterLambda");
            output.put("result", result);
            output.put("numberOfClusters", clusters.size());
            output.put("clusters", clusterJson);
            String json = mapper.writeValueAsString(output);

            if (storage != null) {
                storage.save(id, json);
            }
            if (messaging != null) {
                messaging.publish(json);
            }
            return httpResponse(200, json);
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

    private Map<String, Object> getById(Map<String, Object> event, Context context) {
        String id = requestId(event);
        if (!isUuid(id)) {
            return httpResponse(400, "{\"error\":\"Invalid id\"}");
        }
        if (storage == null) {
            return httpResponse(503, storageMisconfigured
                    ? "{\"error\":\"Storage configuration incomplete\"}"
                    : "{\"error\":\"Storage not configured\"}");
        }
        try {
            String json = storage.get(id);
            return json == null ? httpResponse(404, "{\"error\":\"Not found\"}")
                    : httpResponse(200, json);
        } catch (RuntimeException exception) {
            if (context != null) {
                context.getLogger().log("GazeClusterLambda retrieval failure: "
                        + exception.getClass().getSimpleName());
            }
            return httpResponse(500, "{\"error\":\"Operation failed\"}");
        }
    }

    private JsonNode readInput(Map<String, Object> event) {
        if (event == null || event.get("body") == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        JsonNode input;
        try {
            input = mapper.readTree(event.get("body").toString());
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Request body must be valid JSON", exception);
        }
        if (input == null || !input.isObject()) {
            throw new IllegalArgumentException("Request body must be an object");
        }
        return input;
    }

    private List<DBSCAN.GazePoint> parsePoints(JsonNode rawPoints) {
        if (rawPoints == null || !rawPoints.isArray() || rawPoints.isEmpty()) {
            throw new IllegalArgumentException("At least one point is required");
        }
        List<DBSCAN.GazePoint> points = new ArrayList<>();
        for (JsonNode point : rawPoints) {
            if (!point.isObject()) {
                throw new IllegalArgumentException("Each point must be an object");
            }
            double x = finiteNumber(point.get("x"), "x");
            double y = finiteNumber(point.get("y"), "y");
            points.add(new DBSCAN.GazePoint(x, y));
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

    private List<Map<String, Object>> toClusterJson(List<DBSCAN.Cluster> clusters) {
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

    private String requestMethod(Map<String, Object> event) {
        if (event == null) {
            return "POST";
        }
        Object method = event.get("httpMethod");
        if (method == null && event.get("requestContext") instanceof Map<?, ?> requestContext
                && requestContext.get("http") instanceof Map<?, ?> http) {
            method = http.get("method");
        }
        if (method == null) {
            method = event.get("queryStringParameters") instanceof Map<?, ?> query
                    && query.containsKey("id") ? "GET" : "POST";
        }
        return method.toString().toUpperCase();
    }

    private String requestId(Map<String, Object> event) {
        if (event == null) {
            return null;
        }
        if (event.get("pathParameters") instanceof Map<?, ?> pathParameters) {
            Object id = pathParameters.get("id");
            if (id == null) {
                id = pathParameters.get("proxy");
            }
            if (id != null) {
                return id.toString();
            }
        }
        if (event.get("queryStringParameters") instanceof Map<?, ?> query) {
            Object id = query.get("id");
            return id == null ? null : id.toString();
        }
        return null;
    }

    private boolean isUuid(String id) {
        try {
            return id != null && UUID.fromString(id).toString().equals(id);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean isConfigured(String value) {
        return value != null && !value.isBlank();
    }

    private Map<String, Object> httpResponse(int statusCode, String body) {
        return Map.of("statusCode", statusCode,
                "headers", Map.of("Content-Type", "application/json"), "body", body);
    }
}