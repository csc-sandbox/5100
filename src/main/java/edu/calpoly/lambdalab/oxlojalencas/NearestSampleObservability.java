package edu.calpoly.lambdalab.oxlojalencas;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * writes one structured json log line per event, for cloudwatch logs insights.
 * a line holds only ids, names, status and timing, never the request body,
 * the result json, resource names or exception messages.
 *
 * @author Jess A
 * @version November 10, 2026
 */
final class NearestSampleObservability {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private NearestSampleObservability() {
    }

    /** logs one event; id and requestId are left out when null. */
    static void log(String id, String requestId, String service,
            String event, String status) {
        write(id, requestId, service, event, status, null, null);
    }

    /** logs a failed event with the exception class name, never its message. */
    static void failure(String id, String requestId, String service,
            String event, Exception error) {
        write(id, requestId, service, event, "FAILURE", null, error);
    }

    /** runs one aws call, logs how long it took, and rethrows any failure. */
    static void timed(String id, String service, String event,
            Runnable operation) {
        long start = System.nanoTime();
        try {
            operation.run();
        } catch (RuntimeException exception) {
            write(id, null, service, event, "FAILURE", millisSince(start), exception);
            throw exception;
        }
        write(id, null, service, event, "SUCCESS", millisSince(start), null);
    }

    private static double millisSince(long start) {
        return (System.nanoTime() - start) / 1_000_000.0;
    }

    private static void write(String id, String requestId, String service,
            String event, String status, Double durationMs, Exception error) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("course", "CSC5100");
        fields.put("component", "NearestSampleLambda");
        // readingId is the field name the shared dashboard already uses
        if (id != null) {
            fields.put("readingId", id);
        }
        if (requestId != null) {
            fields.put("requestId", requestId);
        }
        fields.put("service", service);
        fields.put("event", event);
        fields.put("status", status);
        if (durationMs != null) {
            fields.put("durationMs", durationMs);
        }
        if (error != null) {
            fields.put("errorType", error.getClass().getSimpleName());
        }
        try {
            System.out.println(MAPPER.writeValueAsString(fields));
        } catch (Exception ignored) {
            // logging must never change the result of a request
        }
    }
}
