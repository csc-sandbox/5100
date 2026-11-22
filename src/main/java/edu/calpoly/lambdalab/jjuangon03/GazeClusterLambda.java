package edu.calpoly.lambdalab.jjuangon03;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/** API Gateway Lambda for reliable, correlated gaze-clustering operations. */
public class GazeClusterLambda implements RequestHandler<Map<String, Object>, Map<String, Object>> {
    static final String OPERATION = "GazeClusterLambda";

    interface Storage { void save(String id, String json); String get(String id); }
    interface Messaging { void publish(String json); }
    interface EventLogger { void log(Context context, String event, Map<String, Object> fields); }

    private final ObjectMapper mapper = new ObjectMapper();
    private final Storage storage;
    private final Messaging messaging;
    private final Supplier<UUID> uuidSupplier;
    private final EventLogger logger;
    private final GazeClusterRetryPolicy retryPolicy;

    /** Uses A04 destinations only when their environment variables are configured. */
    public GazeClusterLambda() {
        this(createStorage(System.getenv("A04_DYNAMODB_TABLE"), System.getenv("A04_S3_BUCKET")),
                createMessaging(System.getenv("A04_SNS_TOPIC_ARN"), System.getenv("A04_SQS_QUEUE_URL")),
                UUID::randomUUID, new GazeClusterStructuredLogger(), defaultRetryPolicy());
    }

    GazeClusterLambda(Storage storage) { this(storage, null, UUID::randomUUID); }
    GazeClusterLambda(Storage storage, Messaging messaging) { this(storage, messaging, UUID::randomUUID); }
    GazeClusterLambda(Storage storage, Messaging messaging, Supplier<UUID> uuidSupplier) {
        this(storage, messaging, uuidSupplier, new GazeClusterStructuredLogger(), defaultRetryPolicy());
    }
    GazeClusterLambda(Storage storage, Messaging messaging, Supplier<UUID> uuidSupplier,
            EventLogger logger, GazeClusterRetryPolicy retryPolicy) {
        this.storage = storage;
        this.messaging = messaging;
        this.uuidSupplier = uuidSupplier;
        this.logger = logger;
        this.retryPolicy = retryPolicy;
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        try {
            if (event == null) throw new IllegalArgumentException("Request is required");
            String method = httpMethod(event);
            if ("GET".equals(method)) return handleLookup(lookupId(event), context);
            if (!"POST".equals(method)) return httpResponse(405, "{\"error\":\"Method not allowed\"}");
            return handlePost(event, context);
        } catch (IllegalArgumentException exception) {
            logger.log(context, "gaze_cluster.request_rejected", Map.of("reason", String.valueOf(exception.getMessage())));
            return httpResponse(400, "{\"error\":\"Invalid request\"}");
        } catch (Exception exception) {
            logger.log(context, "gaze_cluster.operation_failed", Map.of(
                    "failureType", exception.getClass().getSimpleName()));
            return httpResponse(500, "{\"error\":\"Operation failed\"}");
        }
    }

    private Map<String, Object> handlePost(Map<String, Object> event, Context context) throws Exception {
        Map<String, Object> input = requestBody(event);
        List<DBSCAN.GazePoint> points = points(input);
        double epsilon = finiteNumber(input, "epsilon");
        int minPoints = positiveInteger(input, "minPoints");
        if (epsilon <= 0) throw new IllegalArgumentException("epsilon must be positive");

        List<Map<String, Object>> clusters = clusters(points, epsilon, minPoints);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("numberOfClusters", clusters.size());
        result.put("clusters", clusters);
        String id = uuidSupplier.get().toString();
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("id", id);
        output.put("operation", OPERATION);
        output.put("result", result);
        output.putAll(result); // Preserve the original contract for existing callers.
        String json = mapper.writeValueAsString(output);

        logger.log(context, "gaze_cluster.accepted", Map.of("id", id, "clusterCount", clusters.size()));
        // Saving a fixed ID/key is idempotent, so only that operation is safe to retry automatically.
        if (storage != null) {
            retryPolicy.execute(() -> storage.save(id, json));
            logger.log(context, "gaze_cluster.persisted", Map.of("id", id));
        }
        // Fan-out is intentionally not retried here: SNS/SQS can already have accepted the event.
        if (messaging != null) {
            messaging.publish(json);
            logger.log(context, "gaze_cluster.published", Map.of("id", id));
        }
        return httpResponse(200, json);
    }

    private Map<String, Object> handleLookup(String id, Context context) {
        if (id == null || !isUuid(id)) return httpResponse(400, "{\"error\":\"Invalid id\"}");
        if (storage == null) return httpResponse(503, "{\"error\":\"Storage not configured\"}");
        String json = storage.get(id);
        logger.log(context, "gaze_cluster.lookup", Map.of("id", id, "found", json != null));
        return json == null ? httpResponse(404, "{\"error\":\"Not found\"}") : httpResponse(200, json);
    }

    @SuppressWarnings("unchecked")
    private List<DBSCAN.GazePoint> points(Map<String, Object> input) {
        if (!(input.get("points") instanceof List<?> rawPoints) || rawPoints.isEmpty()) {
            throw new IllegalArgumentException("points are required");
        }
        List<DBSCAN.GazePoint> result = new ArrayList<>();
        for (Object rawPoint : rawPoints) {
            if (!(rawPoint instanceof Map<?, ?> point)) throw new IllegalArgumentException("Point must be an object");
            double x = finiteNumber((Map<String, Object>) point, "x");
            double y = finiteNumber((Map<String, Object>) point, "y");
            if (x < 0 || x > 1 || y < 0 || y > 1) throw new IllegalArgumentException("Points must be normalized");
            result.add(new DBSCAN.GazePoint(x, y));
        }
        return result;
    }

    private List<Map<String, Object>> clusters(List<DBSCAN.GazePoint> points, double epsilon, int minPoints) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (DBSCAN.Cluster cluster : DBSCAN.findClusters(points, epsilon, minPoints)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", cluster.id());
            item.put("centerX", cluster.centerX());
            item.put("centerY", cluster.centerY());
            item.put("points", cluster.points());
            result.add(item);
        }
        return result;
    }

    private Map<String, Object> requestBody(Map<String, Object> event) {
        Object body = event.get("body");
        if (body instanceof Map<?, ?> bodyMap) {
            Map<String, Object> copy = new LinkedHashMap<>();
            bodyMap.forEach((key, value) -> copy.put(String.valueOf(key), value));
            return copy;
        }
        if (!(body instanceof String text) || text.isBlank()) throw new IllegalArgumentException("JSON body is required");
        if (Boolean.TRUE.equals(event.get("isBase64Encoded"))) {
            text = new String(Base64.getDecoder().decode(text), StandardCharsets.UTF_8);
        }
        try {
            return mapper.readValue(text, new TypeReference<>() { });
        } catch (Exception exception) {
            throw new IllegalArgumentException("Body must be a JSON object", exception);
        }
    }

    private String httpMethod(Map<String, Object> event) {
        Object method = event.get("httpMethod");
        if (method == null && event.get("requestContext") instanceof Map<?, ?> requestContext
                && requestContext.get("http") instanceof Map<?, ?> http) method = http.get("method");
        return method == null ? "POST" : String.valueOf(method).toUpperCase();
    }

    private String lookupId(Map<String, Object> event) {
        if (event.get("queryStringParameters") instanceof Map<?, ?> query && query.get("id") != null) return String.valueOf(query.get("id"));
        if (event.get("pathParameters") instanceof Map<?, ?> path) {
            Object id = path.get("id") == null ? path.get("uuid") : path.get("id");
            return id == null ? null : String.valueOf(id);
        }
        return null;
    }

    private double finiteNumber(Map<String, Object> input, String name) {
        Object value = input.get(name);
        if (value == null) throw new IllegalArgumentException("Missing field: " + name);
        double number = Double.parseDouble(value.toString());
        if (!Double.isFinite(number)) throw new IllegalArgumentException("Field must be finite: " + name);
        return number;
    }

    private int positiveInteger(Map<String, Object> input, String name) {
        double number = finiteNumber(input, name);
        if (number <= 0 || number != Math.rint(number) || number > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Field must be a positive integer: " + name);
        }
        return (int) number;
    }

    private boolean isUuid(String id) {
        try { return UUID.fromString(id).toString().equals(id); }
        catch (Exception exception) { return false; }
    }

    private Map<String, Object> httpResponse(int statusCode, String body) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("statusCode", statusCode);
        response.put("headers", Map.of("Content-Type", "application/json"));
        response.put("body", body);
        return response;
    }

    private static Storage createStorage(String table, String bucket) {
        return configured(table) && configured(bucket) ? new GazeClusterStorage(table, bucket) : null;
    }
    private static Messaging createMessaging(String topicArn, String queueUrl) {
        return configured(topicArn) || configured(queueUrl) ? new GazeClusterMessaging(topicArn, queueUrl) : null;
    }
    private static boolean configured(String value) { return value != null && !value.isBlank(); }
    private static GazeClusterRetryPolicy defaultRetryPolicy() {
        return new GazeClusterRetryPolicy(3, 25, Thread::sleep);
    }
}
