
package edu.calpoly.lambdalab.briggsmurphy;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AWS Lambda function that finds the closest LiDAR measurement
 * and its index.
 *
 * @author Briggs Murphy
 * @version 1.0
 */
public class LidarClosestLambda
    implements RequestHandler<Map<String, Object>, Map<String, Object>> {

  interface Storage {
    void save(String id, String json);
    String get(String id);
  }

  private final ObjectMapper mapper = new ObjectMapper();
  private final Storage storage;

  public LidarClosestLambda() {
    this(null);
  }

  LidarClosestLambda(Storage storage) {
    this.storage = storage;
  }

  @Override
  @SuppressWarnings("unchecked")
  public Map<String, Object> handleRequest(
      Map<String, Object> event, Context context) {
    try {
      if (event == null) {
        return httpResponse(400, "{\"error\":\"Invalid request\"}");
      }

      Map<String, Object> requestContext =
          event.get("requestContext") instanceof Map
              ? (Map<String, Object>) event.get("requestContext")
              : Map.of();

      Map<String, Object> http =
          requestContext.get("http") instanceof Map
              ? (Map<String, Object>) requestContext.get("http")
              : Map.of();

      Map<String, Object> query =
          event.get("queryStringParameters") instanceof Map
              ? (Map<String, Object>) event.get("queryStringParameters")
              : Map.of();

      String method = String.valueOf(event.getOrDefault(
          "httpMethod",
          http.getOrDefault(
              "method",
              event.get("queryStringParameters") != null
                  ? "GET" : "POST")))
          .toUpperCase();

      if ("GET".equals(method)) {
        String id = String.valueOf(query.get("id"));

        if (!isUuid(id)) {
          return httpResponse(400, "{\"error\":\"Invalid id\"}");
        }

        if (storage == null) {
          return httpResponse(
              503, "{\"error\":\"Storage not configured\"}");
        }

        String json = storage.get(id);

        if (json == null) {
          return httpResponse(404, "{\"error\":\"Not found\"}");
        }

        return httpResponse(200, json);
      }

      if (!"POST".equals(method)) {
        return httpResponse(400, "{\"error\":\"Invalid request\"}");
      }

      String body = String.valueOf(event.get("body"));
      Map<String, Object> input = mapper.readValue(body, Map.class);

      List<?> distances = (List<?>) input.get("distances");

      if (distances == null || distances.isEmpty()) {
        throw new IllegalArgumentException("Distances array is required.");
      }

      double[] values = new double[distances.size()];

      for (int i = 0; i < distances.size(); i++) {
        values[i] = Double.parseDouble(distances.get(i).toString());

        if (!Double.isFinite(values[i])) {
          throw new IllegalArgumentException(
              "Distances must be finite numbers.");
        }
      }

      Map<String, Object> result = findClosest(values);

      String id = input.containsKey("id")
          ? String.valueOf(input.get("id"))
          : UUID.randomUUID().toString();

      if (!isUuid(id)) {
        return httpResponse(400, "{\"error\":\"Invalid id\"}");
      }

      result.put("id", id);

      String json = mapper.writeValueAsString(result);

      if (storage != null) {
        storage.save(id, json);
      }

      return httpResponse(200, json);

    } catch (JsonProcessingException | IllegalArgumentException e) {
      return httpResponse(400, "{\"error\":\"Invalid request\"}");
    } catch (ClassCastException e) {
      return httpResponse(400, "{\"error\":\"Invalid request\"}");
    } catch (Exception e) {
      if (context != null) {
        context.getLogger().log(
            "LidarClosestLambda failure: "
                + e.getClass().getSimpleName());
      }
      return httpResponse(500, "{\"error\":\"Operation failed\"}");
    }
  }

  private boolean isUuid(String id) {
    try {
      return UUID.fromString(id).toString().equals(id);
    } catch (Exception e) {
      return false;
    }
  }

  private Map<String, Object> findClosest(double[] distances) {
    int closestIndex = 0;
    double closestDistance = distances[0];

    for (int i = 1; i < distances.length; i++) {
      if (distances[i] < closestDistance) {
        closestDistance = distances[i];
        closestIndex = i;
      }
    }

    Map<String, Object> result = new HashMap<>();
    result.put("distance", closestDistance);
    result.put("index", closestIndex);

    return result;
  }

  private Map<String, Object> httpResponse(int statusCode, String body) {
    Map<String, Object> response = new HashMap<>();
    response.put("statusCode", statusCode);
    response.put("headers", Map.of("Content-Type", "application/json"));
    response.put("body", body);

    return response;
  }
}
