package edu.calpoly.lambdalab.briggsmurphy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Verifies structured JSON logging for LidarClosestLambda.
 *
 * @author Briggs Murphy
 * @version 1.0
 */
class LidarClosestObservabilityTest {

  @Test
  void emitsQueryFriendlyJsonWithoutPayload() throws Exception {
    PrintStream previous = System.out;
    ByteArrayOutputStream captured = new ByteArrayOutputStream();

    try {
      System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));

      LidarClosestObservability.log(
          "lambda-request-1",
          "DynamoDB",
          "SAVE",
          "SUCCESS",
          System.nanoTime());

    } finally {
      System.setOut(previous);
    }

    var event = new ObjectMapper().readTree(
        captured.toString(StandardCharsets.UTF_8).trim());

    assertEquals("CSC5100", event.get("course").asText());
    assertEquals("LidarClosestLambda", event.get("component").asText());
    assertEquals("lambda-request-1", event.get("requestId").asText());
    assertEquals("DynamoDB", event.get("service").asText());
    assertEquals("SAVE", event.get("event").asText());
    assertEquals("SUCCESS", event.get("status").asText());
    assertTrue(event.has("durationMs"));
    assertTrue(event.get("durationMs").asDouble() >= 0);
    assertFalse(event.has("payload"));
  }
}