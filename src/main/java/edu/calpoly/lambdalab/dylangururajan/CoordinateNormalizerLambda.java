package edu.calpoly.lambdalab.dylangururajan;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
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

  private final ObjectMapper mapper = new ObjectMapper();
  private final Storage storage;
  private final Messaging messaging;
  private final Supplier<UUID> uuidSupplier;
  private final CoordinateNormalizerObservability observability;

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
    this(storage, messaging, uuidSupplier, new CoordinateNormalizerObservability());
  }

  CoordinateNormalizerLambda(
      Storage storage,
      Messaging messaging,
      Supplier<UUID> uuidSupplier,
      CoordinateNormalizerObservability observability) {
    this.storage = storage;
    this.messaging = messaging;
    this.uuidSupplier = uuidSupplier;
    this.observability = observability;
  }

  @Override
  public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
    long started = System.nanoTime();
    String requestId = requestId(context);
    try {
      if (event == null) {
        throw new IllegalArgumentException("Request is required");
      }

      String method = httpMethod(event);
      if ("GET".equals(method)) {
        String id = lookupId(event);
        return id == null ? handleLegacyGet(event) : handleLookup(id, context, requestId);
      }
      if ("POST".equals(method)) {
        return handlePost(event, context, requestId);
      }
      return httpResponse(405, "{\"error\":\"Method not allowed\"}");
    } catch (IllegalArgumentException exception) {
      printException(exception);
      observability.log(logger(context), "Lambda", "REQUEST_REJECTED", "FAILURE",
          requestId, elapsedMillis(started), exception);
      return httpResponse(400, "{\"error\":\"Invalid request\"}");
    } catch (Exception exception) {
      printException(exception);
      observability.log(logger(context), "Lambda", "REQUEST_FAILED", "FAILURE",
          requestId, elapsedMillis(started), exception);
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

  private Map<String, Object> handlePost(
      Map<String, Object> event, Context context, String requestId) throws Exception {
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
      runLogged(
          context, "Storage", "RESULT_SAVED", requestId, () -> storage.save(id, json));
    }
    if (messaging != null) {
      runLogged(
          context, "Messaging", "RESULT_PUBLISHED", requestId, () -> messaging.publish(json));
    }
    return httpResponse(200, json);
  }

  private Map<String, Object> handleLookup(String id, Context context, String requestId) {
    if (!isUuid(id)) {
      return httpResponse(400, "{\"error\":\"Invalid id\"}");
    }
    if (storage == null) {
      return httpResponse(503, "{\"error\":\"Storage not configured\"}");
    }
    String json = callLogged(
        context, "Storage", "RESULT_LOOKED_UP", requestId, () -> storage.get(id));
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
      return stringKeyedCopy(bodyMap);
    }
    if (!(body instanceof String text) || text.isBlank()) {
      // Accept the query-style contract used by the original GET endpoint and direct Lambda test
      // events as compatibility fallbacks. A supplied nonblank body must still be valid JSON.
      if (event.get("queryStringParameters") instanceof Map<?, ?> query && !query.isEmpty()) {
        return stringKeyedCopy(query);
      }
      if (hasCoordinateField(event)) {
        return stringKeyedCopy(event);
      }
      throw new IllegalArgumentException("JSON body or coordinate parameters are required");
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

  private Map<String, Object> stringKeyedCopy(Map<?, ?> source) {
    Map<String, Object> copy = new LinkedHashMap<>();
    source.forEach((key, value) -> copy.put(String.valueOf(key), value));
    return copy;
  }

  private boolean hasCoordinateField(Map<String, Object> event) {
    return event.containsKey("x")
        || event.containsKey("y")
        || event.containsKey("width")
        || event.containsKey("height");
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

  private void runLogged(
      Context context, String service, String event, String requestId, Runnable operation) {
    callLogged(context, service, event, requestId, () -> {
      operation.run();
      return null;
    });
  }

  private <T> T callLogged(
      Context context,
      String service,
      String event,
      String requestId,
      Supplier<T> operation) {
    long started = System.nanoTime();
    try {
      T result = operation.get();
      observability.log(logger(context), service, event, "SUCCESS", requestId,
          elapsedMillis(started), null);
      return result;
    } catch (RuntimeException failure) {
      observability.log(logger(context), service, event, "FAILURE", requestId,
          elapsedMillis(started), failure);
      throw failure;
    }
  }

  private long elapsedMillis(long started) {
    return Math.max(0, (System.nanoTime() - started) / 1_000_000);
  }

  private String requestId(Context context) {
    return context == null ? null : context.getAwsRequestId();
  }

  private LambdaLogger logger(Context context) {
    return context == null ? null : context.getLogger();
  }

  private void printException(Exception exception) {
    System.out.println(exception.getMessage());
    exception.printStackTrace(System.out);
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
