package edu.calpoly.lambdalab.daome;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.calpoly.lambdalab.daome.data.MovingAverageData;

public class MovingAverageLambda implements RequestHandler<Map<String, Object>, Map<String, Object>> {
    public static final String OPERATION = "MovingAverageLambda";
    public static final String OWNER = "DavidMontiel";
    private static final ObjectMapper mapper = new ObjectMapper();
    private final Storage storage;
    private final Message message;

    public MovingAverageLambda() {
        this(new MovingAverageStorageAdapter(System.getenv("A04_DYNAMODB_TABLE"), System.getenv("A04_S3_BUCKET")),
                new MovingAverageMessage(System.getenv("A04_SNS_TOPIC_ARN"), System.getenv("A04_SQS_QUEUE_URL")));
    }

    MovingAverageLambda(Storage storage, Message message) {
        this.storage = storage;
        this.message = message;
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        String id = UUID.randomUUID().toString();
        try {
            String method = (String) event.get("httpMethod");
            var output = switch (method) {
                case "GET" -> handleGet(event);
                case "POST" -> handlePost(id, event);
                default -> throw new RuntimeException("Unsupported");
            };

            return httpResponse(200, mapper.writeValueAsString(output));
        } catch (Exception e) {
            return httpResponse(400, "{\"error\":\"Invalid request\"}");
        }

    }

    Map<String, Object> handleGet(Map<String, Object> event) throws RuntimeException, JsonProcessingException {
        if (event.get("queryStringParameters") == null) {throw new RuntimeException();}

        var input = mapper.readValue(event.get("queryStringParameters").toString(),
                new TypeReference<Map<String, Object>>() {});
        var id = (String) input.get("id");
        return mapper.readValue(storage.get(id), new TypeReference<>() {});
    }

    Map<String, Object> handlePost(String id, Map<String, Object> event) throws RuntimeException,
            JsonProcessingException {
        String body = event.get("body").toString();
        var input = mapper.readValue(body, new TypeReference<Map<String, Object>>() {});
        Integer windowSize = mapper.convertValue(input.get("window"), Integer.class);
        List<Integer> samples = mapper.convertValue(input.get("samples"), new TypeReference<>() {});
        var movingAverages = movingAverage(samples, windowSize);
        var storageData = new MovingAverageData(id, samples, windowSize, movingAverages);
        var json = mapper.writeValueAsString(storageData);
        storage.save(id, json);
        message.publish(id, json);

        return new HashMap<>(Map.of("id", id, "movingAverage", storageData.averages()));
    }

    private List<Double> movingAverage(List<Integer> numbers, Integer windowSize) {
        if (windowSize > numbers.size() || windowSize <= 0) {
            throw new IllegalArgumentException("Window Size Bigger than List");
        }

        List<Double> result = new ArrayList<>(windowSize);
        for (int i = 0; i <= numbers.size() - windowSize; i++) {
            var sum = 0;
            for (int j = 0; j < windowSize; j++) {
                sum += numbers.get(i + j);
            }

            result.add(1.0 * sum / windowSize);
        }
        return result;
    }

    private Map<String, Object> httpResponse(int statusCode, String body) {
        Map<String, Object> response = new HashMap<>();
        response.put("statusCode", statusCode);
        response.put("headers", Map.of("Content-Type", "application/json"));
        response.put("body", body);
        return response;
    }

    public interface Storage {
        void save(String id, String json);

        String get(String id);

    }

    public interface Message {
        // Separate due to requirements
        void publish(String id, String json);
    }

}
