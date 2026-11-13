package edu.calpoly.lambdalab.paulmotter;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import edu.calpoly.lambdalab.paulmotter.RobotDistanceObservability.Status;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.services.sns.model.SnsException;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Verifies the structured JSON log contract used by the public dashboard.
 * Checks required fields, optional fields, error descriptions, and that no
 * request data or secrets appear in log output.
 *
 * @author Paul Motter
 * @version 1.1 (2026-11-10)
 */
public class RobotDistanceObservabilityTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String READING_ID = "c5f1b1b7-ccf5-4b98-80f9-f1123589a123";
    private static final String REQUEST_ID = "lambda-request-1";

    /** Runs the action with System.out captured and returns each printed line. */
    private static List<String> captureLines(Runnable action) {
        PrintStream previous = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
            action.run();
        } finally {
            System.setOut(previous);
        }
        List<String> lines = new ArrayList<>();
        for (String line : captured.toString(StandardCharsets.UTF_8).split("\\R")) {
            if (!line.isBlank()) lines.add(line.trim());
        }
        return lines;
    }

    private static JsonNode captureOne(Runnable action) throws Exception {
        List<String> lines = captureLines(action);
        assertEquals(1, lines.size());
        System.out.println(lines.get(0));
        return MAPPER.readTree(lines.get(0));
    }

    @Test
    void CheckACompleteLog() throws Exception {
        JsonNode event = captureOne(() -> RobotDistanceObservability.log(READING_ID,
            REQUEST_ID, "S3", "S3_SAVED", Status.SUCCESS, System.nanoTime()));

        assertEquals("CSC5100", event.get("course").asText());
        assertEquals("RobotDistanceLambda", event.get("component").asText());
        assertEquals("S3", event.get("service").asText());
        assertEquals("S3_SAVED", event.get("event").asText());
        assertEquals("SUCCESS", event.get("status").asText());
        assertEquals(READING_ID, event.get("readingId").asText());
        assertEquals(REQUEST_ID, event.get("requestId").asText());
        assertTrue(event.get("durationMs").isNumber());
        assertTrue(event.get("durationMs").asDouble() >= 0);
        assertFalse(event.has("payload"));
        assertFalse(event.has("error"));
    }

    @Test
    void CheckAMinimalLog() throws Exception {
        JsonNode event = captureOne(() -> RobotDistanceObservability.log(null,
            null, "S3", "S3_SAVED", Status.FAILURE, 0));

        assertEquals("FAILURE", event.get("status").asText());
        assertFalse(event.has("readingId"));
        assertFalse(event.has("requestId"));
        assertFalse(event.has("durationMs"));
        assertFalse(event.has("payload"));
        assertFalse(event.has("error"));
    }

    /** Tests the error field: AWS failures give type, HTTP status, and error code; others give type only. */
    @Test
    void CheckFailureIsDescribed() throws Exception {
        SnsException aws = (SnsException) SnsException.builder()
            .statusCode(503)
            .awsErrorDetails(AwsErrorDetails.builder().errorCode("Throttling").build())
            .build();

        JsonNode awsEvent = captureOne(() -> RobotDistanceObservability.log(READING_ID,
            REQUEST_ID, "SNS", "PUBLISHED", Status.FAILURE, System.nanoTime(), aws));
        JsonNode otherEvent = captureOne(() -> RobotDistanceObservability.log(null,
            REQUEST_ID, "Lambda", "REQUEST_FAILED", Status.FAILURE, 0, new IllegalStateException("boom")));

        assertEquals("SnsException (HTTP 503, Throttling)", awsEvent.get("error").asText());
        assertEquals("IllegalStateException", otherEvent.get("error").asText());
    }

    /**
     * Tests every log line from a full POST (success path and AWS failure path):
     * each has the required fields, and none contains the request coordinates or body.
     */
    @Test
    void CheckLambdaLogsDoNotExposeRequestData() throws Exception {
        Map<String, Object> body = RobotDistanceLambdaTest.RobotDistanceFakes.body(
            "123.456", "-654.321", "111.222", "333.444", "-555.666", "777.888");
        Map<String, Object> post = RobotDistanceLambdaTest.RobotDistanceFakes.post(body);

        List<String> lines = new ArrayList<>();
        lines.addAll(captureLines(() -> RobotDistanceLambdaTest.RobotDistanceFakes.lambda(
            new RobotDistanceLambdaTest.RobotDistanceFakes.FakeDynamo(),
            new RobotDistanceLambdaTest.RobotDistanceFakes.FakeS3(),
            new RobotDistanceLambdaTest.RobotDistanceFakes.FakeSNS(),
            new RobotDistanceLambdaTest.RobotDistanceFakes.FakeSQS()).handleRequest(post, null)));
        lines.addAll(captureLines(() -> RobotDistanceLambdaTest.RobotDistanceFakes.lambda(
            new RobotDistanceLambdaTest.RobotDistanceFakes.FakeDynamo(),
            new RobotDistanceLambdaTest.RobotDistanceFakes.FakeS3(),
            new RobotDistanceLambdaTest.RobotDistanceFakes.FailingSNS(),
            new RobotDistanceLambdaTest.RobotDistanceFakes.FakeSQS()).handleRequest(post, null)));

        assertFalse(lines.isEmpty());
        boolean sawFailure = false;
        for (String line : lines) {
            JsonNode event = MAPPER.readTree(line);
            for (String field : new String[] {"course", "component", "service", "event", "status"}) {
                assertTrue(event.hasNonNull(field), field + " missing in " + line);
            }
            sawFailure |= "FAILURE".equals(event.get("status").asText());
            for (Object coordinate : body.values()) {
                assertFalse(line.contains(coordinate.toString()), "coordinate leaked: " + line);
            }
            assertFalse(line.contains("SNS publish failed"), "exception message leaked: " + line);
        }
        assertTrue(sawFailure, "the failing run should log a FAILURE event");
    }
}
