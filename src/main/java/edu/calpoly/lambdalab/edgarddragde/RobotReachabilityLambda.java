package edu.calpoly.lambdalab.edgarddragde;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;


/**
 * AWS Lambda handler that determines whether a 3D point is within
 * a robot workspace centered at the origin.
 *
 * GET: query parameters {@code x}, {@code y}, {@code z}, and
 * {@code radius} return JSON containing {@code distance} and
 * {@code reachable}. Points on the boundary are reachable. This path is
 * read-only and never produces an {@code id}.
 *
 * POST: the same four values in a JSON body. Each successful POST
 * generates one UUID, stores the result JSON under it, and returns that
 * same JSON including {@code id}.
 *
 * GET by id: {@code GET ?id=<uuid>} returns the stored JSON.
 *
 * @author Edgard Aviles
 * @version 1.1.0 October 25, 2026
 */
public class RobotReachabilityLambda implements RequestHandler<Map<String, Object>, Map<String, Object>> {

  /** Persistence boundary; Task 2 supplies a DynamoDB implementation. */
  interface ResultStore {
    void save(String id, String json);

    String get(String id);
  }

  /** Per-container store used until DynamoDB is wired in (Task 2) and by unit tests. */
  static final class InMemoryResultStore implements ResultStore {
    private final Map<String, String> items = new ConcurrentHashMap<>();

    @Override
    public void save(String id, String json) {
      items.put(id, json);
    }

    @Override
    public String get(String id) {
      return items.get(id);
    }

    int size() {
      return items.size();
    }
  }

  private static final String INVALID_REQUEST = "{\"error\":\"Invalid request\"}";

  private final ObjectMapper mapper = new ObjectMapper();
  private final ResultStore store;
  private final Supplier<String> idSupplier;

  /**
   * Lambda runtime entry point; existing callers and tests use this.
   * Uses DynamoDB when A04_DYNAMODB_TABLE is set, otherwise an in-memory store.
   */
  public RobotReachabilityLambda() {
    this(storeFromEnvironment());
  }

  private static ResultStore storeFromEnvironment() {
    String table = System.getenv("A04_DYNAMODB_TABLE");
    return table != null && !table.isBlank() ? new DynamoResultStore(table) : new InMemoryResultStore();
  }

  RobotReachabilityLambda(ResultStore store) {
    this(store, () -> UUID.randomUUID().toString());
  }

  RobotReachabilityLambda(ResultStore store, Supplier<String> idSupplier) {
    this.store = store;
    this.idSupplier = idSupplier;
  }

  /**
  * Routes the request: POST executes and stores, GET with {@code id}
  * looks up a stored result, any other request takes the original GET path.
  *
  * @param event API Gateway event (REST or HTTP API shape)
  * @param context Lambda execution context; may be null in tests
  * @return HTTP 200 with JSON, 400 for invalid input, 404 for an unknown id,
  * or 500 if storage fails
  */
  @Override
  public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
    if (event == null) {
      return httpResponse(400, INVALID_REQUEST);
    }
    try {
      String method = resolveMethod(event);
      if ("POST".equals(method)) {
        return execute(event);
      }
      Object query = event.get("queryStringParameters");
      if ("GET".equals(method) && query instanceof Map<?, ?> q && q.containsKey("id")) {
        return lookup(String.valueOf(q.get("id")));
      }
      return legacyGet(event);
    } catch (IllegalArgumentException e) {
      return httpResponse(400, INVALID_REQUEST);
    } catch (Exception e) {
      if (context != null) {
        // CloudWatch gets the detail; the HTTP response stays generic.
        context.getLogger().log("RobotReachabilityLambda failure: " + e);
      }
      return httpResponse(500, "{\"error\":\"Operation failed\"}");
    }
  }

  /**
  * Original behavior: validate query parameters and return distance and
  * reachability. Any failure is an HTTP 400, exactly as before.
  */
  private Map<String, Object> legacyGet(Map<String, Object> event) {
    try {
      if (!(event.get("queryStringParameters") instanceof Map<?, ?> query)) {
        throw new IllegalArgumentException("not valid input\n");
      }
      @SuppressWarnings("unchecked")
      Map<String, Object> input = (Map<String, Object>) query;
      return httpResponse(200, mapper.writeValueAsString(compute(input)));
    } catch (Exception e) {
      return httpResponse(400, INVALID_REQUEST);
    }
  }

  /** A04 POST: compute, generate one UUID, store, and return the stored JSON. */
  private Map<String, Object> execute(Map<String, Object> event) throws JsonProcessingException {
    Map<String, Object> result = compute(parseBody(event));
    // A non-finite distance (coordinate overflow) cannot be stored faithfully.
    if (!Double.isFinite(((Number) result.get("distance")).doubleValue())) {
      throw new IllegalArgumentException("distance is not finite");
    }
    String id = idSupplier.get();
    Map<String, Object> record = new LinkedHashMap<>();
    record.put("id", id);
    record.putAll(result);
    String json = mapper.writeValueAsString(record);
    store.save(id, json);
    return httpResponse(200, json);
  }

  /** A04 GET by id: return exactly the JSON that was stored. */
  private Map<String, Object> lookup(String id) {
    if (!isUuid(id)) {
      return httpResponse(400, "{\"error\":\"Invalid id\"}");
    }
    String json = store.get(id);
    return json == null ? httpResponse(404, "{\"error\":\"Not found\"}") : httpResponse(200, json);
  }

  private Map<String, Object> parseBody(Map<String, Object> event) {
    if (!(event.get("body") instanceof String body) || body.isBlank()) {
      throw new IllegalArgumentException("missing body");
    }
    try {
      Map<String, Object> input = mapper.readValue(body, new TypeReference<Map<String, Object>>() { });
      if (input == null) {
        throw new IllegalArgumentException("empty body");
      }
      return input;
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("malformed body", e);
    }
  }

  /**
  * Shared validation and calculation for both GET and POST.
  *
  * @param input map containing x, y, z, and radius
  * @return ordered map with distance and reachable
  * @throws IllegalArgumentException if a value is missing, nonnumeric,
  * non-finite, or the radius is negative
  */
  private Map<String, Object> compute(Map<String, Object> input) {
    double x = number(input, "x");
    double y = number(input, "y");
    double z = number(input, "z");
    double radius = number(input, "radius");
    if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !Double.isFinite(radius)) {
      throw new IllegalArgumentException("not valid double\n");
    }
    double dist = distance(x, y, z);
    Map<String, Object> output = new LinkedHashMap<>();
    output.put("distance", dist);
    output.put("reachable", reachable(dist, radius));
    return output;
  }

  private static double number(Map<String, Object> input, String key) {
    Object value = input.get(key);
    if (value == null) {
      throw new IllegalArgumentException("missing " + key);
    }
    return Double.parseDouble(value.toString());
  }

  /** REST API: httpMethod. HTTP API: requestContext.http.method. Neither: GET (original behavior). */
  private static String resolveMethod(Map<String, Object> event) {
    Object method = event.get("httpMethod");
    if (method == null && event.get("requestContext") instanceof Map<?, ?> requestContext
        && requestContext.get("http") instanceof Map<?, ?> http) {
      method = http.get("method");
    }
    return method == null ? "GET" : method.toString().toUpperCase(Locale.ROOT);
  }

  private static boolean isUuid(String id) {
    try {
      return UUID.fromString(id).toString().equals(id);
    } catch (Exception e) {
      return false;
    }
  }

  /**
  * Calculates the Euclidean distance from the origin to a 3D point.
  *
  * @param x the x coordinate
  * @param y the y coordinate
  * @param z the z coordinate
  * @return the distance from the origin
  */
  private double distance(double x, double y, double z) {
    return Math.sqrt(x*x + y*y + z*z);
  }

  /**
  * Determines whether a distance is within the workspace radius,
  * including the boundary.
  *
  * @param dist the distance from the origin
  * @param radius the workspace radius
  * @return true if the distance is less than or equal to the radius
  * @throws IllegalArgumentException if radius is negative or NaN
  */
  private boolean reachable(double dist, double radius) {
    if (radius >= 0) {
      return dist <= radius;
    }
    throw new IllegalArgumentException("invalid input");
  }
  /**
  * Builds an API Gateway response with a JSON content type.
  *
  * @param statusCode the HTTP status code
  * @param body the JSON response body
  * @return a map containing the status code, headers, and body
  */ 
  private Map<String, Object> httpResponse(int statusCode, String body) {
    Map<String, Object> response = new HashMap<>();
    response.put("statusCode", statusCode);
    response.put("headers", Map.of("Content-Type", "application/json"));
    response.put("body", body);
    return response;
  }
}
