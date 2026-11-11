
package edu.calpoly.lambdalab.adrian0427;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;

final class DominantAffectObservability {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private DominantAffectObservability() { }

    static void log(String requestId, String service,
                    String event, String status, long startNanos) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("course", "CSC5100");
        fields.put("component", "DominantAffectLambda");
        fields.put("service", service);
        fields.put("event", event);
        fields.put("status", status);

        if (requestId != null) {
            fields.put("requestId", requestId);
        }

        if (startNanos > 0) {
            fields.put("durationMs",
                    (System.nanoTime() - startNanos) / 1_000_000.0);
        }

        try {
            System.out.println(MAPPER.writeValueAsString(fields));
        } catch (Exception ignored) {
            // Logging must not affect Lambda execution.
        }
    }
}
