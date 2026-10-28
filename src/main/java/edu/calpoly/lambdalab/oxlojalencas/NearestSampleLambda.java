package edu.calpoly.lambdalab.oxlojalencas;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Returns the sensor sample closest to an event timestamp.
 *
 * @author Jess A
 * @version October 4, 2026
 */
public class NearestSampleLambda
        implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    /** saves and loads result json by id. */
    interface Storage {
        void save(String id, String json);

        String get(String id);
    }

    /** publishes one event json string. */
    interface Messaging {
        void publish(String json);
    }

    private final ObjectMapper mapper = new ObjectMapper();
    private final Storage storage;
    private final Messaging messaging;

    public NearestSampleLambda() {
        this(createStorage(), createMessaging());
    }

    NearestSampleLambda(Storage storage) {
        this(storage, null);
    }

    NearestSampleLambda(Storage storage, Messaging messaging) {
        this.storage = storage;
        this.messaging = messaging;
    }

    // storage stays off until the table and bucket are set in the environment
    private static Storage createStorage() {
        String table = System.getenv("A04_DYNAMODB_TABLE");
        String bucket = System.getenv("A04_S3_BUCKET");
        if (table == null || table.isBlank()
                || bucket == null || bucket.isBlank()) {
            return null;
        }
        return new NearestSampleStorage(table, bucket);
    }

    // messaging stays off until the topic arn and queue url are both set
    private static Messaging createMessaging() {
        String topicArn = System.getenv("A04_SNS_TOPIC_ARN");
        String queueUrl = System.getenv("A04_SQS_QUEUE_URL");
        if (topicArn == null || topicArn.isBlank()
                || queueUrl == null || queueUrl.isBlank()) {
            return null;
        }
        return new NearestSampleMessaging(topicArn, queueUrl);
    }

    /**
     * Reads an API Gateway request + returns a JSON response.
     *
     * @param event API Gateway request data
     * @param context Lambda runtime context
     * @return HTTP response
     */
    @Override
    public Map<String, Object> handleRequest(
            Map<String, Object> event,
            Context context) {
        if ("GET".equalsIgnoreCase(readMethod(event))) {
            return getResult(event, context);
        }
        return postResult(event, context);
    }

    // http api v2 events keep the method in requestContext, not httpMethod
    private String readMethod(Map<String, Object> event) {
        if (event.get("httpMethod") != null) {
            return event.get("httpMethod").toString();
        }

        if (event.get("requestContext") instanceof Map<?, ?> requestContext
                && requestContext.get("http") instanceof Map<?, ?> http
                && http.get("method") != null) {
            return http.get("method").toString();
        }

        return "POST";
    }

    private Map<String, Object> postResult(
            Map<String, Object> event,
            Context context) {
        String id;
        String json;
        String eventJson;

        try {
            String body = event.get("body").toString();
            RequestData request = readRequest(body);

            NearestResult result = findNearestSample(
                    request.eventTime(),
                    request.samples());

            id = UUID.randomUUID().toString();

            Map<String, Object> output = new HashMap<>();
            output.put("nearestSample", result.sample());
            output.put("timeDifference", result.timeDifference());
            output.put("id", id);

            json = mapper.writeValueAsString(output);
            eventJson = buildEvent(id, result);
        } catch (Exception exception) {
            return errorResponse(400, "Invalid request");
        }

        try {
            if (storage != null) {
                storage.save(id, json);
            }
        } catch (Exception exception) {
            logError(context, exception);
            return errorResponse(500, "Could not save result");
        }

        try {
            if (messaging != null) {
                messaging.publish(eventJson);
            }
        } catch (Exception exception) {
            logError(context, exception);
            return errorResponse(500, "Could not publish event");
        }

        return response(200, json);
    }

    private String buildEvent(String id, NearestResult result) throws Exception {
        return mapper.writeValueAsString(Map.of(
                "id", id,
                "operation", "NearestSampleLambda",
                "result", Map.of(
                        "nearestSample", result.sample(),
                        "timeDifference", result.timeDifference())));
    }

    private Map<String, Object> getResult(
            Map<String, Object> event,
            Context context) {
        Map<?, ?> query = event.get("queryStringParameters") instanceof Map<?, ?> map
                ? map
                : Map.of();
        String id = String.valueOf(query.get("id"));

        if (!isUuid(id)) {
            return errorResponse(400, "Invalid id");
        }
        if (storage == null) {
            return errorResponse(503, "Storage not configured");
        }

        try {
            String storedResult = storage.get(id);
            if (storedResult == null) {
                return errorResponse(404, "Not found");
            }
            return response(200, storedResult);
        } catch (Exception exception) {
            logError(context, exception);
            return errorResponse(500, "Could not load result");
        }
    }

    /**
     * Finds the closest sample + its absolute time difference.
     *
     * @param eventTime timestamp to compare
     * @param samples sensor samples
     * @return closest sample + time difference
     */
    public NearestResult findNearestSample(
            double eventTime,
            List<Sample> samples) {
        if (!Double.isFinite(eventTime)
                || samples == null
                || samples.isEmpty()) {
            throw new IllegalArgumentException("Invalid samples");
        }

        Sample closest = null;
        double closestDifference = Double.POSITIVE_INFINITY;

        for (Sample sample : samples) {
            if (sample == null
                    || !Double.isFinite(sample.timestamp())
                    || !Double.isFinite(sample.value())) {
                throw new IllegalArgumentException("Invalid sample");
            }

            double difference = Math.abs(sample.timestamp() - eventTime);
            if (difference < closestDifference) {
                closest = sample;
                closestDifference = difference;
            }
        }

        return new NearestResult(closest, closestDifference);
    }

    private RequestData readRequest(String body) throws Exception {
        Map<?, ?> data = mapper.readValue(body, Map.class);

        double eventTime = readNumber(data.get("eventTimestamp"));
        Object rawSamples = data.get("samples");

        if (!(rawSamples instanceof List<?> sampleList)
                || sampleList.isEmpty()) {
            throw new IllegalArgumentException("Samples are required");
        }

        List<Sample> samples = new ArrayList<>();

        for (Object rawSample : sampleList) {
            if (!(rawSample instanceof Map<?, ?> sampleData)) {
                throw new IllegalArgumentException("Invalid sample");
            }

            double timestamp = readNumber(sampleData.get("timestamp"));
            double value = readNumber(sampleData.get("value"));
            samples.add(new Sample(timestamp, value));
        }

        return new RequestData(eventTime, samples);
    }

    private double readNumber(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("Number is required");
        }

        double number = Double.parseDouble(value.toString());

        if (!Double.isFinite(number)) {
            throw new IllegalArgumentException("Number must be finite");
        }

        return number;
    }

    private Map<String, Object> response(int statusCode, String body) {
        Map<String, Object> response = new HashMap<>();
        response.put("statusCode", statusCode);
        response.put("headers", Map.of("Content-Type", "application/json"));
        response.put("body", body);
        return response;
    }

    private Map<String, Object> errorResponse(int statusCode, String message) {
        return response(statusCode, "{\"error\":\"" + message + "\"}");
    }

    private boolean isUuid(String id) {
        try {
            return UUID.fromString(id).toString().equals(id);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private void logError(Context context, Exception exception) {
        if (context != null) {
            context.getLogger().log(exception.toString());
        }
    }

    /** One timestamped sensor sample. */
    public record Sample(double timestamp, double value) {
    }

    /** Closest sample + its time difference. */
    public record NearestResult(Sample sample, double timeDifference) {
    }

    private record RequestData(double eventTime, List<Sample> samples) {
    }
}
