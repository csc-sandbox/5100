package edu.calpoly.lambdalab.adrian0427;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DominantAffectObservabilityTest {

    @Test
    void lambdaInvocationEmitsStructuredLog() throws Exception {
        PrintStream original = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();

        DominantAffectLambda lambda =
                new DominantAffectLambda((DominantAffectLambda.Storage) null);

        Map<String, Object> event = Map.of(
                "httpMethod", "POST",
                "body", "{\"stress\":0.2,\"engagement\":0.8,\"focus\":0.6}");

        Map<String, Object> response;

        try {
            System.setOut(new PrintStream(
                    captured, true, StandardCharsets.UTF_8));

            response = lambda.handleRequest(event, null);

        } finally {
            System.setOut(original);
        }

        assertEquals(200, response.get("statusCode"));

        String output = captured.toString(StandardCharsets.UTF_8).trim();
        var json = new ObjectMapper().readTree(output);

        assertEquals("CSC5100", json.get("course").asText());
        assertEquals("DominantAffectLambda", json.get("component").asText());
        assertEquals("Lambda", json.get("service").asText());
        assertEquals("POST_PROCESSED", json.get("event").asText());
        assertEquals("SUCCESS", json.get("status").asText());
        assertTrue(json.get("durationMs").asDouble() >= 0);

        assertFalse(output.contains("stress"));
        assertFalse(output.contains("engagement"));
        assertFalse(output.contains("focus"));
    }

    @Test
    void emitsStructuredJsonWithRequiredFields() throws Exception {
        PrintStream original = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();

        try {
            System.setOut(new PrintStream(
                    captured, true, StandardCharsets.UTF_8));

            DominantAffectObservability.log(
                    "request-123",
                    "DynamoDB",
                    "DYNAMODB_SAVED",
                    "SUCCESS",
                    System.nanoTime());

        } finally {
            System.setOut(original);
        }

        String output = captured.toString(StandardCharsets.UTF_8).trim();
        var json = new ObjectMapper().readTree(output);

        assertEquals("CSC5100", json.get("course").asText());
        assertEquals("DominantAffectLambda",
                json.get("component").asText());
        assertEquals("DynamoDB", json.get("service").asText());
        assertEquals("DYNAMODB_SAVED", json.get("event").asText());
        assertEquals("SUCCESS", json.get("status").asText());

        assertEquals("request-123", json.get("requestId").asText());
        assertTrue(json.get("durationMs").asDouble() >= 0);

        assertFalse(json.has("payload"));
        assertFalse(json.has("credentials"));
        assertFalse(json.has("secret"));
        assertFalse(output.contains("sensitive-test-value"));
    }
}
