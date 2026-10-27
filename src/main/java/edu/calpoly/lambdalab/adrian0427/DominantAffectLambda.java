package edu.calpoly.lambdalab.adrian0427;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;


public class DominantAffectLambda
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


    public DominantAffectLambda() {
        this(createStorage(), createMessaging());
    }

    DominantAffectLambda(Storage storage) {
        this(storage, null);
    }

    DominantAffectLambda(Storage storage, Messaging messaging) {
        this.storage = storage;
        this.messaging = messaging;
    }

    private static Storage createStorage() {
        String table = System.getenv("A04_DYNAMODB_TABLE");
        String bucket = System.getenv("A04_S3_BUCKET");

        if (table == null || table.isBlank()
                || bucket == null || bucket.isBlank()) {
            return null;
        }

        return new DominantAffectStorage(table, bucket);
    }

    private static Messaging createMessaging() {
        String topicArn = System.getenv("A04_SNS_TOPIC_ARN");
        String queueUrl = System.getenv("A04_SQS_QUEUE_URL");

        if (topicArn == null || topicArn.isBlank()
                || queueUrl == null || queueUrl.isBlank()) {
            return null;
        }

        return new DominantAffectMessaging(topicArn, queueUrl);
    }

    @Override
    public Map<String, Object> handleRequest(
            Map<String, Object> event, Context context) {

        try {
            if (event == null) {
                throw new IllegalArgumentException("Missing request");
            }

            String method = getHttpMethod(event);

            if ("GET".equals(method)) {
                return handleGet(event);
            }

            if (!"POST".equals(method)) {
                return httpResponse(405, "{\"error\":\"Method not allowed\"}");
            }

            if (event.get("body") == null) {
                throw new IllegalArgumentException("Missing request body");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> input =
                    mapper.readValue(event.get("body").toString(), Map.class);

            Map.Entry<String, Double> dominant = findDominantAffect(input);

            String id = UUID.randomUUID().toString();

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("id", id);
            output.put("metric", dominant.getKey());
            output.put("value", dominant.getValue());

            String json = mapper.writeValueAsString(output);


            if (storage != null) {
                storage.save(id, json);
            }

            if (messaging != null) {
                Map<String, Object> eventMessage = new LinkedHashMap<>();
                eventMessage.put("id", id);
                eventMessage.put("operation", "DominantAffectLambda");
                eventMessage.put("result", Map.of(
                        "metric", dominant.getKey(),
                        "value", dominant.getValue()));

                messaging.publish(mapper.writeValueAsString(eventMessage));
            }

            return httpResponse(200, json);

        } catch (IllegalArgumentException e) {
            return httpResponse(400, "{\"error\":\"Invalid request\"}");
        } catch (Exception e) {
            if (context != null) {
                context.getLogger().log(
                        "DominantAffectLambda failure: "
                                + e.getClass().getSimpleName());
            }
            return httpResponse(500, "{\"error\":\"Operation failed\"}");
        }
    }

    private String getHttpMethod(Map<String, Object> event) {
        Object method = event.get("httpMethod");

        if (method == null && event.get("requestContext") instanceof Map<?, ?> context) {
            Object http = context.get("http");
            if (http instanceof Map<?, ?> httpMap) {
                method = httpMap.get("method");
            }
        }

        return method == null ? "POST" : method.toString().toUpperCase();
    }

    private Map<String, Object> handleGet(Map<String, Object> event) {
        if (!(event.get("queryStringParameters") instanceof Map<?, ?> query)) {
            throw new IllegalArgumentException("Missing query parameters");
        }

        Object idValue = query.get("id");

        if (idValue == null || !isUuid(idValue.toString())) {
            return httpResponse(400, "{\"error\":\"Invalid id\"}");
        }

        if (storage == null) {
            return httpResponse(503, "{\"error\":\"Storage not configured\"}");
        }

        String json = storage.get(idValue.toString());

        if (json == null) {
            return httpResponse(404, "{\"error\":\"Not found\"}");
        }

        return httpResponse(200, json);
    }

    private boolean isUuid(String id) {
        try {
            return UUID.fromString(id).toString().equals(id);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private Map.Entry<String, Double> findDominantAffect(
            Map<String, Object> affectValues) {

        if (affectValues == null || affectValues.isEmpty()) {
            throw new IllegalArgumentException("Affect values cannot be empty");
        }

        String dominantMetric = null;
        double dominantValue = Double.NEGATIVE_INFINITY;

        for (Map.Entry<String, Object> entry : affectValues.entrySet()) {
            double value;

            try {
                value = Double.parseDouble(entry.getValue().toString());
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        "All affect values must be numeric");
            }

            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException(
                        "Affect values must be finite");
            }

            if (dominantMetric == null
                    || value > dominantValue
                    || (value == dominantValue
                    && entry.getKey().compareTo(dominantMetric) < 0)) {

                dominantMetric = entry.getKey();
                dominantValue = value;
            }
        }

        return Map.entry(dominantMetric, dominantValue);
    }

    private Map<String, Object> httpResponse(int statusCode, String body) {
        Map<String, Object> response = new LinkedHashMap<>();

        response.put("statusCode", statusCode);
        response.put("headers", Map.of("Content-Type", "application/json"));
        response.put("body", body);

        return response;
    }
}
