package edu.calpoly.lambdalab.ejiswkrosmew;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class LidarSafetyObservabilityTest {
    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void validLog() throws JsonProcessingException {
        PrintStream oldOut = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        String readingId = UUID.randomUUID().toString();
        try {
            System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
            LidarSafetyObservability.log(
                readingId,
                "lambda-request-1",
                "DynamoDB",
                "DYNAMODB_SAVED",
                "SUCCESS",
                System.nanoTime()
            );
        } finally {
            System.setOut(oldOut);
        }
        JsonNode entry = mapper.readTree(captured.toString(StandardCharsets.UTF_8).trim());
        assertEquals("CSC5100", entry.get("course").asText());
        assertEquals("LidarSafetyLambda", entry.get("component").asText());
        assertEquals("DynamoDB", entry.get("service").asText());
        assertEquals("DYNAMODB_SAVED", entry.get("event").asText());
        assertEquals("SUCCESS", entry.get("status").asText());
        assertEquals(readingId, entry.get("readingId").asText());
        assertEquals("lambda-request-1", entry.get("requestId").asText());
        assertTrue(entry.get("durationMs").asDouble() >= 0);
        assertFalse(entry.has("payload"));
    }
}
