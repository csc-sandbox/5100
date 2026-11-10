package edu.calpoly.lambdalab.briggsmurphy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Emits sanitized, single-line structured JSON telemetry
 * for LidarClosestLambda operations.
 *
 * @author Briggs Murphy
 * @version 1.0
 */
final class LidarClosestObservability {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private LidarClosestObservability() { }

  static void log(String requestId, String service,
                  String event, String status, long startNanos) {
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("course", "CSC5100");
    fields.put("component", "LidarClosestLambda");

    if (requestId != null) {
      fields.put("requestId", requestId);
    }

    fields.put("service", service);
    fields.put("event", event);
    fields.put("status", status);

    if (startNanos > 0) {
      fields.put(
          "durationMs",
          (System.nanoTime() - startNanos) / 1_000_000.0);
    }

    try {
      System.out.println(MAPPER.writeValueAsString(fields));
    } catch (Exception ignored) {
      // Telemetry must not change the business outcome.
    }
  }
}
