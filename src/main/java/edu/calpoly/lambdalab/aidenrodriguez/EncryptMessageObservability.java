package edu.calpoly.lambdalab.aidenrodriguez;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Emits sanitized single-line JSON events for Lambda operations. */
final class EncryptMessageObservability {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private EncryptMessageObservability() { }

    static void log(Consumer<String> logger, String requestId, String service,
            String event, String status, long startNanos, Exception error) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("course", "CSC5100");
        fields.put("component", "EncryptMessageLambda");
        if (requestId != null) fields.put("requestId", requestId);
        fields.put("service", service);
        fields.put("event", event);
        fields.put("status", status);
        if (startNanos > 0) {
            fields.put("durationMs", Math.max(0, System.nanoTime() - startNanos) / 1_000_000.0);
        }
        if (error != null) fields.put("errorType", error.getClass().getSimpleName());
        try {
            logger.accept(MAPPER.writeValueAsString(fields));
        } catch (JsonProcessingException | RuntimeException ignored) {
            // Logging must not change the business outcome.
        }
    }
}