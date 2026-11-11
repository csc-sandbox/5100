package edu.calpoly.lambdalab.paulmotter;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.awscore.exception.AwsServiceException;

/**
 * Structured JSON telemetry for the Robot Distance Lambda and its AWS operations.
 * Each call prints one CloudWatch-ready line; failures add an error field without
 * the exception message, which can contain request data.
 *
 * @author Paul Motter (PaulMotter)
 * @version 1.1 (2026-11-10)
 */
public final class RobotDistanceObservability {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Outcome of the operation being logged. */
    public enum Status {
        SUCCESS("SUCCESS"),
        FAILURE("FAILURE");

        private final String value;
        Status(String value) {
            this.value = value;
        }
        public String getValue() {
            return value;
        }
    }



    private RobotDistanceObservability() {
    }

    /**
     * Emits one telemetry line for a Robot Distance operation.
     *
     * @param readingId  domain record id, or {@code null} to omit
     * @param requestId  invocation/request id, or {@code null} to omit
     * @param service    downstream service (e.g. {@code "S3"}, {@code "Lambda"})
     * @param event      event name (e.g. {@code "REQUEST_RECEIVED"})
     * @param status     operation outcome
     * @param startNanos {@link System#nanoTime()} at start, or {@code 0} to omit duration
     */
    static void log(
        String readingId, String requestId, 
        String service, String event, 
        Status status, long startNanos
    ) {
        log(readingId, requestId, service, event, status, startNanos, null);
    }

    /**
     * Emits one telemetry line for a failed operation, with a single {@code error}
     * field describing the exception, e.g. {@code "SnsException (HTTP 500, Throttling)"}
     * or {@code "IllegalStateException"}.
     *
     * <p>The exception message is deliberately not logged, because it can echo request data.
     *
     * @param error the failure, or {@code null} to omit the error field
     */
    static void log(
        String readingId, String requestId,
        String service, String event,
        Status status, long startNanos, Throwable error
    ) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("course", "CSC5100");
        fields.put("component", "RobotDistanceLambda");
        if (readingId != null) fields.put("readingId", readingId);
        if (requestId != null) fields.put("requestId", requestId);
        fields.put("service", service);
        fields.put("event", event);
        fields.put("status", status == null ? null : status.getValue());
        if (startNanos > 0) {
            fields.put("durationMs", (System.nanoTime() - startNanos) / 1_000_000.0);
        }
        if (error != null) fields.put("error", describe(error));
        try {
            System.out.println(MAPPER.writeValueAsString(fields));
        } catch (Exception ignored) {
            // Telemetry must not change the business outcome.
        }
    }

    /** Exception class, plus HTTP status and AWS error code for AWS service errors. */
    private static String describe(Throwable error) {
        String type = error.getClass().getSimpleName();
        if (error instanceof AwsServiceException aws) {
            String code = aws.awsErrorDetails() == null ? null : aws.awsErrorDetails().errorCode();
            return type + " (HTTP " + aws.statusCode() + (code == null ? "" : ", " + code) + ")";
        }
        return type;
    }
}
