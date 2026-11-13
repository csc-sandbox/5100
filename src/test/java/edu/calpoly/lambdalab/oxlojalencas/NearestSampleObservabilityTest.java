package edu.calpoly.lambdalab.oxlojalencas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * tests for the structured json logs.
 *
 * @author Jess A
 * @version November 10, 2026
 */
class NearestSampleObservabilityTest {

    private static final String ID = "c5f1b1b7-ccf5-4b98-80f9-f1123589a123";

    @Test
    void logsRequiredFieldsAndNothingElse() throws Exception {
        JsonNode event = capture(() -> NearestSampleObservability.log(
                ID, "abc-123", "SNS", "SNS_PUBLISHED", "SUCCESS"));

        assertEquals("CSC5100", event.get("course").asText());
        assertEquals("NearestSampleLambda", event.get("component").asText());
        assertEquals("SNS", event.get("service").asText());
        assertEquals("SNS_PUBLISHED", event.get("event").asText());
        assertEquals("SUCCESS", event.get("status").asText());
        assertEquals("abc-123", event.get("requestId").asText());
        assertEquals(ID, event.get("readingId").asText());

        // no payload, body or any other field can reach the log
        Set<String> names = new HashSet<>();
        event.fieldNames().forEachRemaining(names::add);
        assertEquals(Set.of("course", "component", "readingId", "requestId",
                "service", "event", "status"), names);
    }

    @Test
    void leavesOutOptionalFieldsWhenNotGiven() throws Exception {
        JsonNode event = capture(() -> NearestSampleObservability.log(
                null, null, "Lambda", "REQUEST_RECEIVED", "SUCCESS"));

        assertFalse(event.has("readingId"));
        assertFalse(event.has("requestId"));
        assertFalse(event.has("durationMs"));
        assertFalse(event.has("errorType"));
    }

    @Test
    void timedLogsNonNegativeDuration() throws Exception {
        JsonNode event = capture(() -> NearestSampleObservability.timed(
                ID, "S3", "S3_SAVED", () -> { }));

        assertEquals("SUCCESS", event.get("status").asText());
        assertTrue(event.get("durationMs").isNumber());
        assertTrue(event.get("durationMs").asDouble() >= 0);
    }

    @Test
    void timedLogsFailureTypeButNotMessageAndRethrows() throws Exception {
        IllegalStateException failure = new IllegalStateException("secret-token");

        JsonNode event = capture(() -> assertThrows(
                IllegalStateException.class,
                () -> NearestSampleObservability.timed(
                        ID, "SNS", "SNS_PUBLISHED", () -> {
                            throw failure;
                        })));

        assertEquals("FAILURE", event.get("status").asText());
        assertEquals("IllegalStateException", event.get("errorType").asText());
        assertFalse(event.toString().contains("secret-token"));
    }

    // runs the action and parses the one json line it prints
    private static JsonNode capture(Runnable action) throws Exception {
        PrintStream previous = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
            action.run();
        } finally {
            System.setOut(previous);
        }
        return new ObjectMapper().readTree(
                captured.toString(StandardCharsets.UTF_8).trim());
    }
}
