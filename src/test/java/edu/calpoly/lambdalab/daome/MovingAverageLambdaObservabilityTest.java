package edu.calpoly.lambdalab.daome;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class MovingAverageLambdaObservabilityTest {

    @Test
    void emitsQueryJsonWithoutPayload() throws Exception {
        PrintStream previous = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();

        try {
            System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
            MovingAverageLambdaObservability.log("c5f1b1b7-ccf5-4b98-80f9-f1123589a123", "lambda-request-1", "S3",
                    "S3_SAVED", "SUCCESS", System.nanoTime());
        } finally {
            System.setOut(previous);
        }

        var event = new ObjectMapper().readTree(captured.toString());
        assertEquals("CSC5100", event.get("course").asText());
        assertEquals("S3", event.get("service").asText());
        assertEquals("S3_SAVED", event.get("event").asText());
        assertEquals("SUCCESS", event.get("status").asText());
        assertTrue(event.has("readingId"));
        assertTrue(event.has("requestId"));
        assertTrue(event.get("durationMs").asDouble() >= 0);
        assertFalse(event.has("payload"));
    }
}
