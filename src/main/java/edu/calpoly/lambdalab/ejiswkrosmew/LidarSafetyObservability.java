package edu.calpoly.lambdalab.ejiswkrosmew;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

final public class LidarSafetyObservability {
    private static final ObjectMapper mapper  = new ObjectMapper();

    private LidarSafetyObservability () {}

    static void log(String readingId, String requestId, String service, String event, String status, long startNanos) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("course", "CSC5100");
        if (readingId != null) fields.put("readingId", readingId);
        if (requestId != null) fields.put("requestId", requestId);
        fields.put("service", service);
        fields.put("event", status);
        fields.put("status", status);
        if (startNanos > 0) {
            fields.put("durationMs", (System.nanoTime() - startNanos) / 1_000_000.0);
        }
        try {
            System.out.println(mapper.writeValueAsString(fields));
        } catch (Exception e) {
            // Well damn
        }
    }
}
