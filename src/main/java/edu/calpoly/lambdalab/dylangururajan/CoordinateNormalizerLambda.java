package edu.calpoly.lambdalab.dylangururajan;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * API Gateway Lambda that normalizes pixel coordinates.
 *
 * <p>The original GET conversion remains read-only. POST creates one correlated result event and
 * sends the exact same JSON to DynamoDB, S3, SNS, and SQS. GET with an {@code id} retrieves the
 * previously stored result from DynamoDB.</p>
 */
public class CoordinateNormalizerLambda
    implements RequestHandler<Map<String, Object>, Map<String, Object>> {

  static final String OPERATION = "CoordinateNormalizerLambda";

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
  private final Supplier<UUID> uuidSupplier;

  /** Uses the A04 environment variables configured on the deployed Lambda. */
  public CoordinateNormalizerLambda() {
    this(createStorage(
            System.getenv("A04_DYNAMODB_TABLE"), System.getenv("A04_S3_BUCKET")),
        createMessaging(
            System.getenv("A04_SNS_TOPIC_ARN"), System.getenv("A04_SQS_QUEUE_URL")),
        UUID::randomUUID);
  }

  CoordinateNormalizerLambda(Storage storage) {
    this(storage, null, UUID::randomUUID);
  }

  CoordinateNormalizerLambda(Storage storage, Messaging messaging) {
    this(storage, messaging, UUID::randomUUID);
  }

  CoordinateNormalizerLambda(
      Storage storage, Messaging messaging, Supplier<UUID> uuidSupplier) {
    this.storage = storage;
    this.messaging = messaging;
    this.uuidSupplier = uuidSupplier;
  }

  @Override
  public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
    try {
      if (event == null) {
        throw new IllegalArgumentException("Request is required");
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
    } catch (IllegalArgumentException exception) {
      return httpResponse(400, "{\"error\":\"Invalid request\"}");
    } catch (Exception exception) {
      if (context != null) {
        context.getLogger().log(
            "CoordinateNormalizerLambda failure: " + exception.getClass().getSimpleName());
      }
      return httpResponse(500, "{\"error\":\"Operation failed\"}");
    }
  }

  private Map<String, Object> handleLegacyGet(Map<String, Object> event) throws Exception {
    Map<String, Object> query = queryParameters(event);
    Map<String, Double> normalized = normalizeCoordinates(
        parseFiniteNumber(query, "x"),
        parseFiniteNumber(query, "y"),
        parseFiniteNumber(query, "width"),
        parseFiniteNumber(query, "height"));
    return httpResponse(200, mapper.writeValueAsString(normalized));
  }

  private Map<String, Object> handlePost(Map<String, Object> event) throws Exception {
    Map<String, Object> input = requestBody(event);
    double x = parseFiniteNumber(input, "x");
    double y = parseFiniteNumber(input, "y");
    double width = parseFiniteNumber(input, "width");
    double height = parseFiniteNumber(input, "height");
    Map<String, Double> normalized = normalizeCoordinates(x, y, width, height);

    String id = uuidSupplier.get().toString();
    Map<String, Object> resultEvent = new LinkedHashMap<>();
    resultEvent.put("id", id);
    resultEvent.put("operation", OPERATION);
    resultEvent.put("x", x);
    resultEvent.put("y", y);
    resultEvent.put("width", width);
    resultEvent.put("height", height);
    resultEvent.put("result", normalized);
    String json = mapper.writeValueAsString(resultEvent);

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
    return json == null
        ? httpResponse(404, "{\"error\":\"Not found\"}")
        : httpResponse(200, json);
  }

  // Normalizes a point while preserving the original Lambda's validation contract.
  public Map<String, Double> normalizeCoordinates(
      double x, double y, double width, double height) {
    if (!Double.isFinite(x)
        || !Double.isFinite(y)
        || !Double.isFinite(width)
        || !Double.isFinite(height)
        || width <= 0
        || height <= 0
        || x < 0
        || x > width
        || y < 0
        || y > height) {
      throw new IllegalArgumentException("Coordinates and dimensions must describe a valid point");
    }

    Map<String, Double> result = new LinkedHashMap<>();
    result.put("normalizedX", x / width);
    result.put("normalizedY", y / height);
    return result;
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> queryParameters(Map<String, Object> event) {
    if (!(event.get("queryStringParameters") instanceof Map<?, ?>)) {
      throw new IllegalArgumentException("Query parameters are required");
    }
    return (Map<String, Object>) event.get("queryStringParameters");
  }

  private Map<String, Object> requestBody(Map<String, Object> event) {
    Object body = event.get("body");
    if (body instanceof Map<?, ?> bodyMap) {
      Map<String, Object> copy = new LinkedHashMap<>();
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
      return mapper.readValue(text, new TypeReference<>() { });
    } catch (Exception exception) {
      throw new IllegalArgumentException("Body must be a JSON object", exception);
    }
  }

  private String httpMethod(Map<String, Object> event) {
    Object method = event.get("httpMethod");
    if (method == null && event.get("requestContext") instanceof Map<?, ?> requestContext
        && requestContext.get("http") instanceof Map<?, ?> http) {
      method = http.get("method");
    }
    if (method != null) {
      return String.valueOf(method).toUpperCase();
    }
    // Preserve direct invocation of the original query-based Lambda.
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

  private double parseFiniteNumber(Map<String, Object> input, String name) {
    Object value = input.get(name);
    if (value == null) {
      throw new IllegalArgumentException("Missing numeric field: " + name);
    }
    double number = Double.parseDouble(value.toString());
    if (!Double.isFinite(number)) {
      throw new IllegalArgumentException("Field must be finite: " + name);
    }
    return number;
  }

  private boolean isUuid(String id) {
    try {
      return UUID.fromString(id).toString().equals(id);
    } catch (Exception exception) {
      return false;
    }
  }

  private Map<String, Object> httpResponse(int statusCode, String body) {
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("statusCode", statusCode);
    response.put("headers", Map.of("Content-Type", "application/json"));
    response.put("body", body);
    return response;
  }

  private static Storage createStorage(String table, String bucket) {
    return isConfigured(table) && isConfigured(bucket)
        ? new CoordinateNormalizerStorage(table, bucket)
        : null;
  }

  private static Messaging createMessaging(String topicArn, String queueUrl) {
    return isConfigured(topicArn) || isConfigured(queueUrl)
        ? new CoordinateNormalizerMessaging(topicArn, queueUrl)
        : null;
  }

  private static boolean isConfigured(String value) {
    return value != null && !value.isBlank();
  }
}
