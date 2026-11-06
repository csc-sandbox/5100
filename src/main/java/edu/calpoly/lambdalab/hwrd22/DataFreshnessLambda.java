package edu.calpoly.lambdalab.hwrd22;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * AWS Lambda function that checks if the age of a sensor's data
 * is still fresh according to a user-provided threshold.
 *
 * @author Howard Jiang (hwrd22)
 * @version 1.1 (October 27, 2026)
 */
public class DataFreshnessLambda implements RequestHandler<Map<String, Object>, Map<String, Object>> {
    private final ObjectMapper mapper = new ObjectMapper();
    static final String OPERATION = "DataFreshnessLambda";

    interface Storage {
        void save(String id, String json);

        String get(String id);
    }

    interface Messaging {
        void publish(String json);
    }

    private final Storage storage;
    private final Messaging messaging;

    public DataFreshnessLambda() {
        this(createStorage(), createMessaging());
    }

    DataFreshnessLambda(Storage storage) {
        this(storage, null);
    }

    DataFreshnessLambda(Storage storage, Messaging messaging) {
        this.storage = storage;
        this.messaging = messaging;
    }

    private static Storage createStorage() {
        String table = System.getenv("A04_DYNAMODB_TABLE");
        String bucket = System.getenv("A04_S3_BUCKET");
        if (table == null || table.isBlank()) {
            return null;
        }
        return new DataFreshnessStorage(table, bucket);
    }

    private static Messaging createMessaging() {
        String topicArn = System.getenv("A04_SNS_TOPIC_ARN");
        String queueUrl = System.getenv("A04_SQS_QUEUE_URL");
        if (topicArn == null || topicArn.isBlank() || queueUrl == null || queueUrl.isBlank()) {
            return null;
        }
        return new DataFreshnessMessaging(topicArn, queueUrl);
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        try {
            if (event == null) {
                throw new IllegalArgumentException("A request is required.");
            }

            String method = httpMethod(event);
            if ("GET".equals(method)) {
                String id = lookupId(event);
                return id == null ? handleLegacyGet(event) : handleLookup(id);
            }
            if ("POST".equals(method)) {
                return handlePost(event);
            }
            return httpResponse(405, "{\"error\":\"Method not allowed\"}");
        } catch (Exception e) {
            return httpResponse(400, "{\"error\":\"Invalid request\"}");
        }
    }

    private Map<String, Object> handleLegacyGet(Map<String, Object> event) throws Exception {
        Map<String, Object> input;
        // GET
        if (event.get("queryStringParameters") != null) {
            input = (Map<String, Object>)
                    event.get("queryStringParameters");
        }
        // POST
        else {
            String body = event.get("body").toString();
            input = mapper.readValue(body, Map.class);
        }

        long current = Long.parseLong(input.get("current").toString());
        long sample = Long.parseLong(input.get("sample").toString());
        long threshold = Long.parseLong(input.get("threshold").toString());

        Map<String, Object> output = determineStatus(current, sample, threshold);

        return httpResponse(200, mapper.writeValueAsString(output));
    }

    private Map<String, Object> handlePost(Map<String, Object> event) throws Exception {
        Map<String, Object> input = requestBody(event);

        long current = Long.parseLong(input.get("current").toString());
        long sample = Long.parseLong(input.get("sample").toString());
        long threshold = Long.parseLong(input.get("threshold").toString());

        String id = UUID.randomUUID().toString();
        Map<String, Object> output = determineStatus(current, sample, threshold);
        Map<String, Object> response = new HashMap<>();
        response.put("id", id);
        response.put("operation", OPERATION);
        response.put("result", output);

        String json = mapper.writeValueAsString(response);

        if (storage != null) {
            storage.save(id, json);
        }
        if (messaging != null) {
            messaging.publish(json);
        }

        return httpResponse(200, json);
    }

    private Map<String, Object> handleLookup(String id) {
        if (!isUuid(id)) {
            return httpResponse(400, "{\"error\":\"Invalid id\"}");
        }
        if (storage == null) {
            return httpResponse(503, "{\"error\":\"Storage not configured\"}");
        }
        String json = storage.get(id);
        return json == null ? httpResponse(404, "{\"error\":\"Item not found\"}") : httpResponse(200, json);
    }

    private String httpMethod(Map<String, Object> event) {
        Object method = event.get("httpMethod");
        if (method == null && event.get("requestContext") instanceof Map<?, ?> requestContext && requestContext.get("http") instanceof Map<?, ?> http) {
            method = http.get("method");
        }
        if (method != null) {
            return String.valueOf(method).toUpperCase();
        }
        return event.get("queryStringParameters") != null ? "GET" : "POST";
    }

    private String lookupId(Map<String, Object> event) {
        Object queryObject = event.get("queryStringParameters");
        if (queryObject instanceof Map<?, ?> query && query.get("id") != null) {
            return String.valueOf(query.get("id"));
        }
        Object pathObject = event.get("pathParameters");
        if (pathObject instanceof Map<?, ?> path) {
            Object id = path.get("id");
            if (id == null) {
                id = path.get("uuid");
            }
            if (id != null) {
                return String.valueOf(id);
            }
        }
        return null;
    }

    private Map<String, Object> determineStatus(long current, long sample, long threshold) {
        if (current < 0 || sample < 0 || threshold < 0) {
            throw new IllegalArgumentException("Times and threshold must be non-negative.");
        }

        if (sample > current) {
            throw new IllegalArgumentException("Sample time cannot later than current.");
        }

        long age = current - sample;
        String status = age <= threshold ? "FRESH" : "STALE";  // If age has not exceeded threshold, then it is fresh.

        Map<String, Object> output = new HashMap<>();
        output.put("status", status);
        output.put("age", age);
        return output;
    }

    private Map<String, Object> httpResponse(int statusCode, String body) {
        Map<String, Object> response = new HashMap<>();
        response.put("statusCode", statusCode);
        response.put("headers", Map.of("Content-Type", "application/json"));
        response.put("body", body);
        return response;
    }

    private Map<String, Object> requestBody(Map<String, Object> event) {
        Object body = event.get("body");
        if (body instanceof Map<?, ?> bodyMap) {
            Map<String, Object> copy = new HashMap<>();
            bodyMap.forEach((key, value) -> copy.put(String.valueOf(key), value));
            return copy;
        }
        if (!(body instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("JSON body is required");
        }
        if (Boolean.TRUE.equals(event.get("isBase64Encoded"))) {
            text = new String(Base64.getDecoder().decode(text), StandardCharsets.UTF_8);
        }
        try {
            return mapper.readValue(text, new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new IllegalArgumentException("Body must be a JSON", e);
        }
    }

    private boolean isUuid(String id) {
        try {
            return UUID.fromString(id).toString().equals(id);
        } catch (Exception exception) {
            return false;
        }
    }
}
